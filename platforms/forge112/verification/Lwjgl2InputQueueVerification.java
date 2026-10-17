package club.heiqi.uilib.host.forge112;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** 在隔离 JVM 中向目标 LWJGL2 的实际事件缓冲写入记录，执行生产的唯一消费者。 */
public final class Lwjgl2InputQueueVerification {
    private static void field(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void queues(long[] mouseTimes, long[] keyTimes, int[] keys, char[] characters) throws Exception {
        ByteBuffer mouse = ByteBuffer.allocate(mouseTimes.length * Mouse.EVENT_SIZE);
        for (int index = 0; index < mouseTimes.length; index++) {
            mouse.put((byte) 0).put((byte) (index % 2 == 0 ? 1 : 0));
            mouse.putInt(80 + index).putInt(120).putInt(0).putLong(mouseTimes[index]);
        }
        mouse.flip();
        ByteBuffer keyboard = ByteBuffer.allocate(keyTimes.length * Keyboard.EVENT_SIZE);
        for (int index = 0; index < keyTimes.length; index++) {
            keyboard.putInt(keys[index]).put((byte) 1).putInt(characters[index]).putLong(keyTimes[index]).put((byte) 0);
        }
        keyboard.flip();
        field(Mouse.class, "created", true);
        field(Mouse.class, "isGrabbed", false);
        field(Mouse.class, "clipMouseCoordinatesToWindow", false);
        field(Mouse.class, "readBuffer", mouse);
        field(Keyboard.class, "created", true);
        field(Keyboard.class, "readBuffer", keyboard);
        Keyboard.enableRepeatEvents(true);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("预期 " + expected + "，实际 " + actual);
    }

    public static void main(String[] arguments) throws Exception {
        try {
            List<String> trace = new ArrayList<>();
            StringBuilder fieldA = new StringBuilder(), fieldB = new StringBuilder();
            boolean[] focusB = {false};
            queues(new long[] {200, 300}, new long[] {100}, new int[] {Keyboard.KEY_X}, new char[] {'x'});
            Lwjgl2InputQueue.drain(() -> {
                trace.add("M" + Mouse.getEventNanoseconds());
                if (Mouse.getEventButtonState()) focusB[0] = true;
            }, () -> {
                trace.add("K" + Keyboard.getEventNanoseconds());
                (focusB[0] ? fieldB : fieldA).append(Keyboard.getEventCharacter());
            });
            equal(Arrays.asList("K100", "M200", "M300"), trace);
            equal("x", fieldA.toString());
            equal("", fieldB.toString());
            System.out.println("先输入后点击：" + trace + "；文字留在原字段");

            trace.clear();
            boolean[] closed = {false};
            int[] clicks = {0};
            queues(new long[] {220, 240}, new long[] {210}, new int[] {Keyboard.KEY_ESCAPE}, new char[] {0});
            Lwjgl2InputQueue.drain(() -> {
                trace.add("M" + Mouse.getEventNanoseconds());
                if (!closed[0] && Mouse.getEventButtonState()) clicks[0]++;
            }, () -> { trace.add("K" + Keyboard.getEventNanoseconds()); closed[0] = true; });
            equal(Arrays.asList("K210", "M220", "M240"), trace);
            equal(0, clicks[0]);
            System.out.println("先 ESC 后点击：" + trace + "；结束后不激活旧页");

            trace.clear();
            queues(new long[] {400, 400}, new long[] {400, 400},
                    new int[] {Keyboard.KEY_X, Keyboard.KEY_Y}, new char[] {'x', 'y'});
            Lwjgl2InputQueue.drain(() -> trace.add("M" + Mouse.getEventNanoseconds() + ":" + Mouse.getEventButtonState()),
                    () -> trace.add("K" + Keyboard.getEventNanoseconds() + ":" + Keyboard.getEventCharacter()));
            equal(Arrays.asList("K400:x", "K400:y", "M400:true", "M400:false"), trace);
            System.out.println("同时间戳固定先键盘并保持队列内部顺序：" + trace);

            trace.clear();
            queues(new long[] {500, 700}, new long[] {600, 800},
                    new int[] {Keyboard.KEY_X, Keyboard.KEY_Y}, new char[] {'x', 'y'});
            Runnable mouse = () -> trace.add("M" + Mouse.getEventNanoseconds());
            Runnable keyboard = () -> trace.add("K" + Keyboard.getEventNanoseconds());
            Lwjgl2InputQueue.drain(mouse, keyboard);
            equal(Arrays.asList("M500", "K600", "M700", "K800"), trace);
            if (Mouse.next() || Keyboard.next()) throw new AssertionError("批次尚未耗尽");
            Lwjgl2InputQueue.drain(mouse, keyboard);
            equal(Arrays.asList("M500", "K600", "M700", "K800"), trace);
            System.out.println("交错与耗尽：" + trace + "；第二次采集无重复，原生纳秒值未改写");
        } finally {
            field(Mouse.class, "created", false);
            field(Keyboard.class, "created", false);
        }
    }
}
