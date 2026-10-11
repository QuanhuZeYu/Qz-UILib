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
        require(text(driver, "测试广场 · 场景目录"), "默认入口不是场景目录");
        go(driver, TestGallery.Page.CONTROLS);
        require(text(driver, "核心闭环：0"), "新运行不是独立初始状态");
        click(driver, "添加");
        require(text(driver, "核心闭环：1"), "添加未产生业务结果");
        click(driver, "12"); driver.replaceText("73"); driver.frame();
        // 导航不抢编辑焦点；首次卸载后立即送文字，检验路由器清除失活编辑联系。
        click(driver,"返回场景目录");
        driver.replaceText("99"); driver.frame();
        click(driver,TestGallery.Page.ARTICLE.title);
        go(driver, TestGallery.Page.CONTROLS);
        require(text(driver, "核心闭环：1") && text(driver, "73"), "切页丢失草稿或失活页收到输入");
        click(driver, "确认整数");
        require(contains(driver,"整数业务值：73"), "正式提交未更新业务值");
        click(driver, "模态");
        SceneGeometryView.Entry modal = driver.geometry().ordered().stream().filter(e -> e.node.modal()).findFirst().orElseThrow(() -> new AssertionError("模态未挂载"));
        require(modal.box.height > 0 && modal.box.height < driver.geometry().viewport().height / 2, "自然模态被拉伸成整片视口");
        click(driver, "关闭");
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
            require(text(driver, "测试广场 · " + id.title), "页面未切换：" + id);
        }
        go(driver, TestGallery.Page.ARTICLE);
        if (text(driver,"切换中文")) click(driver,"切换中文");
        click(driver,"Switch to English");
        require(contains(driver,"Workshop guide"), "文章语言切换未更新内容");
        click(driver,"切换中文");
        SceneGeometryView.Entry articleBefore = article(driver);
        int readingWidth = articleBefore.box.width;
        click(driver,"收窄阅读宽度");
        require(article(driver).box.width < readingWidth && article(driver).box.width <= 560, "正文未按阅读宽度重排");
        click(driver,"字号：18");
        click(driver,"N=2b");
        require(text(driver,"已激活链接：https://example.test/guide/checklist"), "文章链接未通过正式输入激活");
        require(text(driver,"字号：24") && text(driver,"展开阅读宽度"), "阅读设置未更新");
        require(article(driver).text.lines().stream().flatMap(line -> line.glyphs().stream()).anyMatch(g -> g.logicalEm == 24), "正文未应用阅读字号");
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
        click(driver,"返回场景目录");
        click(driver,page.title);
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
