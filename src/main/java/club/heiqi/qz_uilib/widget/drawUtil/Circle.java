package club.heiqi.qz_uilib.widget.drawUtil;

import club.heiqi.qz_uilib.MyMod;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class Circle extends Shape {

    public void gen(double r, int seg, Vector2d offset) {
        vertex.clear();
        this.index.clear();
        int vertexCount = 4 + seg * 4;
        double perAngle = (double) 360 / vertexCount;

        double startAngle = 0;
        double endAngle = 90;
        List<Vector3d> temp = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (double j = startAngle; j < endAngle; j += perAngle) {
                double x = Math.cos(Math.toRadians(j)) * r;
                double y = Math.sin(Math.toRadians(j)) * r;
                Vector3d pos = new Vector3d(x, y, 0);
                pos.add(offset.x,offset.y,0).add(r, r,0);
                temp.add(pos);
            }
            startAngle += 90;
            endAngle += 90;
        }
        Vector3d center = new Vector3d();
        center.add(offset.x, offset.y, 0).add(r,r,0);
        vertex.add(center);

        int i = 0;
        for (Vector3d pos : temp) {
            vertex.add(pos);    this.index.add((short) (temp.indexOf(pos) + 1));
            short toGet = (short) (((i + 1) % temp.size()) + 1);
            this.index.add(toGet);
            this.index.add((short) 0);
            i++;
        }
    }


    public static void main(String[] a) {
        Circle circle = new Circle();
        circle.gen(5, 5, new Vector2d(100));
        for (Vector3d vertex : circle.vertex) {
            MyMod.LOG.info("({},{})",
                    String.format("%.2f", vertex.x),
                    String.format("%.2f", vertex.y));
        }
    }
}
