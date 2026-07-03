package club.heiqi.uilib.ui.scene.host;


import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 测试专用最小 scene 宿主 fixture —— 替代已删的早期 scene demo 宿主，
 * 供 overlay/pipeline 测试仅依赖 runtime + 空根 + 主树背景色的场景使用。
 *
 * <p>不挂任何 demo 控件（按钮/文本框），保持中性，避免与具体 demo 耦合。
 * 主树背景色对齐 overlay 测试断言（{@code 0xFF333333} 深灰）。</p>
 */
final class SceneTestHost extends AbstractSceneHostWidget {

    private final SceneNode root;

    SceneTestHost() {
        this(null);
    }

    SceneTestHost(PlatformInputSource inputSource) {
        super(inputSource);
        this.root = new SceneNode();
        root.setFillParentHeight(true);
        // 主树背景色：overlay 回放顺序断言依赖此颜色被绘制
        root.setBackgroundColor(0xFF333333);
        runtime.flush();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 测试探针：暴露内部 runtime，供测试 portal/route/flush。 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** 测试探针：暴露主树根节点，供测试 appendChild/route 入口。 */
    SceneNode __getRoot() {
        return root;
    }
}
