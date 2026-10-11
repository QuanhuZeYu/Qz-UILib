package club.heiqi.uilib.testing.gallery;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageContent;
/** 测试广场的显式宿主供给，不成为生产Core的服务定位器。 */
public interface GalleryEnvironment {
    ImageService images();
    default String initialLanguage() { return "zh_CN"; }
    default boolean hasItems() { return false; }
    default ImageSource item(boolean enchanted, int amount, int damage) { return ImageSource.parse("item:unsupported", null); }
    /** 通用公式/文档图像样本；MC可供给真实物品，不外推专项结果。 */
    default ImageSource embed(boolean alternate) {
        int color = alternate ? 0xff22cc88 : 0xff3355dd;
        byte[] rgba = new byte[16 * 16 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte)(color >>> 16); rgba[i+1] = (byte)(color >>> 8);
            rgba[i+2] = (byte)color; rgba[i+3] = (byte)255;
        }
        return ImageSource.bitmap(new ImageContent(16, 16, rgba));
    }
}
