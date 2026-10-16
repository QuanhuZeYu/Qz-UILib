package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** 最终成品测量启动器；classpath与jar清单均由实际归档清单明确给出。 */
public final class MediaStabilityMain {
    private MediaStabilityMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length<6||args.length>7)throw new IllegalArgumentException(
                "参数: mode uri quantification-inputs.json acceptance-target.json output-dir artifact-paths.txt [audio-device]");
        Path inputs=Paths.get(args[2]),acceptance=Paths.get(args[3]),output=Paths.get(args[4]),artifactList=Paths.get(args[5]);
        List<String> lines=Files.readAllLines(artifactList,StandardCharsets.UTF_8);
        if(lines.isEmpty()||lines.size()>64)throw new IllegalArgumentException("最终artifact列表须为1到64项");
        List<Path> artifacts=new ArrayList<>();
        for(String line:lines){
            if(line.trim().isEmpty())throw new IllegalArgumentException("最终artifact列表不接受空项");
            Path artifact=Paths.get(line).toAbsolutePath().normalize();
            if(!Files.isRegularFile(artifact))throw new IllegalArgumentException("最终artifact不存在: "+artifact);
            artifacts.add(artifact);
        }
        // 两个factory仅返回中性延迟装配；不人为open/snapshot预热后移首会话成本。
        MediaAudioSinkFactory audio=args.length==7?IsolatedMediaAdapters.audioSinkFactory(args[6]):IsolatedMediaAdapters.audioSinkFactory();
        new MediaStabilityVerification(IsolatedMediaAdapters.decoderFactory(),audio,new FinalMediaResources(),
                inputs,acceptance,output,artifacts).run(args[0],args[1]);
    }
}
