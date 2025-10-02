package club.heiqi.qz_uilib.widget.drawUtil;

import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;

/**gen -> build*/
public abstract class Shape {
    public double width, height;
    public int colorCache;
    public Vector2d position = new Vector2d();
    public final ArrayList<Vector3d> vertex = new ArrayList<>();
    public final ArrayList<Vector2f> texCoord = new ArrayList<>();
    public final ArrayList<Vector4f> color = new ArrayList<>();
    public final ArrayList<Short> index = new ArrayList<>();

    public boolean set(double width, double height, Vector2d pos, int color) {
        if (Math.abs(this.width - width) <= 0.01 && Math.abs(this.height - height) <= 0.01
                && this.position.equals(pos.x, pos.y)
                && colorCache == color
        ) {
            return false;  // 数据无需更新
        }
        else {
            forceSet(width, height, pos, color);
            return true;
        }
    }

    public void forceSet(double width, double height, Vector2d pos, int color) {
        clean();
        this.width = width;
        this.height = height;
        this.position = pos;
        this.colorCache = color;
    }

    public void clean() {
        this.vertex.clear();
        this.texCoord.clear();
        this.color.clear();
        this.index.clear();
    }

    public float[] getVertexArray() {
        float[] result = new float[vertex.size() * 3];
        for (int i = 0; i < vertex.size(); i++) {
            Vector3d pos = vertex.get(i);
            result[i * 3] = (float) pos.x;
            result[i * 3 + 1] = (float) pos.y;
            result[i * 3 + 2] = (float) pos.z;
        }
        return result;
    }

    public float[] getTexCoordArray() {
        float[] result = new float[texCoord.size() * 2];
        for (int i = 0; i < texCoord.size(); i++) {
            Vector2f tex = texCoord.get(i);
            result[i * 2] = tex.x;
            result[i * 2 + 1] = tex.y;
        }
        return result;
    }

    public float[] getColorArray() {
        float[] result = new float[color.size() * 4];
        for (int i = 0; i < color.size(); i++) {
            Vector4f pos = color.get(i);
            result[i * 4] = pos.x;
            result[i * 4 + 1] = pos.y;
            result[i * 4 + 2] = pos.z;
            result[i * 4 + 3] = pos.w;
        }
        return result;
    }

    public int[] getIndexArray() {
        int[] result = new int[index.size()];
        for (int i = 0; i < index.size(); i++) {
            result[i] = index.get(i);
        }
        return result;
    }

    /**自动构建纹理坐标和顶点颜色*/
    public void build() {
        float red = (float) ((colorCache >> 16) & 255) / 255;
        float green = (float) ((colorCache >> 8) & 255) / 255;
        float blue = (float) ((colorCache) & 255) / 255;
        float alpha = (float) ((colorCache >> 24) & 255) / 255;
        buildColor(red, green, blue, alpha);
        buildTexCoord();
    }

    public void buildColor(float red, float green, float blue, float alpha) {
        if (vertex.isEmpty())
            throw new RuntimeException("错误的函数调用顺序!");
        else {
            for (int i = 0; i < vertex.size(); i++) {
                color.add(new Vector4f(red, green, blue, alpha));  // 为每个顶点赋予同样的颜色
            }
        }
    }

    public void buildTexCoord() {
        if (vertex.isEmpty())
            throw new RuntimeException("错误的函数调用顺序!");
        else {
            for (Vector3d pos : vertex) {
                Vector2f localCoord = new Vector2f((float) (pos.x - position.x), (float) (pos.y - position.y));
                Vector2f texCoord = new Vector2f((float) (localCoord.x / width), (float) (localCoord.y / height));
                this.texCoord.add(texCoord);
            }
        }
    }
}
