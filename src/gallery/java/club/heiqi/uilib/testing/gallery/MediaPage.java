package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneFilePicker;
import club.heiqi.uilib.ui.scene.control.SceneMediaControls;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 共享媒体页面只声明来源、信息与观看区；播放意图和资源归MediaPlayback。 */
final class MediaPage {
    private final ScenePageContext page;
    private final GalleryEnvironment environment;
    private final ReadableSignal<SceneTheme> theme;
    private final SceneAppearance style;
    private final MediaPlayback playback;

    MediaPage(ScenePageContext page, GalleryEnvironment environment, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.environment = environment; this.theme = theme;
        style = new SceneAppearance(page, theme);
        playback = new MediaPlayback(page.domain(), environment); page.own(playback);
    }

    SceneNode create(Runnable back) {
        MediaCatalogView catalog = new MediaCatalogView(page, environment, playback, style, theme);
        SceneNode root = new SceneNode().control(new InputPump()).size(Size.fill(1), Size.fill(1)).gap(10).own(style.scope)
                .bind(NodeBinding.value(() -> playback.large.get() ? 8 : 20, SceneNode::padding));
        root.add(GalleryStyle.header(page, theme, "媒体播放", "选择一个来源，让声音与画面在这里继续。", back)
                .bind(NodeBinding.value(() -> playback.current.get() == null, SceneNode::visible)));
        root.add(sourceCard()).add(sourceActions());
        root.add(GalleryStyle.label(page, playback.message, theme, 13, true).bind(NodeBinding.value(playback.feedback, SceneNode::visible)));
        root.add(catalog.details()).add(catalog.choices());
        SceneNode player = new SceneNode().size(Size.fill(1), Size.fill(1));
        player.bind(NodeBinding.repeat(this::sessions, session -> session, session -> player(session.get(), back)));
        SceneNode empty = GalleryStyle.card(theme).padding(24).gap(12)
                .add(GalleryStyle.label(page, () -> "准备好，按下播放", theme, 24, false))
                .add(GalleryStyle.label(page, () -> "打开本地文件或粘贴媒体地址，也可以先体验内置示例。", theme, 15, true));
        root.add(GalleryStyle.frame(empty, theme).size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.value(() -> playback.current.get() == null, SceneNode::visible)));
        root.add(player.bind(NodeBinding.value(() -> playback.current.get() != null, SceneNode::visible)));
        SceneNode stage = new SceneNode().size(Size.fill(1), Size.fill(1));
        stage.bind(NodeBinding.repeat(() -> playback.picking.get() ? Arrays.asList(false, true) : Collections.singletonList(false), value -> value,
                item -> item.get() ? SceneFilePicker.create(page, playback.pickerDirectory, theme,
                        new SceneFilePicker.Options().filter("媒体文件", "mp4,mkv,mov,webm,avi,ts,m4v,mp3,wav,flac,ogg,m4a,aac"),
                        playback::file, () -> playback.picking.set(false)) : root));
        return stage;
    }

    private SceneNode sourceCard() {
        SceneNode card = GalleryStyle.card(theme).padding(12).gap(8);
        card.add(GalleryStyle.label(page, () -> "媒体来源", theme, 16, false));
        SceneNode field = SceneTextField.create(page, playback.address, () -> style.colors.get().editorTheme(), () -> false,
                playback::openAddress, SceneTextField.SubmitPolicy.CONFIRM,
                () -> "BV号、Bilibili链接、直播间、本地路径或媒体地址", false);
        card.add(GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(8)
                .add(style.field(field).size(Size.fill(1), Size.natural()))
                .add(GalleryStyle.primary(page, () -> "打开媒体", theme, () -> playback.openAddress(playback.address.request(true)))));
        card.add(GalleryStyle.label(page, () -> "支持Bilibili点播与直播；匿名可观看的内容可直接打开。", theme, 13, true));
        return GalleryStyle.frame(card, theme).bind(NodeBinding.value(
                () -> !playback.large.get() && (playback.sourceExpanded.get() || playback.current.get() == null), SceneNode::visible));
    }

    private SceneNode sourceActions() {
        SceneNode actions = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(8).scrollable(true);
        actions.add(GalleryStyle.button(page, () -> playback.sourceExpanded.get() ? "收起来源" : "更换来源", theme,
                () -> playback.sourceExpanded.set(!playback.sourceExpanded.get()))
                .bind(NodeBinding.value(() -> playback.current.get() != null, SceneNode::visible)));
        actions.add(GalleryStyle.button(page, () -> "选择本地视频", theme, () -> playback.picking.set(true)));
        actions.add(GalleryStyle.button(page, () -> "内置示例", theme, playback::example));
        actions.add(GalleryStyle.button(page, () -> "停止", theme, () -> { stop(); playback.message.set("已停止播放。"); }));
        return actions.bind(NodeBinding.value(() -> !playback.large.get(), SceneNode::visible));
    }

    private List<MediaSession> sessions() {
        MediaSession session = playback.current.get();
        return session == null ? Collections.emptyList() : Collections.singletonList(session);
    }

    private SceneNode player(MediaSession session, Runnable back) {
        SceneNode video = SceneVideo.create(page, session).size(Size.fill(1), Size.fill(1)).background(0xFF09090B);
        ReadableSignal<MediaSnapshot> state = SceneVideo.snapshot(video);
        ReadableSignal<Boolean> audio = () -> state.get().info.ready && !state.get().info.hasVideo && state.get().info.hasAudio;
        video.bind(NodeBinding.value(audio, (node, value) -> node.visible(!value)));
        SceneNode watching = SceneMediaControls.viewing(page, session, video, theme)
                .bind(NodeBinding.value(audio, (node, value) -> node.visible(!value)));
        SceneNode audioCard = GalleryStyle.card(theme).padding(24).gap(8).add(style.title("正在播放音频"))
                .add(style.ellipsis(() -> sourceName(state.get().source), 1, true)).bind(NodeBinding.value(audio, SceneNode::visible));
        // 普通流把工具和控制以外的当前空间交给视频，CONTAIN仍由SceneVideo负责。
        return new SceneNode().size(Size.fill(1), Size.fill(1)).gap(8).add(toolbar(video, back)).add(watching).add(audioCard)
                .add(GalleryStyle.frame(SceneMediaControls.create(page, session, theme).padding(12), theme));
    }

    private SceneNode toolbar(SceneNode video, Runnable back) {
        return GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(8)
                .add(GalleryStyle.button(page, () -> "返回场景概览", theme, back)).add(style.label(() -> "媒体播放", 1, false))
                .add(style.ellipsis(() -> playback.catalog.get() == null ? sourceName(SceneVideo.snapshot(video).get().source) : playback.catalog.get().title, 1, true)
                        .size(Size.fill(1), Size.natural()))
                .add(GalleryStyle.button(page, () -> playback.large.get() ? "退出放大" : "放大观看", theme, () -> playback.large.set(!playback.large.get())));
    }

    private static String sourceName(MediaSource source) {
        String path = source.uri.getPath();
        return path == null || path.isEmpty() ? source.uri.toString() : path.substring(path.lastIndexOf('/') + 1);
    }
    void stop() { playback.stop(); }

    private final class InputPump implements SceneControl {
        public long nextInputNanos(SceneNode node) { return playback.busy.get() ? 0 : Long.MAX_VALUE; }
        public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) { playback.receiveCompletion(); }
        public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text, SceneChildMeasurer children) { return SceneFlowLayout.measure(node, constraints, children); }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
}
