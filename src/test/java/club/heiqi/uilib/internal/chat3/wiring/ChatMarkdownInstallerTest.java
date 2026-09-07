package club.heiqi.uilib.internal.chat3.wiring;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatMarkdownInstaller 装配面契约测试(headless 可验证部分):
 * 字段发现(mcp 名 persistantChatGUI)与类型——替换与读回验证依赖真机 Minecraft 实例(S6 真机验证)。
 */
public class ChatMarkdownInstallerTest {

    @Test
    public void shouldDiscoverChatFieldOnGuiIngame() {
        Field field = ChatMarkdownInstaller.findChatField();
        Assert.assertNotNull("应能按 mcp 名发现 GuiIngame.persistantChatGUI 字段", field);
        Assert.assertEquals("字段类型应为 GuiNewChat",
                net.minecraft.client.gui.GuiNewChat.class, field.getType());
    }

    /**
     * R3-C：感知闩的边沿契约 + 换实例必重推。
     *
     * <p>headless 起不了 Minecraft，故直接测闩本身(owner 只比引用身份，语义与真控制器一致)。
     * 旧实现是 {@code private static boolean lastChatOpen} + 只比布尔值：
     * {@code ChatHudWindow} 注销把 controller 置 null 后再接管，新实例 chatOpen 初值 FALSE，
     * 而闩记得 true → 聊天屏开着切总开关时新容器永远停在未打开态。</p>
     */
    @Test
    public void chatOpenLatchReappliesAfterControllerInstanceChanges() {
        ChatMarkdownInstaller.ChatOpenLatch latch = new ChatMarkdownInstaller.ChatOpenLatch();
        Object first = new Object();
        Object second = new Object();

        Assert.assertTrue("首次必须推一次(旧实现因初值同为 false 而永不推)", latch.shouldApply(first, true));
        Assert.assertFalse("同实例同值不重复推(边沿契约)", latch.shouldApply(first, true));
        Assert.assertTrue("同实例开→关是边沿，推", latch.shouldApply(first, false));
        Assert.assertFalse("同实例同值不重复推", latch.shouldApply(first, false));
        Assert.assertTrue("换实例即使同值也必须重推(新容器初值不继承旧闩)", latch.shouldApply(second, false));
        Assert.assertFalse("新实例随后的同值调用回到不推", latch.shouldApply(second, false));
    }

    /**
     * C8 通道③接线锁（headless 代码路径口径）：安装器必须在接管成功（含字段已被置好
     * Facade 的边角重指）时注册 markdown 注入 sink（core::appendMarkdown 旁路），
     * 在接管失败与逃生舱回退时清 null——与 setTakeoverActive 同一批回写点，
     * printMarkdown 的「已接管走旁路 / 未接管降级原版」两态由这里成立。
     */
    @Test
    public void installerRegistersAndClearsMarkdownSinkAlongsideTakeoverFlag() throws Exception {
        String src = readCode("src/main/java/club/heiqi/uilib/internal/chat3/wiring/ChatMarkdownInstaller.java");
        Assert.assertEquals("sink 回写点必须与 setTakeoverActive 成对(装成 ×2 路径 + 清 ×2 路径): ",
                count(src, "setTakeoverActive"), count(src, "setMarkdownSink"));
        Assert.assertTrue("装成路径至少两处 setMarkdownSink(…)",
                count(src, "registerMarkdownSink(") >= 2);
        Assert.assertTrue("sink 必须接 core::appendMarkdown(旁路,不经 Facade.printChatMessage)",
                src.contains("core.appendMarkdown(component, 0)"));
        // 正对照(反空跑):扫描尺对既有回写标志本身可见
        Assert.assertTrue("setTakeoverActive 命中数 > 0(扫描器不瞎): " + count(src, "setTakeoverActive"),
                count(src, "setTakeoverActive") >= 3);
    }

    /** ChatCore 侧结构锁:appendMarkdown 方法体零 decorate 调用(装饰解耦由 appendMessage 独占)。 */
    @Test
    public void coreAppendMarkdownBodyNeverDecorates() throws Exception {
        String src = readCode("src/main/java/club/heiqi/uilib/internal/chat3/wiring/ChatCore.java");
        int bypass = src.indexOf("public void appendMarkdown");
        int next = src.indexOf("public void clear()", bypass);
        Assert.assertTrue("appendMarkdown 存在", bypass >= 0 && next > bypass);
        String body = src.substring(bypass, next);
        Assert.assertFalse("旁路方法体不得调 decorate: " + body, body.contains("decorate"));
        // 正对照:appendMessage(原版注入路径)仍 decorate(既有行为一字未动)
        int append = src.indexOf("public void appendMessage");
        Assert.assertTrue("appendMessage 仍过装饰链",
                src.substring(append, bypass).contains("ChatAccess.getInstance().decorate(component)"));
    }

    private static String readCode(String projectPath) throws java.io.IOException {
        String raw = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(projectPath)),
                java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        boolean inBlock = false;
        for (String line : raw.split("\r?\n")) {
            String t = line.trim();
            if (inBlock) {
                if (t.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (t.startsWith("/*")) {
                if (!t.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (t.startsWith("//") || t.startsWith("*")) {
                continue;
            }
            code.append(t).append((char) 0x0A);
        }
        return code.toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int from = 0;
        while (true) {
            int hit = haystack.indexOf(needle, from);
            if (hit < 0) {
                return n;
            }
            n++;
            from = hit + needle.length();
        }
    }
}
