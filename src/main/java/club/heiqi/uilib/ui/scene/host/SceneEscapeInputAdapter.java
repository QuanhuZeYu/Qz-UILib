package club.heiqi.uilib.ui.scene.host;

/** ESC复用单键按压段门，保留正式入口与既有关闭语义。 */
public final class SceneEscapeInputAdapter extends SceneKeyPressInputAdapter {
    public SceneEscapeInputAdapter(Runnable sink) { super(sink); }
}
