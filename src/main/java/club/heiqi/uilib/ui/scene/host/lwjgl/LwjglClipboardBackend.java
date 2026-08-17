package club.heiqi.uilib.ui.scene.host.lwjgl;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.scene.input.ClipboardBackend;

/**
 * 平台剪贴板后端实现 —— I4c 适配层。
 *
 * <h3>反射解析优先级（与 LwjglStateReader 同构）</h3>
 * <ol>
 *   <li>{@code org.lwjglx.input.Keyboard}（GTNH 对 LWJGL 的升级扩展实现，lwjgl3ify 环境）</li>
 *   <li>{@code org.lwjgl.input.Keyboard}（LWJGL2 原生）</li>
 *   <li>{@code net.minecraft.client.gui.GuiScreen}（MC 静态剪贴板入口，含 lwjgl3ify patch）</li>
 * </ol>
 * <p>逐级探测静态方法 {@code getClipboardString()} / {@code setClipboardString(String)}，
 * 全失败时静默降级：get 返回 null、set 忽略（I4c 叫停关口⑤，不抛异常打断输入链路）。
 * 类解析失败仅记 debug 日志，不向运行态传播。</p>
 *
 * <p>读写均为同步反射调用，在帧内快捷键路径（主线程）执行，无跨线程安全需求。</p>
 */
public class LwjglClipboardBackend implements ClipboardBackend {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/LwjglClipboardBackend");

    /** 探测目标全限定名（按优先级降序）。 */
    private static final String[] OWNERS = {
            "org.lwjglx.input.Keyboard",
            "org.lwjgl.input.Keyboard",
            "net.minecraft.client.gui.GuiScreen"
    };

    /** 解析成功的 getter，全失败为 null。 */
    private static final Method GETTER = resolveGetter();
    /** 解析成功的 setter，全失败为 null。 */
    private static final Method SETTER = resolveSetter();

    /** 逐级解析 getClipboardString()。 */
    private static Method resolveGetter() {
        for (String owner : OWNERS) {
            Method m = findMethod(owner, "getClipboardString");
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    /** 逐级解析 setClipboardString(String)。 */
    private static Method resolveSetter() {
        for (String owner : OWNERS) {
            Method m = findMethod(owner, "setClipboardString", String.class);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    /** 反射查找静态方法；类不存在/方法缺失/安全策略拒绝返回 null。 */
    private static Method findMethod(String owner, String name, Class<?>... parameterTypes) {
        try {
            Class<?> cls = Class.forName(owner);
            return cls.getMethod(name, parameterTypes);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            LOG.debug("UILib 剪贴板后端解析 {}#{} 失败，继续降级", owner, name);
            return null;
        }
    }

    @Override
    public String getClipboardText() {
        if (GETTER == null) {
            return null;
        }
        try {
            Object value = GETTER.invoke(null);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            LOG.debug("UILib 剪贴板读取失败，返回 null 降级", e);
            return null;
        }
    }

    @Override
    public void setClipboardText(String text) {
        if (SETTER == null || text == null) {
            return;
        }
        try {
            SETTER.invoke(null, text);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            LOG.debug("UILib 剪贴板写入失败，静默忽略", e);
        }
    }
}
