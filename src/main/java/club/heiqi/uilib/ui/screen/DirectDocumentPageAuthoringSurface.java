package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.widget.Widget;

/**
 * 直接挂载到根视口的 HTML-like 页面 authoring surface。
 *
 * <p>该实现只负责把当前已迁移页面的单个 `HtmlLikeDocumentWidget` 定位到屏幕 frame，
 * 不再创建旧 retained 页面壳。</p>
 */
public final class DirectDocumentPageAuthoringSurface implements DocumentPageAuthoringSurface {

    private final List<Widget> blocks = new ArrayList<Widget>();
    private Widget root;
    private int minContentWidth = 1;
    private int maxContentWidth = Integer.MAX_VALUE;
    private int minContentHeight = 1;
    private float maxViewportFillWidth = 1.0F;
    private float maxViewportFillHeight = 1.0F;
    private int frameLeft;
    private int frameTop;
    private int frameWidth;
    private int frameHeight;

    /**
     * 绑定根 widget，后续 addBlock 会直接挂到该根节点。
     *
     * @param root 根 widget
     */
    void attachRoot(Widget root) {
        this.root = Objects.requireNonNull(root, "root");
        for (Widget block : blocks) {
            if (block.getParent() == null) {
                this.root.addChild(block);
            }
        }
    }

    /**
     * 根据屏幕尺寸与 chrome 策略更新直接页面 frame。
     *
     * @param hostWidth 宿主宽度
     * @param hostHeight 宿主高度
     * @param chrome 屏幕 chrome 策略
     */
    void applyFrameBounds(int hostWidth, int hostHeight, DocumentScreenChrome chrome) {
        DocumentScreenChrome.Insets rootPadding = chrome == null ? DocumentScreenChrome.Insets.of(0, 0, 0, 0)
                : chrome.getRootPadding();
        int availableLeft = rootPadding.getLeft();
        int availableTop = rootPadding.getTop();
        int availableWidth = Math.max(0, hostWidth - rootPadding.getLeft() - rootPadding.getRight());
        int availableHeight = Math.max(0, hostHeight - rootPadding.getTop() - rootPadding.getBottom());
        if (availableWidth <= 0 || availableHeight <= 0) {
            frameLeft = availableLeft;
            frameTop = availableTop;
            frameWidth = 0;
            frameHeight = 0;
            layoutBlocks();
            return;
        }

        int ratioWidth = Math.max(1, Math.round(availableWidth * maxViewportFillWidth));
        int ratioHeight = Math.max(1, Math.round(availableHeight * maxViewportFillHeight));
        frameWidth = Math.min(availableWidth, Math.min(ratioWidth, maxContentWidth));
        frameHeight = Math.min(availableHeight, ratioHeight);
        frameWidth = Math.max(frameWidth, Math.min(availableWidth, minContentWidth));
        frameHeight = Math.max(frameHeight, Math.min(availableHeight, minContentHeight));
        frameLeft = availableLeft + Math.max(0, (availableWidth - frameWidth) / 2);
        frameTop = availableTop;
        layoutBlocks();
    }

    /**
     * 返回已添加的直接块级 widget。
     *
     * @return 直接块级 widget 列表
     */
    public List<Widget> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    @Override
    public DocumentPageAuthoringSurface addBlock(Widget child) {
        if (child == null) {
            return this;
        }
        blocks.add(child);
        if (root != null && child.getParent() == null) {
            root.addChild(child);
            layoutBlocks();
        }
        return this;
    }

    @Override
    public DocumentPageAuthoringSurface setContentWidthRange(int minContentWidth, int maxContentWidth) {
        this.minContentWidth = Math.max(1, minContentWidth);
        this.maxContentWidth = Math.max(this.minContentWidth, maxContentWidth);
        return this;
    }

    @Override
    public DocumentPageAuthoringSurface setMinContentHeight(int minContentHeight) {
        this.minContentHeight = Math.max(1, minContentHeight);
        return this;
    }

    @Override
    public DocumentPageAuthoringSurface setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight) {
        this.maxViewportFillWidth = clampRatio(maxViewportFillWidth);
        this.maxViewportFillHeight = clampRatio(maxViewportFillHeight);
        return this;
    }

    @Override
    public int getWidth() {
        return frameWidth;
    }

    @Override
    public int getHeight() {
        return frameHeight;
    }

    @Override
    public int getScrollOffset() {
        return 0;
    }

    @Override
    public int getMaxScrollOffset() {
        return 0;
    }

    @Override
    public int getVisibleContentWidth() {
        return frameWidth;
    }

    @Override
    public int getVisibleContentHeight() {
        return frameHeight;
    }

    @Override
    public int getContentWidth() {
        return frameWidth;
    }

    @Override
    public int getContentHeight() {
        return frameHeight;
    }

    private void layoutBlocks() {
        for (Widget block : blocks) {
            block.applyLayoutBounds(frameLeft, frameTop, frameWidth, frameHeight);
        }
    }

    private static float clampRatio(float ratio) {
        return Math.max(0.05F, Math.min(ratio, 1.0F));
    }
}
