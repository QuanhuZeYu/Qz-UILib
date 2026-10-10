package club.heiqi.uilib.ui.render.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 有序提交段。软件与GL消费者共用载荷；资源版本由资源服务持有，不暴露GL纹理ID。 */
public final class OrderedQuadBatch {
    /** 每quad四个顶点；x/y、u/v、图集层、ARGB由回放写入实际顶点格式。 */
    public static final class Quad {
        public final float x, y, width, height, u0, v0, u1, v1;
        public final int layer, color;

        public Quad(
                float x,
                float y,
                float width,
                float height,
                float u0,
                float v0,
                float u1,
                float v1,
                int layer,
                int color) {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(width)
                    || !Float.isFinite(height)
                    || !Float.isFinite(x + width)
                    || !Float.isFinite(y + height)
                    || width < 0
                    || height < 0
                    || !Float.isFinite(u0)
                    || !Float.isFinite(v0)
                    || !Float.isFinite(u1)
                    || !Float.isFinite(v1)
                    || layer < 0) throw new IllegalArgumentException("无效图元");
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.layer = layer;
            this.color = color;
        }
    }

    /** 相邻合批状态。变换和深度由外层提交范围固定，变化时先结束该段。 */
    public static final class State {
        public final long resource, generation;
        public final int material;
        public final LayoutBox clip;

        public State(int material, long resource, long generation, LayoutBox clip) {
            this.material = material;
            this.resource = resource;
            this.generation = generation;
            this.clip = clip;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof State)) return false;
            State other = (State) value;
            return material == other.material
                    && resource == other.resource
                    && generation == other.generation
                    && (clip == other.clip
                            || clip != null
                                    && other.clip != null
                                    && clip.x == other.clip.x
                                    && clip.y == other.clip.y
                                    && clip.width == other.clip.width
                                    && clip.height == other.clip.height);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    material,
                    resource,
                    generation,
                    clip == null ? 0 : Objects.hash(clip.x, clip.y, clip.width, clip.height));
        }
    }

    public static final class Draw {
        public final State state;
        public final int firstQuad, quadCount;

        Draw(State state, int firstQuad, int quadCount) {
            this.state = state;
            this.firstQuad = firstQuad;
            this.quadCount = quadCount;
        }
    }

    private final List<Quad> quads;
    private final List<Draw> draws;

    OrderedQuadBatch(List<Quad> quads, List<Draw> draws) {
        this.quads = Collections.unmodifiableList(new ArrayList<>(quads));
        this.draws = Collections.unmodifiableList(new ArrayList<>(draws));
    }

    public List<Quad> quads() {
        return quads;
    }

    public List<Draw> draws() {
        return draws;
    }

    /** 固定EBO的容量模板；只在建立或扩容时调用，不在每帧重复生成。 */
    public static int[] quadIndices(int capacity) {
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / 6)
            throw new IllegalArgumentException("无效索引容量");
        int[] result = new int[capacity * 6];
        for (int q = 0; q < capacity; q++) {
            int v = q * 4, i = q * 6;
            result[i] = v;
            result[i + 1] = v + 1;
            result[i + 2] = v + 2;
            result[i + 3] = v;
            result[i + 4] = v + 2;
            result[i + 5] = v + 3;
        }
        return result;
    }
}
