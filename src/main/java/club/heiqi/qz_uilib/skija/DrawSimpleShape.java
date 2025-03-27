package club.heiqi.qz_uilib.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;

public class DrawSimpleShape {

    public static void drawRoundRect(Canvas canvas, float x, float y, float width, float height, float radius, Paint paint) {
        canvas.drawRRect(RRect.makeXYWH(x,y,width,height,radius), paint);
    }
}
