package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.internal.devtools.playground.pages.HomePage;
import club.heiqi.uilib.internal.devtools.playground.pages.OverlayPage;
import club.heiqi.uilib.internal.devtools.playground.pages.ReactivePage;
import club.heiqi.uilib.internal.devtools.playground.pages.TextAreaPage;
import club.heiqi.uilib.internal.devtools.playground.pages.TextInputPage;

/**
 * 测试场地演示页注册表 —— 新增演示页的单一扩展点。
 *
 * <p>扩展路径：实现 {@link PlaygroundPage} 放入 {@code pages} 子包 → 在
 * {@link #createDefaultPages()} 列表追加一条 → 宿主导航与 Home 总览自动出现新页。
 * 页面顺序即导航段顺序，首项为默认页。</p>
 */
public final class PlaygroundPageRegistry {

    /** 默认页面清单（不可变，构建期快照）。 */
    private static final List<PlaygroundPage> DEFAULT_PAGES = createDefaultPages();

    private PlaygroundPageRegistry() {
    }

    /**
     * 获取默认页面清单（不可变、按导航顺序）。
     *
     * @return 页面清单
     */
    public static List<PlaygroundPage> defaultPages() {
        return DEFAULT_PAGES;
    }

    /**
     * 按页面 id 查询。
     *
     * @param id 页面 id
     * @return 命中的页面；未找到返回 null
     */
    public static PlaygroundPage lookup(String id) {
        if (id == null) {
            return null;
        }
        for (PlaygroundPage page : DEFAULT_PAGES) {
            if (id.equals(page.id())) {
                return page;
            }
        }
        return null;
    }

    /**
     * 全部页面 id（按导航顺序，供测试断言唯一性）。
     *
     * @return 页面 id 列表（不可变）
     */
    public static List<String> ids() {
        List<String> ids = new ArrayList<String>(DEFAULT_PAGES.size());
        for (PlaygroundPage page : DEFAULT_PAGES) {
            ids.add(page.id());
        }
        return Collections.unmodifiableList(ids);
    }

    private static List<PlaygroundPage> createDefaultPages() {
        List<PlaygroundPage> pages = new ArrayList<PlaygroundPage>();
        pages.add(new HomePage());
        pages.add(new TextInputPage());
        pages.add(new TextAreaPage());
        pages.add(new OverlayPage());
        pages.add(new ReactivePage());
        return Collections.unmodifiableList(pages);
    }
}