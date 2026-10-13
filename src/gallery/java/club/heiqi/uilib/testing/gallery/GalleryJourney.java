package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 操作与预期在共享编译单元，驱动只供给真实宿主输入和呈现事实，不操作控件回调。 */
public final class GalleryJourney {
    public interface Driver {
        void frame();

        SceneGeometryView geometry();

        PaintPlan paint();

        void click(int x, int y);

        void replaceText(String value);

        void scroll(int x, int y, int delta);

        void command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command command, boolean shift);

        void escape();

        void typeText(String value);

        club.heiqi.uilib.ui.scene.node.SceneNode focused();

        default void evidence(String name) {}
    }

    public static void run(Driver driver) {
        driver.frame();
        require(text(driver, "探索场景"), "默认入口不是新工作台");
        require(!contains(driver, "核心闭环") && !contains(driver, "专项定位样本"), "旧页面仍在工作台中");
        go(driver, TestGallery.Page.SETTINGS);
        click(driver, "保存设置");
        require(text(driver, "已保存：工坊计划 · 12 批"), "新运行初始表单未保存");
        click(driver, "12");
        driver.replaceText("0");
        driver.frame();
        click(driver, "保存设置");
        require(text(driver, "批次必须是1至99的整数"), "表单未拒绝越界批次");
        click(driver, "0");
        driver.replaceText("73");
        driver.frame();
        click(driver, "场景概览");
        driver.replaceText("99");
        driver.frame();
        click(driver, "打开知识库");
        go(driver, TestGallery.Page.SETTINGS);
        require(text(driver, "73"), "切页丢失草稿或失活表单收到输入");
        click(driver, "保存设置");
        require(text(driver, "已保存：工坊计划 · 73 批"), "表单保存未更新权威值");
        click(driver, "73");
        driver.replaceText("22");
        driver.frame();
        click(driver, "恢复已保存");
        require(text(driver, "73") && text(driver, "已恢复：工坊计划 · 73 批"), "恢复未取最近保存值");
        go(driver, TestGallery.Page.DETAILS);
        require(text(driver, "材料需求：2；产出：4"), "配方初始数量错误");
        click(driver, "制作批次：1");
        require(text(driver, "材料需求：6；产出：12"), "批次未同步材料产出");
        require(
                formula(driver, "2 \\times 3=6") && formula(driver, "4 \\times 3=12"),
                "配方文档公式未随批次更新");
        go(driver, TestGallery.Page.REPORT);
        require(text(driver, "生产汇总：投入 12；完成 9；待处理 3"), "报告今日数据错误");
        click(driver, "切换到本周");
        require(text(driver, "生产汇总：投入 84；完成 63；待处理 21"), "报告周期切换未更新汇总");
        require(
                formula(driver, "84=63+21") && formula(driver, "\\frac{63}{84}=75\\%"),
                "报告文档公式未随周期更新");
        go(driver, TestGallery.Page.DETAILS);
        require(text(driver, "材料需求：6；产出：12"), "配方切页未保留批次");
        go(driver, TestGallery.Page.MESSAGES);
        click(driver, "接收新消息");
        require(text(driver, "消息总数：2"), "消息未新增");
        click(driver, "展开消息 #2");
        require(contains(driver, "操作员：") || contains(driver, "Operator:"), "消息详情未展开");
        go(driver, TestGallery.Page.REPORT);
        go(driver, TestGallery.Page.MESSAGES);
        require(text(driver, "消息总数：2") && text(driver, "收起消息 #2"), "消息列表或展开状态未保留");
        for (TestGallery.Page id : TestGallery.Page.values())
            if (id != TestGallery.Page.HOME) {
                go(driver, id);
                require(text(driver, id.title), "页面未切换：" + id);
            }
        go(driver, TestGallery.Page.ARTICLE);
        if (text(driver, "中文")) click(driver, "中文");
        click(driver, "English");
        require(contains(driver, "Workshop guide"), "文章语言切换未更新内容");
        click(driver, "中文");
        SceneGeometryView.Entry articleBefore = article(driver);
        int readingWidth = articleBefore.box.width;
        click(driver, "专注阅读");
        require(
                article(driver).box.width < readingWidth && article(driver).box.width <= 560,
                "正文未按阅读宽度重排");
        click(driver, "字号：16");
        click(driver, "N=2b");
        require(text(driver, "已激活链接：https://example.test/guide/checklist"), "文章链接未通过正式输入激活");
        require(text(driver, "字号：20") && text(driver, "舒展阅读"), "阅读设置未更新");
        require(
                article(driver).text.lines().stream()
                        .flatMap(line -> line.glyphs().stream())
                        .anyMatch(g -> g.logicalEm == 20),
                "正文未应用阅读字号");
        long imageDeadline = System.nanoTime() + 5000000000L;
        while (!driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE)
                && System.nanoTime() < imageDeadline) driver.frame();
        require(
                driver.paint().commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE),
                "文章插图未进入正式绘制计划");
        aggregateReading(driver);
        featureCoverage(driver);
        go(driver, TestGallery.Page.ARTICLE);
    }

    private static void featureCoverage(Driver driver) {
        go(driver, TestGallery.Page.TEXT);
        require(contains(driver, "粗体") && contains(driver, "来源单位：UTF16"), "文本操作页没有正式DSL内容");
        click(driver, "输入方式：DSL");
        require(contains(driver, "来源单位：BYTE"), "UTF8模式未保留byte来源");
        click(driver, "输入方式：UTF-8 DSL");
        require(contains(driver, "粗斜体") && contains(driver, "typed完整样式"), "typed样式没有呈现");
        click(driver, "输入方式：typed完整样式");
        require(contains(driver, "<b>粗体"), "PLAIN解释了DSL");
        click(driver, "输入方式：PLAIN");
        click(driver, "文本样本：基础样式");
        require(contains(driver, "主题背景"), "高亮场景没有切换");
        reveal(driver, "主题背景");
        driver.evidence("gallery-text-styles");
        click(driver, "文本样本：高亮与嵌套");
        require(contains(driver, "CO") && contains(driver, "字号与上下标"), "脚本场景没有切换");
        click(driver, "文本样本：字号与上下标");
        require(contains(driver, "疏排") && contains(driver, "紧排"), "安全簇字距场景没有切换");
        click(driver, "文本样本：安全簇字距");
        require(contains(driver, "ภาษาไทย"), "复杂文字样例未进入生产字体");
        click(driver, "文本样本：Unicode与控制");
        require(contains(driver, "错配") && driver.geometry().ordered().stream()
                .anyMatch(e -> e.text != null && e.text.diagnostics().stream().anyMatch(d -> d.kind == club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic.Kind.INVALID_INPUT)), "非法DSL未整段降级并定位");
        click(driver, "预览字号：20");
        click(driver, "预览宽度：480");
        click(driver, "断行：正常");
        click(driver, "段落方向：AUTO");
        click(driver, "行高：真实度量");
        go(driver, TestGallery.Page.REPORT);
        go(driver, TestGallery.Page.TEXT);
        require(text(driver, "预览字号：32") && text(driver, "预览宽度：160"), "排版设置切页丢失");
        click(driver, "预览字号：32");
        click(driver, "预览宽度：160");
        click(driver, "预览宽度：48");
        click(driver, "断行：不软换行");
        click(driver, "断行：紧急换行");
        click(driver, "行高：显式5");
        click(driver, "文本样本：来源与非法内容");
        click(driver, "预览宽度：480");
        click(driver, "预览宽度：160");
        reveal(driver, "co\u00adoperate");
        require(driver.geometry().ordered().stream().filter(e -> e.text != null && e.text.logicalText.startsWith("co\u00adoperate"))
                .flatMap(e -> e.text.lines().stream()).flatMap(line -> line.clusters().stream())
                .anyMatch(c -> c.logicalStart == 2 && c.glyphIndexStart < c.glyphIndexEnd), "广场软连字符窄宽没有真实可见来源簇");
        driver.evidence("gallery-soft-hyphen");
        click(driver, "断行：正常");
        require(driver.geometry().ordered().stream().filter(e -> e.text != null && e.text.logicalText.startsWith("co\u00adoperate"))
                .flatMap(e -> e.text.lines().stream()).flatMap(line -> line.clusters().stream())
                .noneMatch(c -> c.logicalStart == 2 && c.glyphIndexStart < c.glyphIndexEnd), "广场NONE仍显示软连字符");
        click(driver, "文本样本：软连字符");
        click(driver, "输入方式：DSL");
        SceneGeometryView.Entry damaged = driver.geometry().ordered().stream()
                .filter(e -> e.text != null && e.text.logicalText.startsWith("A\ufffdB 后续有效文字"))
                .findFirst().orElseThrow(() -> new AssertionError("损坏UTF8没有正确替代或丢后续文字"));
        require(damaged.text.input.sourceUnit == club.heiqi.uilib.ui.scene.text.TextInput.SourceUnit.BYTE
                && damaged.text.diagnostics().stream().anyMatch(d -> d.kind == club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic.Kind.INVALID_INPUT && d.sourceStart == 1 && d.sourceEnd == 3)
                && damaged.text.diagnostics().stream().anyMatch(d -> d.kind == club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic.Kind.CONTROL_MARKER), "损坏UTF8/C0来源诊断丢失");
        interactions(driver);
        resourcesAndMedia(driver);
    }

    private static void interactions(Driver driver) {
        go(driver, TestGallery.Page.EDITING);
        String unicode = "工坊 Café e\u0301 · العربية 123 · עברית · 👩🏽‍💻 👨‍👩‍👧‍👦 🇨🇳";
        click(driver, unicode);
        driver.replaceText("交接 A e\u0301 👩‍💻"); driver.frame();
        driver.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.CONFIRM, false); driver.frame();
        require(contains(driver, "交接文字：交接 A"), "延迟交接没有保留原请求");
        driver.replaceText("新草稿 B"); driver.frame();
        click(driver, "接受这次交接");
        require(contains(driver, "当前草稿：新草稿 B") && contains(driver, "结果：已接受")
                && !contains(driver, "已接受值：交接 A"), "迟到接受覆盖了当前草稿或权威值");
        click(driver, "设为只读");
        driver.replaceText("只读不许修改"); driver.frame();
        require(contains(driver, "当前草稿：新草稿 B"), "只读允许改写草稿");
        click(driver, "恢复可编辑");
        click(driver, "收到外部更新");
        require(contains(driver, "活跃草稿冲突"), "外部更新未显示冲突选择");
        click(driver, "保留我的草稿");
        require(contains(driver, "当前草稿：新草稿 B"), "保留草稿改写了草稿");
        click(driver, "交接记录\n" + unicode + "\n下一班请核对材料。\n末尾空行保留：\n");
        driver.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DOCUMENT_HOME, false);
        driver.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DOCUMENT_END, true);
        driver.typeText("多行替换结果"); driver.frame();
        require(contains(driver, "当前草稿：多行替换结果") && !contains(driver, "当前草稿：交接记录"), "文档首尾导航选区未覆盖整个多行草稿");
        driver.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SUBMIT, false); driver.frame();
        require(contains(driver, "已接受值：多行替换结果"), "Ctrl+Enter没有完成多行交接");
        driver.evidence("gallery-editing");
        go(driver, TestGallery.Page.STRUCTURE);
        click(driver, "打开来源所属层");
        click(driver, "所属层独立草稿");
        driver.replaceText("正式广场所属草稿"); driver.frame();
        click(driver, "应用所属层草稿");
        require(driver.geometry().ordered().stream().anyMatch(e -> {
            if (!e.node.text().equals("应用所属层草稿")) return false;
            club.heiqi.uilib.ui.scene.node.SceneNode layer = driver.geometry().layerBoundary(e.node);
            if (layer == null) return false;
            SceneGeometryView.Entry panel = driver.geometry().get(layer.parent().parent());
            return panel != null && e.box.y + e.box.height / 2 >= panel.box.y + panel.box.height;
        }), "广场操作未来自面板外实际所属Layer");
        click(driver, "收起所属层");
        require(contains(driver, "所属层已应用：正式广场所属草稿"), "广场Layer未发布当前业务值");
        driver.evidence("gallery-owned-layer");
        click(driver, "展开甲订单");
        click(driver, "甲订单备注：" + unicode);
        driver.replaceText("甲订单重排草稿"); driver.frame();
        club.heiqi.uilib.ui.scene.node.SceneNode orderField = driver.focused();
        require(orderField != null, "订单备注没有有效编辑焦点");
        click(driver, "反转订单顺序");
        require(contains(driver, "收起甲订单") && contains(driver, "甲订单重排草稿")
                && driver.focused() == orderField, "keyed重排丢失展开、原草稿或编辑焦点");
        click(driver, "更新首项进度");
        click(driver, "删除甲订单");
        require(!contains(driver, "收起甲订单"), "删除订单仍然呈现旧行");
        click(driver, "恢复最近删除的订单");
        click(driver, "移到完成列");
        require(contains(driver, "交接卡位于：完成列"), "显式移动没有改变正式目的地");
        click(driver, "打开交接复核");
        click(driver, "打开内层确认");
        driver.escape(); driver.frame();
        require(contains(driver, "关闭外层复核") && !contains(driver, "关闭内层，返回外层"), "ESC未逐层关闭模态");
        driver.escape(); driver.frame();
        require(!contains(driver, "关闭外层复核"), "外层模态未关闭");
        String tip = "完整说明：订单重排保持原订单草稿；真正删除结束原行交互；只读可选择复制；禁用不提交；提示只是阅读辅助，不建立额外输入范围。";
        click(driver, tip);
        require(focusedText(driver, tip), "说明按钮点击未聚焦");
        require(textPaintCount(driver, tip) == 2, "悬停和焦点说明未绘制被动提示");
        click(driver, "移到待办列");
        require(focusedText(driver, "移到待办列"), "普通按钮未从说明按钮转移焦点");
        click(driver, "移到完成列");
        require(focusedText(driver, "移到完成列"), "同区第二按钮仍黏住前一个按钮焦点");
        click(driver, tip);
        click(driver, "完整说明提示");
        require(driver.focused() == null && textPaintCount(driver, tip) == 1,
                "焦点外非交互文字未清焦点和关闭旧提示");
        driver.frame();
        require(driver.focused() == null, "失焦后下一帧擅自补选焦点");
        driver.evidence("gallery-focus-blur");
        click(driver, "随卡移动的独立交接草稿");
        club.heiqi.uilib.ui.scene.node.SceneNode field = driver.focused();
        click(driver, "移到待办列");
        require(driver.focused() == field, "所属移动工具未保留实际编辑字段焦点");
        click(driver, "移到完成列");
        require(driver.focused() == field, "同范围编辑节点移动丢失原焦点");
        click(driver, "甲对象独立草稿");
        click(driver, "移到待办列");
        require(focusedText(driver, "移到待办列"), "另一区域工具错误保留了不属于它的字段焦点");
        driver.evidence("gallery-structure");
    }

    private static void resourcesAndMedia(Driver driver) {
        go(driver, TestGallery.Page.RESOURCES);
        require(contains(driver, "资料 A：第 1 次操作") && text(driver, "1,234.50"), "正式语言资料初始值或locale数值格式缺失");
        click(driver, "参数计数 +1");
        click(driver, "待加载资料：A → 切换 A/B（不重载）");
        require(contains(driver, "资料 A：第 2 次操作"), "未重载时标题提前换表");
        click(driver, "重载当前语言资料");
        require(contains(driver, "资料 B：第 2 次操作（同语言新内容）"), "同语言重载没有更新标题");
        click(driver, "切换中文 / English");
        require(contains(driver, "Resource B: operation 2") && contains(driver, "生效语言：en_US"), "正式语言切换没有更新资料");
        String languageBefore = textStarting(driver, "生效语言：");
        String titleBefore = textStarting(driver, "Resource B: operation");
        click(driver, "预置：读取 IOException 并重载");
        require(textStarting(driver, "语言结果：").contains("IOException")
                && languageBefore.equals(textStarting(driver, "生效语言："))
                && titleBefore.equals(textStarting(driver, "Resource B: operation")), "语言失败改变了已生效语言/标题或发布了新表");
        click(driver, "待加载资料：读取失败 → 切换 A/B（不重载）");
        click(driver, "重载当前语言资料");
        click(driver, "预置：有效嵌套对象 / 对象列表 / map / null");
        click(driver, "导入编辑区 YAML");
        require(contains(driver, "Limits { Integer jobs=8") && contains(driver, "值变化通知批次数：1"), "有效配置没有一次发布整批");
        click(driver, "预置：非法越界 → 无半批");
        click(driver, "导入编辑区 YAML");
        require(contains(driver, "workspace.limits.jobs") && contains(driver, "Limits { Integer jobs=8")
                && contains(driver, "值变化通知批次数：1"), "非法配置出现半批发布");
        click(driver, "仅改注释并导入已生效配置");
        require(contains(driver, "值变化通知批次数：1"), "注释导入通知了业务值");
        click(driver, "修改配置值：切换 enabled");
        click(driver, "导出已生效配置到下方（StringWriter）");
        require(contains(driver, "Boolean enabled=true") && followingText(driver, "导出 YAML（只读，可选择复制）").contains("用户：工作区说明"), "配置导出丢失有效值或未改节点注释");
        driver.evidence("gallery-resources");
        go(driver, TestGallery.Page.MEDIA);
        click(driver, "不存在的资源");
        waitFor(driver, "主图：图像状态：FAILED");
        click(driver, "运行时位图");
        waitFor(driver, "主图：图像状态：READY");
        click(driver, "选择 COVER");
        require(contains(driver, "已选 COVER"), "Fit选项没有更新");
        click(driver, "未知命令");
        require(driver.geometry().ordered().stream().filter(e -> e.node.control() instanceof club.heiqi.uilib.ui.scene.control.SceneMath)
                .anyMatch(e -> { club.heiqi.uilib.ui.scene.text.math.MathLayout layout = club.heiqi.uilib.ui.scene.control.SceneMath.layout(e.node);
                    return layout != null && layout.fallback && !layout.diagnostics.isEmpty()
                            && layout.sourceFallback.logicalText.contains("galleryUnknown"); }), "公式失败没有正式降级源文与数学诊断");
        click(driver, "复杂分式、根号与双脚本");
        require(formula(driver, "\\frac{1+\\frac{x_1^2}{\\sqrt[3]{y^2+1}}}{\\sqrt{1+\\frac{a_i}{b^{n+1}}}}"), "合法公式恢复后没有正式公式布局");
        click(driver, "选择 SCRIPT");
        require(contains(driver, "已选 SCRIPT"), "数学样式没有切换");
        driver.evidence("gallery-media");
    }

    private static void waitFor(Driver driver, String value) {
        long end = System.nanoTime() + 5000000000L;
        do { driver.frame(); if (contains(driver, value)) return; } while (System.nanoTime() < end);
        throw new AssertionError("没有观察到：" + value);
    }

    private static void aggregateReading(Driver driver) {
        // 从同一入口走源替换、语言、资源更新和局部滚动，验证能力组合而非只看目录文案。
        click(driver, "排产核对");
        require(contains(driver, "排产核对单"), "排产文档未替换正文");
        require(!contains(driver, "工坊使用说明"), "旧文章仍留在绘制计划");
        require(contains(driver, "计划数量"), "核对文档缺少共同表格");
        reveal(driver, "安全提示：");
        require(
                driver.geometry().ordered().stream()
                        .anyMatch(
                                e ->
                                        e.text != null
                                                && visible(e)
                                                && e.text.logicalText.contains(
                                                        "安全提示：温度 < 80°C & 保持通风。")),
                "HTML实体备注未进入共同正文");
        require(
                driver.geometry().ordered().stream()
                        .filter(e -> e.text != null)
                        .allMatch(
                                e ->
                                        e.text.diagnostics().stream()
                                                .noneMatch(
                                                        d ->
                                                                d.kind
                                                                                == club.heiqi.uilib
                                                                                        .ui.scene
                                                                                        .text
                                                                                        .TextLayout
                                                                                        .Diagnostic
                                                                                        .Kind
                                                                                        .INVALID_INPUT
                                                                        || d.kind
                                                                                == club.heiqi.uilib
                                                                                        .ui.scene
                                                                                        .text
                                                                                        .TextLayout
                                                                                        .Diagnostic
                                                                                        .Kind
                                                                                        .UNSUPPORTED)),
                "聚合核对文档存在排版诊断");
        horizontalCode(driver);
        // 切页后保留当前阅读材料及字号宽度；失活页面不再接触输入。
        go(driver, TestGallery.Page.REPORT);
        go(driver, TestGallery.Page.ARTICLE);
        require(contains(driver, "排产核对单") && text(driver, "字号：20"), "阅读材料或设置切页后丢失");
        click(driver, "English");
        require(contains(driver, "Production checklist"), "核对文档未随语言声明更新");
        click(driver, "中文");
        click(driver, "交接备忘");
        require(contains(driver, "下一位操作员需要知道"), "交接正文未随资料选择更新");
        require(
                driver.geometry().ordered().stream()
                        .filter(e -> e.text != null)
                        .allMatch(
                                e ->
                                        e.text.diagnostics().stream()
                                                .noneMatch(
                                                        d ->
                                                                d.kind
                                                                                == club.heiqi.uilib
                                                                                        .ui.scene
                                                                                        .text
                                                                                        .TextLayout
                                                                                        .Diagnostic
                                                                                        .Kind
                                                                                        .INVALID_INPUT
                                                                        || d.kind
                                                                                == club.heiqi.uilib
                                                                                        .ui.scene
                                                                                        .text
                                                                                        .TextLayout
                                                                                        .Diagnostic
                                                                                        .Kind
                                                                                        .UNSUPPORTED)),
                "交接内容存在排版诊断");
        click(driver, "工坊指南");
        require(contains(driver, "工坊使用说明"), "未恢复工坊指南");
        Object original = image(driver);
        click(driver, "切换插图");
        awaitImageChange(driver, original);
        Object alternate = image(driver);
        click(driver, "切换插图");
        awaitImageChange(driver, alternate);
        require(text(driver, "字号：20") && text(driver, "舒展阅读"), "聚合操作覆盖阅读设置");
    }

    private static Object image(Driver driver) {
        return driver.paint().commands().stream()
                .filter(c -> c.kind == PaintCommand.Kind.IMAGE)
                .map(c -> c.image.content)
                .findFirst()
                .orElseThrow(() -> new AssertionError("共享文章插图尚未完成"));
    }

    private static void awaitImageChange(Driver driver, Object previous) {
        long deadline = System.nanoTime() + 5000000000L;
        while (System.nanoTime() < deadline) {
            driver.frame();
            if (driver.paint().commands().stream()
                    .anyMatch(
                            c -> c.kind == PaintCommand.Kind.IMAGE && c.image.content != previous))
                return;
        }
        throw new AssertionError("插图source变化未更新正式绘制内容");
    }

    private static void horizontalCode(Driver driver) {
        reveal(driver, "production_order");
        SceneGeometryView.Entry code = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            code =
                    driver.geometry().ordered().stream()
                            .filter(
                                    e ->
                                            e.text != null
                                                    && e.text.logicalText.contains(
                                                            "production_order")
                                                    && visible(e))
                            .findFirst()
                            .orElse(null);
            if (code != null) break;
            club.heiqi.uilib.ui.scene.layout.LayoutBox view = driver.geometry().viewport();
            driver.scroll(view.width / 2, view.height / 2, -4);
            driver.frame();
        }
        require(code != null, "聚合文档长代码不可访问");
        club.heiqi.uilib.ui.scene.node.SceneNode node = code.node;
        int x = code.box.x;
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                code.clip == null ? code.box : code.box.intersect(code.clip);
        driver.scroll(hit.x + hit.width / 2, hit.y + hit.height / 2, -6);
        driver.frame();
        require(driver.geometry().get(node).box.x < x, "长代码未通过正式输入局部横滚");
        driver.scroll(hit.x + hit.width / 2, hit.y + hit.height / 2, 6);
        driver.frame();
        require(driver.geometry().get(node).box.x == x, "长代码横滚未恢复");
    }

    private static void reveal(Driver driver, String value) {
        for (int attempt = 0; attempt < 60; attempt++) {
            SceneGeometryView.Entry target =
                    driver.geometry().ordered().stream()
                            .filter(e -> e.text != null && e.text.logicalText.contains(value))
                            .findFirst()
                            .orElse(null);
            require(target != null, "正文中找不到：" + value);
            if (visible(target)) return;
            club.heiqi.uilib.ui.scene.layout.LayoutBox viewport = driver.geometry().viewport();
            int delta = target.box.y < (target.clip == null ? viewport.y : target.clip.y) ? 4 : -4;
            driver.scroll(viewport.width / 2, viewport.height / 2, delta);
            driver.frame();
        }
        throw new AssertionError("正文无法滚动到可见区域：" + value);
    }

    private static boolean visible(SceneGeometryView.Entry entry) {
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        return hit.width > 0 && hit.height > 0;
    }

    private static boolean formula(Driver driver, String source) {
        return driver.geometry().ordered().stream().anyMatch(e ->
                e.node.control() instanceof club.heiqi.uilib.ui.scene.control.SceneMath
                && e.text != null && source.equals(e.text.originalText)
                && club.heiqi.uilib.ui.scene.control.SceneMath.layout(e.node) != null
                && !club.heiqi.uilib.ui.scene.control.SceneMath.layout(e.node).fallback
                && club.heiqi.uilib.ui.scene.control.SceneMath.layout(e.node).diagnostics.isEmpty());
    }

    private static String textStarting(Driver driver, String prefix) {
        return driver.geometry().ordered().stream().filter(e -> e.text != null && e.text.logicalText.startsWith(prefix))
                .map(e -> e.text.logicalText).findFirst().orElseThrow(() -> new AssertionError("找不到输出：" + prefix));
    }

    private static String followingText(Driver driver, String label) {
        SceneGeometryView.Entry heading = driver.geometry().ordered().stream()
                .filter(e -> e.text != null && label.equals(e.text.originalText)).findFirst()
                .orElseThrow(() -> new AssertionError("找不到输出区标题：" + label));
        java.util.List<club.heiqi.uilib.ui.scene.node.SceneNode> siblings = heading.node.parent().children();
        SceneGeometryView.Entry output = driver.geometry().get(siblings.get(siblings.indexOf(heading.node) + 1));
        require(output != null && output.text != null, "输出区没有正式文本布局：" + label);
        return output.text.logicalText;
    }

    private static SceneGeometryView.Entry article(Driver driver) {
        return driver.geometry().ordered().stream()
                .filter(e -> e.text != null && e.text.logicalText.startsWith("一份生产任务"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("文章正文未排版"));
    }

    private static boolean contains(Driver driver, String value) {
        return driver.paint().commands().stream()
                .anyMatch(
                        c ->
                                c.kind == PaintCommand.Kind.TEXT
                                        && (c.text.originalText == null ? c.text.logicalText : c.text.originalText).contains(value));
    }

    private static void go(Driver driver, TestGallery.Page page) {
        click(driver, "场景概览");
        click(driver, "打开" + page.title);
    }

    private static boolean text(Driver driver, String value) {
        return driver.paint().commands().stream()
                .anyMatch(
                        c -> c.kind == PaintCommand.Kind.TEXT && value.equals(c.text.originalText));
    }

    private static boolean focusedText(Driver driver, String value) {
        return driver.geometry().ordered().stream().anyMatch(e -> e.node == driver.focused()
                && e.text != null && value.equals(e.text.originalText));
    }

    private static long textPaintCount(Driver driver, String value) {
        return driver.paint().commands().stream()
                .filter(c -> c.kind == PaintCommand.Kind.TEXT && value.equals(c.text.originalText)).count();
    }

    private static void click(Driver driver, String value) {
        for (int attempt = 0; attempt < 20; attempt++) {
            SceneGeometryView.Entry target =
                    driver.geometry().ordered().stream()
                            .filter(e -> e.text != null && value.equals(e.text.originalText))
                            .findFirst()
                            .orElse(null);
            if (target != null && visible(target)) break;
            club.heiqi.uilib.ui.scene.layout.LayoutBox viewport = driver.geometry().viewport();
            // 内容充实时目标可能在已滚动页面上方，仍走宿主正式滚轮恢复可见范围。
            int delta =
                    target != null
                                    && target.box.y
                                            < (target.clip == null ? viewport.y : target.clip.y)
                            ? 6
                            : -6;
            club.heiqi.uilib.ui.scene.node.SceneNode parent = target == null ? null : target.node.parent();
            SceneGeometryView.Entry scroller = null;
            while (parent != null) {
                SceneGeometryView.Entry candidate = driver.geometry().get(parent);
                if (parent.scrollable() && candidate != null && visible(candidate)
                        && (delta > 0 ? candidate.scrollOffset > 0 : candidate.scrollOffset < candidate.scrollMaximum)) {
                    scroller = candidate;
                    break;
                }
                // 内层到达端点时仍可能被外层clip挡住；旅程改向尚可移动的祖先递交真实wheel。
                // 沿现有滚动事实选择输入位置，不修改业务布局、offset或窗口来迁就验收。
                if (target != null && parent == driver.geometry().layerBoundary(target.node)) break;
                parent = parent.parent();
            }
            club.heiqi.uilib.ui.scene.layout.LayoutBox hit = scroller == null ? viewport
                    : scroller.clip == null ? scroller.box : scroller.box.intersect(scroller.clip);
            // 沿目标自己的滚动祖先边缘递交滚轮，避开中央嵌套滚动区域。
            driver.scroll(hit.x + 1, hit.y + hit.height / 2, delta);
            driver.frame();
        }
        SceneGeometryView.Entry entry =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.text != null
                                                && value.equals(e.text.originalText)
                                                && (e.clip == null
                                                                        ? e.box
                                                                        : e.box.intersect(e.clip))
                                                                .width
                                                        > 0
                                                && (e.clip == null
                                                                        ? e.box
                                                                        : e.box.intersect(e.clip))
                                                                .height
                                                        > 0)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("找不到可见输入目标：" + value));
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2);
        driver.frame();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
