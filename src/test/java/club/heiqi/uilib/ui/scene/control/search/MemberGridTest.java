package club.heiqi.uilib.ui.scene.control.search;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link MemberGrid} 单元测试（G13 网格族液态玻璃迁移 + 行为回归）。
 *
 * <p><b>外观口径</b>：网格底座 = {@code SceneScrollContainer} 工厂 viewport，六项表面由主题
 * GROUP 配方独占、恰好一条 BACKDROP；单元格零表面写入（无底色/边框/圆角/滤镜、零 BACKDROP、
 * elevation 保持 -1）；主文本 {@code foreground}、副文本 {@code mutedForeground}、徽章文字
 * duplicate 取 {@code warningText}；无效徽章底与图标占位底为图像/状态徽标协议静态值，不随主题
 * 重染。主题切换只重派生：节点身份不变、effect 数不增长；卸载回收全部绑定。</p>
 *
 * <p><b>行为回归</b>：keyed 行/单元复用（数据更新不重建节点、数据收缩回收订阅、列数重排）、
 * 重绑不串态（同 id 节点值换代后徽章/图像/文字全部复位，不残留上一代状态）、编辑/删除回调
 * 携带 memberId 且受控零回写、禁用阻断回调、滚动回夹、Props/常量公共契约冻结。</p>
 */
public class MemberGridTest {

    /** 画布宽。 */
    private static final int CANVAS_WIDTH = 400;
    /** 画布高。 */
    private static final int CANVAS_HEIGHT = 300;
    /** 固定高宿主（滚动视口高度确定的必要父链）。 */
    private static final int WRAPPER_HEIGHT = 200;
    /** 默认单元宽（与 MemberGrid.DEFAULT_CELL_WIDTH 同值，1 列口径）。 */
    private static final int CELL_W = MemberGrid.DEFAULT_CELL_WIDTH;
    /** 默认单元高。 */
    private static final int CELL_H = MemberGrid.DEFAULT_CELL_HEIGHT;
    /** 默认列间距。 */
    private static final int GAP_X = MemberGrid.DEFAULT_GAP_X;
    /** 默认行间距。 */
    private static final int GAP_Y = MemberGrid.DEFAULT_GAP_Y;
    /** 透明底（图像协议静态值断言用）。 */
    private static final int BG_TRANSPARENT = 0x00000000;

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private Signal<List<SearchPickerData.CurrentMember>> membersSignal;
    private Signal<Boolean> enabledSignal;
    private Signal<ScenePickerPanelNav.MemberIssues> issuesSignal;
    private final List<Long> edited = new ArrayList<>();
    private final List<Long> removed = new ArrayList<>();
    private final Map<String, SceneImageSource> imagesByCandidateKey =
            new HashMap<String, SceneImageSource>();

    private MemberGrid.Result result;
    private MountHandle handle;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        ItemRenderTierRegistry.resetForTests();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
        ItemRenderTierRegistry.resetForTests();
    }

    // ==================== 夹具 ====================

    private MemberGrid.Props gridProps(int cellWidth, int cellHeight, int gapX, int gapY) {
        return new MemberGrid.Props(membersSignal, enabledSignal,
                SearchPickerPresentation.defaultEnglish(), visualAdapter(), issuesSignal,
                edited::add, id -> {
                    removed.add(id);
                    return Boolean.TRUE;
                },
                cellWidth, cellHeight, gapX, gapY);
    }

    /** 默认工厂路径挂载：不建局部主题作用域，解析回落 runtime/库默认。 */
    private void mount(List<SearchPickerData.CurrentMember> initial) {
        mountInTheme(null, initial, CELL_W, CELL_H, GAP_X, GAP_Y);
    }

    /**
     * 挂载网格。
     *
     * @param pageTheme 局部主题信号；null = 默认工厂路径（回落库默认），非 null = withTheme 作用域
     */
    private void mountInTheme(Signal<SceneTheme> pageTheme,
                              List<SearchPickerData.CurrentMember> initial,
                              int cellWidth, int cellHeight, int gapX, int gapY) {
        membersSignal = Signal.create(initial);
        enabledSignal = Signal.create(Boolean.TRUE);
        issuesSignal = Signal.create(issuesOf(initial));
        final MemberGrid.Props props = gridProps(cellWidth, cellHeight, gapX, gapY);
        final MemberGrid.Result[] holder = new MemberGrid.Result[1];
        handle = rt.mount(sceneRoot, () -> {
            SceneNode wrapper = new SceneNode();
            wrapper.setPreferredHeight(WRAPPER_HEIGHT);
            if (pageTheme == null) {
                holder[0] = MemberGrid.create(rt, props);
            } else {
                SceneThemes.withTheme(pageTheme, () -> holder[0] = MemberGrid.create(rt, props));
            }
            wrapper.appendChild(holder[0].root());
            return wrapper;
        });
        result = holder[0];
        rt.flush();
        layoutAndBridge();
    }

    /** 受控换代：成员与问题统计同步更新（与生产接线一致，issues 由同一列表分析得出）。 */
    private void updateMembers(List<SearchPickerData.CurrentMember> next) {
        membersSignal.set(next);
        issuesSignal.set(issuesOf(next));
        rt.flush();
        layoutAndBridge();
    }

    private void layoutAndBridge() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 布局引擎按指定画布宽重排（自动列数用例）。 */
    private void layoutWithWidth(int width) {
        layoutEngine.layout(sceneRoot, new Constraints(width, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** issues 快照：analyzeMemberIssues 为 control 包内实现，跨包测试经反射复用生产算法。 */
    private static ScenePickerPanelNav.MemberIssues issuesOf(List<SearchPickerData.CurrentMember> members) {
        try {
            Method method = ScenePickerPanelNav.class
                    .getDeclaredMethod("analyzeMemberIssues", List.class);
            method.setAccessible(true);
            return (ScenePickerPanelNav.MemberIssues) method.invoke(null, members);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            @Override
            public SceneImageSource candidateImage(SearchPickerData.Candidate candidate) {
                return imagesByCandidateKey.get(candidate.key());
            }

            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    private static SceneImageSource image(final String registryKey) {
        return new SceneImageSource() {
            @Override
            public String registryKey() {
                return registryKey;
            }
        };
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label,
                Collections.<SearchPickerData.Variant>emptyList());
    }

    /** 正常枚举成员（ALL 选择，主文本 = candidate.label）。 */
    private static SearchPickerData.CurrentMember member(long id, String key, String label) {
        return new SearchPickerData.CurrentMember(id,
                new SearchPickerData.Selection(key, (String) null), candidate(key, label), true);
    }

    /** 正常枚举成员（默认标签）。 */
    private static SearchPickerData.CurrentMember member(long id, String key) {
        return member(id, key, key);
    }

    /** malformed 成员（selection=null → 无效徽章）。 */
    private static SearchPickerData.CurrentMember malformed(long id) {
        return new SearchPickerData.CurrentMember(id, null, null, false);
    }

    // ==================== 结构访问 ====================

    private SceneNode viewport() {
        return result.viewport();
    }

    private SceneNode rowsContainer() {
        return viewport().__getChildren().get(0);
    }

    private SceneNode row(int rowIndex) {
        return rowsContainer().__getChildren().get(rowIndex);
    }

    private SceneNode cell(int rowIndex, int colIndex) {
        return row(rowIndex).__getChildren().get(colIndex);
    }

    private SceneNode icon(SceneNode cell) {
        return cell.__getChildren().get(0).__getChildren().get(0);
    }

    private SceneNode primary(SceneNode cell) {
        return cell.__getChildren().get(0).__getChildren().get(1);
    }

    private SceneNode badge(SceneNode cell) {
        return cell.__getChildren().get(0).__getChildren().get(2);
    }

    private SceneNode secondary(SceneNode cell) {
        return cell.__getChildren().get(1);
    }

    private SceneNode editButton(SceneNode cell) {
        return cell.__getChildren().get(2).__getChildren().get(0);
    }

    private SceneNode removeButton(SceneNode cell) {
        return cell.__getChildren().get(2).__getChildren().get(1);
    }

    private SceneNode buttonLabel(SceneNode button) {
        return button.__getChildren().get(0);
    }

    // ==================== 绘制观察 ====================

    /** 整场景重绘（fill cachedPaint），供按节点统计命令。 */
    private void repaint() {
        paintEngine.paint(sceneRoot);
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数（调用前须 repaint）；无 fragment 视为 0。 */
    private static int backdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return 0;
        }
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 整树 BACKDROP 命令数（自带重绘）。 */
    private int countType() {
        int count = 0;
        for (PaintCommand command : paintEngine.paint(sceneRoot).getPlan().getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 输入注入 ====================

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[] {box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
    }

    private void routeScrollAt(SceneNode node, int wheelDelta) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, center[0], center[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    // ==================== ① 容器 GROUP 配方逐项 + 单元格零滤镜 ====================

    /**
     * 网格底座（scroll viewport）= 主题 GROUP 配方逐项（background/border/borderWidth/
     * cornerRadius/backdrop/surfaceElevation），恰好一条 BACKDROP；行与单元格零表面写入、
     * 零滤镜（每颗表面只采样一次）。
     */
    @Test
    public void containerBaseUsesGroupRecipeAndCellsCarryNoSurface() {
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());
        mount(Arrays.asList(member(1L, "test:stone"), malformed(2L)));

        SceneNode viewport = viewport();
        Assert.assertEquals("底座染色 = GROUP 配方 idle tint",
                group.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角 = GROUP 配方", group.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座边框宽 = GROUP 配方", group.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("底座缘色 = GROUP 配方 idle edge",
                group.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座实体高度 = GROUP 配方 idle elevation",
                group.getIdle().getElevation(), viewport.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", viewport.getBackdrop());
        Assert.assertEquals("底座滤镜材质 = 配方",
                group.getBackdrop().getEffect().getMaterial(), viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("底座滤镜模糊半径 = 配方",
                group.getBackdrop().getBlurRadius(), viewport.getBackdrop().getBlurRadius());

        repaint();
        int cells = 0;
        for (int r = 0; r < rowsContainer().__getChildren().size(); r++) {
            SceneNode row = row(r);
            Assert.assertNull("行[" + r + "] 不装滤镜", row.getBackdrop());
            Assert.assertEquals("行[" + r + "] 无底色", 0, row.getBackgroundColor());
            Assert.assertEquals("行[" + r + "] 不写圆角", 0, row.getCornerRadius());
            for (int c = 0; c < row.__getChildren().size(); c++) {
                SceneNode cell = row.__getChildren().get(c);
                cells++;
                Assert.assertNull("单元 不装滤镜", cell.getBackdrop());
                Assert.assertEquals("单元 零表面写入：无底色", 0, cell.getBackgroundColor());
                Assert.assertEquals("单元 零表面写入：不写圆角", 0, cell.getCornerRadius());
                Assert.assertEquals("单元 零表面写入：不写边框宽", 0, cell.getBorderWidth());
                Assert.assertEquals("单元未绑定表面，elevation 保持 -1", -1.0F,
                        cell.__getSurfaceElevation(), 0.0001F);
                Assert.assertNull("图标不装滤镜", icon(cell).getBackdrop());
                Assert.assertEquals("图标自身零 BACKDROP", 0, backdropCount(icon(cell)));
            }
        }
        Assert.assertEquals("前置：2 行 × 1 列", 2, cells);

        repaint();
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(viewport));
        for (int r = 0; r < 2; r++) {
            Assert.assertEquals("行[" + r + "] 自身零 BACKDROP", 0, backdropCount(row(r)));
        }
        Assert.assertEquals("整树 BACKDROP = 底座 1 + 每单元 2 按钮各 1（每颗表面只采样一次）",
                1 + cells * 2, countType());
    }

    /** 空成员：无行无单元，底座 GROUP 表面仍在（整树仅底座 1 条 BACKDROP）。 */
    @Test
    public void emptyMembersRenderNoRowsWithBaseStillThemed() {
        mount(Collections.<SearchPickerData.CurrentMember>emptyList());
        Assert.assertEquals("空成员 0 行", 0, rowsContainer().__getChildren().size());
        repaint();
        Assert.assertEquals("底座滤镜保留", 1, backdropCount(viewport()));
        Assert.assertEquals("整树仅底座一条 BACKDROP", 1, countType());
    }

    // ==================== ② 三态底色口径（单元格无状态写入、禁用走按钮配方档） ====================

    /**
     * 底色口径：成员卡片没有选中/悬停交互态（cell/row hitTestable=false 让点击穿透到卡内按钮），
     * 单元格任何数据状态下底色恒透明；禁用态由卡内按钮配方 disabled 档表达（背景取禁用 tint、
     * 文字不重染），单元格自身不受影响。
     */
    @Test
    public void cellsStayTransparentAndDisabledStateLivesOnButtons() {
        SceneSurfaceStyle button = SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);
        SceneTheme dark = SceneThemes.DEFAULT;
        mount(Arrays.asList(member(1L, "test:stone"), malformed(2L)));
        SceneNode cellA = cell(0, 0);
        Assert.assertEquals("前置：单元格 idle 透明", 0, cellA.getBackgroundColor());

        enabledSignal.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("禁用后单元格仍零底色写入", 0, cellA.getBackgroundColor());
        Assert.assertEquals("禁用按钮背景 = 配方 disabled tint",
                button.getDisabled().getTint(), editButton(cellA).getBackgroundColor());
        Assert.assertEquals("禁用不重染文字：按钮文字仍取配方前景",
                button.getForeground().intValue(), buttonLabel(editButton(cellA)).getTextColor());
        Assert.assertEquals("徽章文字仍取主题正文前景",
                dark.foreground(), badge(cellA).getTextColor());

        enabledSignal.set(Boolean.TRUE);
        rt.flush();
        Assert.assertEquals("恢复后按钮回 idle tint",
                button.getIdle().getTint(), editButton(cellA).getBackgroundColor());
        Assert.assertEquals("恢复后单元格仍透明", 0, cellA.getBackgroundColor());
    }

    // ==================== 文字语义前景 + 徽章状态口径 ====================

    /**
     * 主文本 = foreground、副文本 = mutedForeground、徽章 duplicate 文字 = warningText、
     * 无效徽章底 = 静态语义底（状态徽标不迁移）。默认档语义色与旧 chrome 常量同值（正文
     * 0xFFE6E1E5、次要 0xFFCAC4D0、警告 0xFFFBBF24），默认观感不回退。
     */
    @Test
    public void cellTextsUseThemeSemanticForegrounds() {
        SearchPickerPresentation presentation = SearchPickerPresentation.defaultEnglish();
        mount(Arrays.asList(
                member(1L, "test:stone"),
                malformed(2L),
                member(3L, "test:dup"),
                member(4L, "test:dup")));

        SceneTheme dark = SceneThemes.DEFAULT;
        SceneNode normal = cell(0, 0);
        SceneNode invalid = cell(1, 0);
        SceneNode dupA = cell(2, 0);
        SceneNode dupB = cell(3, 0);

        Assert.assertEquals("主文本取正文前景", dark.foreground(), primary(normal).getTextColor());
        Assert.assertEquals("主文本内容 = candidate 标签", "test:stone", primary(normal).getText());
        Assert.assertEquals("副文本取次要前景", dark.mutedForeground(), secondary(normal).getTextColor());
        Assert.assertEquals("无效徽章文字仍取正文前景", dark.foreground(), badge(invalid).getTextColor());
        Assert.assertEquals("无效徽章文案", presentation.invalidMemberBadge(), badge(invalid).getText());
        Assert.assertEquals("无效徽章底 = 静态 danger-subtle（不随主题）",
                0x22EF4444, badge(invalid).getBackgroundColor());
        Assert.assertEquals("正常单元徽章无底", BG_TRANSPARENT, badge(normal).getBackgroundColor());
        Assert.assertEquals("重复徽章文字取 warningText", dark.warningText(), badge(dupA).getTextColor());
        // G19/P-02 收编钉：duplicate 分支取色经 SceneThemes.warningText 公共入口（逐位等值）。
        Assert.assertEquals("重复徽章文字 = 公共 warningText 入口现值",
                SceneThemes.warningText(rt).get().intValue(), badge(dupA).getTextColor());
        Assert.assertEquals("重复徽章文案", presentation.duplicateMemberBadge(), badge(dupA).getText());
        Assert.assertEquals("重复徽章不借无效底", BG_TRANSPARENT, badge(dupA).getBackgroundColor());
        Assert.assertEquals("第二个重复成员同样着色", dark.warningText(), badge(dupB).getTextColor());
    }

    // ==================== ⑥ 图像渲染协议不改色 ====================

    /**
     * 物品图像/缩略图协议不消费主题：有图单元图标透明底 + 原图片源、无图单元静态占位底色；
     * 渲染分级 UNRENDERABLE 回退占位样式；主题切换前后图标底色与图片源逐字节不变、零滤镜。
     */
    @Test
    public void imageProtocolKeepsColorAcrossThemeSwitch() {
        SceneImageSource okImage = image("test:ok:0");
        SceneImageSource brokenImage = image("test:broken:0");
        imagesByCandidateKey.put("test:ok", okImage);
        imagesByCandidateKey.put("test:broken", brokenImage);
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountInTheme(pageTheme, Arrays.asList(
                member(1L, "test:ok"), member(2L, "test:broken"), malformed(3L)),
                CELL_W, CELL_H, GAP_X, GAP_Y);

        SceneNode okIcon = icon(cell(0, 0));
        SceneNode brokenIcon = icon(cell(1, 0));
        SceneNode noCandidateIcon = icon(cell(2, 0));
        Assert.assertSame("有图单元挂原图片源", okImage, okIcon.getImageSource());
        Assert.assertEquals("有图单元底色透明（不改色）", BG_TRANSPARENT, okIcon.getBackgroundColor());
        Assert.assertEquals("无候选单元回退占位底色", MemberGrid.PLACEHOLDER_COLOR,
                noCandidateIcon.getBackgroundColor());
        Assert.assertNull("无候选单元不挂图片源", noCandidateIcon.getImageSource());

        // 渲染分级回退：三次 EXCEPTION → UNRENDERABLE → 回退占位（协议行为保持）。
        for (int i = 0; i < 3; i++) {
            ItemRenderTierRegistry.classify("test:broken:0",
                    ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        }
        rt.flush();
        Assert.assertEquals("不可渲染项回退占位底色", MemberGrid.PLACEHOLDER_COLOR,
                brokenIcon.getBackgroundColor());
        Assert.assertNull("不可渲染项不再挂图片源", brokenIcon.getImageSource());

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();
        Assert.assertSame("主题切换不动图片源", okImage, okIcon.getImageSource());
        Assert.assertEquals("主题切换不重染有图底", BG_TRANSPARENT, okIcon.getBackgroundColor());
        Assert.assertEquals("主题切换不重染占位底", MemberGrid.PLACEHOLDER_COLOR,
                brokenIcon.getBackgroundColor());
        Assert.assertEquals("主题切换不重染无候选占位底", MemberGrid.PLACEHOLDER_COLOR,
                noCandidateIcon.getBackgroundColor());
        Assert.assertNull("图标不装滤镜", okIcon.getBackdrop());
        Assert.assertNull("占位图标不装滤镜", brokenIcon.getBackdrop());
    }

    // ==================== ③ 复用重绑不串态 ====================

    /**
     * 同 memberId 节点跨数据换代重绑：A 由「无效+占位图」换成「正常+有图」、B 反向，节点身份
     * 不变但徽章底、图片源、占位底、主文本全部随新代数据复位——A 的旧态不残留给 B，B 的旧态
     * 也不残留给 A；徽章状态换代（无效→重复→正常）同样不复用旧底色。
     */
    @Test
    public void reboundCellsResetStatesWithoutLeakingAcrossReuses() {
        SceneImageSource imgA = image("test:a:0");
        SceneImageSource imgB = image("test:b:0");
        imagesByCandidateKey.put("test:a", imgA);
        imagesByCandidateKey.put("test:b", imgB);
        mount(Arrays.asList(malformed(1L), member(2L, "test:b")));

        SceneNode cellA = cell(0, 0);
        SceneNode cellB = cell(1, 0);
        SceneNode rowA = row(0);
        Assert.assertEquals("前置：A 无效徽章底", 0x22EF4444, badge(cellA).getBackgroundColor());
        Assert.assertEquals("前置：A 占位底", MemberGrid.PLACEHOLDER_COLOR, icon(cellA).getBackgroundColor());
        Assert.assertEquals("前置：B 徽章无底", BG_TRANSPARENT, badge(cellB).getBackgroundColor());
        Assert.assertSame("前置：B 有图", imgB, icon(cellB).getImageSource());

        // 换代：A 变正常带图，B 变无效。同 id → keyed 复用同节点，不重建。
        updateMembers(Arrays.asList(member(1L, "test:a"), malformed(2L)));
        Assert.assertSame("A 单元节点复用不重建", cellA, cell(0, 0));
        Assert.assertSame("B 单元节点复用不重建", cellB, cell(1, 0));
        Assert.assertSame("行节点复用不重建", rowA, row(0));
        Assert.assertEquals("A 徽章底复位（无效态不残留）", BG_TRANSPARENT,
                badge(cellA).getBackgroundColor());
        Assert.assertSame("A 图标改挂新代图片源", imgA, icon(cellA).getImageSource());
        Assert.assertEquals("A 图标占位底复位", BG_TRANSPARENT, icon(cellA).getBackgroundColor());
        Assert.assertEquals("A 主文本跟新代", "test:a", primary(cellA).getText());
        Assert.assertEquals("B 徽章继承不到 A 的无效底，但自身进入无效态", 0x22EF4444,
                badge(cellB).getBackgroundColor());
        Assert.assertNull("B 图片源复位为无", icon(cellB).getImageSource());
        Assert.assertEquals("B 图标回退占位底", MemberGrid.PLACEHOLDER_COLOR,
                icon(cellB).getBackgroundColor());
        Assert.assertEquals("B 主文本跟新代", "Unable to read this value", primary(cellB).getText());

        // 再换代：双双正常且同 key → 重复态（文字 warning 档）；无效徽章底不得残留。
        updateMembers(Arrays.asList(member(1L, "test:x"), member(2L, "test:x")));
        Assert.assertEquals("A 无效态不残留进重复代", BG_TRANSPARENT, badge(cellA).getBackgroundColor());
        Assert.assertEquals("B 无效态不残留进重复代", BG_TRANSPARENT, badge(cellB).getBackgroundColor());
        Assert.assertEquals("重复徽章文字 = warningText", SceneThemes.DEFAULT.warningText(),
                badge(cellA).getTextColor());
        Assert.assertEquals("重复徽章文字 = warningText", SceneThemes.DEFAULT.warningText(),
                badge(cellB).getTextColor());
        Assert.assertSame("重复代仍复用同节点", cellA, cell(0, 0));
        Assert.assertSame("重复代仍复用同节点", cellB, cell(1, 0));

        // 收敛回正常：重复态不残留（文案清空、文字回正文档）。
        updateMembers(Arrays.asList(member(1L, "test:x"), member(2L, "test:y")));
        Assert.assertEquals("重复代结束后徽章文案清空", "", badge(cellA).getText());
        Assert.assertEquals("重复代结束后徽章文字回正文档", SceneThemes.DEFAULT.foreground(),
                badge(cellA).getTextColor());
    }

    // ==================== keyed 复用行为不回退 ====================

    /** 数据更新（同 id 换值）：全部单元节点身份保持、无重建，滤镜预算不变。 */
    @Test
    public void keyedUpdateKeepsAllCellIdentitiesWithoutRebuild() {
        mount(Arrays.asList(
                member(1L, "test:a"), member(2L, "test:b"),
                member(3L, "test:c"), member(4L, "test:d")));
        List<SceneNode> before = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            before.add(cell(r, 0));
        }
        int backdropsBefore = countType();

        updateMembers(Arrays.asList(
                member(1L, "test:a", "renamed-a"), member(2L, "test:b"),
                member(3L, "test:c"), member(4L, "test:d")));
        for (int r = 0; r < 4; r++) {
            Assert.assertSame("行[" + r + "] 单元不得重建", before.get(r), cell(r, 0));
        }
        Assert.assertEquals("改值单元文本跟新代", "renamed-a", primary(cell(0, 0)).getText());
        Assert.assertEquals("未改值单元文本不动", "test:b", primary(cell(1, 0)).getText());
        Assert.assertEquals("滤镜预算不随数据更新增长", backdropsBefore, countType());
    }

    /** 数据收缩：退场行的单元订阅随 Owner 回收（effect 数下降），保留单元不受影响。 */
    @Test
    public void shrinkingMembersReleasesEffectsOfRemovedCells() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        mount(Arrays.asList(
                member(1L, "test:a"), member(2L, "test:b"),
                member(3L, "test:c"), member(4L, "test:d")));
        int mounted4 = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("4 成员挂载注册响应式绑定", mounted4 > baseline);

        SceneNode kept = cell(0, 0);
        updateMembers(Collections.singletonList(member(1L, "test:a")));
        Assert.assertEquals("收缩到 1 行", 1, rowsContainer().__getChildren().size());
        Assert.assertSame("保留单元不重建", kept, cell(0, 0));
        int mounted1 = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("退场单元的 effect 必须回收（不是只摘节点）: "
                + mounted1 + " vs " + mounted4, mounted1 < mounted4);

        handle.dispose();
        Assert.assertEquals("整组件卸载回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    /** 自动列数随视口宽重排：行按实时数据 + 实时列数派生，不吃创建时陈旧快照。 */
    @Test
    public void autoColumnsRebalanceRowsFromLiveSource() {
        List<SearchPickerData.CurrentMember> many = Arrays.asList(
                member(1L, "test:a"), member(2L, "test:b"), member(3L, "test:c"),
                member(4L, "test:d"), member(5L, "test:e"));
        // viewport 宽 = 400 - 滚动条 8 = 392；(392+4)/(90+4) = 4 列。
        mountInTheme(Signal.create(SceneTheme.liquidGlassDark()), many, 90, 48, 4, 4);
        Assert.assertEquals("4 列时 5 成员分 2 行", 2, rowsContainer().__getChildren().size());
        Assert.assertEquals("首行 4 单元", 4, row(0).__getChildren().size());
        Assert.assertEquals("末行 1 单元", 1, row(1).__getChildren().size());

        // 收窄画布：viewport 宽 = 190 - 8 = 182 → (182+4)/94 = 1 列 → 5 行。
        layoutWithWidth(190);
        Assert.assertEquals("1 列时 5 成员分 5 行", 5, rowsContainer().__getChildren().size());
        Assert.assertEquals("每行 1 单元", 1, row(2).__getChildren().size());
        Assert.assertEquals("行序按实时数据源重排", "test:c", primary(cell(2, 0)).getText());
    }

    // ==================== 回调合同与受控语义（行为回归） ====================

    /** 编辑/删除回调携带 memberId；网格自身不改受控数据（一步直达、零回写）。 */
    @Test
    public void callbacksCarryMemberIdAndGridKeepsControlledData() {
        List<SearchPickerData.CurrentMember> initial =
                Arrays.asList(member(1L, "test:a"), member(2L, "test:b"));
        mount(initial);
        List<SearchPickerData.CurrentMember> authority = membersSignal.get();

        click(editButton(cell(0, 0)));
        click(removeButton(cell(1, 0)));
        Assert.assertEquals("编辑回调收到行首成员 id", Arrays.asList(Long.valueOf(1L)), edited);
        Assert.assertEquals("删除回调收到行首成员 id", Arrays.asList(Long.valueOf(2L)), removed);
        Assert.assertSame("网格不回写受控成员列表", authority, membersSignal.get());
    }

    /** 禁用时编辑/删除点击零回调；恢复后可用。 */
    @Test
    public void disabledBlocksEditAndRemoveCallbacks() {
        mount(Arrays.asList(member(1L, "test:a"), malformed(2L)));
        enabledSignal.set(Boolean.FALSE);
        rt.flush();

        click(editButton(cell(0, 0)));
        click(removeButton(cell(0, 0)));
        Assert.assertTrue("禁用时编辑不回调", edited.isEmpty());
        Assert.assertTrue("禁用时删除不回调", removed.isEmpty());

        enabledSignal.set(Boolean.TRUE);
        rt.flush();
        click(editButton(cell(0, 0)));
        Assert.assertEquals("恢复后编辑回调可达", Arrays.asList(Long.valueOf(1L)), edited);
    }

    /** 数据收缩后滚动偏移回夹 maxScrollY（既有滚动行为不因外观迁移退化）。 */
    @Test
    public void scrollOffsetClampsAfterDataShrink() {
        mount(Arrays.asList(
                member(1L, "test:a"), member(2L, "test:b"),
                member(3L, "test:c"), member(4L, "test:d")));
        routeScrollAt(viewport(), -300);
        Assert.assertTrue("前置：列表可滚动", viewport().getScrollOffsetY() > 0);

        updateMembers(Collections.singletonList(member(1L, "test:a")));
        Assert.assertEquals("收缩后偏移回夹到新 maxScrollY",
                Math.max(0, SceneGeometry.maxScrollY(viewport())), viewport().getScrollOffsetY());
    }

    // ==================== ④ 主题切换只重派生 + ⑤ 卸载回收 ====================

    /**
     * 主题切换：底座六项、主/副/徽章文字、按钮配方前景全部更新；节点身份不变、effect 数
     * 不增长、单元仍零滤镜（重派生不重建、不重复订阅）。
     */
    @Test
    public void themeSwitchRebindsSurfaceAndTextsWithoutRebuilding() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());
        imagesByCandidateKey.put("test:a", image("test:a:0"));
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        mountInTheme(pageTheme, Arrays.asList(member(1L, "test:a"), malformed(2L)),
                CELL_W, CELL_H, GAP_X, GAP_Y);

        SceneNode viewport = viewport();
        SceneNode row0 = row(0);
        SceneNode cell0 = cell(0, 0);
        SceneNode editBtn = editButton(cell0);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("底座染色随主题更新", lightGroup.getIdle().getTint(),
                viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座缘色随主题更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("主文本随主题更新", light.foreground(), primary(cell0).getTextColor());
        Assert.assertEquals("副文本随主题更新", light.mutedForeground(), secondary(cell0).getTextColor());
        Assert.assertNotEquals("测试前提：两档 foreground 不同（徽章正文档跟随可证）",
                dark.foreground(), light.foreground());
        Assert.assertEquals("无效徽章文字随主题回正文档", light.foreground(), badge(cell(1, 0)).getTextColor());
        Assert.assertEquals("无效徽章底不随主题（状态徽标静态语义底）", 0x22EF4444,
                badge(cell(1, 0)).getBackgroundColor());
        Assert.assertEquals("按钮文字随主题配方前景更新",
                light.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground().intValue(),
                buttonLabel(editBtn).getTextColor());

        Assert.assertSame("主题切换不重建底座", viewport, viewport());
        Assert.assertSame("主题切换不重建行", row0, row(0));
        Assert.assertSame("主题切换不重建单元", cell0, cell(0, 0));
        Assert.assertSame("主题切换不重建按钮", editBtn, editButton(cell(0, 0)));
        Assert.assertNull("主题切换后单元仍零滤镜", cell0.getBackdrop());
        repaint();
        Assert.assertEquals("主题切换后底座仍恰好一条 BACKDROP", 1, backdropCount(viewport));
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /** 卸载回收：effect 数回基线，主题更新不再写入旧节点。 */
    @Test
    public void unmountReleasesAllBindingsAndStopsWrites() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountInTheme(pageTheme, Arrays.asList(member(1L, "test:a"), malformed(2L)),
                CELL_W, CELL_H, GAP_X, GAP_Y);
        Assert.assertTrue("挂载注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode viewport = viewport();
        int colorBeforeDispose = viewport.getBackgroundColor();
        handle.dispose();
        Assert.assertEquals("卸载后绑定 effect 全回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧节点",
                colorBeforeDispose, viewport.getBackgroundColor());
    }

    // ==================== 公共契约冻结 ====================

    /** Props record 分量、Result 结构、默认几何常量与校验守卫零改动。 */
    @Test
    public void publicContractConstantsAndGuardsUnchanged() {
        Assert.assertEquals(24, MemberGrid.ICON_SIZE);
        Assert.assertEquals(12, MemberGrid.FONT_SIZE);
        Assert.assertEquals(6, MemberGrid.CELL_PADDING);
        Assert.assertEquals(0xFF454B54, MemberGrid.PLACEHOLDER_COLOR);
        Assert.assertEquals(200, MemberGrid.DEFAULT_CELL_WIDTH);
        Assert.assertEquals(96, MemberGrid.DEFAULT_CELL_HEIGHT);
        Assert.assertEquals(8, MemberGrid.DEFAULT_GAP_X);
        Assert.assertEquals(8, MemberGrid.DEFAULT_GAP_Y);

        membersSignal = Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
        enabledSignal = Signal.create(Boolean.TRUE);
        issuesSignal = Signal.create(issuesOf(Collections.<SearchPickerData.CurrentMember>emptyList()));
        MemberGrid.Props props = gridProps(120, 60, 4, 6);
        Assert.assertSame("Props 成员信号分量原样暴露", membersSignal, props.members());
        Assert.assertSame("Props enabled 分量原样暴露", enabledSignal, props.enabled());
        Assert.assertSame("Props issues 分量原样暴露", issuesSignal, props.issues());
        Assert.assertEquals(120, props.cellWidth());
        Assert.assertEquals(60, props.cellHeight());
        Assert.assertEquals(4, props.gapX());
        Assert.assertEquals(6, props.gapY());

        try {
            new MemberGrid.Props(membersSignal, enabledSignal,
                    SearchPickerPresentation.defaultEnglish(), visualAdapter(), issuesSignal,
                    id -> { }, id -> true, 0, 96, 8, 8);
            Assert.fail("cellWidth<=0 必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 公共校验守卫保持。
        }
        try {
            new MemberGrid.Props(membersSignal, enabledSignal,
                    SearchPickerPresentation.defaultEnglish(), visualAdapter(), issuesSignal,
                    id -> { }, id -> true, 200, 96, -1, 8);
            Assert.fail("gap 负数必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 公共校验守卫保持。
        }
    }

    /** 滚动条结构保持（可见滚动条 = 工厂默认），滚动几何权威节点是 viewport。 */
    @Test
    public void scrollbarStructurePreserved() {
        mount(Collections.singletonList(member(1L, "test:a")));
        SceneNode container = result.root();
        Assert.assertEquals("container = [viewport, 滚动条列]", 2, container.__getChildren().size());
        Assert.assertSame("viewport 是 container 首子", viewport(), container.__getChildren().get(0));
        Assert.assertTrue("viewport 可滚动", viewport().isScrollable());
        Assert.assertEquals("默认滚动条宽度", SceneScrollbar.DEFAULT_BAR_WIDTH,
                container.__getChildren().get(1).getPreferredWidth());
        Assert.assertSame("Result.root 即容器（结构零改动）", container, result.root());
    }
}
