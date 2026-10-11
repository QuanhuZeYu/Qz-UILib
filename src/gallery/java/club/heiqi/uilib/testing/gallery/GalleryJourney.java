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
        void scroll(int x, int y, int delta);
    }
    public static void run(Driver driver) {
        driver.frame();
        require(text(driver,"探索场景"), "默认入口不是新工作台");
        require(!contains(driver,"核心闭环") && !contains(driver,"专项定位样本"), "旧页面仍在工作台中");
        go(driver, TestGallery.Page.SETTINGS);
        click(driver,"保存设置");
        require(text(driver,"已保存：工坊计划 · 12 批"), "新运行初始表单未保存");
        click(driver,"12"); driver.replaceText("0"); driver.frame();
        click(driver,"保存设置");
        require(text(driver,"批次必须是1至99的整数"), "表单未拒绝越界批次");
        click(driver,"0"); driver.replaceText("73"); driver.frame();
        click(driver,"场景概览");
        driver.replaceText("99"); driver.frame();
        click(driver,"打开知识库");
        go(driver,TestGallery.Page.SETTINGS);
        require(text(driver,"73"), "切页丢失草稿或失活表单收到输入");
        click(driver,"保存设置");
        require(text(driver,"已保存：工坊计划 · 73 批"), "表单保存未更新权威值");
        click(driver,"73"); driver.replaceText("22"); driver.frame();
        click(driver,"恢复已保存");
        require(text(driver,"73") && text(driver,"已恢复：工坊计划 · 73 批"), "恢复未取最近保存值");
        go(driver, TestGallery.Page.DETAILS);
        require(text(driver, "材料需求：2；产出：4"), "配方初始数量错误");
        click(driver, "制作批次：1");
        require(text(driver, "材料需求：6；产出：12"), "批次未同步材料产出");
        require(formula(driver, "2 \\times 3=6") && formula(driver, "4 \\times 3=12"), "配方文档公式未随批次更新");
        go(driver, TestGallery.Page.REPORT);
        require(text(driver, "生产汇总：投入 12；完成 9；待处理 3"), "报告今日数据错误");
        click(driver, "切换到本周");
        require(text(driver, "生产汇总：投入 84；完成 63；待处理 21"), "报告周期切换未更新汇总");
        require(formula(driver, "84=63+21") && formula(driver, "\\frac{63}{84}=75\\%"), "报告文档公式未随周期更新");
        go(driver, TestGallery.Page.DETAILS);
        require(text(driver, "材料需求：6；产出：12"), "配方切页未保留批次");
        go(driver, TestGallery.Page.MESSAGES);
        click(driver, "接收新消息");
        require(text(driver, "消息总数：2"), "消息未新增");
        click(driver, "展开消息 #2");
        require(contains(driver,"操作员：") || contains(driver,"Operator:"), "消息详情未展开");
        go(driver, TestGallery.Page.REPORT);
        go(driver, TestGallery.Page.MESSAGES);
        require(text(driver, "消息总数：2") && text(driver, "收起消息 #2"), "消息列表或展开状态未保留");
        for (TestGallery.Page id : TestGallery.Page.values()) if (id != TestGallery.Page.HOME) {
            go(driver, id);
            require(text(driver, id.title), "页面未切换：" + id);
        }
        go(driver, TestGallery.Page.ARTICLE);
        if (text(driver,"中文")) click(driver,"中文");
        click(driver,"English");
        require(contains(driver,"Workshop guide"), "文章语言切换未更新内容");
        click(driver,"中文");
        SceneGeometryView.Entry articleBefore = article(driver);
        int readingWidth = articleBefore.box.width;
        click(driver,"专注阅读");
        require(article(driver).box.width < readingWidth && article(driver).box.width <= 560, "正文未按阅读宽度重排");
        click(driver,"字号：16");
        click(driver,"N=2b");
        require(text(driver,"已激活链接：https://example.test/guide/checklist"), "文章链接未通过正式输入激活");
        require(text(driver,"字号：20") && text(driver,"舒展阅读"), "阅读设置未更新");
        require(article(driver).text.lines().stream().flatMap(line -> line.glyphs().stream()).anyMatch(g -> g.logicalEm == 20), "正文未应用阅读字号");
        long imageDeadline = System.nanoTime() + 5000000000L;
        while (!driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE) && System.nanoTime() < imageDeadline) driver.frame();
        require(driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE), "文章插图未进入正式绘制计划");
    }
    private static boolean formula(Driver driver, String source) {
        return driver.geometry().ordered().stream().anyMatch(e -> e.text != null && source.equals(e.text.originalText) && e.text.diagnostics().isEmpty());
    }
    private static SceneGeometryView.Entry article(Driver driver) {
        return driver.geometry().ordered().stream().filter(e -> e.text != null && e.text.originalText.startsWith("工坊使用说明")).findFirst().orElseThrow(() -> new AssertionError("文章正文未排版"));
    }
    private static boolean contains(Driver driver, String value) {
        return driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text.originalText.contains(value));
    }
    private static void go(Driver driver, TestGallery.Page page) {
        click(driver,"场景概览");
        click(driver,"打开" + page.title);
    }
    private static boolean text(Driver driver, String value) {
        return driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && value.equals(c.text.originalText));
    }
    private static void click(Driver driver, String value) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (driver.geometry().ordered().stream().anyMatch(e -> e.text != null && value.equals(e.text.originalText) && (e.clip == null ? e.box : e.box.intersect(e.clip)).height > 0)) break;
            club.heiqi.uilib.ui.scene.layout.LayoutBox viewport = driver.geometry().viewport();
            driver.scroll(viewport.width / 2, viewport.height / 2, -6); driver.frame();
        }
        SceneGeometryView.Entry entry = driver.geometry().ordered().stream().filter(e -> e.text != null && value.equals(e.text.originalText) && (e.clip == null ? e.box : e.box.intersect(e.clip)).width > 0 && (e.clip == null ? e.box : e.box.intersect(e.clip)).height > 0).findFirst().orElseThrow(() -> new AssertionError("找不到可见输入目标："+value));
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit = entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2); driver.frame();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
