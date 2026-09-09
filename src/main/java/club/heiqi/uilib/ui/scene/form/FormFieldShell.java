package club.heiqi.uilib.ui.scene.form;

import java.util.Objects;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 字段卡片外壳共享构建器：label + helper + 控件 mount 槽 + error 提示 + dirty 标记。
 *
 * <p>从 {@code config.ui.field.FieldShell} 提炼下沉的通用工具，照
 * {@code SceneFormHostWidget.createFieldShell} 范式，供表单消费方复用，避免每个字段
 * renderer 重复外壳样板。外壳结构：</p>
 * <pre>
 * card (COLUMN, bg, border, radius, padding, gap)
 *   ├ bind border color by error/dirty
 *   ├ header (ROW, gap)
 *   │   ├ dot (●, color by error/dirty)
 *   │   └ title (label)
 *   ├ helper text
 *   ├ 控件 (mount 槽，由 caller 提供 Supplier)
 *   └ error text (rt.show 条件挂载：errorSignal 非空时才挂载，空时仅留零尺寸 anchor 占位)
 * </pre>
 *
 * <p><b>两条外观路径（一个属性只有一个写入者，互斥不叠加）</b></p>
 * <ul>
 *   <li><b>默认路径</b>——不传 {@link FormTheme} 的重载：卡片表面（background/borderColor/
 *       borderWidth/cornerRadius/backdrop/surfaceElevation）经 {@link SceneSurfaceBinder#bind}
 *       消费来源主题的 {@link SceneTheme.Role#GROUP} 配方，绑定器是这些属性的唯一写入者；
 *       error/dirty 只把配方缘色换成 {@link FormThemes} 映射的危险色/强调色（派生配方，
 *       仍由同一绑定器写入），不在配方之外再叠第二层玻璃或滤镜。文字语义经
 *       {@link FormThemes#resolve} 取来源主题：标题 {@code textColor}（foreground）、
 *       helper {@code mutedColor}（mutedForeground）、error {@code errorColor}（errorText 危险语义）、
 *       dirty 状态点 {@code dirtyColor}（accent 强调语义）——三类各自可见且色值互不相同。
 *       来源主题切换只重派生外观，不重建节点、不丢编辑状态。</li>
 *   <li><b>显式路径</b>——保留的 {@link FormTheme} 重载：语义与迁移前一致，
 *       cardBg/边框宽/圆角/内边距静态写入、边框色与状态点随 error/dirty 动画派生、
 *       不装滤镜、不订阅主题；显式主题完全覆盖主题派生。</li>
 * </ul>
 * <p>两条路径的结构、布局（内边距/间距/字号/控件高度）与 error 条件挂载策略完全一致；
 * 无边框模板（{@link #buildBorderless}）两条路径都保持无底色/无边框/无圆角，不因主题又加出卡片。</p>
 *
 * <p><b>零 config 依赖</b>：本类只吃 {@link java.lang.String} /
 * {@link ReadableSignal} / {@link Supplier} / {@link FormTheme}，
 * 不感知任何 config 业务类型。caller 负责把 {@code FieldSpec} / {@code DraftSignalAdapter}
 * 拆解为 title / helper / errorSignal / dirtySignal 后传入。</p>
 *
 * <p>外观随状态变化只经 {@code rt.bind/bindComputed/__bindAnimatedColor} 派生（守 I1/I11/R4），
 * 控件 mount 槽由 caller 以 {@code Supplier<SceneNode>} 注入，本类不建业务控件。</p>
 */
public final class FormFieldShell {

    /** 字段外壳卡片恒定启用（卡片本身无禁用态；内部控件自带 enabled 信号）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 工具类，禁止实例化 */
    private FormFieldShell() {
    }

    // ==================== 默认路径：外观跟随来源主题（不传 FormTheme） ====================

    /**
     * 构建跟随来源主题的字段外壳并挂载控件，控件高度取来源主题 {@code inputHeight}。
     *
     * <p>卡片表面取 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#GROUP} 配方
     * （染色/边框/圆角/浮雕/滤镜全归 {@link SceneSurfaceBinder}）；error/dirty 只改缘色，
     * 语义文字取 {@link FormThemes#resolve} 的来源主题映射。来源主题切换
     * （{@link SceneThemes#withTheme}）只重派生外观，不重建节点。</p>
     *
     * @param rt          场景运行时
     * @param title       字段标题（caller 已做回退，例如 label 为空时回退 path）
     * @param helper      帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal 错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal 脏态 signal
     * @param controlFn   控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @return 字段卡片节点（已挂载控件；error 文本条件挂载，空 error 时不占位）
     */
    public static SceneNode build(SceneRuntime rt, String title, String helper,
                                  ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                  Supplier<SceneNode> controlFn) {
        ReadableSignal<FormTheme> theme = FormThemes.resolve(rt);
        return buildCore(rt, title, helper, errorSignal, dirtySignal, controlFn,
                theme, theme.get().inputHeight(), true, true);
    }

    /**
     * 构建跟随来源主题的字段外壳并挂载控件，按字段自带高度设定控件根 preferredHeight。
     *
     * <p><b>字段自带高度</b>（Qt/Flutter/Android/Compose/Web 共识）：控件根的高度下界由
     * 字段自身决定，表单壳不强行压塌。caller 通过 {@code controlHeight} 传字段自带高度——
     * 单行字段传 {@code theme.inputHeight()}，多行字段（如 SIMPLE_LIST）传
     * {@code theme.listHeight()}；传 {@code <=0} 时不设 preferredHeight，让控件自带或
     * 由容器布局决定（守 FormFieldShell 类头"零 config 依赖"：controlHeight 是纯 int，
     * 不感知任何 FieldType 概念）。</p>
     *
     * @param rt            场景运行时
     * @param title         字段标题（caller 已做回退，例如 label 为空时回退 path）
     * @param helper        帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal   错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal   脏态 signal
     * @param controlFn     控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param controlHeight 控件根 preferredHeight；{@code <=0} 时不设，让控件/容器决定
     * @return 字段卡片节点（已挂载控件；error 文本条件挂载，空 error 时不占位）
     */
    public static SceneNode build(SceneRuntime rt, String title, String helper,
                                  ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                  Supplier<SceneNode> controlFn, int controlHeight) {
        return buildCore(rt, title, helper, errorSignal, dirtySignal, controlFn,
                FormThemes.resolve(rt), controlHeight, true, true);
    }

    /**
     * 构建跟随来源主题的无卡片边框字段外壳，供内部项目本身已使用对象卡片的复合字段使用。
     *
     * <p>无边框模板不调用表面绑定器：节点保持无底色/无边框/无圆角/无滤镜，
     * 「不因主题又加出卡片」；error/dirty/helper 仍经状态点与文案各自可见、语义可辨。</p>
     *
     * @param rt          场景运行时
     * @param title       字段标题（caller 已做回退）
     * @param helper      帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal 错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal 脏态 signal
     * @param controlFn   控件构建函数
     * @return 无卡片边框的字段外壳节点（已挂载控件）
     */
    public static SceneNode buildBorderless(SceneRuntime rt, String title, String helper,
                                            ReadableSignal<String> errorSignal,
                                            ReadableSignal<Boolean> dirtySignal,
                                            Supplier<SceneNode> controlFn) {
        return buildCore(rt, title, helper, errorSignal, dirtySignal, controlFn,
                FormThemes.resolve(rt), 0, true, false);
    }

    // ==================== 显式路径：旧 FormTheme 重载（语义不变，显式优先） ====================

    /**
     * 构建字段外壳并挂载控件（显式 {@link FormTheme}，语义与迁移前一致）。
     *
     * @param rt          场景运行时
     * @param title       字段标题（caller 已做回退，例如 label 为空时回退 path）
     * @param helper      帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal 错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal 脏态 signal
     * @param controlFn   控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param theme       主题 token（完全覆盖来源主题）
     * @return 字段卡片节点（已挂载控件；error 文本条件挂载，空 error 时不占位）
     */
    public static SceneNode build(SceneRuntime rt, String title, String helper,
                                  ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                  Supplier<SceneNode> controlFn, FormTheme theme) {
        Objects.requireNonNull(theme, "theme");
        return build(rt, title, helper, errorSignal, dirtySignal, controlFn, theme, theme.inputHeight());
    }

    /**
     * 构建字段外壳并挂载控件，按字段自带高度设定控件根 preferredHeight
     * （显式 {@link FormTheme}，语义与迁移前一致）。
     *
     * <p><b>字段自带高度</b>（Qt/Flutter/Android/Compose/Web 共识）：控件根的高度下界由
     * 字段自身决定，表单壳不强行压塌。caller 通过 {@code controlHeight} 传字段自带高度——
     * 单行字段传 {@code theme.inputHeight()}，多行字段（如 SIMPLE_LIST）传
     * {@code theme.listHeight()}；传 {@code <=0} 时不设 preferredHeight，让控件自带或
     * 由容器布局决定（守 FormFieldShell 类头"零 config 依赖"：controlHeight 是纯 int，
     * 不感知任何 FieldType 概念）。</p>
     *
     * @param rt            场景运行时
     * @param title         字段标题（caller 已做回退，例如 label 为空时回退 path）
     * @param helper        帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal   错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal   脏态 signal
     * @param controlFn     控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param theme         主题 token（完全覆盖来源主题）
     * @param controlHeight 控件根 preferredHeight；{@code <=0} 时不设，让控件/容器决定
     * @return 字段卡片节点（已挂载控件；error 文本条件挂载，空 error 时不占位）
     */
    public static SceneNode build(SceneRuntime rt, String title, String helper,
                                  ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                  Supplier<SceneNode> controlFn, FormTheme theme, int controlHeight) {
        Objects.requireNonNull(theme, "theme");
        return buildCore(rt, title, helper, errorSignal, dirtySignal, controlFn,
                () -> theme, controlHeight, false, true);
    }

    /**
     * 构建无卡片边框的字段外壳（显式 {@link FormTheme}），供内部项目本身已使用对象卡片的复合字段使用。
     *
     * @param rt          场景运行时
     * @param title       字段标题（caller 已做回退）
     * @param helper      帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal 错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal 脏态 signal
     * @param controlFn   控件构建函数
     * @param theme       主题 token（完全覆盖来源主题）
     * @return 无卡片边框的字段外壳节点（已挂载控件）
     */
    public static SceneNode buildBorderless(SceneRuntime rt, String title, String helper,
                                            ReadableSignal<String> errorSignal,
                                            ReadableSignal<Boolean> dirtySignal,
                                            Supplier<SceneNode> controlFn, FormTheme theme) {
        Objects.requireNonNull(theme, "theme");
        return buildCore(rt, title, helper, errorSignal, dirtySignal, controlFn,
                () -> theme, 0, false, false);
    }

    // ==================== 共用外壳装配 ====================

    /**
     * 外壳装配核心：两条外观路径共用同一结构、布局与挂载策略，只在「外观写入者」上分流。
     *
     * @param rt           场景运行时
     * @param title        字段标题
     * @param helper       帮助文本（{@code null}/空串时不渲染）
     * @param errorSignal  错误文案 signal
     * @param dirtySignal  脏态 signal
     * @param controlFn    控件构建函数
     * @param theme        表单主题信号：默认路径为 {@link FormThemes#resolve} 的派生，显式路径为常量
     * @param controlHeight 控件根 preferredHeight；{@code <=0} 时不设
     * @param themedSurface true = 表面/语义色跟随来源主题重派生；false = 旧静态写入语义
     * @param bordered      true = 卡片表面（默认路径装表面绑定 / 显式路径静态写入）；false = 无边框模板
     * @return 字段外壳节点
     */
    private static SceneNode buildCore(SceneRuntime rt, String title, String helper,
                                       ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                       Supplier<SceneNode> controlFn, ReadableSignal<FormTheme> theme,
                                       int controlHeight, boolean themedSurface, boolean bordered) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(controlFn, "controlFn");
        // 布局与字号：两条路径都取表单主题（主题不接管布局，FormThemes 映射的布局分量与旧默认档同源）。
        FormTheme tokens = theme.get();

        SceneNode card = SceneNode.column();
        card.setGap(tokens.fieldGap());
        card.setPadding(bordered ? tokens.cardPad() : 0);

        if (bordered) {
            if (themedSurface) {
                // 默认路径：染色/边框/圆角/浮雕/滤镜全归表面绑定器（GROUP 配方 + error/dirty 缘色派生）。
                bindCardSurface(rt, card, theme, errorSignal, dirtySignal);
            } else {
                // 显式路径：旧静态写入者独占，语义与迁移前一致（不装滤镜、不订阅主题）。
                card.setBackgroundColor(tokens.cardBg());
                card.setBorderWidth(1);
                card.setCornerRadius(tokens.cardRadius());
                rt.__bindAnimatedColor(() -> resolveCardBorder(errorSignal.get(), dirtySignal.get(), theme.get()),
                        card::setBorderColor, SceneChromeTokens.MOTION_STANDARD_MS);
            }
        } else {
            // 无边框模板：节点保持无底色/无边框/无圆角。
            // 默认路径不调用表面绑定器（避免「先绑定再静态覆盖」的双写入者，也避免主题加出卡片）；
            // 显式路径保留旧缘色绑定（边框宽 0，不可见），语义与迁移前一致。
            card.setBackgroundColor(0);
            card.setBorderWidth(0);
            card.setCornerRadius(0);
            if (!themedSurface) {
                rt.__bindAnimatedColor(() -> resolveCardBorder(errorSignal.get(), dirtySignal.get(), theme.get()),
                        card::setBorderColor, SceneChromeTokens.MOTION_STANDARD_MS);
            }
        }

        // header：状态圆点 + 标题
        SceneNode header = SceneNode.row();
        header.setGap(tokens.fieldGap());
        SceneNode dot = text("●", dotColor(tokens, errorSignal.get(), dirtySignal.get()), tokens.fontLabel());
        if (themedSurface) {
            // 默认路径：状态点随来源主题与 error/dirty 重派生（errorText > accent > mutedForeground）。
            rt.bindComputed(() -> Integer.valueOf(dotColor(theme.get(), errorSignal.get(), dirtySignal.get())),
                    dot::setTextColor);
        } else {
            // 显式路径：旧动画派生，语义与迁移前一致（error > dirty > muted）。
            rt.__bindAnimatedColor(() -> dotColor(theme.get(), errorSignal.get(), dirtySignal.get()),
                    dot::setTextColor, SceneChromeTokens.MOTION_STANDARD_MS);
        }
        SceneNode titleNode = text(safe(title), tokens.textColor(), tokens.fontLabel());
        if (themedSurface) {
            rt.bindComputed(() -> Integer.valueOf(theme.get().textColor()), titleNode::setTextColor);
        }
        header.appendChild(dot);
        header.appendChild(titleNode);
        card.appendChild(header);

        // helper 文本：始终取次要前景（mutedForeground），不随 error/dirty 改色。
        if (helper != null && !helper.isEmpty()) {
            SceneNode helperNode = text(helper, tokens.mutedColor(), tokens.fontHelper());
            if (themedSurface) {
                rt.bindComputed(() -> Integer.valueOf(theme.get().mutedColor()), helperNode::setTextColor);
            }
            card.appendChild(helperNode);
        }

        // 控件 mount 槽
        MountHandle handle = rt.mount(card, controlFn);
        SceneNode controlRoot = handle.getRoot();
        if (controlRoot != null && controlHeight > 0) {
            controlRoot.setPreferredHeight(controlHeight);
        }

        // error 文本：errorSignal 非空时才挂载（rt.show 条件渲染，守 R11）
        // 空文案时仅留零尺寸 anchor 占位，不再常驻空 errorNode（m1：消除空节点常驻）。
        // bind 注册在 content supplier 内，随 show 卸载自动退订，重挂时重建。
        // 作用域：condition Computed 归属调用方当前 mount Owner；show handle 内部 Owner
        // 是该 mount Owner 的子 Owner；二者都会随外壳卸载自动清理——此处不单独持有 handle、不单独 dispose，避免误用。
        rt.show(card, Computed.create(() -> !safe(errorSignal.get()).isEmpty()), () -> {
            SceneNode errorNode = text("", theme.get().errorColor(), theme.get().fontError());
            rt.bind(errorSignal, errorNode::setText);
            rt.bindComputed(() -> safe(errorSignal.get()).isEmpty() ? Integer.valueOf(theme.get().mutedColor())
                            : Integer.valueOf(theme.get().errorColor()),
                    errorNode::setTextColor);
            return errorNode;
        });

        return card;
    }

    /**
     * 把来源主题的 GROUP 配方绑到字段卡片：染色/边框/圆角/浮雕/滤镜全归表面绑定器。
     *
     * <p>error/dirty 不改表面归属，只把配方缘色替换为 {@link FormThemes} 映射的
     * {@code cardBorderError}（errorText 危险语义）/ {@code cardBorderDirty}（accent 强调语义），
     * 因此边框色仍只有表面绑定器一个写入者，不产生同属性双写。</p>
     *
     * <p>构建期先声明关心 hover/pressed/focus：Router 对未创建的 signal 直接短路，
     * 不声明则后续事件驱动不了配方状态档（与 {@code FormPageShell} 同构）。</p>
     *
     * @param rt          runtime
     * @param card        字段卡片节点
     * @param theme       表单主题信号（来源主题派生）
     * @param errorSignal 错误文案 signal
     * @param dirtySignal 脏态 signal
     */
    private static void bindCardSurface(SceneRuntime rt, SceneNode card, ReadableSignal<FormTheme> theme,
                                        ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal) {
        ReadableSignal<SceneSurfaceStyle> base = SceneThemes.surface(rt, SceneTheme.Role.GROUP);
        // 初值在构建期读取（base/theme 均为带初值的派生信号）：flush 前也可读，且不制造双写入者。
        SceneSurfaceStyle initial = resolveCardSurface(base.get(), theme.get(),
                errorSignal.get(), dirtySignal.get());
        ReadableSignal<SceneSurfaceStyle> surface = Computed.create(initial,
                () -> resolveCardSurface(base.get(), theme.get(), errorSignal.get(), dirtySignal.get()));
        SceneInteractionState interaction = rt.interactionState(card);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, card, surface, ALWAYS_ENABLED, interaction);
    }

    /**
     * 解析卡片配方：默认取来源主题 GROUP 配方；error/dirty 只替换四态缘色（染色/圆角/浮雕/滤镜不变）。
     *
     * @param base  来源主题 GROUP 配方
     * @param theme 表单主题（提供 error/dirty 语义色）
     * @param error 错误文案
     * @param dirty 是否脏
     * @return 卡片配方
     */
    private static SceneSurfaceStyle resolveCardSurface(SceneSurfaceStyle base, FormTheme theme,
                                                        String error, Boolean dirty) {
        if (!safe(error).isEmpty()) {
            return withEdge(base, theme.cardBorderError());
        }
        if (Boolean.TRUE.equals(dirty)) {
            return withEdge(base, theme.cardBorderDirty());
        }
        return base;
    }

    /** 把配方的四态缘色统一替换为给定色，其余表面分量原样保留。 */
    private static SceneSurfaceStyle withEdge(SceneSurfaceStyle base, int edge) {
        return base.toBuilder()
                .idle(withEdge(base.getIdle(), edge))
                .hovered(withEdge(base.getHovered(), edge))
                .pressed(withEdge(base.getPressed(), edge))
                .disabled(withEdge(base.getDisabled(), edge))
                .build();
    }

    /** 单态缘色替换（染色/浮雕/透镜系数保持不变）。 */
    private static SceneSurfaceStyle.StateStyle withEdge(SceneSurfaceStyle.StateStyle state, int edge) {
        return new SceneSurfaceStyle.StateStyle(state.getTint(), edge, state.getElevation(), state.getLensFactor());
    }

    /**
     * 创建不可命中、带初始文本、颜色与字号的文字节点。
     *
     * @param value    文本
     * @param color    颜色
     * @param fontSize 字号（UI 像素）
     * @return 文字节点
     */
    private static SceneNode text(String value, int color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 解析状态点色：error &gt; dirty &gt; muted（修正旧逻辑 dirty+error 同时为真时显示蓝的小不一致）。
     *
     * <p>默认路径下 {@code errorColor}/{@code dirtyColor}/{@code mutedColor} 即来源主题的
     * {@code errorText}/{@code accent}/{@code mutedForeground}，三类色值互不相同。</p>
     *
     * @param theme 主题 token
     * @param error 错误文案
     * @param dirty 是否脏
     * @return 状态点色
     */
    private static int dotColor(FormTheme theme, String error, Boolean dirty) {
        if (!safe(error).isEmpty()) {
            return theme.errorColor();
        }
        if (Boolean.TRUE.equals(dirty)) {
            return theme.dirtyColor();
        }
        return theme.mutedColor();
    }

    /**
     * 解析卡片边框色：error &gt; dirty &gt; default（显式路径沿用旧语义）。
     *
     * @param error 错误文案
     * @param dirty 是否脏
     * @param theme 主题 token
     * @return 边框色
     */
    private static int resolveCardBorder(String error, Boolean dirty, FormTheme theme) {
        if (!safe(error).isEmpty()) {
            return theme.cardBorderError();
        }
        if (Boolean.TRUE.equals(dirty)) {
            return theme.cardBorderDirty();
        }
        return theme.cardBorder();
    }

    /**
     * null 安全文本。
     *
     * @param value 文本
     * @return 非 null 文本
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
