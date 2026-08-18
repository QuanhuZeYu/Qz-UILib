package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 测试场地总览页（默认首页）。
 *
 * <p>展示场地用途、页面清单（来自 {@link PlaygroundPageRegistry}，自动跟随新增页）与
 * 常用快捷键速查——打开 {@code /qzuilib test} 后首先看到的引导页。</p>
 */
public final class HomePage implements PlaygroundPage {

    @Override
    public String id() {
        return "home";
    }

    @Override
    public String title() {
        return "总览";
    }

    @Override
    public String description() {
        return "测试场地入口引导：页面清单与文本能力快捷键速查";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            SceneNode intro = PlaygroundKit.card();
            intro.appendChild(PlaygroundKit.title("欢迎使用 Qz UILib 测试场地"));
            intro.appendChild(PlaygroundKit.hint(
                    "内部开发调试入口（/qzuilib test），用于在游戏内验证 scene 新栈文本输入、浮层与响应式能力。"
                            + "本场地是 internal 调试设施，不构成公共 API 承诺。"));

            SceneNode pagesCard = PlaygroundKit.card();
            pagesCard.appendChild(PlaygroundKit.title("演示页"));
            for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
                if (page.id().equals(id())) {
                    continue;
                }
                SceneNode row = SceneNode.row(8);
                row.setHitTestable(false);
                row.appendChild(PlaygroundKit.text("· " + page.title(), PlaygroundKit.ACCENT, 14));
                row.appendChild(PlaygroundKit.hint(page.description()));
                pagesCard.appendChild(row);
            }
            pagesCard.appendChild(PlaygroundKit.hint("导航段切换页面；页面内状态在切走再切回后保留。"));

            SceneNode shortcutsCard = PlaygroundKit.card();
            shortcutsCard.appendChild(PlaygroundKit.title("文本能力快捷键速查"));
            shortcutsCard.appendChild(shortcut("Ctrl+C / Ctrl+X / Ctrl+V", "复制 / 剪切 / 粘贴（无选区时 Ctrl+C 复制全文）"));
            shortcutsCard.appendChild(shortcut("Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y", "撤销 / 重做（连续输入 500ms 内合并为一条历史）"));
            shortcutsCard.appendChild(shortcut("Shift+方向键", "扩展选区；双击选词、TextArea 三击选行"));
            shortcutsCard.appendChild(shortcut("Ctrl+←/→", "词跳转；Ctrl+Home/End 文首尾；Ctrl+Backspace/Delete 删词"));
            shortcutsCard.appendChild(shortcut("右键文本输入框", "内置上下文菜单（复制/剪切/粘贴/全选/撤销/重做，按状态启停）"));
            shortcutsCard.appendChild(shortcut("ESC", "关闭对话框 / 上下文菜单；离开测试场地"));
            shortcutsCard.appendChild(PlaygroundKit.hint(
                    "剪贴板需宿主在 runtime 注入 ClipbardBackend（本场地由 Lwjgl 桥接，未注入环境静默降级）。"));

            root.appendChild(intro);
            root.appendChild(pagesCard);
            root.appendChild(shortcutsCard);
            return root;
        };
    }

    private static SceneNode shortcut(String keys, String description) {
        SceneNode row = SceneNode.row(8);
        row.setHitTestable(false);
        row.appendChild(PlaygroundKit.text(keys, PlaygroundKit.TEXT, 13));
        row.appendChild(PlaygroundKit.hint(description));
        return row;
    }
}