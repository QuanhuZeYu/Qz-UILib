package club.heiqi.uilib.internal.devtools.headless;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * PNG 编码出口（包内实现细节，不对外暴露）。
 *
 * <p>用 AWT 的 {@code ImageIO} 而不是自建编码器：headless 出图的目标是「人能看、agent 能读」，
 * PNG 只承担容器职责，不参与像素语义；字体侧已经依赖 AWT，故不引入新的运行期依赖面。</p>
 */
final class PngWriter {

    private PngWriter() {
    }

    /**
     * 把行主序 ARGB 像素写成 PNG。
     *
     * @param path   目标路径（父目录会自动创建）
     * @param argb   行主序 ARGB 像素
     * @param width  像素宽
     * @param height 像素高
     * @return 写入的字节数
     */
    static long write(Path path, int[] argb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw new HeadlessFailure(HeadlessFailure.Stage.ENCODE, "没有可用的 PNG 编码器：" + path);
            }
            return Files.size(path);
        } catch (IOException e) {
            throw new HeadlessFailure(HeadlessFailure.Stage.ENCODE, "写入 PNG 失败：" + path, e);
        }
    }
}
