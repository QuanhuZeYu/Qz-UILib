package club.heiqi.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 基于输入流的配置源实现
 */
class InputStreamConfigSource implements ConfigSource {

    private final InputStream inputStream;
    private final String description;

    InputStreamConfigSource(InputStream inputStream, String description) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        this.inputStream = inputStream;
        this.description = description != null ? description : "input-stream";
    }

    @Override
    public String read() throws ConfigException {
        try {
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader buffered = new BufferedReader(reader);
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = buffered.readLine()) != null) {
                content.append(line).append('\n');
            }
            
            buffered.close();
            return content.toString();
        } catch (IOException e) {
            throw new ConfigException("Failed to read from input stream: " + description, e);
        }
    }

    @Override
    public String getDescription() {
        return description;
    }
}
