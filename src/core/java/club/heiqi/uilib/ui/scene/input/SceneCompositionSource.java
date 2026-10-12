package club.heiqi.uilib.ui.scene.input;

/** 宿主为一次真实组字来源创建的身份；更新、提交与取消必须保留同一实例。
 * 不承载焦点或文本；不能用当前焦点为迟到提交补造新来源。 */
public final class SceneCompositionSource {
    private boolean begun;

    /** Core 在 BEGIN 路由时一次性取得联系，跨页面和重复 BEGIN 均不能复活旧来源。 */
    public synchronized boolean beginOnce() {
        if (begun) return false;
        begun = true;
        return true;
    }
}
