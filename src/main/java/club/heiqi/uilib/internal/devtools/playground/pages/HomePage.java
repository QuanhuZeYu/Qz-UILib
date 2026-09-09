package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

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
                // 保留的显式色样本：页面清单的强调标记刻意用显式 ACCENT 展示强调色差（不走主题
                // accent，也不随主题切换）；G16/公共构件实例的 PlaygroundKitTest 以本节点为
                // 「真实页面显式样本色不被主题接管」的锚点，故本实例不改写它。
                row.appendChild(PlaygroundKit.text("· " + page.title(), PlaygroundKit.ACCENT, 14));
                // 标题占自然宽，说明显式领取剩余列宽；两个默认文本叶各按整行 clamp 会相加越界。
                row.appendChild(PlaygroundKit.hint(page.description()).setFlexGrow(1));
                pagesCard.appendChild(row);
            }
            pagesCard.appendChild(PlaygroundKit.hint("导航段切换页面；页面内状态在切走再切回后保留。"));
            pagesCard.appendChild(PlaygroundKit.hint(
                    "磨玻璃（backdrop-filter）验收请使用独立入口 /qzuilib glass（磨玻璃实验室，含参数台与渲染路径诊断）。"));

            SceneNode shortcutsCard = PlaygroundKit.card();
            shortcutsCard.appendChild(PlaygroundKit.title("文本能力快捷键速查"));
            shortcutsCard.appendChild(shortcut(rt, "Ctrl+C / Ctrl+X / Ctrl+V", "复制 / 剪切 / 粘贴（无选区时 Ctrl+C 复制全文）"));
            shortcutsCard.appendChild(shortcut(rt, "Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y", "撤销 / 重做（连续输入 500ms 内合并为一条历史）"));
            shortcutsCard.appendChild(shortcut(rt, "Shift+方向键", "扩展选区；双击选词、TextArea 三击选行"));
            shortcutsCard.appendChild(shortcut(rt, "Ctrl+←/→", "词跳转；Ctrl+Home/End 文首尾；Ctrl+Backspace/Delete 删词"));
            shortcutsCard.appendChild(shortcut(rt, "右键文本输入框", "内置上下文菜单（复制/剪切/粘贴/全选/撤销/重做，按状态启停）"));
            shortcutsCard.appendChild(shortcut(rt, "ESC", "关闭对话框 / 上下文菜单；离开测试场地"));
            shortcutsCard.appendChild(PlaygroundKit.hint(
                    "剪贴板需宿主在 runtime 注入 ClipbardBackend（本场地由 Lwjgl 桥接，未注入环境静默降级）。"));

            root.appendChild(intro);
            root.appendChild(pagesCard);
            root.appendChild(shortcutsCard);
            return root;
        };
    }

    /**
     * 构造一条快捷键速查行：按键列 + 说明列。
     *
     * <p>按键列是普通正文，取<b>来源主题</b>正文前景
     * （{@link SceneThemes#foreground(SceneRuntime)} 派生信号，构建期不读值）：主题切换只重派生
     * 颜色、不重建节点、不改字号与布局；说明列继续用已主题化的 {@link PlaygroundKit#hint(String)}。</p>
     *
     * @param rt          场景运行时（解析来源主题）
     * @param keys        按键文本
     * @param description 说明文本
     * @return 快捷键行节点
     */
    private static SceneNode shortcut(SceneRuntime rt, String keys, String description) {
        SceneNode row = SceneNode.row(8);
        row.setHitTestable(false);
        row.appendChild(PlaygroundKit.text(rt, keys, SceneThemes.foreground(rt), 13));
        row.appendChild(PlaygroundKit.hint(description).setFlexGrow(1));
        return row;
    }
}