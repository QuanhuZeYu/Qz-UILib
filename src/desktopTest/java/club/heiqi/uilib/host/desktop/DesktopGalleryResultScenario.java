package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.TestApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 真实测试应用与本次结果工件的反例，不为DesktopWindow增加故障接缝。 */
public final class DesktopGalleryResultScenario extends TestApplication {
    private Path output;
    @Override protected void setup() throws Exception {
        String[] args=arguments();
        output=Files.createDirectories(Paths.get(args.length>0?args[0]:"gallery-result-report"));
    }

    @Override protected void runTests() throws Exception {
        scenario("同目录先成功后失败撤销旧PASS且进程失败",this::successThenFailure);
        scenario("场景与清理同时失败保留原始原因",this::failureIdentity);
        scenario("只有清理失败也不能写成功工件",this::cleanupFailure);
    }

    private void successfulRun(Path directory) throws Exception {
        DesktopGalleryResult result=DesktopGalleryResult.begin(directory);
        List<String> events=new ArrayList<>();
        new TestApplication() {
            @Override protected void setup() {
                onClose(() -> {
                    require(!Files.exists(directory.resolve("result.txt")),"清理结束前写入了PASS");
                    events.add("close");
                });
            }
            @Override protected void runTests() throws Exception {
                scenario("本次成功路径",() -> events.add("scene"));
            }
        }.run();
        require(events.equals(Arrays.asList("scene","close")),"成功结果未等到真实场景和清理结束");
        result.passed();
        require(new String(Files.readAllBytes(directory.resolve("result.txt")),StandardCharsets.UTF_8).startsWith("PASS "),
                "本次成功没有产生工件");
    }

    private void successThenFailure() throws Exception {
        Path directory=output.resolve("reused");
        successfulRun(directory);
        Path log=output.resolve("second-run.log");
        String java=Paths.get(System.getProperty("java.home"),"bin","java").toString();
        Process child=new ProcessBuilder(java,"-Dfile.encoding=UTF-8","-cp",System.getProperty("java.class.path"),
                getClass().getName(),"--failure",directory.toAbsolutePath().toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        int exit=child.waitFor();
        require(exit!=0,"第二次失败场景仍以成功退出");
        require(!Files.exists(directory.resolve("result.txt")),"第二次失败保留了上次PASS");
        String evidence=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);
        require(evidence.contains("本次验证场景失败")&&evidence.contains("本次验证清理失败"),"失败进程丢失场景或清理原因");
    }

    private void failureIdentity() throws Exception {
        Path directory=output.resolve("identity");
        successfulRun(directory);
        DesktopGalleryResult result=DesktopGalleryResult.begin(directory);
        AssertionError original=new AssertionError("本次验证场景失败");
        Exception cleanup=new Exception("本次验证清理失败");
        try {
            new FailingApplication(original,cleanup).run();
            result.passed();
            throw new AssertionError("失败应用正常返回");
        } catch(AssertionError caught) {
            require(caught==original,"结果处理覆盖了原始场景失败");
            require(Arrays.equals(caught.getSuppressed(),new Throwable[]{cleanup}),"原始失败没有附带清理原因");
        }
        require(!Files.exists(directory.resolve("result.txt")),"失败应用留下成功工件");
    }

    private void cleanupFailure() throws Exception {
        Path directory=output.resolve("cleanup");
        successfulRun(directory);
        DesktopGalleryResult result=DesktopGalleryResult.begin(directory);
        Exception cleanup=new Exception("本次只有清理失败");
        try {
            new TestApplication() {
                @Override protected void setup() { onClose(() -> { throw cleanup; }); }
                @Override protected void runTests() throws Exception {
                    scenario("清理前的成功场景",() -> require(!Files.exists(directory.resolve("result.txt")),"开始没有撤销旧结果"));
                }
            }.run();
            result.passed();
            throw new AssertionError("清理失败应用正常返回");
        } catch(Exception caught) {
            require(caught==cleanup,"清理失败被结果处理覆盖");
        }
        require(!Files.exists(directory.resolve("result.txt")),"清理失败应用留下成功工件");
    }

    private static final class FailingApplication extends TestApplication {
        private final AssertionError original;
        private final Exception cleanup;
        FailingApplication(AssertionError original,Exception cleanup) { this.original=original;this.cleanup=cleanup; }
        @Override protected void setup() { onClose(() -> { throw cleanup; }); }
        @Override protected void runTests() throws Exception { scenario("本次失败场景",() -> { throw original; }); }
    }

    public static void main(String[] args) throws Exception {
        if(args.length>0&&"--failure".equals(args[0])) {
            Path directory=Paths.get(args[1]);
            DesktopGalleryResult result=DesktopGalleryResult.begin(directory);
            new FailingApplication(new AssertionError("本次验证场景失败"),new Exception("本次验证清理失败")).run();
            result.passed();
            return;
        }
        new DesktopGalleryResultScenario().run(args);
    }
}
