package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.render.BackdropBlurPreset;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.internal.InternalHostedScreenFactory;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import net.minecraft.client.gui.GuiScreen;

/**
 * 业务文档界面创建边界。
 *
 * <p>该类只保留面向业务作者的稳定入口，不再承载内部托管页面 definition、descriptor
 * 或运行时身份识别机制。</p>
 */
public final class UiDocumentScreens {

    private UiDocumentScreens() {}

    /**
     * 文档页面创建环境。
     *
     * <p>把文本测量与运行时适配器收敛成一个显式入口，
     * 让默认值只停留在最外层调用边界，而不是继续散落在 screen/scope 构造链路里。</p>
     */
    public static final class DocumentScreenEnvironment {

        private final TextMeasureService textMeasureService;
        private final UiRuntimeAdapters runtimeAdapters;
        private final TextContentMode defaultTextContentMode;
        private final BackdropBlurPolicy backdropBlurPolicy;

        /**
         * 创建文档页面环境。
         *
         * @param textMeasureService 文本测量服务
         * @param runtimeAdapters 运行时适配器集合
         */
        public DocumentScreenEnvironment(TextMeasureService textMeasureService,
                UiRuntimeAdapters runtimeAdapters) {
            this(textMeasureService, runtimeAdapters, TextContentMode.UILIB_RAW, BackdropBlurPolicy.inheritGlobal());
        }

        /**
         * 创建文档页面环境。
         *
         * @param textMeasureService 文本测量服务
         * @param runtimeAdapters 运行时适配器集合
         * @param defaultTextContentMode 新建文本节点默认解析模式
         */
        public DocumentScreenEnvironment(TextMeasureService textMeasureService,
                UiRuntimeAdapters runtimeAdapters, TextContentMode defaultTextContentMode) {
            this(textMeasureService, runtimeAdapters, defaultTextContentMode, BackdropBlurPolicy.inheritGlobal());
        }

        private DocumentScreenEnvironment(TextMeasureService textMeasureService,
                UiRuntimeAdapters runtimeAdapters, TextContentMode defaultTextContentMode,
                BackdropBlurPolicy backdropBlurPolicy) {
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
            this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
            this.defaultTextContentMode = defaultTextContentMode == null
                    ? TextContentMode.UILIB_RAW : defaultTextContentMode;
            this.backdropBlurPolicy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                    : backdropBlurPolicy;
        }

        /**
         * 创建当前 Minecraft 宿主使用的默认业务文档环境。
         *
         * <p>HTML-like 业务页面默认按 UILib 原始文本处理，不再隐式解析 Minecraft `§` 格式码。</p>
         */
        public static DocumentScreenEnvironment minecraftDefaults() {
            return new DocumentScreenEnvironment(DefaultTextMeasureService.getInstance(),
                    UiRuntimeAdapters.minecraftDefaults(), TextContentMode.UILIB_RAW);
        }

        /**
         * 创建带背景模糊预设的 Minecraft 默认业务文档环境。
         *
         * @param preset 背景模糊预设；为 null 时继承全局配置
         * @return 文档页面环境
         */
        public static DocumentScreenEnvironment minecraftDefaults(BackdropBlurPreset preset) {
            return minecraftDefaults().withBackdropBlurPolicy(BackdropBlurPolicy.fromPreset(preset));
        }

        /**
         * 创建关闭当前页面背景模糊的 Minecraft 默认业务文档环境。
         *
         * @return 文档页面环境
         */
        public static DocumentScreenEnvironment withoutBackgroundBlur() {
            return minecraftDefaults(BackdropBlurPreset.DISABLED);
        }

        /**
         * 创建当前 Minecraft 宿主使用的 Minecraft 文本兼容环境。
         *
         * <p>该环境会保留 `§` 颜色与样式码语义，供诊断页、兼容页或显式需要旧文本格式的场景使用。</p>
         */
        public static DocumentScreenEnvironment minecraftFormattedDefaults() {
            return new DocumentScreenEnvironment(DefaultTextMeasureService.getMinecraftInstance(),
                    UiRuntimeAdapters.minecraftDefaults(), TextContentMode.MINECRAFT_FORMATTED);
        }

        /**
         * 返回文本测量服务。
         *
         * @return 文本测量服务
         */
        public TextMeasureService getTextMeasureService() {
            return textMeasureService;
        }

        /**
         * 返回运行时适配器集合。
         *
         * @return 运行时适配器集合
         */
        public UiRuntimeAdapters getRuntimeAdapters() {
            return runtimeAdapters;
        }

        /**
         * 返回当前环境中新建文本节点使用的默认解析模式。
         *
         * @return 默认文本内容解析模式
         */
        public TextContentMode getDefaultTextContentMode() {
            return defaultTextContentMode;
        }

        /**
         * 返回页面级背景模糊策略。
         *
         * @return 背景模糊策略
         */
        public BackdropBlurPolicy getBackdropBlurPolicy() {
            return backdropBlurPolicy;
        }

        /**
         * 返回替换页面级背景模糊策略后的新环境。
         *
         * @param policy 页面级背景模糊策略；为 null 时继承全局配置
         * @return 新文档页面环境
         */
        public DocumentScreenEnvironment withBackdropBlurPolicy(BackdropBlurPolicy policy) {
            return new DocumentScreenEnvironment(textMeasureService, runtimeAdapters, defaultTextContentMode,
                    policy == null ? BackdropBlurPolicy.inheritGlobal() : policy);
        }
    }

    /**
     * HTML-like 文档内容构建器。
     *
     * <p>宿主层负责创建 `UiDocument`、`HtmlLikeDocumentWidget` 和 Minecraft `GuiScreen`，
     * 使用者只需要在回调中组装文档树、样式和事件。</p>
     */
    public interface DocumentScreenContentBuilder {

        /**
         * 构建 HTML-like 文档内容。
         *
         * @param document 待填充的文档
         */
        void build(UiDocument document);
    }

    /**
     * 文档页面宿主生命周期回调。
     */
    public interface DocumentScreenLifecycle {

        /**
         * 文档页面关闭时调用。
         */
        void onClosed();
    }

    /**
     * 带可选生命周期回调的文档页面 provision。
     */
    public static final class DocumentScreenProvision {

        private final DocumentScreenContentBuilder contentBuilder;
        private final DocumentScreenLifecycle lifecycle;

        private DocumentScreenProvision(DocumentScreenContentBuilder contentBuilder,
                DocumentScreenLifecycle lifecycle) {
            this.contentBuilder = Objects.requireNonNull(contentBuilder, "contentBuilder");
            this.lifecycle = lifecycle;
        }

        public static DocumentScreenProvision of(DocumentScreenContentBuilder contentBuilder,
                DocumentScreenLifecycle lifecycle) {
            return new DocumentScreenProvision(contentBuilder, lifecycle);
        }

        public DocumentScreenContentBuilder getContentBuilder() {
            return contentBuilder;
        }

        public DocumentScreenLifecycle getLifecycle() {
            return lifecycle;
        }
    }

    /**
     * 创建由调用方填充 `UiDocument` 的业务文档界面。
     *
     * <p>该入口用于 Minecraft 宿主层快速打开 HTML-like UI，调用方无需接触内部页面控制器、
     * 页面 definition 或 `HtmlLikeDocumentWidget` 挂载细节。根元素若未显式声明
     * `width`、`height` 或 `overflow-y`，框架会分别兜底为 `100%`、`100%` 与 `auto`。</p>
     *
     * @param contentBuilder 文档内容构建器
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenContentBuilder contentBuilder) {
        return createDocumentScreen(DocumentScreenEnvironment.minecraftDefaults(), contentBuilder);
    }

    /**
     * 使用背景模糊预设创建业务文档界面。
     *
     * @param contentBuilder 文档内容构建器
     * @param preset 背景模糊预设
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenContentBuilder contentBuilder,
            BackdropBlurPreset preset) {
        return createDocumentScreen(DocumentScreenEnvironment.minecraftDefaults(preset), contentBuilder);
    }

    /**
     * 使用页面级背景模糊策略创建业务文档界面。
     *
     * @param contentBuilder 文档内容构建器
     * @param policy 背景模糊策略
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenContentBuilder contentBuilder,
            BackdropBlurPolicy policy) {
        return createDocumentScreen(DocumentScreenEnvironment.minecraftDefaults().withBackdropBlurPolicy(policy),
                contentBuilder);
    }

    /**
     * 基于显式文档环境创建由调用方填充 `UiDocument` 的业务文档界面。
     *
     * @param environment 文档页面创建环境
     * @param contentBuilder 文档内容构建器
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenEnvironment environment,
            DocumentScreenContentBuilder contentBuilder) {
        return createDocumentScreen(environment, DocumentScreenProvision.of(contentBuilder, null));
    }

    /**
     * 基于显式文档环境和页面级背景模糊策略创建业务文档界面。
     *
     * @param environment 文档页面创建环境
     * @param policy 背景模糊策略
     * @param contentBuilder 文档内容构建器
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenEnvironment environment,
            BackdropBlurPolicy policy, DocumentScreenContentBuilder contentBuilder) {
        return createDocumentScreen(Objects.requireNonNull(environment, "environment")
                .withBackdropBlurPolicy(policy), contentBuilder);
    }

    /**
     * 基于显式文档环境和生命周期 provision 创建业务文档界面。
     *
     * @param environment 文档页面创建环境
     * @param provision 页面内容与生命周期 provision
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenEnvironment environment,
            DocumentScreenProvision provision) {
        return InternalHostedScreenFactory.createScreen(InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION,
                Objects.requireNonNull(environment, "environment"),
                Objects.requireNonNull(provision, "provision"));
    }
}
