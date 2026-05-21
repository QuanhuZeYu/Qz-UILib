package club.heiqi.uilib.ui.animation;

import java.util.EnumMap;
import java.util.Objects;

import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * 单个动画属性的运行时取值与 transition 限制规则。
 *
 * <p>每个枚举常量对应一个 {@link DocumentAnimationProperty}，提供该属性的基础值解析、
 * keyframe 归一化和 transition 可动画性判断。</p>
 */
enum PropertyRuntimeSemantics {
    BACKGROUND_COLOR(DocumentAnimationProperty.BACKGROUND_COLOR) {
        @Override
        int resolveBaseColor(ComputedStyle style) {
            return style.getBackgroundColor();
        }
    },
    BORDER_COLOR(DocumentAnimationProperty.BORDER_COLOR) {
        @Override
        int resolveBaseColor(ComputedStyle style) {
            return style.getBorderColor();
        }
    },
    TEXT_COLOR(DocumentAnimationProperty.TEXT_COLOR) {
        @Override
        int resolveBaseColor(ComputedStyle style) {
            return style.getTextColor();
        }
    },
    OPACITY(DocumentAnimationProperty.OPACITY) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getComputedStyle().getOpacity();
        }

        @Override
        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    },
    BORDER_RADIUS(DocumentAnimationProperty.BORDER_RADIUS) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveBorderRadius(box);
        }

        @Override
        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            int limit = Math.min(box.getWidth(), box.getHeight());
            return Math.max(0.0F, Math.min(value, limit / 2.0F));
        }
    },
    BOX_SHADOW_COLOR(DocumentAnimationProperty.BOX_SHADOW_COLOR) {
        @Override
        int resolveBaseColor(ComputedStyle style) {
            UiBoxShadow shadow = style.getBoxShadow();
            return shadow == null ? 0 : shadow.getColor();
        }
    },
    BOX_SHADOW_OFFSET_X(DocumentAnimationProperty.BOX_SHADOW_OFFSET_X) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            UiBoxShadow shadow = box.getComputedStyle().getBoxShadow();
            return shadow == null ? 0.0F : shadow.getOffsetX();
        }
    },
    BOX_SHADOW_OFFSET_Y(DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            UiBoxShadow shadow = box.getComputedStyle().getBoxShadow();
            return shadow == null ? 0.0F : shadow.getOffsetY();
        }
    },
    BOX_SHADOW_BLUR_RADIUS(DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            UiBoxShadow shadow = box.getComputedStyle().getBoxShadow();
            return shadow == null ? 0.0F : shadow.getBlurRadius();
        }
    },
    BOX_SHADOW_SPREAD_RADIUS(DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            UiBoxShadow shadow = box.getComputedStyle().getBoxShadow();
            return shadow == null ? 0.0F : shadow.getSpreadRadius();
        }
    },
    TRANSLATE_X(DocumentAnimationProperty.TRANSLATE_X) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveTransform(box).getTranslateX();
        }
    },
    TRANSLATE_Y(DocumentAnimationProperty.TRANSLATE_Y) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveTransform(box).getTranslateY();
        }
    },
    SCALE_X(DocumentAnimationProperty.SCALE_X) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveTransform(box).getScaleX();
        }
    },
    SCALE_Y(DocumentAnimationProperty.SCALE_Y) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveTransform(box).getScaleY();
        }
    },
    ROTATE(DocumentAnimationProperty.ROTATE) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveTransform(box).getRotateDegrees();
        }
    },
    BACKDROP_BLUR_RADIUS(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return resolveBackdropBlurRadius(box);
        }

        @Override
        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            return Math.max(0.0F, Math.min(value, DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS));
        }
    },
    WIDTH(DocumentAnimationProperty.WIDTH) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getContentWidth();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getWidth());
        }
    },
    HEIGHT(DocumentAnimationProperty.HEIGHT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getContentHeight();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getHeight());
        }
    },
    TOP(DocumentAnimationProperty.TOP) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getResolvedTopInset();
        }
    },
    RIGHT(DocumentAnimationProperty.RIGHT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getResolvedRightInset();
        }
    },
    BOTTOM(DocumentAnimationProperty.BOTTOM) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getResolvedBottomInset();
        }
    },
    LEFT(DocumentAnimationProperty.LEFT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getResolvedLeftInset();
        }
    },
    MARGIN_LEFT(DocumentAnimationProperty.MARGIN_LEFT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getMargin().getLeft();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getMargin().getLeft());
        }
    },
    MARGIN_RIGHT(DocumentAnimationProperty.MARGIN_RIGHT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getMargin().getRight();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getMargin().getRight());
        }
    },
    PADDING_LEFT(DocumentAnimationProperty.PADDING_LEFT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getPadding().getLeft();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getPadding().getLeft());
        }

        @Override
        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            return Math.max(0.0F, value);
        }
    },
    PADDING_RIGHT(DocumentAnimationProperty.PADDING_RIGHT) {
        @Override
        float resolveBaseFloat(DocumentLayoutBox box) {
            return box.getPadding().getRight();
        }

        @Override
        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return isPixelLength(box.getComputedStyle().getPadding().getRight());
        }

        @Override
        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            return Math.max(0.0F, value);
        }
    };

    private static final EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> BY_PROPERTY =
            createLookup();

    private final DocumentAnimationProperty property;

    private PropertyRuntimeSemantics(DocumentAnimationProperty property) {
        this.property = property;
    }

    /**
     * 根据动画属性查找对应的运行时语义。
     *
     * @param property 动画属性
     * @return 对应的运行时语义枚举常量
     * @throws IllegalArgumentException 如果属性不受支持
     */
    static PropertyRuntimeSemantics forProperty(DocumentAnimationProperty property) {
        PropertyRuntimeSemantics semantics = BY_PROPERTY.get(Objects.requireNonNull(property, "property"));
        if (semantics == null) {
            throw new IllegalArgumentException("unsupported animation property: " + property);
        }
        return semantics;
    }

    private static EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> createLookup() {
        EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> lookup =
                new EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics>(DocumentAnimationProperty.class);
        for (PropertyRuntimeSemantics semantics : values()) {
            lookup.put(semantics.property, semantics);
        }
        return lookup;
    }

    private static boolean isPixelLength(UiStyleLength length) {
        return length.getType() == UiStyleLength.Type.PIXEL;
    }

    private static int resolveBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }

    private static int resolveBackdropBlurRadius(DocumentLayoutBox box) {
        int availableSpace = Math.max(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBackdropBlurRadius().resolve(availableSpace, 0);
        return Math.max(0, Math.min(radius, DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS));
    }

    private static UiTransform resolveTransform(DocumentLayoutBox box) {
        UiTransform transform = box.getComputedStyle().getTransform();
        return transform == null ? UiTransform.identity() : transform;
    }

    /**
     * 解析属性的基础颜色值。
     *
     * @param style 计算样式
     * @return 颜色 ARGB 值
     */
    int resolveBaseColor(ComputedStyle style) {
        throw new IllegalArgumentException("color value is not supported for: " + property);
    }

    /**
     * 解析属性的基础浮点值。
     *
     * @param box 布局盒
     * @return 浮点值
     */
    float resolveBaseFloat(DocumentLayoutBox box) {
        throw new IllegalArgumentException("float value is not supported for: " + property);
    }

    /**
     * 判断浮点 transition 目标是否可动画。
     *
     * @param box 布局盒
     * @return 是否可动画
     */
    boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
        return true;
    }

    /**
     * 归一化声明式 keyframe 浮点值。
     *
     * @param box   布局盒
     * @param value 原始值
     * @return 归一化后的值
     */
    float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
        return value;
    }
}
