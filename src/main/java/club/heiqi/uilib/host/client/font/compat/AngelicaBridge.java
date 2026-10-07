package club.heiqi.uilib.host.client.font.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Objects;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** Angelica 2.2.10版本锁定接缝（源码61b5301f）；无MC wrapper、无旧font度量。
 * optional mixin marker必须实际安装；priority不是安装证明。跨线程/错ABI直接诊断。
 * ReplaySink负责Iris兼容producer与effective状态guard；不得以UIshader伪装shaderpack支持。 */
public final class AngelicaBridge implements OptionalFontHostBridge {
    private static AngelicaBridge active;
    private final Thread thread=Thread.currentThread();
    private final ReplaySink sink;private final FontHostOrderQueue queue;private final ClassLoader loader;
    private final Method seal,defer,recording,listMode,record,pause,resume;
    private final Field arena,depth,segments,deferred,segmentOwner,from,to;
    private final Class<?> commandType;
    private int flushDepth;private boolean closed,primed;
    private final IdentityHashMap<Object,Boolean> replayed=new IdentityHashMap<Object,Boolean>();
    AngelicaBridge(Class<?> font,ClassLoader loader,ReplaySink sink,int capacity) {
        this.sink=sink;this.loader=loader;queue=new FontHostOrderQueue(capacity);
        try{
            font.getMethod("uilibFontOrderHooksInstalled");
            seal=declared(font,"sealBatchSegment");defer=declared(font,"shouldDeferNow");arena=field(font,"arenaOwner");depth=field(font,"batchDepth");
            segments=field(font,"batchSegments");deferred=field(font,"deferredSegments");
            Class<?> segment=Class.forName(font.getName()+"$TextSegment",false,loader);segmentOwner=field(segment,"owner");from=field(segment,"cmdStart");to=field(segment,"cmdEnd");
            Class<?> lists=Class.forName("com.gtnewhorizons.angelica.glsm.DisplayListManager",false,loader);
            commandType=Class.forName("com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand",false,loader);
            Class<?> recorder=Class.forName("com.gtnewhorizons.angelica.glsm.recording.CommandRecorder",false,loader);
            recording=lists.getMethod("isRecording");listMode=lists.getMethod("getListMode");record=lists.getMethod("recordComplexCommand",commandType);
            pause=lists.getMethod("pauseRecording");resume=lists.getMethod("resumeRecording",recorder);
            // 精确辅助方法descriptor也提前检查，不等到第一次延期才发现失配。
            Class<?> cmds=java.lang.reflect.Array.newInstance(Class.forName(font.getName()+"$FontDrawCmd",false,loader),0).getClass();
            declared(font,"drawCommands",cmds,int.class,int.class,font);
            declared(font,"emitRangeThroughPipeline",font,cmds,int.class,int.class,int.class,float.class,float.class,float.class);
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("Angelica字体bridge ABI或order mixin未安装；不能安全开启替代",failure);}
        if(active!=null)throw new IllegalStateException("Angelica字体bridge只能有一个共享owner");active=this;
    }
    private static Method declared(Class<?> type,String name,Class<?>... args)throws ReflectiveOperationException{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object invoke(Method method,Object target,Object... args){try{return method.invoke(target,args);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Angelica接缝执行失败："+method,failure);}}
    private static Object read(Field field,Object owner){try{return field.get(owner);}catch(IllegalAccessException failure){throw new IllegalStateException("Angelica状态读取失败："+field,failure);}}
    private void check(){if(closed||Thread.currentThread()!=thread)throw new IllegalStateException("Angelica bridge必须在活动owner宿主线程使用");}
    @Override public void submit(PaintPlan plan,FontRenderContext context,AutoCloseable lease){
        Objects.requireNonNull(lease,"sourceLease");boolean transferred=false;
        try{check();Objects.requireNonNull(plan,"plan");Objects.requireNonNull(context,"context");
            if((Boolean)invoke(recording,null)){
                int mode=((Number)invoke(listMode,null)).intValue();if(mode!=0x1300&&mode!=0x1301)throw new IllegalStateException("未知显示列表录制模式");
                DisplayListFontCommand logical=new DisplayListFontCommand(this,plan,lease);
                invoke(record,null,logical.proxy(commandType));transferred=true;
                if(mode==0x1301)logical.execute();return;
            }
            Object owner=read(arena,null);
            if(!primed){for(Object segment:(List<?>)read(deferred,null))register(segment,read(segmentOwner,segment));if(owner!=null)for(Object segment:(List<?>)read(segments,owner))register(segment,owner);primed=true;}
            if(owner!=null)invoke(seal,owner);
            boolean queued=(Boolean)invoke(defer,null)||(owner!=null&&((Number)read(depth,owner)).intValue()>0)||!((List<?>)read(deferred,null)).isEmpty();
            HostProgramSnapshot program=HostProgramSnapshot.capture(loader);
            sink.registerVisible(plan,context);
            queue.submit(()->HostProgramSnapshot.replay(program,()->sink.draw(plan,context)),lease);transferred=true;
            if(!queued)queue.drainTail();
        }finally{if(!transferred)FontHostOrderQueue.release(lease);}
    }
    void executeList(PaintPlan plan){check();Object recorder=invoke(pause,null);
        try{FontRenderContext context=sink.capture();sink.registerVisible(plan,context);sink.draw(plan,context);}
        finally{invoke(resume,null,recorder);}
    }
    private void onSealed(Object owner){check();List<?> list=(List<?>)read(segments,owner);if(!list.isEmpty()){Object segment=list.get(list.size()-1);if(!queue.containsOld(segment)){replayed.remove(segment);register(segment,owner);}}}
    private void register(Object segment,Object owner){queue.sealed(segment);}
    private void beforeOld(Object owner,int start,int end){check();if(start==end)return;
        Object match=find((List<?>)read(segments,owner),owner,start,end);
        if(match==null)match=find((List<?>)read(deferred,null),owner,start,end);
        if(match!=null){
            // pipeline尝试返回false后会再走direct helper，同一segment的屏障只能消费一次。
            if(replayed.containsKey(match))return;
            if(!queue.containsOld(match))throw new IllegalStateException("旧font segment未被seal hook登记");queue.beforeOld(match);replayed.put(match,Boolean.TRUE);
        }
        else queue.beforeUnsealedTail();
    }
    private Object find(List<?> list,Object owner,int start,int end){for(Object segment:list)if(read(segmentOwner,segment)==owner&&((Number)read(from,segment)).intValue()==start&&((Number)read(to,segment)).intValue()==end)return segment;return null;}
    public static void sealed(Object owner){if(active!=null)active.onSealed(owner);}
    public static void beforeOldReplay(Object owner,int start,int end){if(active!=null)active.beforeOld(owner,start,end);}
    public static void beginFlush(){if(active!=null){active.check();active.flushDepth++;}}
    public static void endFlush(){if(active!=null){active.check();active.flushDepth--;if(active.flushDepth==0){active.queue.drainTail();active.replayed.clear();}}}
    /** 内部正常flush的discard在尾消费之前发生，不能取消尚未消费的新tail。 */
    public static void discarded(){if(active!=null&&active.flushDepth==0)active.abort();}
    public static void frameEnded(){if(active!=null)active.abort();}
    @Override public void abort(){check();flushDepth=0;replayed.clear();queue.close();}
    @Override public int pending(){check();return queue.pending();}
    @Override public void close(){if(!closed){check();try{queue.close();}finally{closed=true;if(active==this)active=null;}}}
}
