package club.heiqi.uilib.ui.runtime;

import java.util.Objects;

import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.ItemIconRenderer;
import club.heiqi.uilib.ui.image.MinecraftHostImageRenderer;
import club.heiqi.uilib.ui.image.MinecraftItemIconRenderer;

/**
 * UI 运行时适配器集合。
 *
 * <p>普通 texture/bitmap 与 ItemStack icon 使用物理分离的委托：普通图片保持轻量路径，
 * icon 走当帧直绘（2D 等价自绘 / 原版委托）。</p>
 */
public final class UiRuntimeAdapters implements AutoCloseable {

    private final HostImageRenderer hostImageRenderer;
    private final ItemIconRenderer itemIconRenderer;
    /** fluent 派生值共享一个 exactly-once 生命周期；用户注入实例永不由该 owner 关闭。 */
    private final OwnedResources ownedResources;

    UiRuntimeAdapters(HostImageRenderer hostImageRenderer, ItemIconRenderer itemIconRenderer,
            HostImageRenderer ownedHostImageRenderer) {
        this(hostImageRenderer, itemIconRenderer, new OwnedResources(ownedHostImageRenderer));
    }

    private UiRuntimeAdapters(HostImageRenderer hostImageRenderer, ItemIconRenderer itemIconRenderer,
            OwnedResources ownedResources) {
        this.hostImageRenderer = hostImageRenderer;
        this.itemIconRenderer = itemIconRenderer;
        this.ownedResources = ownedResources;
    }

    /**
     * 创建不附带任何运行时默认值的适配器集合。
     *
     * <p>测试或非 Minecraft 宿主可以从该空集合开始，按需显式注入所需能力。</p>
     *
     * @return 空适配器集合
     */
    public static UiRuntimeAdapters empty() {
        return new UiRuntimeAdapters(null, null, (HostImageRenderer) null);
    }

    /**
     * 创建使用 Minecraft 默认运行时行为的适配器集合。
     *
     * <p>默认 renderer 的创建责任收敛在适配器边界，避免控件内部再隐式回退到 Minecraft 运行时。</p>
     *
     * @return 默认适配器集合
     */
    public static UiRuntimeAdapters minecraftDefaults() {
        MinecraftHostImageRenderer hostImageRenderer = new MinecraftHostImageRenderer();
        return new UiRuntimeAdapters(hostImageRenderer, new MinecraftItemIconRenderer(), hostImageRenderer);
    }

    /**
     * 返回注入指定宿主图片渲染委托后的新适配器集合。
     *
     * <p>该委托保持普通图片轻量路径，不添加 ItemStack 状态围栏。注入实例的生命周期仍归调用方。</p>
     *
     * @param hostImageRenderer 宿主图片渲染委托
     * @return 新适配器集合
     */
    public UiRuntimeAdapters withHostImageRenderer(HostImageRenderer hostImageRenderer) {
        ownedResources.ensureOpen();
        return new UiRuntimeAdapters(Objects.requireNonNull(hostImageRenderer, "hostImageRenderer"),
                itemIconRenderer, ownedResources);
    }

    /**
     * 返回注入指定 ItemStack icon 委托后的新适配器集合。
     *
     * <p>委托负责当帧直绘 icon 内容（2D 判定、原版非 2D 委托与 GL 状态自净）。</p>
     *
     * @param itemIconRenderer ItemStack icon 委托
     * @return 新适配器集合
     */
    public UiRuntimeAdapters withItemIconRenderer(ItemIconRenderer itemIconRenderer) {
        ownedResources.ensureOpen();
        return new UiRuntimeAdapters(hostImageRenderer,
                Objects.requireNonNull(itemIconRenderer, "itemIconRenderer"), ownedResources);
    }

    /**
     * 获取宿主图片渲染委托。
     *
     * @return 宿主图片渲染委托；为空时无法使用 `img`/背景贴图这类宿主图片能力
     */
    public HostImageRenderer getHostImageRenderer() {
        ownedResources.ensureOpen();
        return hostImageRenderer;
    }

    /**
     * 获取 ItemStack icon 内容委托。
     *
     * @return item icon 委托；为空时 item source 跳过绘制（不画占位）
     */
    public ItemIconRenderer getItemIconRenderer() {
        ownedResources.ensureOpen();
        return itemIconRenderer;
    }

    /**
     * 释放本集合内部创建的 Minecraft plain renderer 资源。
     *
     * <p>所有 {@code with*} 派生值共享同一生命周期；任一派生值成功关闭后，整个派生族都不可再使用。
     * 通过 {@code withHostImageRenderer} 注入的实例可能被多个宿主共享，始终由调用方自行关闭。</p>
     */
    @Override
    public void close() {
        ownedResources.close();
    }

    private static final class OwnedResources {
        private HostImageRenderer hostImageRenderer;
        private boolean closed;

        private OwnedResources(HostImageRenderer hostImageRenderer) {
            this.hostImageRenderer = hostImageRenderer;
        }

        private synchronized void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("runtime adapters already closed");
            }
        }

        private synchronized void close() {
            if (closed) {
                return;
            }
            if (hostImageRenderer != null) {
                hostImageRenderer.close();
                hostImageRenderer = null;
            }
            closed = true;
        }
    }
}
