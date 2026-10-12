package club.heiqi.uilib.host.client.image;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageLoader;

/** Java8宿主解码器；AWT/ImageIO不进入核心。读取前限制编码字节，分配像素前检查真实维度。
 * HTTP一次请求总时限与连接/读取时限明确；仅瞬时网络I/O或502/503/504一次重试，无每帧重发。
 */
public final class JavaImageLoader implements ImageLoader {
    public interface Resources {
        InputStream snapshot(String id) throws IOException;
    }

    public static final class Budget {
        public final int encodedBytes, pixelBytes, connectMillis, readMillis, totalMillis;

        public Budget(
                int encodedBytes,
                int pixelBytes,
                int connectMillis,
                int readMillis,
                int totalMillis) {
            if (encodedBytes < 1
                    || pixelBytes < 4
                    || connectMillis < 1
                    || readMillis < 1
                    || totalMillis < 1) throw new IllegalArgumentException("无效加载预算");
            this.encodedBytes = encodedBytes;
            this.pixelBytes = pixelBytes;
            this.connectMillis = connectMillis;
            this.readMillis = readMillis;
            this.totalMillis = totalMillis;
        }

        public static Budget defaults() {
            return new Budget(8 * 1024 * 1024, 16 * 1024 * 1024, 3000, 3000, 10000);
        }
    }

    private final Resources resources;
    private final Budget budget;

    public JavaImageLoader(Resources resources, Budget budget) {
        this.resources = resources;
        this.budget = java.util.Objects.requireNonNull(budget, "budget");
    }

    @Override
    public Callable<ImageContent> prepare(ImageSource source) throws Exception {
        switch (source.kind) {
            case BITMAP:
                return () -> source.bitmap;
            case RESOURCE:
                if (resources == null)
                    throw new ImageLoader.Failure(true, "resolve", "宿主缺少资源图像适配: " + source);
                // ResourceManager只在宿主线程调用；独立stream快照在worker读/关，旧代不会再访问manager。
                InputStream stream;
                try {
                    stream = resources.snapshot(source.location);
                } catch (IOException failure) {
                    throw new ImageLoader.Failure("resource", failure);
                }
                return new ImageLoader.Task() {
                    private InputStream owned = stream;

                    public synchronized void close() throws IOException {
                        if (owned != null) {
                            owned.close();
                            owned = null;
                        }
                    }

                    public ImageContent call() throws Exception {
                        InputStream input;
                        synchronized (this) {
                            input = owned;
                        }
                        if (input == null) throw new IOException("资源快照已取消");
                        try {
                            return decode(read(input, 0), source.region);
                        } finally {
                            close();
                        }
                    }
                };
            case HTTP:
                return () -> download(source.location);
            case INVALID:
                throw new ImageLoader.Failure(false, "resolve", "无效图像来源: " + source.location);
            default:
                throw new ImageLoader.Failure(true, "resolve", "宿主缺少物品图像适配: " + source);
        }
    }

    private byte[] read(InputStream input, long deadline) throws IOException, ImageLoader.Failure {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (; ; ) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("图像请求已取消");
            if (deadline != 0 && System.nanoTime() > deadline)
                throw new SocketTimeoutException("图像请求总时限");
            int count = input.read(buffer);
            if (count < 0) break;
            if ((long) bytes.size() + count > budget.encodedBytes)
                throw new ImageLoader.Failure(false, "budget", "编码图像超过字节预算");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private ImageContent download(String address) throws Exception {
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(budget.connectMillis);
                connection.setReadTimeout(budget.readMillis);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "image/*");
                long deadline = System.nanoTime() + budget.totalMillis * 1000000L;
                int status = connection.getResponseCode();
                if (status == 502 || status == 503 || status == 504) {
                    if (attempt == 0) {
                        Thread.sleep(100);
                        continue;
                    }
                    throw new ImageLoader.Failure(false, "http", "HTTP " + status);
                }
                if (status < 200 || status >= 300)
                    throw new ImageLoader.Failure(false, "http", "HTTP " + status + "（首版不自动跟随重定向）");
                if (connection.getContentLengthLong() > budget.encodedBytes)
                    throw new ImageLoader.Failure(false, "budget", "HTTP内容长度超过预算");
                byte[] encoded;
                try (InputStream input = connection.getInputStream()) {
                    encoded = read(input, deadline);
                }
                return decode(encoded, null);
            } catch (IOException transientFailure) {
                if (attempt != 0 || Thread.currentThread().isInterrupted())
                    throw new ImageLoader.Failure("http", transientFailure);
                Thread.sleep(100);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }

    private ImageContent decode(byte[] encoded, ImageSource.Region region) throws Exception {
        try (ImageInputStream input =
                ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ImageLoader.Failure(false, "decode", "不支持的图像编码");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > budget.pixelBytes / 4)
                    throw new ImageLoader.Failure(
                            false, "budget", "图像维度超过解码预算: " + width + "x" + height);
                if (region != null
                        && ((long) region.x + region.width > width
                                || (long) region.y + region.height > height))
                    throw new ImageLoader.Failure(false, "region", "所选区域超出图像");
                BufferedImage image = reader.read(0);
                int x = region == null ? 0 : region.x,
                        y = region == null ? 0 : region.y,
                        w = region == null ? width : region.width,
                        h = region == null ? height : region.height;
                byte[] rgba = new byte[w * h * 4];
                for (int row = 0; row < h; row++)
                    for (int col = 0; col < w; col++) {
                        int argb = image.getRGB(x + col, y + row), i = (row * w + col) * 4;
                        rgba[i] = (byte) (argb >>> 16);
                        rgba[i + 1] = (byte) (argb >>> 8);
                        rgba[i + 2] = (byte) argb;
                        rgba[i + 3] = (byte) (argb >>> 24);
                    }
                return new ImageContent(w, h, rgba);
            } catch (ImageLoader.Failure failure) {
                throw failure;
            } catch (IOException failure) {
                throw new ImageLoader.Failure("decode", failure);
            } finally {
                reader.dispose();
            }
        }
    }
}
