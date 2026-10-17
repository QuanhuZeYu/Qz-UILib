package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.Collections;
import java.util.List;

/** 自定义控件声明内容与区域；测量/排列/绘制不能写业务状态或回写布局。 */
public interface SceneControl {
    /** null 表示普通流；所属层策略不得返回 null。 */
    default SceneLayer.Policy layerPolicy(SceneNode node) { return null; }

    /** 所属层只声明位置；null 沿默认锚定，尺寸仍由正式测量决定，裁剪与范围仍归核心。 */
    default LayoutBox placeLayer(SceneNode node, LayoutBox sourceBox, LayoutBox viewport, MeasuredSize size) {
        return null;
    }

    /** 主键释放前可更新位置意图；仅当前有效 owner 收到，框架结算变化并重新准备正式几何后才派 UP。
     * 默认无操作，不合成 MOVE；此阶段不发布放下或激活动作。 */
    default void preparePointerRelease(SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event, SceneGeometryView geometry) {}

    /** 只读输入时间需求；MAX 表示静止。实际读取的 Signal 随挂载订阅，不逐帧扫描控件树。 */
    default long nextInputNanos(SceneNode node) { return Long.MAX_VALUE; }

    /** 有效几何后、绘制前每宿主批次至多一次；可发布交互呈现状态，不在 measure/paint 推进。 */
    default void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {}

    /** 本批活动输入使共同几何改变后同步交互呈现；可随重排结算多次。
     * 与 inputFrame 分离：只消费新几何，不推进时间、激活等待或再次滚动。 */
    default void inputGeometryChanged(SceneNode node, SceneGeometryView geometry) {}

    /** 本批活动输入或后续 Effect 失败时，只结束本批实际参与控件的临时输入状态。
     * 与指针捕获丢失分别通知，不能补发业务提交或重复 captureLost。 */
    default void inputFrameCancelled(SceneNode node) {}

    /** 只有显式声明的子 activator 可把其命中交给祖先；普通子按钮仍优先。 */
    default Object pointerActivator(SceneNode node, SceneNode hit, SceneGeometryView geometry, int x, int y) {
        return null;
    }

    /** 显式子 activator 的键盘委派，不建立通用编辑冒泡。 */
    default boolean editActivator(SceneNode node, SceneNode focused,
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event) { return false; }
    /** 声明测量使用的响应来源；框架在挂载期订阅，变更重新测量。 */
    default List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 仅定位或视口变化：复用内容度量，在共同几何中更新排列与命中。 */
    default List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 仅绘制状态变化，不使内容度量或几何失效。 */
    default List<ReadableSignal<?>> paintDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 只读的下一次绘制时刻（System.nanoTime同一时基）；有限过渡结束返回MAX_VALUE。
     * 不请求测量/布局，不创建线程或在绘制阶段写业务状态；未声明的控件继续复用静态计划。 */
    default long nextPaintNanos(SceneNode node) { return Long.MAX_VALUE; }

    /** 唯一 scene 布局的文本入口，属性内容不另建排版树。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout layoutText(
            SceneNode node, SceneTextMeasurer text, int width) {
        return text.layout(node.text(), node.style(), width, node.textPolicy());
    }

    /** 复合内容显式接收同批直属子测量能力；文字服务本身不持scene节点。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout layoutText(
            SceneNode node, SceneTextMeasurer text, int width, SceneChildMeasurer children) {
        return layoutText(node, text, width);
    }

    /** 内部目标身份与资格；几何仅来自当前 Entry，输入阶段不解析或重排。 */
    default Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
        return null;
    }

    default boolean pointerTargetValid(SceneNode node, Object target) {
        return false;
    }

    /** 唯一 router 已确认同目标、同挂载后由 runtime 分发。 */
    default void activatePointer(SceneNode node, Object target) {}

    /** constraints 是框架已扣除 node.padding 的正文约束，返回正文自然尺寸。 */
    MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text);

    /** 布局引擎为组合控件提供子测量能力；既有纯文字控件沿原入口执行。 */
    default MeasuredSize measure(
            SceneNode node,
            Constraints constraints,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return measure(node, constraints, text);
    }

    /**
     * allocated 是本次分配的外层盒，包含 node.padding；子盒位置相对此盒原点。
     * children 只测量直属逻辑子项，复用本批布局事实，不能保留到后续批次。
     * 普通容器在这里按实际正文空间测量、排列，不借文字呈现准备结果。
     */
    ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children);

    /** 内容受实际文字区域限制后的不可变定位；不回写文本或几何。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text) {
        return full;
    }

    /** 真实文字/文档/数学内容投影使用同批子测量；普通容器的排列由 arrange 承担。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return presentText(node, full, width, height, text);
    }

    void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target);

    /** 只控制后代绘制，布局、挂载及输入事实保持；例如排序来源的不可见插槽。 */
    default boolean paintChildren(SceneNode node) { return true; }

    /** 本节点当前有效几何子树结束后、下一兄弟内容之前的局部装饰。 与paint共用同代几何和失效生命周期；全页tooltip仍由paintOverlay处理。 */
    default void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}

    /** 只读呈现准备；仅在现有paint失效路径调用，动态文字缓存消费当前主题/资源/viewport。 */
    default void preparePaint(
            SceneNode node,
            SceneGeometryView geometry,
            SceneTextMeasurer text,
            long metricsRevision) {}

    /** 返回已挂载的直属逻辑占位子树。框架捕获同代paint，不创建挂载或修改hidden声明。 主输入几何仍依显隐事实；捕获计划仅作本次资源不可用时的呈现分支。 */
    default SceneNode paintFallbackChild(SceneNode node) {
        return null;
    }

    /** fallbackPaint是框架沿同一布局/paint能力捕获的不可变子片段，不可回访活节点。 */
    default void paint(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            club.heiqi.uilib.ui.scene.paint.PaintPlan fallbackPaint) {
        paint(node, geometry, target);
    }

    /** 唯一当前焦点接收编辑事件；返回消费，不处理宿主原生队列。 */
    default boolean edit(SceneNode node, club.heiqi.uilib.ui.scene.input.SceneEditEvent event) {
        return false;
    }

    /** 键盘几何与指针消费同一事实；既有控件保持原 edit 接口。 */
    default boolean edit(SceneNode node, club.heiqi.uilib.ui.scene.input.SceneEditEvent event,
            SceneGeometryView geometry) { return edit(node, event); }

    /** 只有需要DOWN定位的控件申请；普通按钮仍在UP聚焦。 */
    default boolean capturesPointer() {
        return false;
    }

    /** 是否让普通滚轮推动节点的轴向滚动位置；复合双轴视口可指定主滚轮轴。
     * 像素滚动、导航显露及范围计算不受影响，未消费事件仍由唯一路由交给祖先。 */
    default boolean scrollsWithWheel(SceneNode node) { return true; }

    /** 内部内容视口参与正式滚轮命中，不要求外层布局盒成为滚动容器。 */
    default boolean scrollableContent() {
        return false;
    }

    /** 专用内容公开的滚动轴；标准视口默认包含条，页面无需另加节点。 */
    default SceneScrollArea.Axes scrollbarAxes(SceneNode node) { return SceneScrollArea.Axes.NONE; }

    /** 框架装饰在完整内容几何后定位，不参与宿主内容测量或范围。 */
    default LayoutBox placeScrollDecoration(SceneNode node, SceneGeometryView geometry) { return null; }

    /** 专用内容视口的同批事实；缺省null由普通Node滚动适配。位置仍归控件。 */
    default SceneScrollViewport.Snapshot scrollViewport(SceneNode node, SceneNode.Axis axis, SceneGeometryView geometry) { return null; }

    /** 专用内容像素移动；返回实际位移，runtime统一按变化失效共同几何。 */
    default int scrollByPixels(SceneNode node, SceneNode.Axis axis, int pixels, SceneGeometryView geometry) { return 0; }

    /** 正式结果区分消费与变化；旧扩展的boolean按既有“消费即变化”语义接入。 */
    default SceneScrollResult scrollResult(SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event, SceneGeometryView geometry) {
        return scroll(node, event, geometry) ? SceneScrollResult.CHANGED : SceneScrollResult.IGNORED;
    }

    /** 同一受控输入分发；消费后不同时滚动外层容器。 */
    default boolean scroll(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {
        return false;
    }

    /**
     * 主动Tab导航在查询几何前请求控件内部必要显露；只调整内部呈现，不请求编辑提交。
     * 返回true时框架使排列失效并重排，再查询navigationRegion；缺省不改变内部视口。
     */
    default boolean prepareNavigation(SceneNode node, SceneGeometryView geometry) {
        return false;
    }

    /**
     * Tab显露的内部焦点/编辑区域，使用当前Entry对应的scene坐标；缺省null沿节点整盒。
     * 仅在节点大于祖先视口的轴优先使用，查询不能重排、写状态或自行滚动祖先。
     * 区域应表达已由控件内部视口协调的当前事实，不预先套祖先clip。
     */
    default LayoutBox navigationRegion(SceneNode node, SceneGeometryView geometry) {
        return null;
    }

    /** 当前只读/可编辑事实由控件声明，宿主据此启停真实文本输入。 */
    default boolean acceptsTextInput(SceneNode node) {
        return false;
    }

    /**
     * 唯一 router 取消原 owner 时的内部清理；目标可能已经隐藏、禁用或卸载。
     * 不依赖几何，不激活、提交或执行业务 handler；正常 UP 仍由 pointer 处理。
     */
    default void captureLost(SceneNode node) {}

    /** 当前捕获明确参与 ESC 取消；只读查询，真正终止仍归唯一 router。 */
    default boolean cancelCaptureOnEscape(SceneNode node) { return false; }

    /** 指针定位/拖选沿已有owner，区域来自本次生产布局。 */
    default void pointer(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {}

    /** 正常焦点离开与失活原因分开；失活/关闭不得隐含提交。 */
    default void focusChanged(SceneNode node, boolean focused, boolean eligible) {}

    /** 无激活动作的控件可参与被动悬停，不建立 click/capture/focus。 */
    default boolean passiveHover() {
        return false;
    }

    /** 自动提示只声明当前事实；显式绑定优先，由页面协调统一触发与呈现。 */
    default SceneTooltip.Hint tooltip(SceneNode node,SceneGeometryView geometry){return null;}

    /** 被动提示在内容末端绘制，不创建命中/布局节点。 */
    default void paintOverlay(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            SceneTextMeasurer text) {}
}
