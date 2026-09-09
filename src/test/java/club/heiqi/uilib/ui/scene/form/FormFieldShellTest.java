package club.heiqi.uilib.ui.scene.form;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FormFieldShell} 单元测试。
 *
 * <p><b>默认路径</b>（不传 {@link FormTheme}）：卡片表面逐项等于来源主题
 * {@link SceneTheme.Role#GROUP} 配方（染色/边框/边框宽/圆角/浮雕/滤镜），语义文字取主题
 * {@code foreground}/{@code mutedForeground}/{@code errorText}/{@code accent}——error、dirty、
 * helper 三类各自可见且色值互不相同；来源主题切换只重派生（节点身份、控件节点与订阅数不变）；
 * 无 Owner 构建期走绑定器自带的 root 兜底。</p>
 *
 * <p><b>显式路径</b>（旧 {@link FormTheme} 重载）：静态写入者与 error/dirty 派生语义与迁移前
 * 一致，不装滤镜、不订阅主题，显式主题完全覆盖主题派生；旧签名全部保留。</p>
 *
 * <p><b>无边框模板</b>：默认与显式两条路径都保持无底色/无边框/无圆角/无滤镜，不因主题又加出卡片，
 * error/dirty 仍经状态点与文案可见。</p>
 *
 * <p>另观察 PaintPlan：默认路径的语义表面确有且仅有一颗 {@code BACKDROP}；
 * 关闭滤镜档与无边框模板不得发 {@code BACKDROP} 命令。</p>
 *
 * <p>分层：build 内部经 {@link SceneRuntime} 注册 mount/show/bind 与表面绑定，属 L3 集成范畴，
 * 照 {@code FormPageShellTest} 范式直接 new SceneRuntime，放 form 同包（白盒测试留对应包）。</p>
 */
public class FormFieldShellTest {

    private static final float EPSILON = 0.0001F;
    private static final String TITLE = "我的标题";
    private static final String HELPER = "帮助文本";
    private static final String ERROR_TEXT = "必填字段";
    private static final String CONTROL_TEXT = "control";

    /** 恒无错误 / 恒不脏的只读信号（显式构造场景用）。 */
    private static final ReadableSignal<String> NO_ERROR = () -> "";
    private static final ReadableSignal<Boolean> NOT_DIRTY = () -> Boolean.FALSE;

    /** 场景运行时（build 消费方），每用例独立 new，避免跨用例污染。 */
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 装配与查找工具 ====================

    /** 控件槽占位节点（带可查找文本，不设高度：由字段外壳决定）。 */
    private static SceneNode control() {
        SceneNode node = new SceneNode();
        node.setText(CONTROL_TEXT);
        node.setHitTestable(false);
        return node;
    }

    /** 在可切换局部主题作用域内构建默认路径外壳（不传 FormTheme）。 */
    private MountHandle mountDefault(Signal<SceneTheme> pageTheme, ReadableSignal<String> error,
                                     ReadableSignal<Boolean> dirty, String helper, final SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = FormFieldShell.build(
                    runtime, TITLE, helper, error, dirty, FormFieldShellTest::control));
            return holder[0];
        });
    }

    /** 默认路径便捷重载：错误/脏态用可变信号，helper 固定。 */
    private MountHandle mountDefault(Signal<SceneTheme> pageTheme, Signal<String> error,
                                     Signal<Boolean> dirty, final SceneNode[] holder) {
        return mountDefault(pageTheme, error, dirty, HELPER, holder);
    }

    /** 在可切换局部主题作用域内构建无边框模板（默认路径）。 */
    private MountHandle mountBorderless(Signal<SceneTheme> pageTheme, ReadableSignal<String> error,
                                        ReadableSignal<Boolean> dirty, final SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = FormFieldShell.buildBorderless(
                    runtime, TITLE, HELPER, error, dirty, FormFieldShellTest::control));
            return holder[0];
        });
    }

    /** 在可切换局部主题作用域内构建显式 FormTheme 路径（bordered 或 borderless）。 */
    private MountHandle mountExplicit(Signal<SceneTheme> pageTheme, FormTheme explicit,
                                      ReadableSignal<String> error, ReadableSignal<Boolean> dirty,
                                      boolean borderless, final SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = borderless
                    ? FormFieldShell.buildBorderless(runtime, TITLE, HELPER, error, dirty,
                            FormFieldShellTest::control, explicit)
                    : FormFieldShell.build(runtime, TITLE, HELPER, error, dirty,
                            FormFieldShellTest::control, explicit));
            return holder[0];
        });
    }

    private static SceneNode header(SceneNode card) {
        return card.__getChildren().get(0);
    }

    private static SceneNode dot(SceneNode card) {
        return header(card).__getChildren().get(0);
    }

    private static SceneNode title(SceneNode card) {
        return header(card).__getChildren().get(1);
    }

    /** helper 节点（helper 非空时紧跟 header）。 */
    private static SceneNode helperNode(SceneNode card) {
        return card.__getChildren().get(1);
    }

    /** 递归查找指定文本的节点；不存在返回 null。 */
    private static SceneNode findByText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findByText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 统计 PaintPlan 中的 BACKDROP 命令数（语义表面是否真的采样了背景）。 */
    private static int backdropCount(SceneNode root) {
        PaintPlan plan = new ScenePaintEngine(new FixedTextMeasurer(8, 16)).paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 把卡片挂到独立场景根上并完成 flush + layout（PaintPlan 断言用）。 */
    private MountHandle mountInScene(Signal<SceneTheme> pageTheme, SceneNode sceneRoot,
                                     boolean borderless, final SceneNode[] holder) {
        return runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = borderless
                    ? FormFieldShell.buildBorderless(runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY,
                            FormFieldShellTest::control)
                    : FormFieldShell.build(runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY,
                            FormFieldShellTest::control));
            return holder[0];
        });
    }

    private static SceneNode layoutScene(SceneNode sceneRoot) {
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16))
                .layout(sceneRoot, new Constraints(240, 400));
        return sceneRoot;
    }

    // ==================== 默认路径：表面 ====================

    /** 默认路径：卡片表面逐项等于来源主题 GROUP 配方，绑定器独占全部表面属性。 */
    @Test
    public void defaultBuildBindsGroupRecipeOnCard() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        SceneSurfaceStyle group = theme.surface(SceneTheme.Role.GROUP);
        FormTheme mapped = FormThemes.of(theme);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());

        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(theme), Signal.create(""),
                Signal.create(Boolean.FALSE), holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("卡片背景 = GROUP idle 染色", group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡片边框色 = GROUP idle 缘色", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡片边框宽 = GROUP 配方", group.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("卡片圆角 = GROUP 配方", group.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡片浮雕高度 = GROUP 配方",
                group.getIdle().getElevation(), card.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("卡片装 GROUP 滤镜", card.getBackdrop());
        Assert.assertEquals("滤镜模糊半径 = 配方",
                group.getBackdrop().getBlurRadius(), card.getBackdrop().getBlurRadius());
        Assert.assertEquals("滤镜材质 = 配方",
                group.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());

        Assert.assertEquals("标题取主题 foreground", mapped.textColor(), title(card).getTextColor());
        Assert.assertEquals("helper 取主题 mutedForeground",
                mapped.mutedColor(), helperNode(card).getTextColor());
        Assert.assertEquals("默认状态点取主题 mutedForeground", mapped.mutedColor(), dot(card).getTextColor());
        Assert.assertNull("无错误时不挂载 error 文案", findByText(card, ERROR_TEXT));
        handle.dispose();
    }

    /** 默认路径：布局与字号仍取既有表单主题常量（主题不接管布局），控件高度取主题 inputHeight。 */
    @Test
    public void defaultBuildKeepsLayoutAndFontContract() {
        FormTheme mapped = FormThemes.of(SceneTheme.liquidGlassDark());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(SceneTheme.liquidGlassDark()), Signal.create(""),
                Signal.create(Boolean.FALSE), holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("内边距 = 主题映射 cardPad", mapped.cardPad(), card.getPaddingLeft());
        Assert.assertEquals("间距 = 主题映射 fieldGap", mapped.fieldGap(), card.getGap());
        Assert.assertEquals("标题字号 = 既有 fontLabel", mapped.fontLabel(), title(card).getFontSize());
        Assert.assertEquals("helper 字号 = 既有 fontHelper",
                mapped.fontHelper(), helperNode(card).getFontSize());
        Assert.assertEquals("控件根高度 = 主题 inputHeight",
                mapped.inputHeight(), findByText(card, CONTROL_TEXT).getPreferredHeight());
        handle.dispose();
    }

    /** 默认路径 8 参重载：显式 controlHeight 写入控件根；{@code <=0} 时不设高度。 */
    @Test
    public void defaultBuildControlHeightOverloadAppliesExplicitHeight() {
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> holder[0] = FormFieldShell.build(runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY,
                            FormFieldShellTest::control, 220));
            return holder[0];
        });
        runtime.flush();
        Assert.assertEquals("显式 controlHeight 写入控件根", 220,
                findByText(holder[0], CONTROL_TEXT).getPreferredHeight());
        handle.dispose();

        final SceneNode[] zero = new SceneNode[1];
        MountHandle zeroHandle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()),
                    () -> zero[0] = FormFieldShell.build(runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY,
                            FormFieldShellTest::control, 0));
            return zero[0];
        });
        runtime.flush();
        Assert.assertEquals("controlHeight<=0 时不设控件高度", 0,
                findByText(zero[0], CONTROL_TEXT).getPreferredHeight());
        zeroHandle.dispose();
    }

    // ==================== 默认路径：error / dirty / helper 三类语义 ====================

    /** error 态：边框、状态点、错误文案统一取主题危险语义色（errorText），不使用 muted。 */
    @Test
    public void defaultErrorStateUsesDangerSemantics() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        FormTheme mapped = FormThemes.of(theme);
        Signal<String> error = Signal.create(ERROR_TEXT);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(theme), error, Signal.create(Boolean.FALSE), holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("error 边框 = 主题 errorText（危险语义）",
                theme.errorText(), card.getBorderColor());
        Assert.assertEquals("error 状态点 = 主题 errorText", mapped.errorColor(), dot(card).getTextColor());
        SceneNode errorNode = findByText(card, ERROR_TEXT);
        Assert.assertNotNull("error 文案已挂载", errorNode);
        Assert.assertEquals("error 文案色 = 主题 errorText", theme.errorText(), errorNode.getTextColor());
        Assert.assertEquals("error 字号 = 既有 fontError", mapped.fontError(), errorNode.getFontSize());
        Assert.assertNotEquals("error 不得误取次要色", theme.mutedForeground(), errorNode.getTextColor());
        handle.dispose();
    }

    /** dirty 态：边框与状态点取主题强调语义色（accent），与 error/helper 色不同。 */
    @Test
    public void defaultDirtyStateUsesAccentSemantics() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        Signal<Boolean> dirty = Signal.create(Boolean.TRUE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(theme), Signal.create(""), dirty, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("dirty 边框 = 主题 accent", theme.accent(), card.getBorderColor());
        Assert.assertEquals("dirty 状态点 = 主题 accent", theme.accent(), dot(card).getTextColor());
        Assert.assertNotEquals("dirty 不得与 error 同色", theme.errorText(), card.getBorderColor());
        Assert.assertNotEquals("dirty 不得与 helper 同色", theme.mutedForeground(), dot(card).getTextColor());
        Assert.assertNull("dirty 不挂 error 文案", findByText(card, ERROR_TEXT));
        handle.dispose();
    }

    /** error + dirty 同时为真：error 优先；三类提示色互不相同且 helper 不被染色。 */
    @Test
    public void defaultErrorWinsOverDirtyAndThreeCuesStayDistinct() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        FormTheme mapped = FormThemes.of(theme);
        Assert.assertNotEquals("前置：errorText 与 accent 不同", theme.errorText(), theme.accent());
        Assert.assertNotEquals("前置：errorText 与 mutedForeground 不同",
                theme.errorText(), theme.mutedForeground());
        Assert.assertNotEquals("前置：accent 与 mutedForeground 不同",
                theme.accent(), theme.mutedForeground());

        Signal<String> error = Signal.create(ERROR_TEXT);
        Signal<Boolean> dirty = Signal.create(Boolean.TRUE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(theme), error, dirty, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("error 压过 dirty（边框）", theme.errorText(), card.getBorderColor());
        Assert.assertEquals("error 压过 dirty（状态点）", theme.errorText(), dot(card).getTextColor());
        Assert.assertEquals("error 文案仍取 errorText",
                theme.errorText(), findByText(card, ERROR_TEXT).getTextColor());
        Assert.assertEquals("helper 仍取 mutedForeground，不被 error/dirty 染色",
                mapped.mutedColor(), helperNode(card).getTextColor());

        error.set("");
        runtime.flush();
        Assert.assertEquals("error 清空后回落到 dirty 强调色", theme.accent(), card.getBorderColor());
        Assert.assertEquals("error 清空后状态点回落到 accent", theme.accent(), dot(card).getTextColor());
        handle.dispose();
    }

    /** helper 为 null / 空串时不渲染 helper 区（行为保持）。 */
    @Test
    public void emptyHelperIsNotRendered() {
        final SceneNode[] nullHolder = new SceneNode[1];
        MountHandle nullHandle = mountDefault(Signal.create(SceneTheme.liquidGlassDark()), NO_ERROR,
                NOT_DIRTY, null, nullHolder);
        runtime.flush();
        // 卡片子节点 = header + 控件 + show 占位 anchor（无 helper、无 error）
        Assert.assertEquals("helper=null 时卡片仅 header + 控件 + anchor 三子",
                3, nullHolder[0].__getChildren().size());
        Assert.assertNull("helper=null 时不渲染 helper 文本", findByText(nullHolder[0], HELPER));
        nullHandle.dispose();

        final SceneNode[] emptyHolder = new SceneNode[1];
        MountHandle emptyHandle = mountDefault(Signal.create(SceneTheme.liquidGlassDark()), NO_ERROR,
                NOT_DIRTY, "", emptyHolder);
        runtime.flush();
        Assert.assertEquals("helper=空串时卡片仅 header + 控件 + anchor 三子",
                3, emptyHolder[0].__getChildren().size());
        Assert.assertNull("helper=空串时不渲染 helper 文本", findByText(emptyHolder[0], HELPER));
        emptyHandle.dispose();
    }

    /** error 文案随信号动态挂载/卸载（rt.show 条件渲染），空文案不占位。 */
    @Test
    public void errorNodeMountsAndUnmountsWithSignal() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        Signal<String> error = Signal.create("");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(Signal.create(theme), error, Signal.create(Boolean.FALSE), holder);
        runtime.flush();

        SceneNode card = holder[0];
        int childrenWithoutError = card.__getChildren().size();
        Assert.assertNull("空错误不挂载 error 文案", findByText(card, ERROR_TEXT));

        error.set(ERROR_TEXT);
        runtime.flush();
        SceneNode errorNode = findByText(card, ERROR_TEXT);
        Assert.assertNotNull("错误出现时挂载 error 文案", errorNode);
        Assert.assertEquals("error 文案色 = 主题 errorText", theme.errorText(), errorNode.getTextColor());
        Assert.assertEquals("挂载后新增一子", childrenWithoutError + 1, card.__getChildren().size());

        error.set("");
        runtime.flush();
        Assert.assertNull("错误清空时卸载 error 文案", findByText(card, ERROR_TEXT));
        Assert.assertEquals("卸载后子数回退", childrenWithoutError, card.__getChildren().size());
        handle.dispose();
    }

    // ==================== 默认路径：无边框模板 ====================

    /** 无边框模板：保持无底色/无边框/无圆角/无滤镜，不因主题加出卡片；error 仍可辨。 */
    @Test
    public void defaultBorderlessStaysBorderlessAndKeepsStateCues() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        Signal<String> error = Signal.create(ERROR_TEXT);
        Signal<Boolean> dirty = Signal.create(Boolean.TRUE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountBorderless(Signal.create(theme), error, dirty, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("无边框模板保持无底色", 0, card.getBackgroundColor());
        Assert.assertEquals("无边框模板保持无边框", 0, card.getBorderWidth());
        Assert.assertEquals("无边框模板保持无圆角", 0, card.getCornerRadius());
        Assert.assertEquals("无边框模板保持零内边距", 0, card.getPaddingLeft());
        Assert.assertNull("无边框模板不装滤镜", card.getBackdrop());
        Assert.assertEquals("无边框模板不绑浮雕", -1.0F, card.__getSurfaceElevation(), EPSILON);
        Assert.assertEquals("error 状态点仍取危险语义色", theme.errorText(), dot(card).getTextColor());
        Assert.assertNotNull("error 文案仍可见", findByText(card, ERROR_TEXT));

        error.set("");
        runtime.flush();
        Assert.assertEquals("error 清空后状态点回落 dirty 强调色", theme.accent(), dot(card).getTextColor());
        Assert.assertEquals("无边框模板不因 dirty 加出底色", 0, card.getBackgroundColor());
        handle.dispose();
    }

    // ==================== 默认路径：无 Owner 构建与主题更新 ====================

    /** 无 Owner 上下文构建：表面绑定走 SceneSurfaceBinder 自带的 root 兜底，不另造一层。 */
    @Test
    public void defaultBuildWithoutOwnerUsesRuntimeRootFallback() {
        SceneTheme theme = SceneThemes.DEFAULT;
        SceneSurfaceStyle group = theme.surface(SceneTheme.Role.GROUP);

        SceneNode card = FormFieldShell.build(runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY,
                FormFieldShellTest::control);
        runtime.flush();

        Assert.assertEquals("无 Owner 构建仍绑定 GROUP 染色",
                group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("无 Owner 构建仍绑定 GROUP 缘色",
                group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertNotNull("无 Owner 构建仍写入滤镜", card.getBackdrop());
        Assert.assertEquals("标题取库默认 foreground", theme.foreground(), title(card).getTextColor());
        Assert.assertEquals("helper 取库默认 mutedForeground",
                theme.mutedForeground(), helperNode(card).getTextColor());
    }

    /**
     * 主题切换：卡片表面与三类语义色随来源主题重派生，节点身份/控件节点/订阅数不变，
     * 且 error 态仍取新主题的危险语义色。
     */
    @Test
    public void themeSwitchRepaintsWithoutRebuilding() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("前置：两主题 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        Signal<String> error = Signal.create(ERROR_TEXT);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(pageTheme, error, Signal.create(Boolean.FALSE), holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneNode titleNode = title(card);
        SceneNode dotNode = dot(card);
        SceneNode helperText = helperNode(card);
        SceneNode controlNode = findByText(card, CONTROL_TEXT);
        SceneNode errorNode = findByText(card, ERROR_TEXT);
        Assert.assertEquals("前置：深色档卡片背景", darkGroup.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("前置：深色档 error 边框", dark.errorText(), card.getBorderColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后卡片背景 = 浅色 GROUP idle",
                lightGroup.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("切换后滤镜材质 = 浅色 GROUP 配方",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("切换后 error 边框仍取新主题 errorText（危险语义优先）",
                light.errorText(), card.getBorderColor());
        Assert.assertEquals("切换后标题取新主题 foreground",
                light.foreground(), titleNode.getTextColor());
        Assert.assertEquals("切换后 helper 取新主题 mutedForeground",
                light.mutedForeground(), helperText.getTextColor());
        Assert.assertEquals("切换后状态点仍取新主题 errorText",
                light.errorText(), dotNode.getTextColor());
        Assert.assertEquals("切换后 error 文案取新主题 errorText",
                light.errorText(), errorNode.getTextColor());
        Assert.assertSame("主题切换不重建卡片", card, holder[0]);
        Assert.assertSame("主题切换不重建标题节点", titleNode, title(card));
        Assert.assertSame("主题切换不重建控件节点", controlNode, findByText(card, CONTROL_TEXT));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 卸载回收：默认路径的外观绑定 effect 随 mount 卸载全部退订，主题更新不再写入旧卡片。 */
    @Test
    public void unmountReleasesThemeSurfaceBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountDefault(pageTheme, Signal.create(""), Signal.create(Boolean.FALSE), holder);
        runtime.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode card = holder[0];
        int colorBeforeDispose = card.getBackgroundColor();
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧卡片",
                colorBeforeDispose, card.getBackgroundColor());
    }

    // ==================== 显式路径：旧 FormTheme 语义不变 ====================

    /** 显式路径：静态写入者与旧语义一致（无滤镜、不绑浮雕、不订阅主题）。 */
    @Test
    public void explicitThemeKeepsLegacyStaticWriters() {
        FormTheme explicit = FormTheme.defaultDark();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountExplicit(pageTheme, explicit, Signal.create(""),
                Signal.create(Boolean.FALSE), false, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("卡片底色 = 显式 cardBg", explicit.cardBg(), card.getBackgroundColor());
        Assert.assertEquals("边框宽 = 旧常量 1", 1, card.getBorderWidth());
        Assert.assertEquals("圆角 = 显式 cardRadius", explicit.cardRadius(), card.getCornerRadius());
        Assert.assertEquals("内边距 = 显式 cardPad", explicit.cardPad(), card.getPaddingLeft());
        Assert.assertEquals("默认边框色 = 显式 cardBorder", explicit.cardBorder(), card.getBorderColor());
        Assert.assertNull("显式路径不装滤镜", card.getBackdrop());
        Assert.assertEquals("显式路径不绑浮雕", -1.0F, card.__getSurfaceElevation(), EPSILON);
        Assert.assertEquals("标题取显式 textColor", explicit.textColor(), title(card).getTextColor());
        Assert.assertEquals("helper 取显式 mutedColor", explicit.mutedColor(), helperNode(card).getTextColor());
        Assert.assertEquals("状态点取显式 mutedColor", explicit.mutedColor(), dot(card).getTextColor());

        int effectsAfterMount = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(SceneTheme.liquidGlassDark());
        runtime.flush();

        Assert.assertEquals("主题切换不改显式卡片底色", explicit.cardBg(), card.getBackgroundColor());
        Assert.assertEquals("主题切换不改显式边框色", explicit.cardBorder(), card.getBorderColor());
        Assert.assertEquals("主题切换不改显式标题色", explicit.textColor(), title(card).getTextColor());
        Assert.assertEquals("显式路径不因主题更新新增订阅",
                effectsAfterMount, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 显式路径：error/dirty 仍取旧 FormTheme 的 cardBorderError/dirty 与 errorColor/dirtyColor。 */
    @Test
    public void explicitErrorAndDirtyUseLegacyColors() {
        FormTheme explicit = FormTheme.defaultDark();
        Signal<String> error = Signal.create("");
        Signal<Boolean> dirty = Signal.create(Boolean.FALSE);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountExplicit(Signal.create(SceneTheme.liquidGlassDark()), explicit,
                error, dirty, false, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("初始边框色 = 显式 cardBorder", explicit.cardBorder(), card.getBorderColor());

        dirty.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("dirty 边框 = 显式 cardBorderDirty",
                explicit.cardBorderDirty(), card.getBorderColor());
        Assert.assertEquals("dirty 状态点 = 显式 dirtyColor", explicit.dirtyColor(), dot(card).getTextColor());

        error.set(ERROR_TEXT);
        runtime.flush();
        Assert.assertEquals("error 压过 dirty（边框）= 显式 cardBorderError",
                explicit.cardBorderError(), card.getBorderColor());
        Assert.assertEquals("error 状态点 = 显式 errorColor", explicit.errorColor(), dot(card).getTextColor());
        Assert.assertEquals("error 文案 = 显式 errorColor",
                explicit.errorColor(), findByText(card, ERROR_TEXT).getTextColor());
        handle.dispose();
    }

    /** 显式路径的无边框模板：仍保持无卡片，error/dirty 走显式色，语义与迁移前一致。 */
    @Test
    public void explicitBorderlessKeepsLegacyNoCard() {
        FormTheme explicit = FormTheme.defaultDark();
        Signal<String> error = Signal.create(ERROR_TEXT);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountExplicit(Signal.create(SceneTheme.liquidGlassDark()), explicit,
                error, Signal.create(Boolean.TRUE), true, holder);
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("显式无边框模板保持无底色", 0, card.getBackgroundColor());
        Assert.assertEquals("显式无边框模板保持无边框", 0, card.getBorderWidth());
        Assert.assertEquals("显式无边框模板保持无圆角", 0, card.getCornerRadius());
        Assert.assertEquals("显式无边框模板保持零内边距", 0, card.getPaddingLeft());
        Assert.assertNull("显式无边框模板不装滤镜", card.getBackdrop());
        Assert.assertEquals("error 状态点 = 显式 errorColor", explicit.errorColor(), dot(card).getTextColor());
        Assert.assertNotNull("error 文案仍可见", findByText(card, ERROR_TEXT));
        handle.dispose();
    }

    // ==================== PaintPlan：语义表面确有 BACKDROP 且无滤镜档不发 ====================

    /** 默认路径：卡片确有且仅有一颗 BACKDROP（语义表面采样一次背景）。 */
    @Test
    public void defaultPaintPlanEmitsSingleBackdropOnCard() {
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = mountInScene(Signal.create(SceneTheme.liquidGlassDark()), sceneRoot, false, holder);
        runtime.flush();
        layoutScene(sceneRoot);

        Assert.assertNotNull("默认路径卡片确有 GROUP 滤镜", holder[0].getBackdrop());
        Assert.assertEquals("语义表面只采样一次背景", 1, backdropCount(sceneRoot));
        handle.dispose();
    }

    /** 无边框模板：不得发 BACKDROP 命令，也不得有底色（不因主题加出卡片）。 */
    @Test
    public void borderlessPaintPlanEmitsNoBackdrop() {
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = mountInScene(Signal.create(SceneTheme.liquidGlassDark()), sceneRoot, true, holder);
        runtime.flush();
        layoutScene(sceneRoot);

        Assert.assertNull("无边框模板不装滤镜", holder[0].getBackdrop());
        Assert.assertEquals("无边框模板不发 BACKDROP 命令", 0, backdropCount(sceneRoot));
        Assert.assertEquals("无边框模板无底色", 0, holder[0].getBackgroundColor());
        handle.dispose();
    }

    /** 关闭滤镜档（withoutBackdrop）：替代底色不透明可读，且不发 BACKDROP 命令。 */
    @Test
    public void withoutBackdropThemeKeepsOpaqueTintAndNoBackdropCommand() {
        SceneTheme noBackdrop = SceneTheme.liquidGlassDark().withoutBackdrop();
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = mountInScene(Signal.create(noBackdrop), sceneRoot, false, holder);
        runtime.flush();
        layoutScene(sceneRoot);

        SceneNode card = holder[0];
        Assert.assertNull("关闭滤镜时不装滤镜", card.getBackdrop());
        Assert.assertEquals("替代底色不透明可读", 0xFF, (card.getBackgroundColor() >>> 24) & 0xFF);
        Assert.assertEquals("替代底色 = 分组替代底色",
                SceneTheme.FALLBACK_BG_GROUP, card.getBackgroundColor());
        Assert.assertEquals("关闭滤镜时不发 BACKDROP 命令", 0, backdropCount(sceneRoot));
        Assert.assertEquals("圆角与边框仍由同一配方给出",
                noBackdrop.surface(SceneTheme.Role.GROUP).getCornerRadius(), card.getCornerRadius());
        handle.dispose();
    }

    /** 旧签名可编译性回归：两个 build 重载 + 两个 buildBorderless 重载均可调用。 */
    @Test
    public void legacySignaturesRemainCallable() {
        FormTheme explicit = FormTheme.defaultDark();
        final SceneNode[] holder = new SceneNode[4];
        MountHandle[] handles = new MountHandle[4];
        handles[0] = runtime.mount(new SceneNode(), () -> holder[0] = FormFieldShell.build(
                runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY, FormFieldShellTest::control, explicit));
        handles[1] = runtime.mount(new SceneNode(), () -> holder[1] = FormFieldShell.build(
                runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY, FormFieldShellTest::control, explicit, 220));
        handles[2] = runtime.mount(new SceneNode(), () -> holder[2] = FormFieldShell.buildBorderless(
                runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY, FormFieldShellTest::control, explicit));
        handles[3] = runtime.mount(new SceneNode(), () -> holder[3] = FormFieldShell.build(
                runtime, TITLE, HELPER, NO_ERROR, NOT_DIRTY, FormFieldShellTest::control));
        runtime.flush();

        for (SceneNode card : holder) {
            Assert.assertNotNull("旧/新签名均返回非 null 卡片", card);
            Assert.assertEquals("标题文本不变", TITLE, title(card).getText());
        }
        Assert.assertEquals("显式 8 参重载控件高度", 220,
                findByText(holder[1], CONTROL_TEXT).getPreferredHeight());
        Assert.assertEquals("显式无边框重载保持无边框", 0, holder[2].getBorderWidth());
        List<SceneNode> firstChildren = holder[0].__getChildren();
        Assert.assertEquals("结构不变：header + helper + 控件 + show anchor 四子",
                4, firstChildren.size());
        Assert.assertSame("首子仍为 header", header(holder[0]), firstChildren.get(0));
        for (MountHandle handle : handles) {
            handle.dispose();
        }
    }
}
