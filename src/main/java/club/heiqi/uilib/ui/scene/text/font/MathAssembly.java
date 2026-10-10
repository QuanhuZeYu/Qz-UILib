package club.heiqi.uilib.ui.scene.text.font;

import club.heiqi.uilib.ui.scene.text.font.nativefont.MathData;
import club.heiqi.uilib.ui.scene.text.math.MathAxis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** OpenType MATH 一维连接算法；只消费 Java 快照，不读取墨迹、不缩放字形。 */
public final class MathAssembly {
    // 限制单个构建的需求数量和浮点坐标范围；超限沿数学字体诊断，不无界增加部件。
    private static final int MAX_PARTS = 4096;
    private static final double MAX_ADVANCE = 1048576;

    private MathAssembly() {}

    public static final class Placement {
        public final MathData.Part part;

        /** 从轴起点的字形原点向前的距离；竖向向上，横向向右。 */
        public final float offset;

        private Placement(MathData.Part part, double offset) {
            this.part = part;
            this.offset = (float) offset;
        }
    }

    public static final class Result {
        private final List<Placement> placements;
        public final float stretchAdvance;

        /** 旧竖向字段；横向结果返回 NaN。 */
        public final float verticalAdvance;

        public final MathAxis axis;

        private Result(List<Placement> placements, double advance, MathAxis axis) {
            this.placements = Collections.unmodifiableList(placements);
            this.axis = axis;
            this.stretchAdvance = (float) advance;
            this.verticalAdvance = axis == MathAxis.VERTICAL ? stretchAdvance : Float.NaN;
        }

        public List<Placement> placements() {
            return placements;
        }
    }

    public static Result build(MathData.Assembly assembly, float target) {
        if (!Float.isFinite(target) || target < 0)
            throw new IllegalArgumentException("数学伸缩目标必须有限且非负");
        if (assembly == null || assembly.parts().isEmpty()) throw unavailable("字体没有轴向 assembly");
        if (target > MAX_ADVANCE) throw unavailable("目标超过浮点几何能力");
        if (!Float.isFinite(assembly.minOverlap)
                || assembly.minOverlap < 0
                || !Float.isFinite(assembly.italicCorrection))
            throw unavailable("assembly 重叠或倾斜修正非法");
        int fixed = 0, extenders = 0;
        for (MathData.Part part : assembly.parts()) {
            if (part.glyphId <= 0
                    || !Float.isFinite(part.fullAdvance)
                    || part.fullAdvance <= 0
                    || !Float.isFinite(part.startConnector)
                    || part.startConnector < 0
                    || part.startConnector > part.fullAdvance
                    || !Float.isFinite(part.endConnector)
                    || part.endConnector < 0
                    || part.endConnector > part.fullAdvance) throw unavailable("assembly 部件度量非法");
            if (part.extender) extenders++;
            else fixed++;
        }
        double previousMaximum = -1;
        for (int repeats = 0; ; repeats++) {
            long count = fixed + (long) extenders * repeats;
            if (count > MAX_PARTS) throw unavailable("assembly 超过部件数量上限 " + MAX_PARTS);
            if (count == 0) {
                if (extenders == 0) throw unavailable("assembly 没有部件");
                continue;
            }
            List<MathData.Part> parts = new ArrayList<MathData.Part>((int) count);
            for (MathData.Part part : assembly.parts())
                for (int i = 0, copies = part.extender ? repeats : 1; i < copies; i++)
                    parts.add(part);
            double full = 0, overlapSum = 0;
            double[] overlaps = new double[parts.size() - 1];
            boolean legal = true;
            for (int i = 0; i < parts.size(); i++) {
                MathData.Part part = parts.get(i);
                full += part.fullAdvance;
                if (i > 0) {
                    double overlap = Math.min(parts.get(i - 1).endConnector, part.startConnector);
                    if (overlap < assembly.minOverlap) legal = false;
                    overlaps[i - 1] = overlap;
                    overlapSum += overlap;
                }
            }
            if (!legal) {
                // 去掉 extender 会产生字体未承诺的直接连接；加入各一个后才判断不可构建。
                if (repeats == 0 && extenders > 0) continue;
                throw unavailable("assembly 连接长度不足最小重叠");
            }
            double minimum = full - overlapSum;
            double maximum = full - overlaps.length * (double) assembly.minOverlap;
            if (maximum >= target) {
                double desired = Math.max(minimum, target);
                if (desired > MAX_ADVANCE) throw unavailable("assembly 超过浮点几何能力");
                double extension = equalExtension(overlaps, assembly.minOverlap, desired - minimum);
                List<Placement> positions = new ArrayList<Placement>(parts.size());
                double offset = 0;
                for (int i = 0; i < parts.size(); i++) {
                    positions.add(new Placement(parts.get(i), offset));
                    if (i < overlaps.length) {
                        double overlap =
                                overlaps[i]
                                        - Math.min(extension, overlaps[i] - assembly.minOverlap);
                        offset += parts.get(i).fullAdvance - overlap;
                    }
                }
                return new Result(
                        positions, offset + parts.get(parts.size() - 1).fullAdvance, assembly.axis);
            }
            if (extenders == 0) throw unavailable("assembly 无 extender，无法达到目标");
            // 第一轮含 extender 可能增加此前非法的连接，不能与零 extender 的结果比较。
            if (repeats > 1 && maximum <= previousMaximum)
                throw unavailable("assembly extender 不能继续增加轴向长度");
            previousMaximum = maximum;
        }
    }

    /** 各连接等量伸展；先到最小重叠的连接停止，其余继续均分，维护花括号对称。 */
    private static double equalExtension(double[] overlaps, double minimum, double extra) {
        double[] capacities = overlaps.clone();
        for (int i = 0; i < capacities.length; i++) capacities[i] -= minimum;
        Arrays.sort(capacities);
        double level = 0;
        for (int i = 0; i < capacities.length; i++) {
            int active = capacities.length - i;
            double needed = (capacities[i] - level) * active;
            if (extra <= needed) return level + extra / active;
            extra -= needed;
            level = capacities[i];
        }
        return level;
    }

    private static UnsupportedOperationException unavailable(String message) {
        return new UnsupportedOperationException(message);
    }
}
