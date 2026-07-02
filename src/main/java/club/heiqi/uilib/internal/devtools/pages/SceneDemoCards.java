package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * demo 页面静态卡片外壳 builder 模板。
 *
 * <p>统一各 demo 页重复出现的「CARD_BG 底 + CARD_BORDER 边 + 圆角 10 + padding 12 + gap 8
 * + 标题 + helper 说明」静态卡片外壳。带动态 bind 边框的复杂外壳（如 Form 的
 * createFieldShell 派生边框色）不在此收口，仍留各页私有。</p>
 */
public final class SceneDemoCards {
    private SceneDemoCards() {
    }

    /**
     * 创建静态卡片外壳：COLUMN 容器，CARD_BG 底 + 1px CARD_BORDER 边 + 圆角 10 +
     * padding 12 + gap 8，首行标题（TEXT_COLOR），次行 helper（MUTED_COLOR，可为空）。
     *
     * <p>调用方在返回的 card 上 appendChild 具体内容节点。</p>
     *
     * @param title  卡片标题（非空）
     * @param helper 帮助说明，null 或空串则不追加 helper 行
     * @return 卡片外壳节点
     */
    public static SceneNode cardShell(String title, String helper) {
        SceneNode card = SceneNode.column();
        card.setBackgroundColor(SceneDemoTokens.CARD_BG);
        card.setBorderWidth(1);
        card.setBorderColor(SceneDemoTokens.CARD_BORDER);
        card.setCornerRadius(10);
        card.setPadding(12);
        card.setGap(8);
        card.appendChild(text(title, SceneDemoTokens.TEXT_COLOR));
        if (helper != null && !helper.isEmpty()) {
            card.appendChild(text(helper, SceneDemoTokens.MUTED_COLOR));
        }
        return card;
    }

    /**
     * 创建文字节点：setText + setTextColor + setHitTestable(false)，与各页私有
     * text() helper 行为一致。
     *
     * @param value 文本内容
     * @param color 文本颜色
     * @return 文本节点
     */
    private static SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }
}
