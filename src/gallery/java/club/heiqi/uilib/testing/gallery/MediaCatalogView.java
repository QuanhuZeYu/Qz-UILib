package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/** 平台内容信息与选择的声明；只消费播放状态、发布选择意图。 */
final class MediaCatalogView {
    private final ScenePageContext page;
    private final GalleryEnvironment environment;
    private final MediaPlayback playback;
    private final SceneAppearance style;
    private final ReadableSignal<SceneTheme> theme;

    MediaCatalogView(ScenePageContext page, GalleryEnvironment environment, MediaPlayback playback,
            SceneAppearance style, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.environment = environment; this.playback = playback; this.style = style; this.theme = theme;
    }

    SceneNode details() {
        SceneNode details = new SceneNode().size(Size.fill(1), Size.natural()).gap(7)
                .bind(NodeBinding.value(() -> playback.catalog.get() != null && !playback.large.get(), SceneNode::visible));
        SceneNode summary = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(10);
        if (environment.images() != null) summary.add(cover());
        summary.add(new SceneNode().size(Size.fill(1), Size.natural()).gap(4)
                .add(style.ellipsis(this::title, 1, false)).add(style.ellipsis(this::author, .86f, true)));
        return details.add(summary).add(actions());
    }

    private SceneNode cover() {
        SceneNode cover = new SceneNode().size(Size.fixed(96), Size.fixed(54))
                .bind(NodeBinding.value(() -> page.viewportWidth().get() >= 720, SceneNode::visible));
        cover.bind(NodeBinding.repeat(this::posters, uri -> uri,
                poster -> SceneImage.create(environment.images(), () -> ImageSource.http(poster.get().toString()),
                        () -> new ImageOptions(Size.fixed(96), Size.fixed(54), ImageOptions.Fit.COVER, .5f, .5f, 96, 54, "媒体封面", false))));
        return cover;
    }

    private List<URI> posters() {
        MediaResolver.Result result = playback.catalog.get();
        return result == null || result.poster == null ? Collections.emptyList() : Collections.singletonList(result.poster);
    }

    private SceneNode actions() {
        SceneNode options = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(8).scrollable(true);
        options.add(action(() -> "选择分P", () -> playback.choosing.set(MediaPlayback.Choice.PART))
                .bind(NodeBinding.value(() -> playback.catalog.get() != null && playback.catalog.get().parts.size() > 1, SceneNode::visible)));
        options.add(action(this::quality, () -> playback.choosing.set(MediaPlayback.Choice.QUALITY))
                .bind(NodeBinding.value(() -> playback.catalog.get() != null && !playback.catalog.get().qualities.isEmpty(), SceneNode::visible)));
        return options.add(action(() -> "刷新地址", playback::refresh));
    }

    private SceneNode action(ReadableSignal<String> label, Runnable action) {
        return GalleryStyle.button(page, label, theme, action).bind(NodeBinding.value(() -> !playback.busy.get(), SceneNode::enabled));
    }

    SceneNode choices() {
        SceneNode list = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(6).scrollable(true);
        list.bind(NodeBinding.repeat(this::options, item -> item.id, item -> GalleryStyle.button(page, () -> item.get().label,
                theme, () -> playback.select(item.get().id)).size(Size.fill(1), Size.natural())));
        SceneNode cancel = GalleryStyle.button(page, () -> "取消选择", theme, () -> playback.choosing.set(MediaPlayback.Choice.NONE));
        SceneNode panel = GalleryStyle.card(theme).size(Size.fill(1), Size.fill(1)).padding(16).gap(12)
                .add(style.label(() -> playback.choosing.get() == MediaPlayback.Choice.PART ? "选择分P" : "选择清晰度", 1.2f, false)).add(list).add(cancel);
        return SceneLayer.create(page, () -> new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1), Size.fill(1)).padding(24).background(SceneAppearance.MODAL_SCRIM).modal(true).initialFocus(cancel)
                .onEscape(() -> { playback.choosing.set(MediaPlayback.Choice.NONE); return true; }).add(panel)
                .bind(NodeBinding.value(() -> playback.choosing.get() != MediaPlayback.Choice.NONE, SceneNode::visible));
    }

    private List<MediaResolver.Option> options() {
        MediaResolver.Result result = playback.catalog.get();
        if (result == null) return Collections.emptyList();
        return playback.choosing.get() == MediaPlayback.Choice.PART ? result.parts : result.qualities;
    }
    private String title() { MediaResolver.Result result = playback.catalog.get(); return result == null ? "" : result.title; }
    private String author() {
        MediaResolver.Result result = playback.catalog.get();
        return result == null ? "" : result.author + " · " + (result.live ? "直播" : option(result.parts, result.part));
    }
    private String quality() {
        MediaResolver.Result result = playback.catalog.get();
        return result == null ? "清晰度" : "清晰度 · " + option(result.qualities, result.quality);
    }
    private static String option(List<MediaResolver.Option> options, String selected) {
        for (MediaResolver.Option item : options) if (item.id.equals(selected)) return item.label;
        return selected;
    }
}
