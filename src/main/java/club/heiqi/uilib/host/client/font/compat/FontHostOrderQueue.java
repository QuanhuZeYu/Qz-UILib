package club.heiqi.uilib.host.client.font.compat;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Objects;

/** 新旧consumer共用单调顺序。旧segment身份来自实际对象，不以可复用的range当身份。
 * 只保logical ticket及source lease，任何atlas Frame/entry/UV必须在回放时短期获取。 */
public final class FontHostOrderQueue implements AutoCloseable {
    private final Thread thread=Thread.currentThread();
    private final int capacity;
    private long sequence;
    private final IdentityHashMap<Object,Long> old=new IdentityHashMap<Object,Long>();
    private final ArrayDeque<Ticket> tickets=new ArrayDeque<Ticket>();
    private boolean draining;
    private static final class Ticket {
        final long sequence;final Runnable replay;final AutoCloseable lease;
        Ticket(long sequence,Runnable replay,AutoCloseable lease){this.sequence=sequence;this.replay=replay;this.lease=lease;}
    }
    public FontHostOrderQueue(int capacity){if(capacity<1)throw new IllegalArgumentException("queue容量必须正数");this.capacity=capacity;}
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("font host queue只能宿主线程访问");}
    public void sealed(Object segment){check();if(old.containsKey(segment))return;old.put(Objects.requireNonNull(segment,"segment"),++sequence);}
    public void submit(Runnable replay,AutoCloseable lease){check();Objects.requireNonNull(replay,"replay");Objects.requireNonNull(lease,"lease");
        if(draining)throw new IllegalStateException("文字回放不能重新提交同队列");
        if(tickets.size()>=capacity){throw new IllegalStateException("font host延期需求超过有界queue，未强制TESR flush");}
        tickets.addLast(new Ticket(++sequence,replay,lease));
    }
    public void beforeOld(Object identity){check();Long order=old.remove(identity);if(order==null)throw new IllegalStateException("未登记的旧font segment");drain(order);}
    /** 未seal的旧tail在helper实际位置出现；它之前的新ticket应先提交。 */
    public void beforeUnsealedTail(){check();drain(++sequence);}
    public void drainTail(){check();drain(Long.MAX_VALUE);old.clear();}
    private void drain(long before){if(draining)throw new IllegalStateException("font host queue回放重入");draining=true;
        try{while(!tickets.isEmpty()&&tickets.peekFirst().sequence<before){Ticket ticket=tickets.removeFirst();try{ticket.replay.run();}finally{release(ticket.lease);}}}
        catch(RuntimeException|Error error){try{close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}throw error;}finally{draining=false;}
    }
    public int pending(){check();return tickets.size();}
    public boolean containsOld(Object identity){check();return old.containsKey(identity);}
    public static void release(AutoCloseable lease){try{lease.close();}catch(RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException("font source lease释放失败",error);}}
    @Override public void close(){check();RuntimeException failure=null;while(!tickets.isEmpty()){try{release(tickets.removeFirst().lease);}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}}old.clear();if(failure!=null)throw failure;}
}
