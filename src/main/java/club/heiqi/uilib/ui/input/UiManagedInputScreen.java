package club.heiqi.uilib.ui.input;

/**
 * 标记由 UILib 自身输入管线管理的屏幕。
 *
 * <p>原生文本输入探测器遇到该标记时不会再反射扫描 Minecraft 文本框，
 * 以免 input 包直接认识具体 screen 基类。</p>
 */
public interface UiManagedInputScreen {
}
