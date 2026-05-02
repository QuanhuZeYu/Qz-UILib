package club.heiqi.uilib.ui.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 命名 keyframes 定义。
 *
 * <p>当前 MVP 支持 paint/effect 属性，以及受控 layout 数值属性的多段 stop 列表，供作者侧 `animation-name` 复用。</p>
 */
public final class DocumentKeyframes {

    private final String name;
    private final Map<DocumentAnimationProperty, ColorTrack> colorTracks;
    private final Map<DocumentAnimationProperty, FloatTrack> floatTracks;

    private DocumentKeyframes(String name, Map<DocumentAnimationProperty, ColorTrack> colorTracks,
            Map<DocumentAnimationProperty, FloatTrack> floatTracks) {
        this.name = normalizeName(name);
        EnumMap<DocumentAnimationProperty, ColorTrack> colorCopy =
                new EnumMap<DocumentAnimationProperty, ColorTrack>(DocumentAnimationProperty.class);
        EnumMap<DocumentAnimationProperty, FloatTrack> floatCopy =
                new EnumMap<DocumentAnimationProperty, FloatTrack>(DocumentAnimationProperty.class);
        colorCopy.putAll(colorTracks);
        floatCopy.putAll(floatTracks);
        this.colorTracks = Collections.unmodifiableMap(colorCopy);
        this.floatTracks = Collections.unmodifiableMap(floatCopy);
    }

    /**
     * 创建命名 keyframes builder。
     *
     * @param name keyframes 名称
     * @return builder
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * 返回 keyframes 名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 返回颜色属性 stop 轨道。
     *
     * @return 颜色属性 stop 轨道
     */
    public Map<DocumentAnimationProperty, ColorTrack> getColorTracks() {
        return colorTracks;
    }

    /**
     * 返回数值属性 stop 轨道。
     *
     * @return 数值属性 stop 轨道
     */
    public Map<DocumentAnimationProperty, FloatTrack> getFloatTracks() {
        return floatTracks;
    }

    /**
     * 返回是否不包含任何属性轨道。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return colorTracks.isEmpty() && floatTracks.isEmpty();
    }

    private static String normalizeName(String value) {
        String normalized = Objects.requireNonNull(value, "name").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        return normalized;
    }

    private static float normalizeOffset(float offset) {
        if (Float.isNaN(offset) || offset < 0.0F || offset > 1.0F) {
            throw new IllegalArgumentException("keyframe stop offset must be in [0, 1]: " + offset);
        }
        if (offset == 0.0F) {
            return 0.0F;
        }
        if (offset == 1.0F) {
            return 1.0F;
        }
        return offset;
    }

    /**
     * keyframes builder。
     */
    public static final class Builder {

        private final String name;
        private final EnumMap<DocumentAnimationProperty, NavigableMap<Float, Integer>> colorTracks =
                new EnumMap<DocumentAnimationProperty, NavigableMap<Float, Integer>>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, NavigableMap<Float, Float>> floatTracks =
                new EnumMap<DocumentAnimationProperty, NavigableMap<Float, Float>>(DocumentAnimationProperty.class);

        private Builder(String name) {
            this.name = normalizeName(name);
        }

        /**
         * 添加颜色属性首末帧。
         *
         * @param property 动画属性
         * @param fromColor 首帧颜色
         * @param toColor 末帧颜色
         * @return 当前 builder
         */
        public Builder setColor(DocumentAnimationProperty property, int fromColor, int toColor) {
            DocumentAnimationProperty resolvedProperty = requireColorProperty(property);
            NavigableMap<Float, Integer> track = new TreeMap<Float, Integer>();
            track.put(Float.valueOf(0.0F), Integer.valueOf(fromColor));
            track.put(Float.valueOf(1.0F), Integer.valueOf(toColor));
            colorTracks.put(resolvedProperty, track);
            floatTracks.remove(resolvedProperty);
            return this;
        }

        /**
         * 添加或替换颜色属性 stop。
         *
         * @param property 动画属性
         * @param offset stop 偏移，取值范围为 0..1
         * @param color stop 颜色
         * @return 当前 builder
         */
        public Builder setColorStop(DocumentAnimationProperty property, float offset, int color) {
            DocumentAnimationProperty resolvedProperty = requireColorProperty(property);
            NavigableMap<Float, Integer> track = getOrCreateColorTrack(resolvedProperty);
            track.put(Float.valueOf(normalizeOffset(offset)), Integer.valueOf(color));
            floatTracks.remove(resolvedProperty);
            return this;
        }

        /**
         * 添加数值属性首末帧。
         *
         * @param property 动画属性
         * @param fromValue 首帧值
         * @param toValue 末帧值
         * @return 当前 builder
         */
        public Builder setFloat(DocumentAnimationProperty property, float fromValue, float toValue) {
            DocumentAnimationProperty resolvedProperty = requireFloatProperty(property);
            NavigableMap<Float, Float> track = new TreeMap<Float, Float>();
            track.put(Float.valueOf(0.0F), Float.valueOf(fromValue));
            track.put(Float.valueOf(1.0F), Float.valueOf(toValue));
            floatTracks.put(resolvedProperty, track);
            colorTracks.remove(resolvedProperty);
            return this;
        }

        /**
         * 添加或替换数值属性 stop。
         *
         * @param property 动画属性
         * @param offset stop 偏移，取值范围为 0..1
         * @param value stop 数值
         * @return 当前 builder
         */
        public Builder setFloatStop(DocumentAnimationProperty property, float offset, float value) {
            DocumentAnimationProperty resolvedProperty = requireFloatProperty(property);
            NavigableMap<Float, Float> track = getOrCreateFloatTrack(resolvedProperty);
            track.put(Float.valueOf(normalizeOffset(offset)), Float.valueOf(value));
            colorTracks.remove(resolvedProperty);
            return this;
        }

        /**
         * 创建不可变 keyframes 定义。
         *
         * @return keyframes 定义
         */
        public DocumentKeyframes build() {
            EnumMap<DocumentAnimationProperty, ColorTrack> resolvedColorTracks =
                    new EnumMap<DocumentAnimationProperty, ColorTrack>(DocumentAnimationProperty.class);
            EnumMap<DocumentAnimationProperty, FloatTrack> resolvedFloatTracks =
                    new EnumMap<DocumentAnimationProperty, FloatTrack>(DocumentAnimationProperty.class);
            for (Map.Entry<DocumentAnimationProperty, NavigableMap<Float, Integer>> entry : colorTracks.entrySet()) {
                resolvedColorTracks.put(entry.getKey(), new ColorTrack(createColorStops(entry.getKey(),
                        entry.getValue())));
            }
            for (Map.Entry<DocumentAnimationProperty, NavigableMap<Float, Float>> entry : floatTracks.entrySet()) {
                resolvedFloatTracks.put(entry.getKey(), new FloatTrack(createFloatStops(entry.getKey(),
                        entry.getValue())));
            }
            return new DocumentKeyframes(name, resolvedColorTracks, resolvedFloatTracks);
        }

        private NavigableMap<Float, Integer> getOrCreateColorTrack(DocumentAnimationProperty property) {
            NavigableMap<Float, Integer> track = colorTracks.get(property);
            if (track == null) {
                track = new TreeMap<Float, Integer>();
                colorTracks.put(property, track);
            }
            return track;
        }

        private NavigableMap<Float, Float> getOrCreateFloatTrack(DocumentAnimationProperty property) {
            NavigableMap<Float, Float> track = floatTracks.get(property);
            if (track == null) {
                track = new TreeMap<Float, Float>();
                floatTracks.put(property, track);
            }
            return track;
        }

        private static List<ColorStop> createColorStops(DocumentAnimationProperty property,
                NavigableMap<Float, Integer> track) {
            if (track.size() < 2) {
                throw new IllegalArgumentException("keyframe track requires at least two stops: " + property);
            }
            List<ColorStop> stops = new ArrayList<ColorStop>(track.size());
            for (Map.Entry<Float, Integer> entry : track.entrySet()) {
                stops.add(new ColorStop(entry.getKey().floatValue(), entry.getValue().intValue()));
            }
            return stops;
        }

        private static List<FloatStop> createFloatStops(DocumentAnimationProperty property,
                NavigableMap<Float, Float> track) {
            if (track.size() < 2) {
                throw new IllegalArgumentException("keyframe track requires at least two stops: " + property);
            }
            List<FloatStop> stops = new ArrayList<FloatStop>(track.size());
            for (Map.Entry<Float, Float> entry : track.entrySet()) {
                stops.add(new FloatStop(entry.getKey().floatValue(), entry.getValue().floatValue()));
            }
            return stops;
        }

        private static DocumentAnimationProperty requireColorProperty(DocumentAnimationProperty property) {
            DocumentAnimationProperty resolvedProperty = Objects.requireNonNull(property, "property");
            if (!resolvedProperty.isColorValue()) {
                throw new IllegalArgumentException("color keyframes are not supported for: " + resolvedProperty);
            }
            return resolvedProperty;
        }

        private static DocumentAnimationProperty requireFloatProperty(DocumentAnimationProperty property) {
            DocumentAnimationProperty resolvedProperty = Objects.requireNonNull(property, "property");
            if (!resolvedProperty.isFloatValue()) {
                throw new IllegalArgumentException("float keyframes are not supported for: " + resolvedProperty);
            }
            return resolvedProperty;
        }
    }

    /**
     * 颜色属性 stop 轨道。
     */
    public static final class ColorTrack {

        private final List<ColorStop> stops;

        private ColorTrack(List<ColorStop> stops) {
            this.stops = Collections.unmodifiableList(new ArrayList<ColorStop>(stops));
        }

        /**
         * 返回按 offset 升序排列的 stop 列表。
         *
         * @return stop 列表
         */
        public List<ColorStop> getStops() {
            return stops;
        }

        /**
         * 返回首个 stop 颜色。
         *
         * @return 首个颜色
         */
        public int getFirstColor() {
            return stops.get(0).getColor();
        }

        /**
         * 返回最后一个 stop 颜色。
         *
         * @return 最后颜色
         */
        public int getLastColor() {
            return stops.get(stops.size() - 1).getColor();
        }
    }

    /**
     * 数值属性 stop 轨道。
     */
    public static final class FloatTrack {

        private final List<FloatStop> stops;

        private FloatTrack(List<FloatStop> stops) {
            this.stops = Collections.unmodifiableList(new ArrayList<FloatStop>(stops));
        }

        /**
         * 返回按 offset 升序排列的 stop 列表。
         *
         * @return stop 列表
         */
        public List<FloatStop> getStops() {
            return stops;
        }

        /**
         * 返回首个 stop 数值。
         *
         * @return 首个数值
         */
        public float getFirstValue() {
            return stops.get(0).getValue();
        }

        /**
         * 返回最后一个 stop 数值。
         *
         * @return 最后数值
         */
        public float getLastValue() {
            return stops.get(stops.size() - 1).getValue();
        }
    }

    /**
     * 颜色 keyframe stop。
     */
    public static final class ColorStop {

        private final float offset;
        private final int color;

        private ColorStop(float offset, int color) {
            this.offset = offset;
            this.color = color;
        }

        /**
         * 返回 stop 偏移，取值范围为 0..1。
         *
         * @return stop 偏移
         */
        public float getOffset() {
            return offset;
        }

        /**
         * 返回 stop 颜色。
         *
         * @return stop 颜色
         */
        public int getColor() {
            return color;
        }
    }

    /**
     * 数值 keyframe stop。
     */
    public static final class FloatStop {

        private final float offset;
        private final float value;

        private FloatStop(float offset, float value) {
            this.offset = offset;
            this.value = value;
        }

        /**
         * 返回 stop 偏移，取值范围为 0..1。
         *
         * @return stop 偏移
         */
        public float getOffset() {
            return offset;
        }

        /**
         * 返回 stop 数值。
         *
         * @return stop 数值
         */
        public float getValue() {
            return value;
        }
    }
}
