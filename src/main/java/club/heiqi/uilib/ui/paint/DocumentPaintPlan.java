package club.heiqi.uilib.ui.paint;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 一趟绘制命令构建的产物：命令列表 + 滚动依赖快照。
 *
 * <p>方案2 下，走回放期偏移栈（eligible）的滚动容器滚动时只改回放参数、不重建命令，因此其滚动偏移不计入
 * 缓存失效条件。但被判回退（ineligible，子树含 positioned 后代）的可滚动容器仍把构建期滚动烘焙进 flow
 * content 坐标，且不发 {@code SCROLL_OFFSET} 作用域，滚动后必须重建命令否则内容停在旧坐标。</p>
 *
 * <p>{@code scrollDependencies} 登记这些回退容器及其构建期滚动偏移 {@code [scrollLeft, scrollTop]}。缓存命中
 * 判定时，只需比对快照中各容器的当前偏移是否仍等于构建期偏移：相等则命令仍有效、可免重建；不等则该容器
 * 滚动了、必须重建。普通页面（所有可滚动容器都 eligible，或无可滚动容器）快照为空 → 滚动永不触发重建。</p>
 */
public final class DocumentPaintPlan {

    private final List<DocumentPaintCommand> commands;
    private final Map<ElementNode, int[]> scrollDependencies;

    DocumentPaintPlan(List<DocumentPaintCommand> commands, Map<ElementNode, int[]> scrollDependencies) {
        this.commands = commands == null ? Collections.<DocumentPaintCommand>emptyList() : commands;
        this.scrollDependencies = scrollDependencies == null
                ? Collections.<ElementNode, int[]>emptyMap() : scrollDependencies;
    }

    /**
     * 返回绘制命令列表。
     *
     * @return 命令列表
     */
    public List<DocumentPaintCommand> getCommands() {
        return commands;
    }

    /**
     * 返回滚动依赖快照：回退（ineligible）可滚动容器 -&gt; 构建期滚动偏移 {@code [scrollLeft, scrollTop]}。
     *
     * @return 滚动依赖快照；普通页面为空表
     */
    public Map<ElementNode, int[]> getScrollDependencies() {
        return scrollDependencies;
    }

    /**
     * 判断这趟命令在给定滚动态下是否仍然有效（无需重建）。
     *
     * <p>快照为空时恒返回 {@code true}（普通页面滚动只走回放期偏移栈）。否则要求快照中每个回退容器的当前
     * 滚动偏移都仍等于构建期偏移；任一回退容器滚动了即失效。</p>
     *
     * @param scrollLeftOf 元素 -&gt; 当前横向滚动偏移查询
     * @param scrollTopOf 元素 -&gt; 当前纵向滚动偏移查询
     * @return 命令是否仍有效
     */
    public boolean isValidFor(ScrollOffsetQuery scrollLeftOf, ScrollOffsetQuery scrollTopOf) {
        if (scrollDependencies.isEmpty()) {
            return true;
        }
        for (Map.Entry<ElementNode, int[]> entry : scrollDependencies.entrySet()) {
            ElementNode element = entry.getKey();
            int[] buildScroll = entry.getValue();
            if (scrollLeftOf.scrollOf(element) != buildScroll[0]
                    || scrollTopOf.scrollOf(element) != buildScroll[1]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 元素滚动偏移查询函数式接口（Java 8 lambda 友好）。
     */
    public interface ScrollOffsetQuery {

        /**
         * 返回元素当前滚动偏移（单一方向）。
         *
         * @param element 滚动容器元素
         * @return 当前滚动偏移
         */
        int scrollOf(ElementNode element);
    }
}
