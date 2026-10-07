package club.heiqi.uilib.ui.scene.runtime;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
/** 同一代不可变几何与绘制计划；下一次呈现不改动既有对象。 */
public final class ScenePresentation {
    public final long generation;
    public final SceneGeometry geometry;
    public final PaintPlan paint;
    public ScenePresentation(long generation, SceneGeometry geometry, PaintPlan paint) { this.generation = generation; this.geometry = geometry; this.paint = paint; }
}
