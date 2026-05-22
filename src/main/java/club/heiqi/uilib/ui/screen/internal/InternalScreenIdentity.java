package club.heiqi.uilib.ui.screen.internal;

import java.util.Objects;

/**
 * 内部托管页面的稳定身份识别工具。
 *
 * <p>类与必要方法对外提升为 public，仅供 ui.screen / ui.screen.internal 内的协作类
 * 与诊断工具跨包使用，不构成对业务作者的稳定 API。</p>
 *
 * @apiNote 内部类型，LTS 不承诺其稳定性。{@link PageDescriptor}、{@link DescriptorOwner}
 *          以及静态方法仅供框架运行时识别诊断/托管页面身份，业务代码不应直接依赖这些类型。
 *          稳定页面 id 字符串可作为只读契约通过 {@link InternalDiagnosticScreenRegistry}
 *          的查询方法使用。
 */
public final class InternalScreenIdentity {

    private InternalScreenIdentity() {}

    /**
     * 页面描述对象，仅承载稳定页面标识。
     */
    public static final class PageDescriptor {

        private final String pageId;

        public PageDescriptor(String pageId) {
            this.pageId = Objects.requireNonNull(pageId, "pageId");
        }

        /**
         * 返回稳定页面标识。
         *
         * @return 页面标识
         */
        public String getPageId() {
            return pageId;
        }
    }

    /**
     * 描述对象持有者。
     */
    public interface DescriptorOwner {

        /**
         * 返回当前页面的稳定描述对象。
         *
         * @return 页面描述对象
         */
        PageDescriptor getPageDescriptor();
    }

    /**
     * 判断对象是否声明了目标页面标识。
     *
     * @param screen 待判断对象
     * @param expectedPageId 目标页面标识
     * @return 是否匹配
     */
    public static boolean hasPageId(Object screen, String expectedPageId) {
        return expectedPageId != null && expectedPageId.equals(getPageId(screen));
    }

    /**
     * 读取对象的稳定页面标识。
     *
     * @param screen 目标对象
     * @return 页面标识，不存在时返回空字符串
     */
    public static String getPageId(Object screen) {
        if (!(screen instanceof DescriptorOwner)) {
            return "";
        }
        PageDescriptor descriptor = ((DescriptorOwner) screen).getPageDescriptor();
        return descriptor == null ? "" : descriptor.getPageId();
    }

    /**
     * 读取对象的运行时身份标识。
     *
     * <p>优先返回稳定页面标识；若界面尚未接入 descriptor seam，则回退到具体类名。</p>
     *
     * @param screen 目标对象
     * @return 运行时身份标识
     */
    public static String runtimeScreenNameOf(Object screen) {
        String pageId = getPageId(screen);
        if (!pageId.isEmpty()) {
            return pageId;
        }
        return screen == null ? "" : screen.getClass().getSimpleName();
    }
}
