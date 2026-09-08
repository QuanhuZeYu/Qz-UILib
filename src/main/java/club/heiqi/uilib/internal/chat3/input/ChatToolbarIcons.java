package club.heiqi.uilib.internal.chat3.input;

import java.awt.image.BufferedImage;
import java.util.Objects;

import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Entry;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Status;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 工具栏私有图标：共享网络缓存 → UI 帧信号 → scene image / PaintCommand。 */
final class ChatToolbarIcons {

    /**
     * Google Material Icons outlined（Apache-2.0），固定提交避免上游更新改变图案。
     * 官方 PNG 只有黑色版本；复制 alpha 并置白，不能修改共享缓存中的原图。
     * 六个地址均已验证 HTTP 200、image/png、48×48。
     * 来源：https://github.com/google/material-design-icons （Google，Apache-2.0）。
     * 许可证：https://github.com/google/material-design-icons/blob/
     * 0cbb08816df07faaae3dca060d4ebb10b66c214f/LICENSE
     */
    private static final String BASE_URL = "https://raw.githubusercontent.com/google/material-design-icons/"
            + "0cbb08816df07faaae3dca060d4ebb10b66c214f/png/";

    private ChatToolbarIcons() {}

    /**
     * 在 UI 线程的 mount / forEach 项构建期，对独立图标节点调用一次。
     * 调用方设置 preferredWidth/Height = 16；此处只管理图像与回退文本。
     * 无当前 Owner 时归属 runtime 根，随 runtime.dispose() 清理。
     */
    static void attach(SceneRuntime rt, SceneNode icon, String name) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(icon, "icon");
        Owner owner = Owner.current();
        if (owner == null) {
            rt.__runRoot(() -> attach(rt, icon, name));
            return;
        }
        if (owner.isDisposed()) return;

        icon.setImageSource(null);
        icon.setText(fallbackFor(name));
        icon.setTextColor(0xFFFFFFFF);
        icon.setFontSize(14);
        icon.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
        icon.setTextVerticalAlign(TextVerticalAlign.CENTER);

        String url = urlFor(name);
        // request 的 callback 仅在首次请求成功时执行，后续订阅者和失败均不会收到通知。
        // 不把节点/Owner 捕获到下载线程：只在 UI 帧读取 Entry 的 volatile 终态。
        Entry entry = DocumentRemoteImageCache.getInstance().request(url, null);
        PendingIcon pending = new PendingIcon(icon, url, entry);
        owner.onCleanup(pending::dispose);
        pending.binding = rt.bind(rt.__frameTimeNanos(), frame -> pending.refresh());
    }

    static String urlFor(String name) {
        String path;
        String glyph;
        if ("edit".equals(name)) {
            path = "image/edit";
            glyph = "edit";
        } else if ("finish".equals(name)) {
            path = "navigation/check";
            glyph = "check";
        } else if ("cancel".equals(name)) {
            path = "navigation/close";
            glyph = "close";
        } else if ("reset-current".equals(name)) {
            path = "content/undo";
            glyph = "undo";
        } else if ("reset-all".equals(name)) {
            path = "action/restore";
            glyph = "restore";
        } else {
            path = "navigation/more_horiz";
            glyph = "more_horiz";
        }
        return BASE_URL + path + "/materialiconsoutlined/24dp/2x/outline_" + glyph + "_black_24dp.png";
    }

    static String fallbackFor(String name) {
        if ("edit".equals(name)) return "E";
        if ("finish".equals(name)) return "V";
        if ("cancel".equals(name)) return "X";
        if ("reset-current".equals(name)) return "<";
        if ("reset-all".equals(name)) return "R";
        return "*";
    }

    /** 只在挂载期间持有节点；终态停止帧订阅，卸载释放节点与缓存条目引用。 */
    private static final class PendingIcon {
        private SceneNode icon;
        private final String url;
        private Entry entry;
        private Binding binding;

        PendingIcon(SceneNode icon, String url, Entry entry) {
            this.icon = icon;
            this.url = url;
            this.entry = entry;
        }

        void refresh() {
            if (icon == null || entry == null) return;
            Status status = entry.getStatus();
            if (status != Status.LOADED && status != Status.FAILED) return;
            if (status == Status.LOADED) {
                BufferedImage source = entry.getImage();
                if (source != null) {
                    BufferedImage white = new BufferedImage(source.getWidth(), source.getHeight(),
                            BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < source.getHeight(); y++) {
                        for (int x = 0; x < source.getWidth(); x++) {
                            white.setRGB(x, y, (source.getRGB(x, y) & 0xFF000000) | 0x00FFFFFF);
                        }
                    }
                    // 相同 URL/白色变换复用宿主纹理键；尺寸由节点布局盒决定。
                    icon.setImageSource(HostImageSource.bufferedImage(white, "chat-toolbar:white:" + url));
                    icon.setText("");
                }
            }
            entry = null;
            stopWatching();
        }

        private void stopWatching() {
            if (binding != null) {
                binding.dispose();
                binding = null;
            }
        }

        void dispose() {
            stopWatching();
            entry = null;
            if (icon != null) {
                icon.setImageSource(null);
                icon.setText("");
                icon = null;
            }
            // 下载/解码缓存和 GPU 纹理由各自共享宿主生命周期回收，不能清空其他消费者。
        }
    }
}
