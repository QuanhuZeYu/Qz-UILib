package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 独立共享图像纹理owner，不借glyph atlas。prepare上传，Frame固定内容与texture直到回放结束。
 * negative resource空间与glyph atlas分开；实际内容identity永不复用，reload不会将旧命令解释为新图。
 */
public final class GlImageResources implements GlQuadRenderer.ResourceProvider,AutoCloseable {
    private final Thread thread=Thread.currentThread();private final GlApi gl;
    private final int maxTextures;private final long maxBytes;
    private final LinkedHashMap<Long,Resident> cache=new LinkedHashMap<>(16,.75f,true);
    public interface Listener { void presentation(long identity,boolean pending,String failure);
        default void failure(long identity,String stage,boolean unsupported,String diagnostic){presentation(identity,false,diagnostic);}
    }
    private final Listener listener;private final long uploadBytes;private final int uploadCalls;
    public interface HostItems { boolean supports(ImageContent content);void render(club.heiqi.uilib.ui.scene.image.ImagePlacement image,club.heiqi.uilib.ui.scene.layout.LayoutBox clip,int width,int height); }
    private HostItems hostItems;
    public void hostItems(HostItems adapter){check();if(active!=null)throw new IllegalStateException("先关闭图像Frame");hostItems=Objects.requireNonNull(adapter,"adapter");}
    public boolean renderHost(club.heiqi.uilib.ui.scene.image.ImagePlacement image,club.heiqi.uilib.ui.scene.layout.LayoutBox clip,int width,int height){check();try{hostItems.render(image,clip,width,height);return true;}catch(RuntimeException failure){active.failed.add(image.content.identity);listener.failure(image.content.identity,"item-render",false,failure.toString());return false;}}
    private long bytes;private Frame active;private boolean closed;
    private static final class Resident {final ImageContent content;final int texture;Resident(ImageContent content,int texture){this.content=content;this.texture=texture;}}
    public GlImageResources(GlApi gl,int maxTextures,long maxBytes){this(gl,maxTextures,maxBytes,16L*1024*1024,4,(identity,pending,failure)->{});}
    public GlImageResources(GlApi gl,int maxTextures,long maxBytes,long uploadBytes,int uploadCalls,Listener listener){if(uploadBytes<4||uploadCalls<1)throw new IllegalArgumentException("无效上传预算");this.uploadBytes=uploadBytes;this.uploadCalls=uploadCalls;this.listener=Objects.requireNonNull(listener,"listener");if(maxTextures<1||maxBytes<4)throw new IllegalArgumentException("无效图像GPU预算");this.gl=Objects.requireNonNull(gl,"gl");this.maxTextures=maxTextures;this.maxBytes=maxBytes;}
    private void check(){if(Thread.currentThread()!=thread||closed)throw new IllegalStateException("图像纹理owner已关闭或不在GL线程");}
    public Frame prepare(PaintPlan plan){check();if(active!=null)throw new IllegalStateException("先关闭图像Frame");Frame frame=new Frame();active=frame;
        try{long uploaded=0;int calls=0;for(PaintCommand command:plan.commands())if(command.kind==PaintCommand.Kind.IMAGE){ImageContent content=command.image.content;Resident resident=cache.get(content.identity);if(resident!=null){frame.residents.put(content.identity,resident);listener.presentation(content.identity,false,null);}}
            for(PaintCommand command:plan.commands())if(command.kind==PaintCommand.Kind.IMAGE&&!frame.residents.containsKey(command.image.content.identity)){ImageContent content=command.image.content;
                if(frame.attempted.contains(content.identity))continue;frame.attempted.add(content.identity);
                try{
                if(content.kind==ImageContent.Kind.HOST_ITEM){if(hostItems==null||!hostItems.supports(content))throw new UnsupportedOperationException("缺少宿主物品绘制适配");frame.host.add(content.identity);continue;}
                if(content.bytes()>uploadBytes)throw new IllegalArgumentException("单张图像超过上传字节预算");
                if(content.width>gl.glGetInteger(GL_MAX_TEXTURE_SIZE)||content.height>gl.glGetInteger(GL_MAX_TEXTURE_SIZE)||content.bytes()>maxBytes)throw new IllegalArgumentException("图像超出GL尺寸/字节预算: "+content.identity);
                if(calls>=uploadCalls||uploaded+content.bytes()>uploadBytes){listener.presentation(content.identity,true,null);continue;}
                while(cache.size()>=maxTextures||bytes+content.bytes()>maxBytes){boolean evicted=false;Iterator<Resident> it=cache.values().iterator();while(it.hasNext()){Resident r=it.next();if(!frame.residents.containsKey(r.content.identity)&&!bound(r.texture)){gl.glDeleteTextures(r.texture);bytes-=r.content.bytes();it.remove();evicted=true;break;}}if(!evicted)throw new IllegalStateException("图像GPU预算被当前Frame/宿主绑定占满");}
                Resident resident=upload(content);cache.put(content.identity,resident);bytes+=content.bytes();frame.residents.put(content.identity,resident);uploaded+=content.bytes();calls++;listener.presentation(content.identity,false,null);
                }catch(RuntimeException failure){frame.failed.add(content.identity);listener.failure(content.identity,content.kind==ImageContent.Kind.HOST_ITEM?"item-adapter":"upload",failure instanceof UnsupportedOperationException,failure.toString());}
            }return frame;
        }catch(RuntimeException|Error failure){frame.close();throw failure;}
    }
    private boolean bound(int texture){int unit=gl.glGetInteger(GL_ACTIVE_TEXTURE),units=gl.glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);try{for(int i=0;i<units;i++){gl.glActiveTexture(GL_TEXTURE0+i);if(gl.glGetInteger(GL_TEXTURE_BINDING_2D)==texture)return true;}return false;}finally{gl.glActiveTexture(unit);}}
    private Resident upload(ImageContent content){int unit=gl.glGetInteger(GL_ACTIVE_TEXTURE),pbo=gl.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);int[] names={GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_IMAGES,GL_UNPACK_SWAP_BYTES,GL_UNPACK_LSB_FIRST};int[] values=new int[names.length];for(int i=0;i<names.length;i++)values[i]=gl.glGetInteger(names[i]);gl.glActiveTexture(GL_TEXTURE0);int old=gl.glGetInteger(GL_TEXTURE_BINDING_2D),texture=gl.glGenTextures();
        try{gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);for(int name:names)gl.glPixelStorei(name,name==GL_UNPACK_ALIGNMENT?1:0);gl.glBindTexture(GL_TEXTURE_2D,texture);gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            byte[] pixels=content.pixels();for(int i=0;i<pixels.length;i+=4){int alpha=pixels[i+3]&255;for(int c=0;c<3;c++)pixels[i+c]=(byte)(((pixels[i+c]&255)*alpha+127)/255);}ByteBuffer data=ByteBuffer.allocateDirect(pixels.length);data.put(pixels).flip();gl.glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,content.width,content.height,0,GL_RGBA,GL_UNSIGNED_BYTE,data);return new Resident(content,texture);
        }catch(RuntimeException|Error failure){gl.glDeleteTextures(texture);throw failure;}
        finally{gl.glBindTexture(GL_TEXTURE_2D,old);gl.glActiveTexture(unit);gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);for(int i=0;i<names.length;i++)gl.glPixelStorei(names[i],values[i]);}
    }
    public final class Frame implements AutoCloseable {private final Map<Long,Resident> residents=new HashMap<>();private final Set<Long> attempted=new HashSet<>(),failed=new HashSet<>(),host=new HashSet<>();public boolean ready(long identity){return residents.containsKey(identity)||host.contains(identity)&&!failed.contains(identity);}public boolean failed(long identity){return failed.contains(identity);}private boolean done;@Override public void close(){if(done)return;check();done=true;residents.clear();active=null;}}
    @Override public GlQuadRenderer.Texture resolve(long resource,long generation){check();if(active==null)throw new IllegalStateException("图像Frame未准备");Resident r=active.residents.get(-resource);if(r==null||generation!=r.content.identity)throw new IllegalArgumentException("失效图像资源");return new GlQuadRenderer.Texture(GL_TEXTURE_2D,r.texture);}
    public long retainedBytes(){check();return bytes;}
    @Override public void close(){if(closed)return;check();if(active!=null)throw new IllegalStateException("先结束图像Frame");for(Resident resident:cache.values())gl.glDeleteTextures(resident.texture);cache.clear();bytes=0;closed=true;}
}
