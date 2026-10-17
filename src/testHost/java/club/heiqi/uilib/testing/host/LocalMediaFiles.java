package club.heiqi.uilib.testing.host;

import club.heiqi.uilib.ui.media.MediaSource;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 测试宿主仅标准化本地路径及来源 URI；文件选择界面归正式 scene 控件。 */
public final class LocalMediaFiles {
    private LocalMediaFiles() {}
    public static MediaSource resolve(String text) {
        String value=text.trim();
        // Windows盘符与UNC优先按平台路径处理，避免盘符误认成URI scheme；网络URL原样保留。
        boolean drive=value.length()>=3&&Character.isLetter(value.charAt(0))&&value.charAt(1)==':'
                &&(value.charAt(2)=='\\'||value.charAt(2)=='/');
        if(drive||value.startsWith("\\\\")||value.startsWith("/")) {
            Path path=Paths.get(value);return MediaSource.of(path.toAbsolutePath().toUri());
        }
        return MediaSource.of(URI.create(value));
    }
    public static Path initialDirectory() {
        return Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }
}
