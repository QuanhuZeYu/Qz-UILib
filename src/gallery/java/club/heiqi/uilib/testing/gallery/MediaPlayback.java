package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import java.nio.file.Path;

/** 页面播放意图和所属资源的唯一owner；不构造节点，不执行布局或绘制。 */
final class MediaPlayback implements AutoCloseable {
    enum Choice { NONE, PART, QUALITY }
    private enum Position { BEGINNING, CURRENT }
    private static final class Resolution {
        final SceneEditState.Submission submission;
        final Position position;
        Resolution(SceneEditState.Submission submission, Position position) { this.submission = submission; this.position = position; }
    }

    final SceneEditState address;
    final Signal<MediaSession> current;
    final Signal<MediaResolver.Result> catalog;
    final Signal<Boolean> picking, sourceExpanded, large, busy, feedback;
    final Signal<String> message;
    final Signal<Choice> choosing;
    private final GalleryEnvironment environment;
    private final GalleryMediaResolver resolver;
    private Resolution pending;
    Path pickerDirectory;

    MediaPlayback(ReactiveDomain domain, GalleryEnvironment environment) {
        this.environment = environment;
        address = new SceneEditState(domain, "");
        current = domain.signal(null); catalog = domain.signal(null);
        picking = domain.signal(false); sourceExpanded = domain.signal(true); large = domain.signal(false);
        busy = domain.signal(false); feedback = domain.signal(false); choosing = domain.signal(Choice.NONE);
        message = domain.signal("选择本地视频，输入媒体地址，或打开内置示例。");
        resolver = environment.mediaResolver() == null ? null : new GalleryMediaResolver(environment.mediaResolver());
        pickerDirectory = environment.initialMediaDirectory();
        if (environment.media() == null) show("当前入口未提供媒体播放，仍可浏览其他场景。");
    }

    void openAddress(SceneEditState.Submission submission) {
        String raw = submission.text.trim();
        if (resolver != null && environment.mediaResolver().supports(raw)) {
            resolve(new MediaResolver.Request(raw, "", ""), Position.BEGINNING, submission);
            return;
        }
        try { open(environment.resolveSource(raw)); address.accept(submission, raw); }
        catch (Exception failure) {
            sourceExpanded.set(true); show("无法打开媒体：" + failure.getMessage());
            address.reject(submission, message.get());
        }
    }

    void example() {
        try { open(environment.exampleSource()); }
        catch (Exception failure) { sourceExpanded.set(true); message.set("无法打开示例：" + failure.getMessage()); }
    }

    void file(Path selected) {
        picking.set(false); pickerDirectory = selected.getParent();
        try {
            MediaSource source = MediaSource.of(selected.toUri());
            open(source); address.reset(source.uri.toString());
        } catch (Exception failure) { sourceExpanded.set(true); message.set("无法打开本地视频：" + failure.getMessage()); }
    }

    private void open(MediaSource source) {
        cancelResolution(); catalog.set(null); openResolved(source);
    }

    private void openResolved(MediaSource source) {
        MediaService service = environment.media();
        if (service == null) throw new IllegalStateException("当前入口未提供媒体播放");
        closePlayback();
        MediaSession opened = service.open(source, false);
        try {
            current.set(opened); sourceExpanded.set(false); feedback.set(false);
            message.set("输入新来源后打开，当前播放不受草稿编辑影响。");
            opened.play();
        } catch (RuntimeException | Error failure) {
            current.set(null);
            try { opened.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void refresh() {
        MediaResolver.Result result = catalog.get();
        resolve(new MediaResolver.Request(result.canonical.toString(), result.part, result.quality), Position.CURRENT, null);
    }

    void select(String id) {
        MediaResolver.Result result = catalog.get();
        boolean part = choosing.get() == Choice.PART;
        resolve(new MediaResolver.Request(result.canonical.toString(), part ? id : result.part, part ? "" : id),
                part ? Position.BEGINNING : Position.CURRENT, null);
    }

    private void resolve(MediaResolver.Request request, Position position, SceneEditState.Submission submission) {
        if (resolver == null) return;
        pending = new Resolution(submission, position); busy.set(true);
        show("正在解析网络媒体…"); choosing.set(Choice.NONE); resolver.request(request);
    }

    /** 只由正式输入帧领取；编辑提交与本次解析意图成对生效。 */
    void receiveCompletion() {
        if (resolver == null || !busy.get()) return;
        GalleryMediaResolver.Completion completion = resolver.take();
        if (completion == null) return;
        Resolution intent = pending; pending = null; busy.set(false);
        if (completion.failure != null) { reject(intent, completion.failure); return; }
        if (!accept(intent)) return;
        apply(completion.result, intent.position);
    }

    private void reject(Resolution intent, Exception failure) {
        String reason = "无法解析媒体：" + failure.getMessage();
        if (intent.submission != null && !address.reject(intent.submission, reason)) { feedback.set(false); return; }
        show(reason); sourceExpanded.set(true);
    }

    private boolean accept(Resolution intent) {
        if (intent.submission == null || address.accept(intent.submission, intent.submission.text.trim())) return true;
        show("地址已修改，请打开当前输入的新地址。"); return false;
    }

    private void apply(MediaResolver.Result result, Position position) {
        catalog.set(result);
        if (result.source == null) { closePlayback(); show(result.message); return; }
        MediaSession session = current.get();
        if (session == null) { openResolved(result.source); return; }
        MediaSnapshot previous = session.snapshot();
        long target = position == Position.CURRENT && !result.live ? previous.positionNanos : 0;
        session.source(result.source, target); feedback.set(false);
    }

    private void show(String text) { feedback.set(true); message.set(text); }
    private void cancelResolution() {
        if (resolver != null) resolver.cancel();
        busy.set(false); pending = null; feedback.set(false);
    }

    void stop() {
        cancelResolution(); catalog.set(null); choosing.set(Choice.NONE); closePlayback();
    }

    private void closePlayback() {
        picking.set(false); sourceExpanded.set(true); large.set(false);
        MediaSession previous = current.get(); current.set(null);
        if (previous != null) previous.close();
    }

    @Override public void close() {
        try { stop(); } finally { if (resolver != null) resolver.close(); }
    }
}
