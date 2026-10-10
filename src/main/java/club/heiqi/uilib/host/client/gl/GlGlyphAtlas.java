package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;

/** GL上下文线程的稀疏、按需图集。mono/color 分页，数组的层数由预算指定。
 * Frame 在 prepare 收集整个范围的可见需求并 pin，freeze 后只读，不在 draw 中上传或淘汰。
 * UV 只属于当前 Frame；旧 PaintPlan 保存字体来源而非 UV，下次 prepare 可重建已淘汰资源。
 */
public final class GlGlyphAtlas implements GlQuadRenderer.ResourceProvider,AutoCloseable {
    public enum Mode { TEXTURE_2D, TEXTURE_ARRAY }
    public enum SamplingView { PREMULT_RGBA, STRAIGHT_RGBA }
    private static final class ViewKey {
        final GlyphKey glyph;final SamplingView view;
        ViewKey(GlyphKey glyph,SamplingView view){this.glyph=glyph;this.view=view;}
        @Override public int hashCode(){return 31*glyph.hashCode()+view.hashCode();}
        @Override public boolean equals(Object other){return other instanceof ViewKey&&glyph.equals(((ViewKey)other).glyph)&&view==((ViewKey)other).view;}
    }
    public static final class Budget {
        public final int pageSize,layers,maxGroups,maxResidents,maxDemand;
        public final long gpuBytes;
        public Budget(int pageSize,int layers,int maxGroups,int maxResidents,int maxDemand,long gpuBytes) {
            if(pageSize<4||layers<1||maxGroups<1||maxResidents<1||maxDemand<1||gpuBytes<4)
                throw new IllegalArgumentException("无效图集预算");
            this.pageSize=pageSize;this.layers=layers;this.maxGroups=maxGroups;
            this.maxResidents=maxResidents;this.maxDemand=maxDemand;this.gpuBytes=gpuBytes;
        }
    }
    /** 最小提交单位为含透明padding的RGBA整行：bytes须容纳4*(bitmap.width+2)。
     * 不累计不足一行的跨帧预算；bytes>=4*pageSize可覆盖所有可放入该页的字形。
     * nanos在驱动调用边界检查，无法抢占单次GL调用；任一预算为0时该帧不上传。 */
    public static final class UploadBudget {
        public final long bytes,nanos;public final int calls;
        public UploadBudget(long bytes,long nanos,int calls) {
            if(bytes<0||nanos<0||calls<0)throw new IllegalArgumentException("无效帧上传预算");
            this.bytes=bytes;this.nanos=nanos;this.calls=calls;
        }
    }
    public static final class Resident {
        public final long resource,generation;
        public final int layer,width,height;
        public final float u0,v0,u1,v1,bearingX,bearingY,pixelScale;
        public final boolean color;
        public final SamplingView samplingView;
        private final Group group;
        private Resident(Group group,int pageSize){this.group=group;samplingView=group.view;resource=group.resource;generation=group.generation;layer=0;width=1;height=1;bearingX=0;bearingY=0;pixelScale=1;color=false;u0=u1=1.5f/pageSize;v0=v1=1.5f/pageSize;}
        private Resident(Group group,int layer,int x,int y,int pageSize,GlyphBitmap b) {
            this.group=group;samplingView=group.view;resource=group.resource;generation=group.generation;this.layer=layer;
            width=b.width;height=b.height;bearingX=b.bearingX;bearingY=b.bearingY;pixelScale=b.pixelScale;color=b.color;
            u0=(x+1f)/pageSize;v0=(y+1f)/pageSize;u1=(x+1f+b.width)/pageSize;v1=(y+1f+b.height)/pageSize;
        }
    }
    public static final class Stats {
        public final long gpuBytes,uploadedBytes,uploadCalls,createdGroups,evictedGroups,rejected,version;
        public final int groups,residents;
        private Stats(GlGlyphAtlas a){gpuBytes=a.gpuBytes;uploadedBytes=a.uploadedBytes;uploadCalls=a.uploadCalls;
            createdGroups=a.createdGroups;evictedGroups=a.evictedGroups;rejected=a.rejected;version=a.version;
            groups=a.groups.size();residents=a.residents.size();}
    }
    private static final class Page {
        int x,y,rowHeight;
        boolean fits(int w,int h,int size){return w<=size&&h<=size&&(x+w<=size?y+h<=size:y+rowHeight+h<=size);}
        int[] allocate(int w,int h,int size){if(x+w>size){x=0;y+=rowHeight;rowHeight=0;}int[] p={x,y};x+=w;rowHeight=Math.max(rowHeight,h);return p;}
    }
    private static final class Group {
        final long resource,generation;final int texture,target;final boolean color;final Page[] pages;
        long lastUsed;boolean pinned,whiteReady;
        final SamplingView view;
        final List<ViewKey> keys=new ArrayList<ViewKey>();
        Group(long resource,long generation,int texture,boolean color,int layers,SamplingView view,int target){this.target=target;this.view=view;this.resource=resource;this.generation=generation;
            this.texture=texture;this.color=color;pages=new Page[layers];for(int i=0;i<layers;i++)pages[i]=new Page();}
    }
    private static final class Upload {
        final GlyphKey key;final GlyphBitmap bitmap;final Resident resident;final int x,y;int row;
        Upload(GlyphKey key,GlyphBitmap bitmap,Resident resident,int x,int y){this.key=key;this.bitmap=bitmap;this.resident=resident;this.x=x;this.y=y;}
    }
    private final Thread thread=Thread.currentThread();
    private final GlApi gl;private final ByteBuffer whiteScratch=ByteBuffer.allocateDirect(36).order(ByteOrder.nativeOrder());private ByteBuffer uploadScratch;
    private final Mode mode;private final Budget budget;private final int layers,target;
    private final LinkedHashMap<Long,Group> groups=new LinkedHashMap<Long,Group>();
    private final Map<ViewKey,Resident> residents=new HashMap<ViewKey,Resident>();
    private long gpuBytes,uploadedBytes,uploadCalls,createdGroups,evictedGroups,rejected,version,nextResource=1,nextGeneration=1,clock;
    private boolean closed;private Frame active;private Upload uploading;
    private final List<Frame> borrowedParents=new ArrayList<>();
    private final List<Upload> borrowedUploads=new ArrayList<>();
    /** 已flush的宿主委托可暂借图集；父Frame仍冻结并pin，nested consumer不能淘汰其resident或改变旧UV。 */
    public void borrowFrozenFrame(Runnable delegated){
        check();Frame parent=active;if(parent==null||!parent.frozen)throw new IllegalStateException("委托借用需要冻结的父Frame");
        if(!borrowedParents.isEmpty())throw new IllegalStateException("物品字体委托只允许一层借用");
        Upload pending=uploading;borrowedParents.add(parent);borrowedUploads.add(pending);active=null;uploading=null;
        try{delegated.run();}
        finally{boolean leaked=active!=null;try{if(leaked)active.close();}finally{borrowedParents.clear();borrowedUploads.clear();active=parent;uploading=pending;pinParents();}if(leaked)throw new IllegalStateException("委托字体consumer未关闭Frame");}
    }
    private void pinParents(){for(Upload upload:borrowedUploads)if(upload!=null)upload.resident.group.pinned=true;for(Frame parent:borrowedParents)parent.pin();if(active!=null)active.pin();if(uploading!=null)uploading.resident.group.pinned=true;}
    // 两种资源格式各保留一个partial；同原生bitmap对象，不新增CPUviewcache或长期Frame。
    private final java.util.EnumMap<SamplingView,Upload> partials=new java.util.EnumMap<SamplingView,Upload>(SamplingView.class);
    private UploadState preparingState;
    public GlGlyphAtlas(GlApi gl,Mode mode,Budget budget) {
        this.gl=Objects.requireNonNull(gl,"gl");
        this.mode=java.util.Objects.requireNonNull(mode,"mode");this.budget=java.util.Objects.requireNonNull(budget,"budget");
        layers=mode==Mode.TEXTURE_ARRAY?budget.layers:1;target=mode==Mode.TEXTURE_ARRAY?GL_TEXTURE_2D_ARRAY:GL_TEXTURE_2D;
        if(budget.pageSize>gl.glGetInteger(GL_MAX_TEXTURE_SIZE)||layers>gl.glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS)
            ||(long)budget.pageSize*budget.pageSize*layers*4>budget.gpuBytes)throw new IllegalArgumentException("图集配置超出GL能力/预算");
    }
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("图集仅限所属GL线程");if(closed)throw new IllegalStateException("图集已关闭");}
    public Stats stats(){check();return new Stats(this);}
    public Frame beginFrame(Collection<GlyphKey> demand) {return beginFrame(demand,false);}
    /** managed consumer保留此前consumer的分帧upload，epoch结束时才裁决过期。 */
    public Frame beginFrame(Collection<GlyphKey> demand,boolean preserveUpload) {return beginFrame(demand,preserveUpload,SamplingView.PREMULT_RGBA);}
    public Frame beginFrame(Collection<GlyphKey> demand,boolean preserveUpload,SamplingView view) {return beginFrame(demand,preserveUpload,view,false);}
    public Frame beginFrame(Collection<GlyphKey> demand,boolean preserveUpload,SamplingView view,boolean needsSolid) {
        return beginFrameLayers(java.util.Collections.singletonList(demand),preserveUpload,view,needsSolid);
    }
    /** 单Frame按调用方已裁决的资源优先层准备；只先pin主层，备用缓存不能阻挡主层新字形。
     * 所有层共享一次prepare预算和一次freeze，既有冻结父Frame/partial保护保持不变。
     */
    public Frame beginFrameLayers(List<? extends Collection<GlyphKey>> layers,boolean preserveUpload,SamplingView view,boolean needsSolid) {
        check();if(active!=null)throw new IllegalStateException("先结束上一个图集范围");
        Frame frame=new Frame(view,needsSolid);
        for(Collection<GlyphKey> layer:layers){
            List<GlyphKey> admitted=new ArrayList<>();
            for(GlyphKey key:layer){
                if(frame.demand.contains(key))continue;
                if(frame.demand.size()>=budget.maxDemand){rejected++;continue;}
                frame.demand.add(key);admitted.add(key);
            }
            if(!admitted.isEmpty())frame.priorityLayers.add(admitted);
        }
        if(!frame.priorityLayers.isEmpty())frame.pinLayer(frame.priorityLayers.get(0));
        uploading=partials.remove(view);
        // 本consumer的低优先partial已降到备用时，不允许它占据唯一upload槽阻挡主层。
        // managed preserveUpload及冻结借用父upload属于外部live lease，不为本计划优先级卸载；
        // 它们造成的压力只能保持未ready，不宣称跨consumer全域优先。CPUbitmap不随partial舍弃。
        if(uploading!=null&&!preserveUpload&&(frame.priorityLayers.isEmpty()||!frame.priorityLayers.get(0).contains(uploading.key)))uploading=null;
        if(uploading!=null)uploading.resident.group.pinned=true;
        active=frame;return frame;
    }
    /** 不持Frame/UV的epoch结束裁决，仅丢弃不再可见的未完成upload。 */
    public void retainUploadDemand(Collection<GlyphKey> demand) {
        check();if(active!=null)throw new IllegalStateException("先关闭Frame再结束epoch");
        if(uploading!=null&&!demand.contains(uploading.key))uploading=null;
        java.util.Iterator<Upload> pending=partials.values().iterator();while(pending.hasNext())if(!demand.contains(pending.next().key))pending.remove();
    }
    public final class Frame implements AutoCloseable {
        public final SamplingView view;
        private final boolean needsSolid;private Resident white;
        private Frame(SamplingView view,boolean needsSolid){this.view=view;this.needsSolid=needsSolid;}
        public Resident solid(){checkFrame();if(!frozen)throw new IllegalStateException("先freeze");return white;}
        private final LinkedHashSet<GlyphKey> demand=new LinkedHashSet<GlyphKey>();
        private final List<List<GlyphKey>> priorityLayers=new ArrayList<>();
        private void pinLayer(Collection<GlyphKey> layer){for(GlyphKey key:layer){Resident r=residents.get(new ViewKey(key,view));if(r!=null){r.group.pinned=true;r.group.lastUsed=++clock;}}}
        private final Map<GlyphKey,Resident> visible=new HashMap<GlyphKey,Resident>();
        private boolean frozen,done;
        public Collection<GlyphKey> demand(){return java.util.Collections.unmodifiableSet(demand);}
        public Resident lookup(GlyphKey key){checkFrame();return frozen?visible.get(key):residents.get(new ViewKey(key,view));}
        private void checkFrame(){check();if(done||active!=this)throw new IllegalStateException("图集范围已结束");}
        /** 时间预算只在驱动提交边界检查，无法中断单次 GL 调用。RGBA8 padded 区域分行上传。 */
        public void prepare(AsyncGlyphRasterizer cpu,UploadBudget uploadBudget) {prepare(cpu,uploadBudget,true);}
        /** 共享owner在presentation结束时统一retain；单consumer默认保持旧行为。 */
        public void prepare(AsyncGlyphRasterizer cpu,UploadBudget uploadBudget,boolean retainPending) {
            checkFrame();if(frozen)throw new IllegalStateException("绘制期间禁止上传");
            if(cpu!=null){if(retainPending)cpu.retainPending(demand);cpu.drainReady(budget.maxDemand);}
            else if(!demand.isEmpty())throw new IllegalArgumentException("非empty glyph需求须提供CPUworker");
            for(GlyphKey key:demand)if(!residents.containsKey(new ViewKey(key,view)))cpu.request(key);
            long start=System.nanoTime(),remaining=uploadBudget.bytes;int calls=0;
            try(UploadState state=new UploadState()) {
                preparingState=state;
                if(needsSolid&&view==SamplingView.STRAIGHT_RGBA){
                    Group whiteGroup=null;for(Group g:groups.values())if(g.view==view){whiteGroup=g;break;}
                    if(whiteGroup==null&&remaining>=36&&calls<uploadBudget.calls&&System.nanoTime()-start<uploadBudget.nanos)whiteGroup=createGroup(false,view);
                    if(whiteGroup!=null){whiteGroup.pinned=true;whiteGroup.lastUsed=++clock;
                        if(!whiteGroup.whiteReady&&remaining>=36&&calls<uploadBudget.calls&&System.nanoTime()-start<uploadBudget.nanos){
                            ByteBuffer whiteData=whiteScratch;whiteData.clear();try{for(int i=0;i<36;i++)whiteData.put((byte)255);whiteData.flip();gl.glBindTexture(whiteGroup.target,whiteGroup.texture);
                                gl.glTexSubImage2D(whiteGroup.target,0,0,0,3,3,GL_RGBA,GL_UNSIGNED_BYTE,whiteData);
                            }finally{}whiteGroup.whiteReady=true;remaining-=36;calls++;uploadedBytes+=36;uploadCalls++;
                        }
                        if(whiteGroup.whiteReady)white=new Resident(whiteGroup,budget.pageSize);
                    }
                }
                for(List<GlyphKey> layer:priorityLayers){
                    pinLayer(layer);
                    for(GlyphKey key:layer) {
                    if(calls>=uploadBudget.calls||remaining==0||System.nanoTime()-start>=uploadBudget.nanos)break;
                    if(residents.containsKey(new ViewKey(key,view)))continue;
                    if(uploading!=null&&(!uploading.key.equals(key)||uploading.resident.samplingView!=view))continue;
                    if(uploading==null) {
                        GlyphBitmap bitmap=cpu.peek(key);if(bitmap==null)continue;
                        uploading=allocate(key,bitmap,view);if(uploading==null)continue;
                    }
                    Upload job=uploading;int rowBytes=Math.multiplyExact(job.bitmap.width+2,4),totalRows=job.bitmap.height+2;
                    while(job.row<totalRows&&calls<uploadBudget.calls&&remaining>=rowBytes&&System.nanoTime()-start<uploadBudget.nanos) {
                        int rows=(int)Math.min(totalRows-job.row,remaining/rowBytes);
                        // 复用小块提交，单次 staging 不超过64KiB；宽行本身是最小预算单位。
                        rows=Math.min(rows,Math.max(1,65536/rowBytes));
                        ByteBuffer data=uploadScratch(Math.multiplyExact(rows,rowBytes));
                        try {
                            // MASK straight view的RGB恒白，含alpha0 gutter；避免linear过滤覆盖率时二次变暗。
                            // COLOR view的alpha0 RGB定义为0，保持明确RGBA格式而不偷偷改premult页。
                            if(view==SamplingView.STRAIGHT_RGBA&&!job.bitmap.color)for(int i=0;i<data.limit();i+=4){data.put(i,(byte)255);data.put(i+1,(byte)255);data.put(i+2,(byte)255);}
                            ByteBuffer source=job.bitmap.pixelsBuffer();
                            for(int r=0;r<rows;r++) {
                                int bitmapRow=job.row+r-1;if(bitmapRow<0||bitmapRow>=job.bitmap.height)continue;
                                source.position(bitmapRow*job.bitmap.width*4);source.limit((bitmapRow+1)*job.bitmap.width*4);
                                data.position(r*rowBytes+4);
                                if(view==SamplingView.PREMULT_RGBA)data.put(source);
                                else for(int x=0;x<job.bitmap.width;x++){int offset=bitmapRow*job.bitmap.width*4+x*4,a=source.get(offset+3)&255;
                                    for(int c=0;c<3;c++){int premult=source.get(offset+c)&255;data.put((byte)(!job.bitmap.color?255:a==0?0:Math.min(255,(premult*255+a/2)/a)));}data.put((byte)a);}
                                source.limit(source.capacity());
                            }
                            data.position(0);data.limit(rows*rowBytes);gl.glBindTexture(job.resident.group.target,job.resident.group.texture);
                            if(job.resident.group.target==GL_TEXTURE_2D_ARRAY)gl.glTexSubImage3D(job.resident.group.target,0,job.x,job.y+job.row,job.resident.layer,
                                job.bitmap.width+2,rows,1,GL_RGBA,GL_UNSIGNED_BYTE,data);
                            else gl.glTexSubImage2D(job.resident.group.target,0,job.x,job.y+job.row,job.bitmap.width+2,rows,GL_RGBA,GL_UNSIGNED_BYTE,data);
                        }finally{}
                        long bytes=(long)rows*rowBytes;remaining-=bytes;uploadedBytes+=bytes;uploadCalls++;calls++;job.row+=rows;
                    }
                    if(job.row==totalRows) {
                        ViewKey residentKey=new ViewKey(key,view);residents.put(residentKey,job.resident);job.resident.group.keys.add(residentKey);job.resident.group.lastUsed=++clock;
                        uploading=null;version++;
                    }else break;
                    }
                }
            }finally{preparingState=null;}
        }
        public void freeze() {
            checkFrame();if(frozen)return;
            for(GlyphKey key:demand){Resident r=residents.get(new ViewKey(key,view));if(r!=null){visible.put(key,r);r.group.pinned=true;}}
            frozen=true;
        }
        private void pin(){for(Resident resident:visible.values())resident.group.pinned=true;if(white!=null)white.group.pinned=true;}
        @Override public void close(){if(done)return;checkFrame();done=true;if(uploading!=null)partials.put(view,uploading);uploading=null;for(Group g:groups.values())g.pinned=false;active=null;pinParents();}
    }
    private int residentSlots(){java.util.Set<Upload> pending=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Upload,Boolean>());pending.addAll(partials.values());if(uploading!=null)pending.add(uploading);for(Upload upload:borrowedUploads)if(upload!=null)pending.add(upload);return residents.size()+pending.size();}
    private Upload allocate(GlyphKey key,GlyphBitmap bitmap,SamplingView view) {
        int w=bitmap.width+2,h=bitmap.height+2;
        if(w>budget.pageSize||h>budget.pageSize){rejected++;return null;}
        while(residentSlots()>=budget.maxResidents)if(!evict()){rejected++;return null;}
        for(Group g:groups.values())if(g.color==bitmap.color&&g.view==view)for(int layer=0;layer<g.pages.length;layer++)if(g.pages[layer].fits(w,h,budget.pageSize))
            return place(key,bitmap,g,layer,w,h);
        Group newGroup=createGroup(bitmap.color,view);return newGroup==null?null:place(key,bitmap,newGroup,0,w,h);
    }
    private Group createGroup(boolean color,SamplingView view){
        // source/fontkey仍唯一；资源view决定实际sampler target，premultarray与straight2D共用同总预算。
        int groupTarget=view==SamplingView.STRAIGHT_RGBA?GL_TEXTURE_2D:target,groupLayers=view==SamplingView.STRAIGHT_RGBA?1:layers;
        long size=(long)budget.pageSize*budget.pageSize*groupLayers*4;
        while(groups.size()>=budget.maxGroups||gpuBytes+size>budget.gpuBytes)if(!evict()){rejected++;return null;}
        int texture=gl.glGenTextures();
        try {
            gl.glBindTexture(groupTarget,texture);gl.glTexParameteri(groupTarget,GL_TEXTURE_MIN_FILTER,GL_LINEAR);gl.glTexParameteri(groupTarget,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            gl.glTexParameteri(groupTarget,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);gl.glTexParameteri(groupTarget,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(groupTarget,GL_TEXTURE_BASE_LEVEL,0);gl.glTexParameteri(groupTarget,GL_TEXTURE_MAX_LEVEL,0);
            if(groupTarget==GL_TEXTURE_2D_ARRAY)gl.glTexImage3D(groupTarget,0,GL_RGBA8,budget.pageSize,budget.pageSize,groupLayers,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            else gl.glTexImage2D(groupTarget,0,GL_RGBA8,budget.pageSize,budget.pageSize,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            Group group=new Group(nextResource++,nextGeneration++,texture,color,groupLayers,view,groupTarget);groups.put(group.resource,group);gpuBytes+=size;createdGroups++;
            if(view==SamplingView.STRAIGHT_RGBA)group.pages[0].allocate(3,3,budget.pageSize);
            return group;
        }catch(RuntimeException|Error failure){gl.glDeleteTextures(texture);throw failure;}
    }
    private Upload place(GlyphKey key,GlyphBitmap bitmap,Group group,int layer,int w,int h) {
        int[] pos=group.pages[layer].allocate(w,h,budget.pageSize);group.pinned=true;group.lastUsed=++clock;
        return new Upload(key,bitmap,new Resident(group,layer,pos[0],pos[1],budget.pageSize,bitmap),pos[0],pos[1]);
    }
    private boolean hasPartial(Group group){for(Upload pending:borrowedUploads)if(pending!=null&&pending.resident.group==group)return true;if(uploading!=null&&uploading.resident.group==group)return true;for(Upload pending:partials.values())if(pending.resident.group==group)return true;return false;}
    private boolean evict() {
        // glDeleteTextures 会解除所有单元上的绑定。仅在真实淘汰压力下查询各单元，
        // 不删除宿主仍绑定的资源；预算不足留白，不能破坏上传进入态或其他单元。
        java.util.HashSet<Integer> bound=new java.util.HashSet<Integer>();
        if(preparingState!=null){bound.add(preparingState.two);bound.add(preparingState.array);}
        int activeUnit=gl.glGetInteger(GL_ACTIVE_TEXTURE),units=gl.glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        try{for(int i=0;i<units;i++){gl.glActiveTexture(GL_TEXTURE0+i);bound.add(gl.glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY));bound.add(gl.glGetInteger(GL_TEXTURE_BINDING_2D));}}
        finally{gl.glActiveTexture(activeUnit);}
        Group candidate=null;for(Group group:groups.values())if(!group.pinned&&!bound.contains(group.texture)&&!hasPartial(group)
            &&(candidate==null||group.lastUsed<candidate.lastUsed))candidate=group;
        if(candidate==null)return false;
        for(ViewKey key:candidate.keys)residents.remove(key);
        groups.remove(candidate.resource);gl.glDeleteTextures(candidate.texture);gpuBytes-=(long)budget.pageSize*budget.pageSize*candidate.pages.length*4;evictedGroups++;return true;
    }
    /** 只读取已 freeze 的 pin 范围，不上传、不创建、不推测资源代际。 */
    /** 单atlas可复用scratch，最大为64KiB或一行；每上传清零padding而不分配native行buffer。 */
    private ByteBuffer uploadScratch(int bytes){if(uploadScratch==null||uploadScratch.capacity()<bytes)uploadScratch=ByteBuffer.allocateDirect(Math.max(bytes,65536)).order(ByteOrder.nativeOrder());uploadScratch.clear();uploadScratch.limit(bytes);for(int i=0;i<bytes;i++)uploadScratch.put(i,(byte)0);return uploadScratch;}
    @Override public GlQuadRenderer.Texture resolve(long resource,long generation) {
        check();if(active==null||!active.frozen)throw new IllegalStateException("先冻结图集范围");
        Group group=groups.get(resource);if(group==null||!group.pinned||group.generation!=generation)throw new IllegalArgumentException("失效图集资源");
        return new GlQuadRenderer.Texture(group.target,group.texture);
    }
    /** 上传触及的状态集中保存/恢复；host 的 unpack PBO 与各布局参数不能渗入 CPU 指针。 */
    private final class UploadState implements AutoCloseable {
        final int unit=gl.glGetInteger(GL_ACTIVE_TEXTURE),pbo=gl.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] names={GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_IMAGES,GL_UNPACK_SWAP_BYTES,GL_UNPACK_LSB_FIRST};
        final int[] values=new int[names.length];final int two,array;
        UploadState() {
            for(int i=0;i<names.length;i++)values[i]=gl.glGetInteger(names[i]);
            gl.glActiveTexture(GL_TEXTURE0);two=gl.glGetInteger(GL_TEXTURE_BINDING_2D);array=gl.glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
            gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);for(int name:names)gl.glPixelStorei(name,name==GL_UNPACK_ALIGNMENT?1:0);
        }
        @Override public void close(){gl.glBindTexture(GL_TEXTURE_2D,two);gl.glBindTexture(GL_TEXTURE_2D_ARRAY,array);gl.glActiveTexture(unit);
            gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);for(int i=0;i<names.length;i++)gl.glPixelStorei(names[i],values[i]);}
    }
    @Override public void close(){if(closed)return;check();if(active!=null)throw new IllegalStateException("先结束图集范围");
        for(Group group:groups.values())gl.glDeleteTextures(group.texture);groups.clear();residents.clear();uploading=null;partials.clear();gpuBytes=0;closed=true;}
}
