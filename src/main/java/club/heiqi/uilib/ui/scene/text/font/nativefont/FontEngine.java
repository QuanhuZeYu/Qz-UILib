package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 可跨线程共享的工厂；构造工厂不初始化native。session必须在实际使用线程创建。 */
public interface FontEngine {
    FontSession openSession();
}
