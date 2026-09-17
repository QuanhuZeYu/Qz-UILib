package club.heiqi.config.ui.field;

import club.heiqi.config.ui.editor.CandidateSourceValueEditorProvider;
import club.heiqi.config.ui.editor.PickerIconSource;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.env.ResourceEnvironment;
import club.heiqi.uilib.ui.scene.control.search.PickerIconCache;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * PickerIconResolver —— 把「图标源纯函数 + UILib 有界缓存」接到既有 {@link VisualAdapter} 上的适配器。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2 A8/§5.5。分工：</p>
 * <ul>
 *   <li>文本（candidateLabel/variantLabel）与变体图标<b>原样委托</b> provider 自己的适配器；</li>
 *   <li>候选图标：{@code iconSource().candidateIcon(key)} 纯函数结果经 {@link PickerIconCache}
 *       按候选域键缓存（Miner 侧不再自建无界 HashMap）；</li>
 *   <li>失效：资源代际（{@link ResourceEnvironment#resourceEpoch()}，<b>构造注入的环境端口</b>）变化 →
 *       清空两层；分级代际（{@link ItemRenderTierRegistry#tierGeneration()}）变化 → 丢弃不可渲染键
 *       （按 key 重取）。两者都是 O(1) long 比对，仅在取图标时检查（无每帧成本）。</li>
 * </ul>
 *
 * <p><b>为什么资源代际走注入端口而不是直读 {@code ResourceReloadService}</b>：直读进程单例让本类在
 * headless 出图 / 测试里无法替换环境事实（配置界面在无头进程里也会装配到本类）。端口缺席值恒 0
 * 与「未注册可重载资源管理器」逐位等价，故生产行为不变。</p>
 *
 * <p><b>变体级图标的已知边界</b>：既有 {@link VisualAdapter#variantImage(SearchPickerData.Variant)}
 * 的入参只有变体、没有候选上下文，无法拼出 {@code PickerIconKey.variant(candidateKey, meta)}；
 * 本阶段保持委托（变体级图标解析待 VariantChooser 侧接线，见实现记录未决项 U-A7）。</p>
 *
 * <p>线程：仅客户端主线程（缓存入口自带断言）。</p>
 */
public final class PickerIconResolver implements VisualAdapter {

    private final VisualAdapter delegate;
    private final PickerIconSource iconSource;
    /** 资源代际来源（构造注入的环境端口；缺席态恒 0，等价于「未注册可重载资源管理器」）。 */
    private final ResourceEnvironment resources;
    /** 首个图标请求时创建（ADR §1.4 的创建点）。 */
    private PickerIconCache cache;
    private long lastResourceEpoch;
    private long lastTierGeneration;

    /**
     * 便捷构造：资源代际按缺席处理（恒 0 = 不因资源重载失效）。
     *
     * @param delegate   原 provider 的展示适配器（非 null）
     * @param iconSource 图标源纯函数（非 null）
     */
    public PickerIconResolver(VisualAdapter delegate, PickerIconSource iconSource) {
        this(delegate, iconSource, ResourceEnvironment.EMPTY);
    }

    /**
     * @param delegate   原 provider 的展示适配器（非 null）
     * @param iconSource 图标源纯函数（非 null）
     * @param resources  资源代际端口（不可为 null；缺席请显式传 {@link ResourceEnvironment#EMPTY}）
     */
    public PickerIconResolver(VisualAdapter delegate, PickerIconSource iconSource,
            ResourceEnvironment resources) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (iconSource == null) throw new IllegalArgumentException("iconSource must not be null");
        if (resources == null) throw new IllegalArgumentException("resources must not be null");
        this.delegate = delegate;
        this.iconSource = iconSource;
        this.resources = resources;
        this.lastResourceEpoch = resources.resourceEpoch();
        this.lastTierGeneration = ItemRenderTierRegistry.tierGeneration();
    }

    /**
     * 按 provider 能力创建解析器。
     *
     * @param provider  已冻结的注册快照（可为 null）
     * @param resources 资源代际端口（不可为 null）——由装配点从宿主环境提供，本类不回落进程单例
     * @return 解析器；provider 未实现 SPI、无图标源或适配器缺失时返回 null（⇒ 保持原适配器）
     */
    public static PickerIconResolver of(ValueEditorProvider provider, ResourceEnvironment resources) {
        if (!(provider instanceof CandidateSourceValueEditorProvider)) {
            return null;
        }
        PickerIconSource iconSource = ((CandidateSourceValueEditorProvider) provider).iconSource();
        if (iconSource == null || provider.visualAdapter() == null) {
            return null;
        }
        return new PickerIconResolver(provider.visualAdapter(), iconSource, resources);
    }

    @Override
    public SceneImageSource candidateImage(SearchPickerData.Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        refreshEpochs();
        return cache().candidateIcon(candidate.key(), iconSource);
    }

    @Override
    public SceneImageSource variantImage(SearchPickerData.Variant variant) {
        return delegate.variantImage(variant);
    }

    @Override
    public String candidateLabel(SearchPickerData.Candidate candidate) {
        return delegate.candidateLabel(candidate);
    }

    @Override
    public String variantLabel(SearchPickerData.Variant variant) {
        return delegate.variantLabel(variant);
    }

    /** 释放缓存（挂屏级释放链：Owner 清理 / ConfigScreen.dispose 路径）。 */
    public void release() {
        if (cache != null) {
            cache.release();
        }
    }

    /** @return 图标缓存（首个图标请求后非 null；诊断/测试探针） */
    PickerIconCache cache() {
        if (cache == null) {
            cache = new PickerIconCache();
        }
        return cache;
    }

    /** 代际刷新（O(1) long 比对；只在取图标时执行，无每帧成本）。 */
    private void refreshEpochs() {
        if (cache == null) {
            lastResourceEpoch = resources.resourceEpoch();
            lastTierGeneration = ItemRenderTierRegistry.tierGeneration();
            return;
        }
        long resource = resources.resourceEpoch();
        if (resource != lastResourceEpoch) {
            lastResourceEpoch = resource;
            cache.invalidateAll("resource_reload");
        }
        long tier = ItemRenderTierRegistry.tierGeneration();
        if (tier != lastTierGeneration) {
            lastTierGeneration = tier;
            cache.onTierGenerationChanged(tier);
        }
    }
}
