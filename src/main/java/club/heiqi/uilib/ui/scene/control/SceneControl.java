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
    /** 被动提示在内容末端绘制，不创建命中/布局节点。 */
    default void paintOverlay(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target, SceneTextMeasurer text) {}
}
