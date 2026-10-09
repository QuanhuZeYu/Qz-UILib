package club.heiqi.uilib.ui.scene.control;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
/** 自定义控件声明内容与区域；测量/排列/绘制不能写业务状态或回写布局。 */
public interface SceneControl {
    /** 声明测量使用的响应来源；框架在挂载期订阅，变更重新测量。 */
    default List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.emptyList(); }
    MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text);
    ChildPlacements arrange(SceneNode node, LayoutBox allocated);
    /** 内容受实际文字区域限制后的不可变定位；不回写文本或几何。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(SceneNode node, club.heiqi.uilib.ui.scene.text.TextLayout full, int width, int height, SceneTextMeasurer text) { return full; }
    void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target);
    /** 唯一当前焦点接收编辑事件；返回消费，不处理宿主原生队列。 */
    default boolean edit(SceneNode node, club.heiqi.uilib.ui.scene.input.SceneEditEvent event) { return false; }
    /** 只有需要DOWN定位的控件申请；普通按钮仍在UP聚焦。 */
    default boolean capturesPointer() { return false; }
    /** 当前只读/可编辑事实由控件声明，宿主据此启停真实文本输入。 */
    default boolean acceptsTextInput(SceneNode node) { return false; }
    /** 指针定位/拖选沿已有owner，区域来自本次生产布局。 */
    default void pointer(SceneNode node, club.heiqi.uilib.ui.scene.input.ScenePointerEvent event, SceneGeometryView geometry) {}
    /** 正常焦点离开与失活原因分开；失活/关闭不得隐含提交。 */
    default void focusChanged(SceneNode node, boolean focused, boolean eligible) {}
    /** 被动提示在内容末端绘制，不创建命中/布局节点。 */
    default void paintOverlay(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target, SceneTextMeasurer text) {}
}
