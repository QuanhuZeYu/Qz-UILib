package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.video.VideoPlacement;
import club.heiqi.uilib.ui.scene.video.MediaViewPump;


import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;

import club.heiqi.uilib.ui.scene.node.SceneNode;

import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 正式视频叶控件；只读已选不可变帧，布局/命中仍由 scene 维护。 */
public final class SceneVideo implements SceneControl {
    public static final class Options {
        public final VideoPlacement.Fit fit;
        public final float alignX, alignY;
        public Options(VideoPlacement.Fit fit, float alignX, float alignY) {
            this.fit = Objects.requireNonNull(fit, "fit");
            if (!Float.isFinite(alignX) || !Float.isFinite(alignY) || alignX < 0 || alignX > 1 || alignY < 0 || alignY > 1)
                throw new IllegalArgumentException("视频对齐须在0到1之间");
            this.alignX = alignX; this.alignY = alignY;
        }
        public static Options defaults() { return new Options(VideoPlacement.Fit.CONTAIN, .5f, .5f); }
    }
    private final MediaViewPump.View view;
    private final ReadableSignal<Options> options;
    private SceneVideo(MediaViewPump.View view, ReadableSignal<Options> options) { this.view = view; this.options = options; }
    public static SceneNode create(ScenePageContext page, MediaSession session) {
        Options options = Options.defaults(); return create(page, session, () -> options);
    }
    public static SceneNode create(ScenePageContext page, MediaSession session, ReadableSignal<Options> options) {
        MediaViewPump pump = page.mediaViews();
        MediaViewPump.View view = pump.view(page.domain(), session);
        SceneNode node = new SceneNode().control(new SceneVideo(view, Objects.requireNonNull(options, "options")));
        node.bind((runtime, target, owner) -> pump.attach(view, runtime.domain()));
        return node;
    }
    public static ReadableSignal<MediaSnapshot> snapshot(SceneNode node) {
        if (!(node.control() instanceof SceneVideo)) throw new IllegalArgumentException("不是视频控件");
        return ((SceneVideo) node.control()).view.snapshot();
    }
    @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(view.extent()); }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(view.frame(), options); }
    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
        MediaViewPump.Extent size = view.extent().get();
        int w = size.width, h = size.height;
        if (w > 0 && h > 0) {
            if (node.width().mode != Size.Mode.NATURAL && node.height().mode == Size.Mode.NATURAL) {
                // 正文约束已由layout扣padding并应用外层bounds，不能再次读FIXED原值。
                w = constraints.availableWidth;
                h = round((double) w * size.height / size.width);
            } else if (node.width().mode == Size.Mode.NATURAL && node.height().mode != Size.Mode.NATURAL && constraints.availableHeight != Integer.MAX_VALUE) {
                h = constraints.availableHeight;
                w = round((double) h * size.width / size.height);
            }
        }
        // 返回正文自然尺寸；外层padding和Size边界只由同一layout引擎合成。
        return new MeasuredSize(w, h);
    }
    private static int round(double value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(value))); }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
        return new ChildPlacements(Collections.emptyList());
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        if ((node.background() >>> 24) != 0) target.add(PaintCommand.rect(entry.box, entry.clip, node.background()));
        MediaVideoFrame frame = view.frame().get();
        if (frame == null) return;
        LayoutBox box = SceneControlPaint.inner(entry.box, node.padding());
        LayoutBox clip = entry.clip == null ? box : box.intersect(entry.clip);
        Options value = options.get();
        target.add(PaintCommand.video(box, clip, VideoPlacement.fit(frame, box, value.fit, value.alignX, value.alignY)));
    }
}
