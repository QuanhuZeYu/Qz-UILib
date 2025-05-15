package club.heiqi.qz_uilib.skija.alignment;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import org.joml.Vector2f;

import java.util.List;

/**
 * 组件对齐工具包，方法第一个参数为操作对象，第二个为目标对象
 */
public class ComponentAlignUtils {
    public static enum ComponentAlign {
        CENTER_CENTER,
        LEFT_LEFT,
        TOP_TOP,
        RIGHT_RIGHT,
        BOTTOM_BOTTOM,
        LEFT_RIGHT,
        TOP_BOTTOM,
        RIGHT_LEFT,
        BOTTOM_TOP,
        HORIZON_CENTER_ALIGN,
        VERTICAL_CENTER_ALIGN
    }

    public static <T extends UIComponent> T align(T operate, T referenceOBJ, ComponentAlign align) {
        float opL = operate.x;
        float opR = opL + operate.width;
        float opCenterX = (opL + opR) / 2f;
        float opT = operate.y;
        float opB = opT + operate.height;
        float opCenterY = (opT + opB) / 2f;

        float refL = referenceOBJ.x;
        float refR = refL + referenceOBJ.width;
        float refCenterX = (refL + refR) / 2f;
        float refT = referenceOBJ.y;
        float refB = refT + referenceOBJ.height;
        float refCenterY = (refT + refB) / 2f;

        switch (align) {
            case CENTER_CENTER -> {
                Vector2f vec = new Vector2f(refCenterX, refCenterY).sub(opCenterX, opCenterY);
                operate.x += vec.x;
                operate.y += vec.y;
            }
            case LEFT_LEFT -> {
                operate.x = refL;
            }
            case TOP_TOP -> {
                operate.y = refT;
            }
            case RIGHT_RIGHT -> {
                operate.x = refR - operate.width;
            }
            case BOTTOM_BOTTOM -> {
                operate.y = refB - operate.height;
            }
            case LEFT_RIGHT -> {
                operate.x = refR;
            }
            case TOP_BOTTOM -> {
                operate.y = refB;
            }
            case RIGHT_LEFT -> {
                operate.x = refL - operate.width;
            }
            case BOTTOM_TOP -> {
                operate.y = refT - operate.height;
            }
            case HORIZON_CENTER_ALIGN -> {
                float dx = refCenterX - opCenterX;
                operate.x += dx;
            }
            case VERTICAL_CENTER_ALIGN -> {
                float dy = refCenterY - opCenterY;
                operate.y += dy;
            }
            default -> throw new IllegalArgumentException("Unexpected align type: " + align);
        }

        return operate;
    }

    public static <T extends UIComponent> T[] align(T[] operates, T referenceOBJ, ComponentAlign align) {
        for (T operate : operates) {
            align(operate, referenceOBJ, align);
        }
        return operates;
    }

    public static <T extends UIComponent> List<T> align(List<T> operates, T referenceOBJ, ComponentAlign align) {
        for (T operate : operates) {
            align(operate, referenceOBJ, align);
        }
        return operates;
    }
}
