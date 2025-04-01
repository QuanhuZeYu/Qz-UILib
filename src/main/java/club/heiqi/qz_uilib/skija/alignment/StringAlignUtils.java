package club.heiqi.qz_uilib.skija.alignment;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import org.joml.Vector2f;

public class StringAlignUtils {

    /**
     * 将文字左下角坐标对齐到指定坐标
     * @param text 需要放入的文字
     * @param font 文字使用的字体
     * @param targetPos 需要对齐到的左下角坐标
     * @return 返回计算好的可直接使用的文字坐标
     */
    public static Vector2f textBLToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft()/*将文字左侧对齐到x:0*/;
        float offsetY = -rect.getBottom()/*将文字下侧对齐到y:0*/;
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textTLToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft();
        float offsetY = -rect.getTop();
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textBRToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft() - rect.getRight();
        float offsetY = -rect.getBottom();
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textTRToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft() - rect.getRight();
        float offsetY = -rect.getTop();
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textCenterToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = (-rect.getLeft() - rect.getRight())/2;
        float offsetY = (-rect.getTop())/2;
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textTopCenterToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = (-rect.getLeft() - rect.getRight())/2;
        float offsetY = -rect.getTop();
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textBottomCenterToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = (-rect.getLeft() - rect.getRight())/2;
        float offsetY = -rect.getBottom();
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textLeftCenterToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft();
        float offsetY = (-rect.getBottom())/2;
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }

    public static Vector2f textRightCenterToTarget(String text, Font font, Vector2f targetPos) {
        Rect rect = font.measureText(text);
        float offsetX = -rect.getLeft() - rect.getRight();
        float offsetY = (-rect.getBottom())/2;
        return new Vector2f(offsetX + targetPos.x, offsetY + targetPos.y);
    }
}
