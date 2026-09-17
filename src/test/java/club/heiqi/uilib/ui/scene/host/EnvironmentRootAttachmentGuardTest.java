package club.heiqi.uilib.ui.scene.host;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 环境根登记守卫：每条宿主装配路径都必须调 {@code SceneHostAssembly.attachTree}。
 *
 * <h3>它守什么</h3>
 * <p>{@link SceneHostAssembly} 的 javadoc 早已写明「守卫需钉『各装配路径必须出现 attachTree』」，
 * 但该守卫此前并不存在。漏登记的后果是静默的：{@code SceneNode#resolveFontEnvironment} 沿父链找不到
 * 环境持有者就返回 null，该子树于是读不到层 3 默认字号与用户倍率——现象是「倍率改了，这部分文字不动」，
 * 不报错、不改尺寸，只有把两次出图对拍才看得出来。</p>
 *
 * <p>实测出处：{@code --page=text-probe --font-scale=0} 与不缩放逐像素相同、{@code playground} 每页
 * 都残留外壳那 2 条文本（外壳走 {@code buildShell} 直接建树，不经 {@code SceneRuntime.mount}）。
 * 基类 {@link AbstractSceneHostWidget} 修好后，全部页面宿主派生类自动覆盖。</p>
 */
public class EnvironmentRootAttachmentGuardTest {

    /** 装配路径（相对仓库根，正斜杠）。 */
    private static final String[] HOST_PATHS = {
            "src/main/java/club/heiqi/uilib/ui/scene/host/AbstractSceneHostWidget.java",
            "src/main/java/club/heiqi/uilib/ui/scene/host/SceneHostWindow.java",
            "src/main/java/club/heiqi/uilib/internal/devtools/headless/ChatSceneProbeHost.java"};

    @Test
    public void everyHostAssemblyPathRegistersTheEnvironmentRoot() throws Exception {
        for (String path : HOST_PATHS) {
            String code = stripComments(read(path));
            assertTrue("正锚：必须读到真源码 " + path, code.contains("class "));
            assertTrue(path + " 必须登记环境根（SceneHostAssembly.attachTree）：漏登记会让该子树读不到"
                    + "默认字号与用户倍率，且不报错", code.contains("SceneHostAssembly.attachTree("));
        }
    }

    /**
     * 页面宿主基类必须登记<b>它自己的外框根</b>（而不只是内容根）。
     *
     * <p>内容根由 {@code SceneRuntime.mount} 登记，外框根只有宿主自己知道——这正是此前漏掉的一半。</p>
     */
    @Test
    public void pageHostBaseRegistersItsOwnShellRoot() throws Exception {
        String code = stripComments(read(HOST_PATHS[0]));
        assertTrue("基类必须在 render 内把 getRoot() 交给 attachTree",
                code.contains("SceneHostAssembly.attachTree(runtime, root)"));
        assertTrue("登记必须幂等（root 未变时不重复登记）", code.contains("root != attachedRoot"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relative)), StandardCharsets.UTF_8);
    }

    /** 剥离行注释与块注释（保留换行以免拼接出假匹配）。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inBlock) {
                if (current == '*' && next == '/') {
                    inBlock = false;
                    index++;
                } else if (current == '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                out.append('\n');
                continue;
            }
            if (current == '/' && next == '*') {
                inBlock = true;
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }
}
