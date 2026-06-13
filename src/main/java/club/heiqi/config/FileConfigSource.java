package club.heiqi.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 基于文件的配置源实现
 */
class FileConfigSource implements ConfigSource {

    private final File file;

    FileConfigSource(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        this.file = file;
    }

    @Override
    public String read() throws ConfigException {
        if (!file.exists()) {
            throw new ConfigException("File does not exist: " + file.getAbsolutePath());
        }

        if (!file.isFile()) {
            throw new ConfigException("Not a file: " + file.getAbsolutePath());
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            try {
                InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader buffered = new BufferedReader(reader);
                StringBuilder content = new StringBuilder();
                String line;
                
                while ((line = buffered.readLine()) != null) {
                    content.append(line).append('\n');
                }
                
                buffered.close();
                return content.toString();
            } finally {
                fis.close();
            }
        } catch (IOException e) {
            throw new ConfigException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public String getDescription() {
        return "file:" + file.getAbsolutePath();
    }

    /**
     * 获取文件对象
     * 
     * @return 文件
     */
    public File getFile() {
        return file;
    }
}
