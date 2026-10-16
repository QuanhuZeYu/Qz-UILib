package club.heiqi.uilib.testing.host;

import club.heiqi.uilib.ui.media.MediaSource;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** 仅测试宿主提供系统文件选择与路径事实；不改变共享页面或媒体来源身份。 */
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
    public static MediaSource choose() throws Exception {
        final MediaSource[] selected={null};
        Runnable show=()->{
            JFileChooser chooser=new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.setAcceptAllFileFilterUsed(true);
            JDialog dialog=new JDialog((java.awt.Frame)null,"选择本地视频",true);
            try {
                dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                chooser.addActionListener(event->{
                    if(JFileChooser.APPROVE_SELECTION.equals(event.getActionCommand()))
                        selected[0]=MediaSource.of(chooser.getSelectedFile().toPath().toAbsolutePath().toUri());
                    dialog.dispose();
                });
                dialog.setContentPane(chooser);dialog.pack();dialog.setLocationRelativeTo(null);dialog.setVisible(true);
            } finally { dialog.dispose(); }
        };
        // 对话框只在EDT存在；返回当前scene调用线程后，页面才更新信号与会话。
        if(SwingUtilities.isEventDispatchThread())show.run();else SwingUtilities.invokeAndWait(show);
        return selected[0];
    }
}
