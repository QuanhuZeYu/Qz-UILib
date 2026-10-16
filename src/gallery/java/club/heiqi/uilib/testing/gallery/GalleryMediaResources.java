package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 一次测试宿主拥有的媒体服务与按需示例文件；共享广场不依赖任何native绑定。 */
public final class GalleryMediaResources implements AutoCloseable {
    private final MediaService service;
    private Path sample;
    private boolean closed;
    public GalleryMediaResources(MediaService service) { this.service=java.util.Objects.requireNonNull(service,"service"); }
    public GalleryEnvironment environment(club.heiqi.uilib.ui.settings.SettingsConfiguration settings) {
        return environment(settings,text->MediaSource.of(java.net.URI.create(text.trim())),()->null);
    }
    public GalleryEnvironment environment(club.heiqi.uilib.ui.settings.SettingsConfiguration settings,
            java.util.function.Function<String,MediaSource> resolver,java.util.concurrent.Callable<MediaSource> chooser) {
        return new GalleryEnvironment() {
            @Override public club.heiqi.uilib.ui.settings.SettingsConfiguration settings() { return settings; }
            @Override public MediaService media() { return service(); }
            @Override public MediaSource resolveSource(String text) { return resolver.apply(text); }
            @Override public MediaSource chooseMediaSource() throws Exception { return chooser.call(); }
            @Override public MediaSource exampleSource() throws IOException { return GalleryMediaResources.this.exampleSource(); }
        };
    }
    public MediaService service() { if(closed)throw new IllegalStateException("媒体资源已关闭");return service; }
    public MediaSource exampleSource() throws IOException {
        if(closed)throw new IllegalStateException("媒体资源已关闭");
        if(sample==null) {
            Path created=Files.createTempFile("qz-media 示例-",".ts");
            try(InputStream input=GalleryMediaResources.class.getResourceAsStream("/uilib-media/segment.ts")) {
                if(input==null)throw new IOException("内置示例不可用");
                Files.copy(input,created,StandardCopyOption.REPLACE_EXISTING);sample=created;
            } catch(IOException|RuntimeException|Error failure) {
                try{Files.deleteIfExists(created);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
            }
        }
        return MediaSource.of(sample.toUri());
    }
    @Override public void close() throws Exception {
        if(closed)return;closed=true;Throwable failure=null;
        try{service.close();}catch(Throwable next){failure=next;}
        try{if(sample!=null)Files.deleteIfExists(sample);}catch(Throwable next){if(failure==null)failure=next;else if(next!=failure)failure.addSuppressed(next);}
        if(failure instanceof Error)throw(Error)failure;if(failure instanceof Exception)throw(Exception)failure;
    }
}
