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

    MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text);

    /** 布局引擎为组合控件提供子测量能力；既有纯文字控件沿原入口执行。 */
    default MeasuredSize measure(
            SceneNode node,
            Constraints constraints,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return measure(node, constraints, text);
    }

    ChildPlacements arrange(SceneNode node, LayoutBox allocated);

    /** 内容受实际文字区域限制后的不可变定位；不回写文本或几何。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text) {
        return full;
    }

    /** 缓存命中也消费传入的本次文字事实，子项排列不能沿用其它宽度的残留。 */
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

    /** 只有需要DOWN定位的控件申请；普通按钮仍在UP聚焦。 */
    default boolean capturesPointer() {
        return false;
    }

    /** 内部内容视口参与正式滚轮命中，不要求外层布局盒成为滚动容器。 */
    default boolean scrollableContent() {
        return false;
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

    /** 被动提示在内容末端绘制，不创建命中/布局节点。 */
    default void paintOverlay(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            SceneTextMeasurer text) {}
}
