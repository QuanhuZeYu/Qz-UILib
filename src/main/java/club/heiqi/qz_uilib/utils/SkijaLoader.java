package club.heiqi.qz_uilib.utils;

import io.github.humbleui.skija.impl.Library;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SkijaLoader {
    public static Logger LOG = LogManager.getLogger();
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final String ARCH = System.getProperty("os.arch").toLowerCase();
    private static final String MC_DIR = System.getProperty("user.dir");
    private static volatile boolean loaded = false;

    // 需要检查的 Skija 核心类列表
    private static final String[] SKIJA_CORE_CLASSES = {
        "io.github.humbleui.skija.Typeface",
        "io.github.humbleui.skija.Surface",
        "io.github.humbleui.skija.impl.Managed"
    };

    public static synchronized void load() {
        if (loaded) return;

        File modsDir = new File(MC_DIR, "mods");
        File skijaDir = new File(modsDir, "skija");
        if (!skijaDir.exists()) skijaDir.mkdirs();

        // 动态构建库路径
        String platformPath;
        if (OS.contains("win")) {
            platformPath = "windows/x64/skija.dll";
        } else if (OS.contains("linux")) {
            platformPath = "linux/x64/libskija.so";
        } else if (OS.contains("mac")) {
            platformPath = (ARCH.contains("aarch64") || ARCH.contains("arm64"))
                ? "macos/arm64/libskija.dylib"
                : "macos/x64/libskija.dylib";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + OS);
        }

        File libFile = new File(skijaDir, platformPath);

        // 验证文件存在性
        if (!libFile.exists()) {
            /*try {
                *//*extractNativeLibs();*//*
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!libFile.exists())*/ throw new RuntimeException("无法加载skija动态链接库 " + libFile.getAbsolutePath());
        }

        // 使用Skija官方加载方式
        System.setProperty("skija.library.path", libFile.getParent());
        Library._loadFromDir(libFile.getParentFile());

        loaded = true;
        LOG.info("Successfully loaded Skija from: {}", libFile);
    }

    public static void extractNativeLibs() throws IOException {
        String libPath = getLibPath(); // 根据平台返回资源路径
        InputStream is = SkijaLoader.class.getResourceAsStream(libPath);
        File targetDir = new File(System.getProperty("user.dir"), "mods/skija");

        Files.createDirectories(targetDir.toPath());
        File libFile = new File(targetDir, libPath);
        Files.copy(is, libFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 获取当前平台的本地库路径
     */
    private static String getLibPath() {
        if (OS.contains("win")) {
            return "qzuilib_skija/windows/x64/skija.dll";
        } else if (OS.contains("linux")) {
            return "qzuilib_skija/linux/x64/libskija.so";
        } else if (OS.contains("mac")) {
            return (ARCH.contains("aarch64") || ARCH.contains("arm64"))
                ? "qzuilib_skija/macos/arm64/libskija.dylib"
                : "qzuilib_skija/macos/x64/libskija.dylib";
        }
        throw new UnsupportedOperationException("不支持的操作系统: " + OS);
    }
}
