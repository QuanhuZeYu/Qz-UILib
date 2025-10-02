package club.heiqi.qz_uilib.widget.drawUtil;

import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;

public class RoundedRectangle extends Shape {

    private double r;
    private int seg;
    public void gen(double width, double height, double r, int seg, Vector2d offset, int color) {
        if (!set(width, height, offset, color) && Math.abs(this.r - r) <= 0.01 && this.seg == seg) {
            return;
        }
        else {
            this.r = r;
            this.seg = seg;

            // 钳制r避免意外值
            r = Math.min(Math.min(r, width / 2), height / 2);

            // 计算角度步进
            int totalCount = 4 + seg * 4;
            double perAngle = (double) 360 / totalCount;

            // 中心坐标
            Vector3d center = new Vector3d(width / 2, height / 2, 0);
            center.add(offset.x, offset.y, 0);
            vertex.add(center);

            int indexCounter = 0;
            // 生成左上角半圆
            double start = 180;
            double end = 90;
            double curAngle = start;
            index.add((short) 0);
            indexCounter++;
            while (curAngle >= end) {
                double x = Math.cos(Math.toRadians(curAngle)) * r + r;
                double y = Math.sin(Math.toRadians(curAngle)) * r + r;
                Vector3d pos = new Vector3d(x, y, 0);
                pos.add(offset.x, offset.y + height - r * 2, 0);
                vertex.add(pos);

                index.add((short) vertex.indexOf(pos));
                indexCounter++;
                if (indexCounter % 3 == 0) {  // 每两个外围点添加一次中心点
                    index.add((short) vertex.indexOf(pos));  // 再一次添加该点
                    index.add((short) 0);
                    indexCounter += 2;  // 添加外围计数器
                }

                curAngle -= perAngle;
            }

            // 生成右上角半圆
            start = 90;
            end = 0;
            curAngle = start;
            while (curAngle >= end) {
                double x = Math.cos(Math.toRadians(curAngle)) * r + r;
                double y = Math.sin(Math.toRadians(curAngle)) * r + r;
                Vector3d pos = new Vector3d(x, y, 0);
                pos.add(offset.x + width - r * 2, offset.y + height - r * 2, 0);
                vertex.add(pos);

                index.add((short) vertex.indexOf(pos));
                indexCounter++;
                if (indexCounter % 3 == 0) {  // 每两个外围点添加一次中心点
                    index.add((short) vertex.indexOf(pos));  // 再一次添加该点
                    index.add((short) 0);
                    indexCounter += 2;  // 添加外围计数器
                }

                curAngle -= perAngle;
            }

            // 生成右下角半圆
            start = 0;
            end = -90;
            curAngle = start;
            while (curAngle >= end) {
                double x = Math.cos(Math.toRadians(curAngle)) * r + r;
                double y = Math.sin(Math.toRadians(curAngle)) * r + r;
                Vector3d pos = new Vector3d(x, y, 0);
                pos.add(offset.x + width - r * 2, offset.y, 0);
                vertex.add(pos);

                index.add((short) vertex.indexOf(pos));
                indexCounter++;
                if (indexCounter % 3 == 0) {  // 每两个外围点添加一次中心点
                    index.add((short) vertex.indexOf(pos));  // 再一次添加该点
                    index.add((short) 0);
                    indexCounter += 2;  // 添加外围计数器
                }

                curAngle -= perAngle;
            }

            // 生成左下角半圆
            start = -90;
            end = -180;
            curAngle = start;
            while (curAngle >= end) {
                double x = Math.cos(Math.toRadians(curAngle)) * r + r;
                double y = Math.sin(Math.toRadians(curAngle)) * r + r;
                Vector3d pos = new Vector3d(x, y, 0);
                pos.add(offset.x, offset.y, 0);
                vertex.add(pos);

                index.add((short) vertex.indexOf(pos));
                indexCounter++;
                if (indexCounter % 3 == 0) {  // 每两个外围点添加一次中心点
                    index.add((short) vertex.indexOf(pos));  // 再一次添加该点
                    index.add((short) 0);
                    indexCounter += 2;  // 添加外围计数器
                }

                curAngle -= perAngle;
            }

            // 对于索引最后需要完成最后的闭合
            index.add((short) 1);  // 圆角矩形的第一个点

            build();
        }
    }
}
