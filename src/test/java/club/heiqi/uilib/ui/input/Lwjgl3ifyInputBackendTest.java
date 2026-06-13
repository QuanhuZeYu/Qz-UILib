package club.heiqi.uilib.ui.input;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * `Lwjgl3ifyInputBackend` 的反射隔离与降级契约测试。
 */
public class Lwjgl3ifyInputBackendTest {

    /**
     * 验证 `InputEvents` 监听注册失败时会启用键盘轮询兜底。
     */
    @Test
    public void shouldEnableKeyboardPollingFallbackWhenListenerRegistrationFails() throws Exception {
        UiInputService inputService = createInputService();
        RecordingInputBackend pollingBackend = new RecordingInputBackend();
        RecordingFallback fallback = new RecordingFallback();
        Lwjgl3ifyInputBackend backend = createBackend(inputService, FailingInputEvents.class,
                FailingInputEvents.class.getMethod("addKeyboardListener", Object.class), new Object(), pollingBackend,
                fallback);

        backend.initialize();

        Assert.assertEquals(1, pollingBackend.initializeCount);
        Assert.assertEquals(1, fallback.runCount);
    }

    /**
     * 验证 `InputEvents` 监听注册成功时仍保持事件监听优先，不额外打开键盘轮询。
     */
    @Test
    public void shouldKeepEventListenerModeWhenRegistrationSucceeds() throws Exception {
        UiInputService inputService = createInputService();
        RecordingInputBackend pollingBackend = new RecordingInputBackend();
        RecordingFallback fallback = new RecordingFallback();
        Lwjgl3ifyInputBackend backend = createBackend(inputService, SuccessfulInputEvents.class,
                SuccessfulInputEvents.class.getMethod("addKeyboardListener", Object.class), new Object(), pollingBackend,
                fallback);

        backend.initialize();

        Assert.assertEquals(1, pollingBackend.initializeCount);
        Assert.assertEquals(0, fallback.runCount);
    }

    /**
     * 验证当前 `lwjgl3ify` 的 sdl 键码字段可以通过反射 fallback 写入 UI 键盘事件。
     */
    @Test
    public void shouldMapSdlFieldsAndModifiersFromInputEvents() throws Exception {
        UiInputService inputService = createInputService();

        Lwjgl3ifyInputBackend.handleKeyEvent(inputService, new ReflectiveKeyEvent(30, 65, 4, "REPEATED",
                (short) (0x0040 | 0x0002 | 0x0100 | 0x0800)), 7L);

        UiInputFrame frame = inputService.collectFrame();
        Assert.assertEquals(1, frame.getKeyEvents().size());
        UiKeyEvent keyEvent = frame.getKeyEvents().get(0);
        Assert.assertEquals(30, keyEvent.getKeyCode());
        Assert.assertEquals(65, keyEvent.getGlfwKeyCode());
        Assert.assertEquals(4, keyEvent.getGlfwScanCode());
        Assert.assertEquals(UiKeyEvent.Action.REPEATED, keyEvent.getAction());
        Assert.assertTrue(keyEvent.isControlPressed());
        Assert.assertTrue(keyEvent.isShiftPressed());
        Assert.assertTrue(keyEvent.isAltPressed());
        Assert.assertTrue(keyEvent.isSuperPressed());
        Assert.assertEquals(7L, keyEvent.getTimeNanos());
    }

    /**
     * 验证即时输入去重窗口会抑制后续全局监听收集到的同帧按键与文本事件。
     */
    @Test
    public void shouldSuppressCollectedKeyAndTextEvents() throws Exception {
        UiInputService inputService = createInputService();

        inputService.suppressNextCollectedKeyboardEvent(30, UiKeyEvent.Action.PRESSED, "A");
        Lwjgl3ifyInputBackend.handleKeyEvent(inputService, new ReflectiveKeyEvent(30, 65, 4, "PRESSED", (short) 0),
                7L);
        invokeHandleTextEvent(inputService, new ReflectiveTextEvent("A"));

        UiInputFrame frame = inputService.collectFrame();
        Assert.assertTrue(frame.getKeyEvents().isEmpty());
        Assert.assertTrue(frame.getTextEvents().isEmpty());
    }

    /**
     * 验证主源码没有重新引入对 `lwjgl3ify` API 的静态类型绑定。
     */
    @Test
    public void shouldKeepMainSourcesFreeOfStaticLwjgl3ifyApiReferences() throws IOException {
        assertNoMainSourceMatch(Pattern.compile("\\bimport\\s+me\\.eigenraven\\.lwjgl3ify"));
        assertNoMainSourceMatch(Pattern.compile("\\bimplements\\s+InputEvents"));
        assertNoMainSourceMatch(Pattern.compile("\\bInputEvents\\s*\\."));
        assertNoMainSourceMatch(Pattern.compile("\\bimport\\s+org\\.lwjglx\\."));
    }

    private static UiInputService createInputService() throws Exception {
        return new UiInputService(new RecordingInputBackend());
    }

    private static Lwjgl3ifyInputBackend createBackend(UiInputService inputService, Class<?> inputEventsClass,
            Method addKeyboardListenerMethod, Object keyboardListener, UiInputBackend pollingBackend,
            Runnable keyboardPollingFallback) {
        return new Lwjgl3ifyInputBackend(inputService, inputEventsClass, addKeyboardListenerMethod, keyboardListener,
                pollingBackend, keyboardPollingFallback);
    }

    private static void invokeHandleTextEvent(UiInputService inputService, Object event) throws Exception {
        Method method = Lwjgl3ifyInputBackend.class.getDeclaredMethod("handleTextEvent", UiInputService.class,
                Object.class);
        method.setAccessible(true);
        method.invoke(null, inputService, event);
    }

    private static void assertNoMainSourceMatch(Pattern pattern) throws IOException {
        Path sourceRoot = Paths.get("src/main/java");
        List<Path> javaFiles = new ArrayList<Path>();
        try (Stream<Path> sourcePaths = Files.walk(sourceRoot)) {
            sourcePaths.filter(sourcePath -> sourcePath.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        List<String> violations = new ArrayList<String>();
        for (Path javaFile : javaFiles) {
            List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex);
                if (pattern.matcher(line).find()) {
                    violations.add(javaFile + ":" + (lineIndex + 1) + " " + line.trim());
                }
            }
        }
        Assert.assertTrue("主源码不能静态引用 lwjgl3ify API：" + violations, violations.isEmpty());
    }

    /**
     * 模拟注册失败的 `InputEvents` 类。
     */
    public static final class FailingInputEvents {

        public static void addKeyboardListener(Object listener) {
            throw new IllegalStateException("listener registration failed");
        }
    }

    /**
     * 模拟注册成功的 `InputEvents` 类。
     */
    public static final class SuccessfulInputEvents {

        public static void addKeyboardListener(Object listener) {}
    }

    /**
     * 记录后端生命周期调用的测试替身。
     */
    private static final class RecordingInputBackend implements UiInputBackend {

        private int initializeCount;

        @Override
        public void initialize() {
            initializeCount++;
        }

        @Override
        public void tick() {}

        @Override
        public void beginTextInput() {}

        @Override
        public void endTextInput() {}

        @Override
        public UiInputFrame createImmediateKeyboardFrame() {
            return null;
        }

        @Override
        public UiInputFrame createImmediateMouseFrame() {
            return null;
        }
    }

    /**
     * 记录键盘轮询兜底是否启用的测试替身。
     */
    private static final class RecordingFallback implements Runnable {

        private int runCount;

        @Override
        public void run() {
            runCount++;
        }
    }

    /**
     * 模拟 `InputEvents.KeyEvent` 的公开字段结构。
     */
    public static final class ReflectiveKeyEvent {

        public final int lwjgl2KeyCode;
        public final int sdlKeyCode;
        public final int sdlScanCode;
        public final String action;
        public final short sdlKeyModifiers;

        private ReflectiveKeyEvent(int lwjgl2KeyCode, int sdlKeyCode, int sdlScanCode, String action,
                short sdlKeyModifiers) {
            this.lwjgl2KeyCode = lwjgl2KeyCode;
            this.sdlKeyCode = sdlKeyCode;
            this.sdlScanCode = sdlScanCode;
            this.action = action;
            this.sdlKeyModifiers = sdlKeyModifiers;
        }
    }

    /**
     * 模拟 `InputEvents.TextEvent` 的公开字段结构。
     */
    public static final class ReflectiveTextEvent {

        public final String text;

        private ReflectiveTextEvent(String text) {
            this.text = text;
        }
    }
}
