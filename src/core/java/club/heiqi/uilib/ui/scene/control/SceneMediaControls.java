package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;



import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.video.MediaViewPump;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** 标准按钮/标签/编辑字段的媒体组合，不另建手势、输入或播放器状态机。 */
public final class SceneMediaControls {
    private SceneMediaControls() {}
    public static SceneNode create(ScenePageContext page, MediaSession session) {
        ReactiveDomain domain = page.domain();
        MediaViewPump pump = page.mediaViews(); MediaViewPump.View view = pump.view(domain, session);
        ReadableSignal<MediaSnapshot> state = view.snapshot();
        SceneNode root = new SceneNode().axis(SceneNode.Axis.COLUMN).gap(8).size(Size.fill(1), Size.natural());
        root.bind((runtime, node, owner) -> pump.attach(view, runtime.domain()));
        root.add(SceneLabel.create(page, () -> description(state.get()), page.theme()));
        SceneNode buttons = new SceneNode().axis(SceneNode.Axis.ROW).gap(8).size(Size.fill(1), Size.natural());
        buttons.add(SceneButton.create(page, () -> state.get().playIntent ? "暂停" : "播放", page.theme(),
                () -> { if (session.snapshot().playIntent) session.pause(); else session.play(); }));
        buttons.add(SceneButton.create(page, () -> state.get().muted ? "取消静音" : "静音", page.theme(),
                () -> session.muted(!session.snapshot().muted)));
        buttons.bind(NodeBinding.value(() -> state.get().state != MediaState.CLOSED && state.get().state != MediaState.FAILED, SceneNode::enabled));
        root.add(buttons);
        root.add(SceneLabel.create(page, () -> "音量 " + String.format(Locale.ROOT, "%.0f%%", state.get().volume * 100), page.theme()));
        SceneEditState volume = new SceneEditState(domain, Double.toString(state.get().volume));
        root.add(SceneTextField.create(page, volume, page.theme(), () -> state.get().state == MediaState.CLOSED,
                submission -> {
                    try {
                        double value = Double.parseDouble(submission.text.trim());
                        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("音量须为0到1");
                        session.volume(value); volume.accept(submission, Double.toString(value));
                    } catch (IllegalArgumentException | IllegalStateException failure) { volume.reject(submission, failure.getMessage()); }
                }, SceneTextField.SubmitPolicy.CONFIRM, () -> "音量（0到1）"));
        SceneEditState seek = new SceneEditState(domain, "");
        root.add(SceneTextField.create(page, seek, page.theme(), () -> state.get().info.seekable == null || state.get().state == MediaState.CLOSED,
                submission -> {
                    try {
                        long nanos = new BigDecimal(submission.text.trim()).movePointRight(9).setScale(0, RoundingMode.HALF_UP).longValueExact();
                        MediaRange range = session.snapshot().info.seekable;
                        if (range == null || !range.contains(nanos)) throw new IllegalArgumentException("定位目标不在当前有效范围");
                        session.seek(nanos); seek.accept(submission, submission.text.trim());
                    } catch (ArithmeticException | IllegalArgumentException | IllegalStateException failure) { seek.reject(submission, failure.getMessage()); }
                }, SceneTextField.SubmitPolicy.CONFIRM, () -> "定位秒数，Enter确认"));
        return root;
    }
    private static String description(MediaSnapshot snapshot) {
        String time = String.format(Locale.ROOT, "%.1f", snapshot.positionNanos / 1_000_000_000d);
        String duration = snapshot.info.durationKnown() ? String.format(Locale.ROOT, "%.1f秒", snapshot.info.durationNanos / 1_000_000_000d) : "未知时长";
        String range = snapshot.info.seekable == null ? "不可定位" : String.format(Locale.ROOT, "可定位 %.1f–%.1f秒", snapshot.info.seekable.startNanos / 1_000_000_000d, snapshot.info.seekable.endNanos / 1_000_000_000d);
        return snapshot.state + "  " + time + "秒 / " + duration + "  " + range
                + (snapshot.failure == null ? "" : "  " + snapshot.failure.stage + ": " + snapshot.failure.cause);
    }
}
