package club.heiqi.uilib.testing.gallery;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
/** 操作与预期在共享编译单元，驱动只供给真实宿主输入和呈现事实，不操作控件回调。 */
public final class GalleryJourney {
    public interface Driver {
        void frame();
        SceneGeometryView geometry();
        PaintPlan paint();
        void click(int x, int y);
        void replaceText(String value);
    }
    public static void run(Driver driver) {
        driver.frame();
        require(text(driver, "核心闭环：0"), "新运行不是独立初始状态");
        click(driver, "添加");
        require(text(driver, "核心闭环：1"), "添加未沿正式输入产生业务结果");
        click(driver, "12");
        driver.replaceText("73"); driver.frame();
        require(text(driver, "73"), "正式字段未收到文字");
        click(driver, "语言");
        require(text(driver, "测试广场 · 语言"), "导航未显示语言页");
        driver.replaceText("99"); driver.frame();
        click(driver, "控件");
        require(text(driver, "核心闭环：1") && text(driver, "73"), "切页丢失本次运行的状态或草稿");
        click(driver, "确认整数");
        require(driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text.originalText.startsWith("整数业务值：73")), "正式提交没有更新业务值");
        click(driver, "模态");
        SceneGeometryView.Entry modal = driver.geometry().ordered().stream().filter(e -> e.node.modal()).findFirst().orElseThrow(() -> new AssertionError("模态未挂载"));
        require(modal.box.height > 0 && modal.box.height < driver.geometry().viewport().height / 2, "自然模态被拉伸成整片视口");
        click(driver, "关闭");
        for (TestGallery.Page id : TestGallery.Page.values()) {
            click(driver, id.title);
            require(text(driver, "测试广场 · " + id.title), "页面未切换：" + id);
            require(driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.RECT), "缺少背景：" + id);
        }
        click(driver, "图像");
        require(driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE), "通用图像未进入正式绘制计划");
    }
    private static boolean text(Driver driver, String value) {
        return driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && value.equals(c.text.originalText));
    }
    private static void click(Driver driver, String value) {
        SceneGeometryView.Entry entry = driver.geometry().ordered().stream().filter(e -> e.text != null && value.equals(e.text.originalText) && (e.clip == null ? e.box : e.box.intersect(e.clip)).width > 0 && (e.clip == null ? e.box : e.box.intersect(e.clip)).height > 0).findFirst().orElseThrow(() -> new AssertionError("找不到可见输入目标："+value));
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit = entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2); driver.frame();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
