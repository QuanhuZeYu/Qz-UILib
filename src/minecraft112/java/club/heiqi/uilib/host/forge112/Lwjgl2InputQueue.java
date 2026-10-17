package club.heiqi.uilib.host.forge112;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** 两个原生队列各保留一个当前事件，按采集时刻递交页面唯一序列。
 * 同时刻固定先键盘，保持各队列内部顺序；消费回调返回前不推进对应队列。 */
final class Lwjgl2InputQueue {
    private Lwjgl2InputQueue() {}

    static void drain(Runnable mouse, Runnable keyboard) {
        boolean hasMouse = Mouse.isCreated() && Mouse.next();
        boolean hasKeyboard = Keyboard.isCreated() && Keyboard.next();
        while (hasMouse || hasKeyboard) {
            if (hasKeyboard && (!hasMouse || Keyboard.getEventNanoseconds() <= Mouse.getEventNanoseconds())) {
                keyboard.run();
                hasKeyboard = Keyboard.next();
            } else {
                mouse.run();
                hasMouse = Mouse.next();
            }
        }
    }
}
