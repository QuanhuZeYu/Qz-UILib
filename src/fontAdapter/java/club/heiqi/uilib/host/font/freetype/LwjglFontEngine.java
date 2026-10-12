package club.heiqi.uilib.host.font.freetype;

import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 绑定工厂构造/加载不初始化native；openSession才在调用线程打开FT/HB作用域。 */
public final class LwjglFontEngine implements FontEngine {
    @Override
    public FontSession openSession() {
        return new NativeFontService();
    }
}
