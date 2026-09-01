package club.heiqi.uilib.ui.scene.input;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * I10 包隔离红线单元测试。
 *
 * <p>验证 src/main/java/club/heiqi/uilib/ui/scene/input/ 下的所有核心源文件
 * 不引入任何平台绑定或 Minecraft 依赖，确保输入层与平台完全解耦。</p>
 *
 * <p>红线规则：任何非注释代码行中不得出现以下平台包前缀（不仅限于 import 行，
 * 也包括全限定名直接引用）：</p>
 * <ul>
 *   <li>org.lwjgl</li>
 *   <li>org.lwjglx</li>
 *   <li>net.minecraft</li>
 *   <li>net.minecraftforge</li>
 *   <li>club.heiqi.uilib.ui.event</li>
 * </ul>
 */
public class ScenePackageIsolationTest {

    /**
     * 禁止的平台包前缀正则。
     * 匹配形式包括：
     * <ul>
     *   <li>import 语句：import org.lwjgl.xxx</li>
     *   <li>全限定名引用：org.lwjgl.xxx.yyy（类型注解、参数声明等）</li>
     * </ul>
     * 跳过以 // 或 * 开头的纯注释行。
     */
    private static final Pattern FORBIDDEN_PLATFORM_REF = Pattern.compile(
            "(org\\.lwjgl|org\\.lwjglx|net\\.minecraft|net\\.minecraftforge|club\\.heiqi\\.uilib\\.ui\\.event)\\.");

    /**
     * 禁止 scene 核心包 import 渲染上下文 / 字体渲染器 / ui.text.* 度量实现的正则。
     *
     * <p>scene 核心（layout/paint/node/input）只认窄端口 {@code SceneTextMeasurer}，
     * 真实度量由装配层 adapter（scene/text 子包）委托完成，故核心包绝不出现下列引用。</p>
     */
    private static final Pattern FORBIDDEN_RENDER_REF = Pattern.compile(
            "(UiRenderContext|FontRenderer|club\\.heiqi\\.uilib\\.ui\\.text\\.)");

    /**
     * 禁止 scene 核心包 import {@code ui.style} 的正则（守不变量 I6）。
     *
     * <p>scene 数据层（layout/paint/node/overlay 及顶层）不得反向依赖样式系统
     * {@code club.heiqi.uilib.ui.style}：样式解析在构建期完成，回放期只消费纯数值。
     * render 层可继续用 ui.style，但 scene 层不行。scene/text 装配子包是合法接缝，
     * 不纳入本正则扫描范围。</p>
     */
    private static final Pattern FORBIDDEN_STYLE_REF = Pattern.compile(
            "club\\.heiqi\\.uilib\\.ui\\.style\\.");

    /**
     * 反向红线：render / host 层不得伸入 scene 的<b>装配与运行时子包</b>（接缝类、runtime、
     * host、control 等）。白名单为封闭集，仅两个值类型端口：
     * <ul>
     *   <li>{@code scene.image.SceneImageSource}：平台中立绘制值类型（后端契约参数）；</li>
     *   <li>{@code scene.image.ItemRenderTierRegistry}：文档化的渲染分级反向端口；</li>
     *   <li>{@code scene.text.SceneTextMode}：scene 内容模式唯一语义锚（值类型，code 对齐
     *       由 SceneTextModeTest 守卫）——UiRenderContext.drawText 的 int→模式归一取它，
     *       而<b>不</b>取同包的装配接缝类 TextMeasureServiceSceneAdapter（2026-09-01
     *       越界引用收回，本守卫即其回归锁）。</li>
     * </ul>
     */
    private static final Pattern FORBIDDEN_RENDER_SIDE_SCENE_REF = Pattern.compile(
            "club\\.heiqi\\.uilib\\.ui\\.scene\\.(?!image\\.|text\\.SceneTextMode)[A-Za-z]");

    /**
     * 验证：input 包及其子包下所有 .java 源文件不包含任何禁止的平台引用。
     */
    @Test
    public void shouldHaveNoForbiddenImports() throws IOException {
        // 源文件所在目录（相对于项目根目录）
        Path inputDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "input");

        Assert.assertTrue("input 源文件目录应存在", Files.isDirectory(inputDir));

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(inputDir)) {
            javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Assert.assertTrue("input 包下应有至少 24 个 .java 文件（含 I4a FocusManager + I4c SceneCursor/CursorBackend 等新增文件）",
                javaFiles.size() >= 24);

        for (Path javaFile : javaFiles) {
            assertNoForbiddenPlatformRef(javaFile);
        }
    }

    /**
     * 验证：scene 核心 layout + paint + node + overlay 包不引入任何平台引用，
     * 也不 import 渲染上下文 / FontRenderer / ui.text.* 度量实现。
     *
     * <p>node 子包是文本/字号/度量字段的实际持有者，是未来最可能被误引入度量实现的位置，
     * 故与 layout/paint 同列入渲染纯度红线（守 I10：核心只认窄端口 {@code SceneTextMeasurer}）。
     * overlay 子包是 top-layer 数据地基，也必须保持平台和渲染实现无关。</p>
     *
     * <p>注意 scene/text 装配子包是合法接缝<b>不纳入</b>本渲染纯度断言范围：
     * 它的 adapter 合法 import {@code ui.text.*}，是核心与渲染侧度量服务的唯一桥接点
     * （本测试只扫 layout/paint/node 目录，天然不含 text 子包）。</p>
     *
     * <p>A 组 S1-S4 接口化收口后，{@code ScenePaintReplayer} <b>不再按文件名豁免</b>：
     * 它现在只 import 渲染出口接口 {@code UiRenderBackend}（该名不含具体类 {@code UiRenderContext}
     * 子串），故与 paint 包其它文件同等纳入本渲染纯度统一扫描。专门的接口依赖回归断言见
     * {@link #replayerShouldDependOnRenderBackendInterfaceNotConcreteClass()}。</p>
     */
    @Test
    public void layoutAndPaintCoreShouldNotReferenceRenderOrPlatform() throws IOException {
        Path layoutDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "layout");
        Path paintDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "paint");
        Path nodeDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "node");
        Path overlayDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "overlay");

        Assert.assertTrue("layout 源文件目录应存在", Files.isDirectory(layoutDir));
        Assert.assertTrue("paint 源文件目录应存在", Files.isDirectory(paintDir));
        Assert.assertTrue("node 源文件目录应存在", Files.isDirectory(nodeDir));
        Assert.assertTrue("overlay 源文件目录应存在", Files.isDirectory(overlayDir));

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(layoutDir)) {
            javaFiles = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        try (Stream<Path> files = Files.walk(paintDir)) {
            javaFiles.addAll(files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
        }
        try (Stream<Path> files = Files.walk(nodeDir)) {
            javaFiles.addAll(files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
        }
        try (Stream<Path> files = Files.walk(overlayDir)) {
            javaFiles.addAll(files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
        }

        for (Path javaFile : javaFiles) {
            // 平台引用（lwjgl/minecraft）对所有 layout/paint/node 文件一律禁止
            assertNoForbiddenPlatformRef(javaFile);
            // 渲染层引用（具体 UiRenderContext / FontRenderer / ui.text.*）对所有文件一律禁止。
            // A 组 S1-S4 接口化后 ScenePaintReplayer 已改持有渲染出口接口 UiRenderBackend，
            // 不再 import 具体 UiRenderContext，故移除既往整文件豁免，统一纳入扫描。
            assertNoForbiddenRenderRef(javaFile);
            // ui.style 反向依赖禁止（守 I6：scene 数据层不得 import 样式系统）
            assertNoForbiddenStyleRef(javaFile);
        }
    }

    /**
     * 验证：A 组 S1-S4 接口化收口铁证 —— {@code ScenePaintReplayer} 依赖渲染出口接口
     * {@code UiRenderBackend}，而非具体渲染后端类 {@code UiRenderContext}。
     *
     * <p>这是 scene 脱 MC 移植契约线（宪章信条六）的可回归守线：scene 核心只通过接口
     * 认识渲染层。断言 replayer 源文件的 import 区<b>含</b> {@code UiRenderBackend} 接口、
     * <b>不含</b>对具体 {@code UiRenderContext} 类的 import（注释中合法提及 MC 实现不计）。</p>
     *
     * @throws IOException 读取源文件失败
     */
    @Test
    public void replayerShouldDependOnRenderBackendInterfaceNotConcreteClass() throws IOException {
        Path replayerSrc = Paths.get("src", "main", "java",
                "club", "heiqi", "uilib", "ui", "scene", "paint", "ScenePaintReplayer.java");
        Assert.assertTrue("ScenePaintReplayer.java 源文件应存在", Files.isRegularFile(replayerSrc));

        boolean importsBackendInterface = false;
        for (String line : Files.readAllLines(replayerSrc)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue; // 只检查 import 行，注释/正文里合法提及 MC 实现不计
            }
            Assert.assertFalse(
                    "replayer import 行不得依赖具体渲染类 UiRenderContext（应只认接口 UiRenderBackend）: " + trimmed,
                    trimmed.contains("UiRenderContext"));
            Assert.assertFalse(
                    "replayer import 行不得反向依赖 ui.style（守 I6）: " + trimmed,
                    trimmed.contains("club.heiqi.uilib.ui.style"));
            if (trimmed.contains("club.heiqi.uilib.ui.render.UiRenderBackend")) {
                importsBackendInterface = true;
            }
        }
        Assert.assertTrue(
                "replayer 应 import 渲染出口接口 club.heiqi.uilib.ui.render.UiRenderBackend",
                importsBackendInterface);
    }

    /**
     * 验证：scene 核心顶层包（ui.scene 直接子 .java，不含子包）不含具体渲染后端类 UiRenderContext
     * 及任何平台引用（守宪章信条六 / I6）。
     *
     * <p>顶层包（如 {@code UiSurface}）是 scene 渲染面入口，只能认渲染出口抽象接口
     * {@link club.heiqi.uilib.ui.render.UiRenderBackend}，绝不 import 焊 GL 的具体后端。</p>
     *
     * <p>注意：只扫顶层直接子 .java，不递归子包（子包已由
     * {@link #layoutAndPaintCoreShouldNotReferenceRenderOrPlatform()} 等方法覆盖，
     * 且 scene/text 装配子包合法 import ui.text.* 不应被此处误覆盖）。</p>
     */
    @Test
    public void sceneCoreTopLevelShouldNotReferenceConcreteRenderBackend() throws IOException {
        Path sceneTopDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene");
        Assert.assertTrue("ui.scene 顶层源文件目录应存在", Files.isDirectory(sceneTopDir));

        List<Path> topLevelFiles;
        try (Stream<Path> entries = Files.list(sceneTopDir)) {
            topLevelFiles = entries
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Assert.assertFalse("ui.scene 顶层包下应至少有一个 .java 文件", topLevelFiles.isEmpty());

        for (Path javaFile : topLevelFiles) {
            assertNoForbiddenPlatformRef(javaFile);
            assertNoForbiddenRenderRef(javaFile);
            assertNoForbiddenStyleRef(javaFile);
        }
    }

    /**
     * 验证：render / host 层对 scene 的引用被限制在封闭白名单内（反向红线）。
     *
     * <p>与 {@link #layoutAndPaintCoreShouldNotReferenceRenderOrPlatform()} 互为正反两向：
     * 前者锁"scene 核心不认具体渲染"，本方法锁"渲染/宿主层不伸入 scene 装配运行时"。
     * 历史上唯一实例是 UiRenderContext 直调 scene.text 装配接缝类取映射（已收回），
     * 本方法即防其复发的回归锁。</p>
     *
     * @throws IOException 读取源文件失败
     */
    @Test
    public void renderAndHostShouldNotReachIntoSceneAssemblyRuntime() throws IOException {
        Path renderDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "render");
        Path hostDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "host");
        Assert.assertTrue("ui/render 源文件目录应存在", Files.isDirectory(renderDir));
        Assert.assertTrue("ui/host 源文件目录应存在", Files.isDirectory(hostDir));

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(renderDir)) {
            javaFiles = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        try (Stream<Path> files = Files.walk(hostDir)) {
            javaFiles.addAll(files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
        }
        Assert.assertFalse("render+host 应至少扫描到一个源文件", javaFiles.isEmpty());

        for (Path javaFile : javaFiles) {
            List<String> lines = Files.readAllLines(javaFile);
            int lineNum = 1;
            for (String line : lines) {
                String trimmed = line.trim();
                boolean comment = trimmed.startsWith("//") || trimmed.startsWith("*")
                        || trimmed.startsWith("/*");
                lineNum++;
                if (comment) {
                    continue; // 注释/javadoc 中提及类型名合法（如本收回说明）
                }
                if (FORBIDDEN_RENDER_SIDE_SCENE_REF.matcher(line).find()) {
                    Assert.fail("文件 " + javaFile.getFileName() + " 第 " + lineNum
                            + " 行伸入 scene 装配/运行时子包（白名单仅 scene.image.* 与 scene.text.SceneTextMode）："
                            + line);
                }
            }
        }
    }

    /**
     * 断言单个源文件不含禁止的平台引用（跳过纯注释行）。
     *
     * @param javaFile 待检查的源文件
     * @throws IOException 读取文件失败
     */
    private void assertNoForbiddenPlatformRef(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile);
        int lineNum = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                lineNum++;
                continue;
            }
            if (FORBIDDEN_PLATFORM_REF.matcher(line).find()) {
                Assert.fail("文件 " + javaFile.getFileName()
                        + " 第 " + lineNum + " 行包含禁止的平台引用：\"" + line + "\"");
            }
            lineNum++;
        }
    }

    /**
     * 断言单个源文件不含禁止的渲染层 / ui.text.* 引用（跳过纯注释行）。
     *
     * @param javaFile 待检查的源文件
     * @throws IOException 读取文件失败
     */
    private void assertNoForbiddenRenderRef(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile);
        int lineNum = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                lineNum++;
                continue;
            }
            if (FORBIDDEN_RENDER_REF.matcher(line).find()) {
                Assert.fail("文件 " + javaFile.getFileName()
                        + " 第 " + lineNum + " 行包含禁止的渲染层/ui.text 引用：\"" + line + "\"");
            }
            lineNum++;
        }
    }

    /**
     * 断言单个源文件不含禁止的 {@code ui.style} 反向依赖（跳过纯注释行，守 I6）。
     *
     * @param javaFile 待检查的源文件
     * @throws IOException 读取文件失败
     */
    private void assertNoForbiddenStyleRef(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile);
        int lineNum = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                lineNum++;
                continue;
            }
            if (FORBIDDEN_STYLE_REF.matcher(line).find()) {
                Assert.fail("文件 " + javaFile.getFileName()
                        + " 第 " + lineNum + " 行包含禁止的 ui.style 反向依赖：\"" + line + "\"");
            }
            lineNum++;
        }
    }
}
