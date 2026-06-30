package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * ConstraintResolver 早退 WARN 日志单元测试。
 *
 * <p>验证：有 grow 子 + 容器自身高度无法先验时打 1 条 WARN；
 * 有 grow 子 + 固定兄弟高度无法先验时打 1 条 WARN；
 * 无 grow 子场景 0 条 WARN；多帧只打 1 条（per-node 去重）。</p>
 *
 * <p>日志捕获用自写最简 collecting appender（直接实现 log4j2 core 的 Appender 接口），
 * 挂到 {@code QzUiLib/Layout} logger 的 LoggerConfig 上收集 WARN 事件。
 * 兼容 log4j-core 2.0-beta9 API（无 PatternLayout.createDefaultLayout / Property.EMPTY_ARRAY）。</p>
 */
public class ConstraintResolverWarnTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;

    private CollectingAppender appender;
    private org.apache.logging.log4j.core.config.LoggerConfig loggerConfig;
    private org.apache.logging.log4j.core.LoggerContext ctx;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();

        // 挂 collecting appender 到 QzUiLib/Layout logger 的 LoggerConfig
        appender = new CollectingAppender("ConstraintResolverWarnTest");
        appender.start();
        ctx = (org.apache.logging.log4j.core.LoggerContext)
                org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
        // getLoggerConfig 返回处理该 logger 名的 LoggerConfig（无精确匹配时返回 root）
        loggerConfig = config.getLoggerConfig("QzUiLib/Layout");
        // 在 LoggerConfig 上加 appender + 设级别（beta9：事件经 LoggerConfig 派发到 appender）
        loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.WARN, null);
        loggerConfig.setLevel(org.apache.logging.log4j.Level.WARN);
        ctx.updateLoggers();
    }

    @After
    public void tearDown() {
        if (loggerConfig != null) {
            loggerConfig.removeAppender(appender.getName());
        }
        appender.stop();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 场景 1：COLUMN 容器有 grow 子，但容器自身高度无法先验（无 preferredHeight、非 fill/grow/percent，
     * 且父约束无高）→ 打 1 条 WARN（容器自身高度无法先验）。
     *
     * <p>构造：root(COLUMN, 无高约束) → container(COLUMN, 无 preferredHeight, 非 fill) →
     * growChild(flexGrow=1) + fixedChild(preferredHeight=50)。
     * root 收到 UNCONSTRAINED 高约束，container 无 fill/grow/preferredHeight →
     * priorKnownInnerHeight 返回 UNCONSTRAINED → 早退。</p>
     */
    @Test
    public void containerHeightUnconstrainedWithGrowChildShouldWarnOnce() {
        sceneRoot.setFlexDirection(FlexDirection.COLUMN);

        SceneNode container = new SceneNode();
        container.setFlexDirection(FlexDirection.COLUMN);
        // 故意不设 preferredHeight、不设 fillParentHeight → priorKnownInnerHeight 返回 UNCONSTRAINED
        sceneRoot.appendChild(container);

        SceneNode growChild = new SceneNode();
        growChild.setFlexGrow(1);
        container.appendChild(growChild);

        SceneNode fixedChild = new SceneNode();
        fixedChild.setPreferredHeight(50);
        container.appendChild(fixedChild);

        appender.clear();
        // 用无高约束的 Constraints 触发容器自身高度无法先验
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, Constraints.UNCONSTRAINED));

        int warnCount = appender.warnCount();
        Assert.assertTrue("有 grow 子 + 容器自身高度无法先验应打 WARN（≥1）",
                warnCount >= 1);

        // 去重验证：清空后多帧不再打新 WARN（per-node 去重）
        appender.clear();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, Constraints.UNCONSTRAINED));
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, Constraints.UNCONSTRAINED));
        Assert.assertEquals("per-node 去重：多帧不再打新 WARN", 0, appender.warnCount());
    }

    /**
     * 场景 2：COLUMN 容器高度可先验（preferredHeight），有 grow 子，
     * 但固定兄弟是容器（有子节点、无 preferredHeight）→ priorKnownChildHeight 返回 UNCONSTRAINED → 早退打 WARN。
     */
    @Test
    public void siblingHeightUnconstrainedWithGrowChildShouldWarn() {
        sceneRoot.setFlexDirection(FlexDirection.COLUMN);

        SceneNode container = new SceneNode();
        container.setFlexDirection(FlexDirection.COLUMN);
        container.setPreferredHeight(200); // 容器自身高度可先验
        sceneRoot.appendChild(container);

        // 固定兄弟：容器节点（有子），无 preferredHeight → priorKnownChildHeight 返回 UNCONSTRAINED
        SceneNode fixedSibling = new SceneNode();
        fixedSibling.setFlexDirection(FlexDirection.COLUMN);
        SceneNode grandChild = new SceneNode();
        grandChild.setPreferredHeight(30);
        fixedSibling.appendChild(grandChild);
        container.appendChild(fixedSibling);

        // grow 子
        SceneNode growChild = new SceneNode();
        growChild.setFlexGrow(1);
        container.appendChild(growChild);

        appender.clear();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        int warnCount = appender.warnCount();
        Assert.assertTrue("有 grow 子 + 固定兄弟高度无法先验应打 WARN（≥1）",
                warnCount >= 1);
    }

    /**
     * 场景 3：无 grow 子（纯固定子）→ 早退是正常 shrink 路径，不应打 WARN。
     */
    @Test
    public void noGrowChildShouldNotWarn() {
        sceneRoot.setFlexDirection(FlexDirection.COLUMN);

        SceneNode container = new SceneNode();
        container.setFlexDirection(FlexDirection.COLUMN);
        // 不设 preferredHeight → priorKnownInnerHeight UNCONSTRAINED，但无 grow 子
        sceneRoot.appendChild(container);

        SceneNode fixedChild1 = new SceneNode();
        fixedChild1.setPreferredHeight(50);
        container.appendChild(fixedChild1);

        SceneNode fixedChild2 = new SceneNode();
        fixedChild2.setPreferredHeight(60);
        container.appendChild(fixedChild2);

        appender.clear();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, Constraints.UNCONSTRAINED));

        Assert.assertEquals("无 grow 子场景不应打 WARN", 0, appender.warnCount());
    }

    // ==================== 最简 collecting appender（直接实现 Appender 接口） ====================

    /**
     * 自写最简 collecting appender —— 直接实现 log4j2 core 的 Appender 接口，
     * 收集所有 log 事件，提供 WARN 计数。不依赖 PatternLayout / Property.EMPTY_ARRAY
     * （兼容 log4j-core 2.0-beta9 API）。
     */
    public static final class CollectingAppender implements org.apache.logging.log4j.core.Appender {

        private final String name;
        private final List<org.apache.logging.log4j.core.LogEvent> events = new ArrayList<>();
        private volatile boolean started = false;
        private org.apache.logging.log4j.core.ErrorHandler handler;

        CollectingAppender(String name) {
            this.name = name;
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            events.add(event);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public org.apache.logging.log4j.core.Layout<? extends java.io.Serializable> getLayout() {
            return null;
        }

        @Override
        public boolean ignoreExceptions() {
            return true;
        }

        @Override
        public org.apache.logging.log4j.core.ErrorHandler getHandler() {
            return handler;
        }

        @Override
        public void setHandler(org.apache.logging.log4j.core.ErrorHandler handler) {
            this.handler = handler;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        public int warnCount() {
            int count = 0;
            for (org.apache.logging.log4j.core.LogEvent e : events) {
                if (e.getLevel() == org.apache.logging.log4j.Level.WARN) {
                    count++;
                }
            }
            return count;
        }

        public void clear() {
            events.clear();
        }
    }
}
