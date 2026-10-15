package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import club.heiqi.uilib.testing.gallery.DragPerformanceWorkload;
import club.heiqi.uilib.testing.gallery.PerformanceReport;
import club.heiqi.uilib.testing.gallery.SortPerformanceWorkload;
import club.heiqi.uilib.testing.gallery.SortPerformanceWorkload.FontEntry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.testing.gallery.PerformanceSamples;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** 隐藏真实GL窗口的有限压测；只调用已安装回调，不移动系统鼠标或操作前台。 */
public final class DesktopDraggablePerformance {
    private DesktopDraggablePerformance() {}
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length > 0 ? args[0] : "draggable-performance");
        String scales = args.length > 1 ? args[1] : "50,200,1000";
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        int frames = args.length > 3 ? Integer.parseInt(args[3]) : 120;
        if (warmup < 0 || frames <= 0) throw new IllegalArgumentException("采样参数非法");
        String loads = args.length > 4 ? args[4] : "plain,sort";
        int edgeFrames = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        if (edgeFrames != 0 && (edgeFrames < 10 || edgeFrames > 20)) throw new IllegalArgumentException("边缘hold样本须为10..20或0");
        Files.createDirectories(output);
        for (String mode : loads.split(",")) {
            if (!"plain".equals(mode) && !"sort".equals(mode) && !"edge".equals(mode)) throw new IllegalArgumentException("未知负载：" + mode);
            if ("edge".equals(mode) && edgeFrames == 0) throw new IllegalArgumentException("edge负载需要10..20帧");
            for (String value : scales.split(",")) {
                int scale = Integer.parseInt(value.trim());
                if ("plain".equals(mode)) run(output, scale, warmup, frames);
                else if ("sort".equals(mode)) { runSort(output, scale, warmup, frames); if (edgeFrames > 0) runSortEdge(output, scale, warmup, edgeFrames); }
                else runSortEdge(output, scale, warmup, edgeFrames);
            }
        }
    }
    private static void run(Path output, int scale, int warmup, int frames) throws Exception {
        DragPerformanceWorkload load = new DragPerformanceWorkload(scale);
        PerformanceSamples samples = new PerformanceSamples(frames);
        int[] xs = new int[frames], ys = new int[frames], phases = new int[frames];
        boolean[] actives = new boolean[frames];
        PerformanceReport.Snapshot before = PerformanceReport.Snapshot.capture();
        try (DesktopWindow window = DesktopWindow.openWithServices(output.resolve("drag-" + scale + ".yaml"), false, services -> load)) {
            require(glfwGetWindowAttrib(window.handle(), GLFW_VISIBLE) == GLFW_FALSE, "窗口创建后必须隐藏");
            require(glfwGetCurrentContext() == window.handle(), "缺少真实GL上下文");
            glfwSwapInterval(0);
            long firstStart = System.nanoTime();
            window.frame();
            long firstFrame = System.nanoTime() - firstStart;
            String renderer = glGetString(GL_RENDERER), glVersion = glGetString(GL_VERSION), vendor = glGetString(GL_VENDOR);
            require(renderer != null && glVersion != null, "GL上下文未提供渲染器信息");
            DesktopTestInput input = new DesktopTestInput(window);
            LayoutBox initial = DesktopTestInput.hit(DesktopTestInput.geometry(window, load.firstContent()));
            require(initial.width > 0 && initial.height > 0, "首行不在可见命中区域");
            double x = initial.x + Math.min(20, initial.width / 2), y = initial.y + Math.min(15, initial.height / 2);
            int[] nativeWidth = {0}, nativeHeight = {0};
            glfwGetWindowSize(window.handle(), nativeWidth, nativeHeight);
            double ratioX = nativeWidth[0] / (double)window.width(), ratioY = nativeHeight[0] / (double)window.height();
            long previousStart = 0;
            int observedChanges = 0, activeFrames = 0, movedFrames = 0, releasedFrames = 0;
            int previousX = initial.x, previousY = initial.y;
            boolean previousActive = false;
            final boolean[] currentActive = {false};
            Runnable observeActive = () -> currentActive[0] = load.active();
            for (int step = 0; step < warmup + frames; step++) {
                require(!window.shouldClose(), "压测窗口提前关闭");
                int phase = step % 6;
                long begin = System.nanoTime();
                if (phase == 0) {
                    input.cursor.invoke(window.handle(), x * ratioX, y * ratioY);
                    input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
                } else if (phase < 5) {
                    input.cursor.invoke(window.handle(), (x + phase * 18) * ratioX, (y + phase * 7) * ratioY);
                } else input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
                window.frame();
                long end = System.nanoTime();
                // 计时外读取共同几何和正式状态；current沿用已呈现时间，不推进动画。
                LayoutBox actual = DesktopTestInput.geometry(window, load.firstContent()).box;
                window.domain.execute(observeActive);
                boolean active = currentActive[0];
                require(active == (phase != 5), "DOWN/MOVE/UP未进入正式拖拽状态：phase=" + phase);
                if (phase > 0 && phase < 5) require(actual.x != initial.x || actual.y != initial.y, "MOVE没有移动正式几何");
                if (phase == 5) require(actual.x == initial.x && actual.y == initial.y, "UP未回原位");
                if (step >= warmup) {
                    int index = step - warmup;
                    samples.add(end - begin, previousStart == 0 ? 0 : begin - previousStart);
                    xs[index] = actual.x; ys[index] = actual.y; phases[index] = phase; actives[index] = active;
                    if (active != previousActive || actual.x != previousX || actual.y != previousY) observedChanges++;
                    if (active) activeFrames++;
                    if (actual.x != initial.x || actual.y != initial.y) movedFrames++;
                    if (phase == 5 && !active) releasedFrames++;
                }
                previousStart = begin; previousX = actual.x; previousY = actual.y; previousActive = active;
            }
            // 非完整循环参数也必须正常释放；清理动作不进入稳定样本。
            input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); window.frame();
            require(glfwGetWindowAttrib(window.handle(), GLFW_VISIBLE) == GLFW_FALSE, "运行结束窗口必须仍隐藏");
            require(observedChanges > 0 && movedFrames > 0 && activeFrames > 0 && releasedFrames > 0, "稳定采样没有完整拖拽证据");
            require(glGetError() == GL_NO_ERROR, "真实GL帧执行错误");
            String report = load.report(System.getProperty("uilib.version", "unknown"), warmup, frames, true,
                    observedChanges, window.width(), window.height(), firstFrame, samples, before, PerformanceReport.Snapshot.capture());
            report = report.substring(0, report.length() - 1)
                    + ",\"revision\":" + PerformanceReport.quote(System.getProperty("drag.revision", "unknown"))
                    + ",\"renderer\":" + PerformanceReport.quote(renderer) + ",\"glVersion\":" + PerformanceReport.quote(glVersion)
                    + ",\"vendor\":" + PerformanceReport.quote(vendor) + ",\"glfwVersion\":" + PerformanceReport.quote(glfwGetVersionString())
                    + ",\"hiddenAtCreateAndEnd\":true,\"realGlContext\":true,\"vsync\":0"
                    + ",\"activeFrames\":" + activeFrames + ",\"movedFrames\":" + movedFrames + ",\"releasedFrames\":" + releasedFrames
                    + ",\"observationBoundary\":\"outside host timer; frame intervals include observation\""
                    + ",\"phases\":" + Arrays.toString(phases) + ",\"contentX\":" + Arrays.toString(xs)
                    + ",\"contentY\":" + Arrays.toString(ys) + ",\"active\":" + Arrays.toString(actives) + "}";
            Files.write(output.resolve("drag-" + scale + ".json"), report.getBytes(StandardCharsets.UTF_8));
            System.out.println("DRAG_BENCHMARK scale=" + scale + " samples=" + samples.size() + " observedChanges=" + observedChanges
                    + " p50Nanos=" + samples.frame().p50Nanos + " p95Nanos=" + samples.frame().p95Nanos + " hidden=true realGl=true");
        }
    }
    private static final class SortObservation {
        FontEntry selected, second;
        int head, commits, lastMoved;
        boolean identity, duplicates;
    }
    private static void runSort(Path output, int scale, int warmup, int frames) throws Exception {
        SortPerformanceWorkload load = new SortPerformanceWorkload(scale);
        PerformanceSamples samples = new PerformanceSamples(frames);
        int[] phases = new int[frames], heads = new int[frames], commitCounts = new int[frames];
        int[] selectedKeys = new int[frames], contentYs = new int[frames], previewYs = new int[frames];
        boolean[] previewActive = new boolean[frames];
        PerformanceReport.Snapshot before = PerformanceReport.Snapshot.capture();
        try (DesktopWindow window = DesktopWindow.openWithServices(output.resolve("sort-" + scale + ".yaml"), false, services -> load)) {
            checkHiddenContext(window); glfwSwapInterval(0);
            long firstStart = System.nanoTime(); window.frame(); long firstFrame = System.nanoTime() - firstStart;
            String graphics = graphics(window);
            window.domain.execute(load::captureIdentity);
            SceneGeometryView initialGeometry = DesktopTestInput.geometry(window);
            int minRowHeight = Integer.MAX_VALUE, maxRowHeight = 0;
            for (int i = 0; i < scale; i++) {
                int height = initialGeometry.get(load.content(load.initialEntry(i))).box.height;
                minRowHeight = Math.min(minRowHeight, height); maxRowHeight = Math.max(maxRowHeight, height);
            }
            require(maxRowHeight > minRowHeight, "长路径负载必须产生不同实际行高");
            DesktopTestInput input = new DesktopTestInput(window);
            SortObservation observation = new SortObservation();
            Runnable observe = () -> { observation.head = load.at(0).number; observation.commits = load.commits(); observation.lastMoved = load.lastMovedNumber(); };
            Runnable select = () -> { observation.selected = load.at(0); observation.second = load.at(1); observe.run(); };
            Runnable identities = () -> { observation.identity = load.identityPreserved(); observation.duplicates = load.duplicatePathIndependent(); };
            int[] nativeWidth = {0}, nativeHeight = {0}; glfwGetWindowSize(window.handle(), nativeWidth, nativeHeight);
            double ratioX = nativeWidth[0] / (double)window.width(), ratioY = nativeHeight[0] / (double)window.height();
            double startX = 0, startY = 0, targetY = 0;
            int headBefore = -1, commitsBefore = 0, stableDrops = 0, orderChanges = 0, previousPreviewY = 0, proxyMoves = 0;
            long previousStart = 0;
            // 每轮只在DOWN边界读取两个key，不在每帧复制或重建权威序列。
            for (int step = 0; step < warmup + frames; step++) {
                require(!window.shouldClose(), "排序窗口提前关闭");
                int phase = step % 6;
                if (phase == 0) {
                    window.domain.execute(select);
                    SceneGeometryView geometry = DesktopTestInput.geometry(window);
                    LayoutBox handle = DesktopTestInput.hit(geometry.get(load.handle(observation.selected)));
                    LayoutBox second = geometry.get(load.content(observation.second)).box;
                    require(handle.width > 0 && handle.height > 0, "排序把手不可命中");
                    startX = handle.x + handle.width / 2.0; startY = handle.y + handle.height / 2.0;
                    targetY = second.y + second.height * .8;
                    headBefore = observation.head; commitsBefore = observation.commits;
                }
                long begin = System.nanoTime();
                if (phase == 0) {
                    input.cursor.invoke(window.handle(), startX * ratioX, startY * ratioY);
                    input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
                } else if (phase < 5) {
                    input.cursor.invoke(window.handle(), startX * ratioX, (startY + (targetY - startY) * phase / 4.0) * ratioY);
                } else input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
                window.frame(); long end = System.nanoTime();
                // 观测、完整identity检查和数组记录都在宿主计时外；interval仍包含这些成本。
                window.domain.execute(observe);
                SceneGeometryView currentGeometry = DesktopTestInput.geometry(window);
                LayoutBox actual = currentGeometry.get(load.content(observation.selected)).box;
                SceneGeometryView.Entry proxy = load.preview() == null ? null : currentGeometry.get(load.preview());
                require((proxy != null) == (phase != 5), "拾取/释放未更新正式预览几何");
                int proxyY = proxy == null ? 0 : proxy.box.y;
                if (phase > 0 && phase < 5) require(proxyY != previousPreviewY, "MOVE没有更新正式预览位置");
                if (phase == 5) {
                    require(observation.commits == commitsBefore + 1, "每次drop须且仅须一次权威更新");
                    require(observation.lastMoved == observation.selected.number, "把手拾取了错误key");
                    require(observation.head != headBefore, "drop没有改变真实key顺序");
                    window.domain.execute(identities);
                    require(observation.identity && observation.duplicates, "排序重建节点或合并了重复路径key");
                    if (step >= warmup) { stableDrops++; orderChanges++; }
                } else require(observation.commits == commitsBefore, "MOVE期间提前提交了业务顺序");
                if (step >= warmup) {
                    int i = step - warmup; samples.add(end - begin, previousStart == 0 ? 0 : begin - previousStart);
                    phases[i] = phase; heads[i] = observation.head; commitCounts[i] = observation.commits;
                    selectedKeys[i] = observation.selected.number; contentYs[i] = actual.y; previewYs[i] = proxyY; previewActive[i] = proxy != null;
                    if (phase > 0 && phase < 5 && proxyY != previousPreviewY) proxyMoves++;
                }
                previousPreviewY = proxyY; previousStart = begin;
            }
            int cancelCommits = cancelSort(window, input, load, ratioX, ratioY);
            window.domain.execute(identities);
            require(stableDrops > 0 && orderChanges == stableDrops && observation.identity, "稳定排序路径无完整drop");
            checkHiddenContext(window); require(glGetError() == GL_NO_ERROR, "排序真实GL帧错误");
            String report = load.report("SORTABLE_FONT_ROWS", System.getProperty("uilib.version", "unknown"), warmup, frames, true,
                    orderChanges, window.width(), window.height(), firstFrame, samples, before, PerformanceReport.Snapshot.capture());
            String extra = graphics + ",\"stableDrops\":" + stableDrops + ",\"stableOrderChanges\":" + orderChanges
                    + ",\"cancelCommits\":" + cancelCommits + ",\"identityPreserved\":" + observation.identity
                    + ",\"duplicatePathIndependent\":" + observation.duplicates + ",\"rowFactoryCalls\":" + load.rowFactoryCalls()
                    + ",\"minRowHeight\":" + minRowHeight + ",\"maxRowHeight\":" + maxRowHeight
                    + ",\"observationBoundary\":\"outside host timer; intervals include per-drop identity validation and geometry observation; no per-frame sequence copies\""
                    + ",\"trajectory\":\"handle DOWN, four MOVE across next row, UP; each drop changes authority once; cancel outside stable samples\""
                    + ",\"phases\":" + Arrays.toString(phases) + ",\"headKeys\":" + Arrays.toString(heads)
                    + ",\"commitCounts\":" + Arrays.toString(commitCounts) + ",\"selectedKeys\":" + Arrays.toString(selectedKeys)
                    + ",\"contentY\":" + Arrays.toString(contentYs) + ",\"previewY\":" + Arrays.toString(previewYs)
                    + ",\"previewActive\":" + Arrays.toString(previewActive) + ",\"proxyMoves\":" + proxyMoves;
            writeExtended(output.resolve("sort-" + scale + ".json"), report, extra);
            System.out.println("SORT_BENCHMARK scale=" + scale + " samples=" + samples.size() + " stableDrops=" + stableDrops
                    + " p50Nanos=" + samples.frame().p50Nanos + " p95Nanos=" + samples.frame().p95Nanos + " hidden=true realGl=true identity=true cancelCommits=0");
        }
    }
    private static int cancelSort(DesktopWindow window, DesktopTestInput input, SortPerformanceWorkload load,
            double ratioX, double ratioY) {
        int[] before = {0}, after = {0}, head = {0}; FontEntry[] selected = {null}, second = {null};
        window.domain.execute(() -> { before[0] = load.commits(); head[0] = load.at(0).number; selected[0] = load.at(0); second[0] = load.at(1); });
        SceneGeometryView geometry = DesktopTestInput.geometry(window);
        LayoutBox handle = DesktopTestInput.hit(geometry.get(load.handle(selected[0]))), target = geometry.get(load.content(second[0])).box;
        double x = handle.x + handle.width / 2.0, y = handle.y + handle.height / 2.0;
        input.cursor.invoke(window.handle(), x * ratioX, y * ratioY); input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); window.frame();
        input.cursor.invoke(window.handle(), x * ratioX, (target.y + target.height * .8) * ratioY); window.frame();
        require(load.preview() != null && DesktopTestInput.geometry(window).get(load.preview()) != null, "取消校验没有激活正式预览");
        input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0); input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0); window.frame();
        input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); window.frame();
        window.domain.execute(() -> { after[0] = load.commits(); require(load.at(0).number == head[0], "取消修改了权威key序"); });
        require(after[0] == before[0], "取消不能提交业务顺序");
        require(DesktopTestInput.geometry(window).get(load.preview()) == null, "取消后预览几何仍存在");
        return after[0] - before[0];
    }
    private static void runSortEdge(Path output, int scale, int warmup, int frames) throws Exception {
        SortPerformanceWorkload load = new SortPerformanceWorkload(scale);
        PerformanceSamples samples = new PerformanceSamples(frames); int[] offsets = new int[frames];
        PerformanceReport.Snapshot before = PerformanceReport.Snapshot.capture();
        try (DesktopWindow window = DesktopWindow.openWithServices(output.resolve("sort-edge-" + scale + ".yaml"), false, services -> load)) {
            checkHiddenContext(window); glfwSwapInterval(0);
            long start = System.nanoTime(); window.frame(); long firstFrame = System.nanoTime() - start;
            String graphics = graphics(window); window.domain.execute(load::captureIdentity);
            for (int i = 0; i < warmup; i++) window.frame();
            DesktopTestInput input = new DesktopTestInput(window); FontEntry[] selected = {null};
            window.domain.execute(() -> selected[0] = load.at(0));
            LayoutBox handle = DesktopTestInput.hit(DesktopTestInput.geometry(window, load.handle(selected[0])));
            int[] nativeWidth = {0}, nativeHeight = {0}; glfwGetWindowSize(window.handle(), nativeWidth, nativeHeight);
            double ratioX = nativeWidth[0] / (double)window.width(), ratioY = nativeHeight[0] / (double)window.height();
            double x = handle.x + handle.width / 2.0, y = handle.y + handle.height / 2.0;
            input.cursor.invoke(window.handle(), x * ratioX, y * ratioY); input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); window.frame();
            long previousStart = System.nanoTime();
            input.cursor.invoke(window.handle(), x * ratioX, (window.height() - 20) * ratioY); window.frame();
            int[] offset = {0}, commits = {0}; Runnable observe = () -> { offset[0] = window.page.runtime().scrollOffset(load.list()); commits[0] = load.commits(); };
            window.domain.execute(observe); int initialOffset = offset[0], previousOffset = initialOffset, scrollChanges = 0;
            for (int i = 0; i < frames; i++) {
                long begin = System.nanoTime(); window.frame(); long end = System.nanoTime();
                window.domain.execute(observe); require(commits[0] == 0, "边缘hold期间不能提交排序");
                if (offset[0] != previousOffset) scrollChanges++;
                offsets[i] = offset[0]; samples.add(end - begin, previousStart == 0 ? 0 : begin - previousStart);
                previousOffset = offset[0]; previousStart = begin;
            }
            input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0); input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0); window.frame();
            input.mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); window.frame(); window.domain.execute(observe);
            require(commits[0] == 0 && scrollChanges > 0, "无新MOVE的持续hold没有真实自动滚动，或取消发生提交");
            checkHiddenContext(window); require(glGetError() == GL_NO_ERROR, "边缘hold真实GL错误");
            String report = load.report("SORTABLE_EDGE_HOLD", System.getProperty("uilib.version", "unknown"), warmup, frames, true,
                    scrollChanges, window.width(), window.height(), firstFrame, samples, before, PerformanceReport.Snapshot.capture());
            writeExtended(output.resolve("sort-edge-" + scale + ".json"), report, graphics + ",\"scrollChanges\":" + scrollChanges
                    + ",\"cancelCommits\":0,\"trajectory\":\"independent edge hold; DOWN and MOVE at edge before samples; samples contain stationary-pointer window.frame only; ESC cancels afterward\""
                    + ",\"initialScrollOffset\":" + initialOffset + ",\"scrollOffsets\":" + Arrays.toString(offsets));
            System.out.println("SORT_EDGE_BENCHMARK scale=" + scale + " samples=" + frames + " scrollChanges=" + scrollChanges + " cancelCommits=0");
        }
    }
    private static void checkHiddenContext(DesktopWindow window) {
        require(glfwGetWindowAttrib(window.handle(), GLFW_VISIBLE) == GLFW_FALSE, "原生窗口必须始终隐藏");
        require(glfwGetCurrentContext() == window.handle(), "缺少真实GL上下文");
    }
    private static String graphics(DesktopWindow window) {
        String renderer = glGetString(GL_RENDERER), version = glGetString(GL_VERSION);
        require(renderer != null && version != null, "实际GL环境信息缺失");
        return ",\"revision\":" + PerformanceReport.quote(System.getProperty("drag.revision", "unknown"))
                + ",\"renderer\":" + PerformanceReport.quote(renderer) + ",\"glVersion\":" + PerformanceReport.quote(version)
                + ",\"vendor\":" + PerformanceReport.quote(glGetString(GL_VENDOR)) + ",\"glfwVersion\":" + PerformanceReport.quote(glfwGetVersionString())
                + ",\"hiddenAtCreateAndEnd\":true,\"realGlContext\":true,\"vsync\":0,\"layoutProbe\":\"unavailable\",\"independentGpuTimer\":false";
    }
    private static void writeExtended(Path output, String report, String extra) throws java.io.IOException {
        Files.write(output, (report.substring(0, report.length() - 1) + extra + "}").getBytes(StandardCharsets.UTF_8));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
