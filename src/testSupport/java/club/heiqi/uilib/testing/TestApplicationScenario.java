package club.heiqi.uilib.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 校准可能使所有测试误报或泄漏的共同生命周期，不依赖日志或源码字面。 */
public final class TestApplicationScenario extends TestApplication {
    private static void scopeOrder() throws Exception {
        List<String> events = new ArrayList<>();
        TestApplication application = new TestApplication() {
            @Override protected void setup() {
                events.add("setup");
                onClose(() -> events.add("application-close"));
            }
            @Override protected void runTests() throws Exception {
                scenario("first", () -> {
                    onClose(() -> events.add("first-close"));
                    onClose(() -> events.add("second-close"));
                    events.add("first");
                });
                scenario("next", () -> events.add("next"));
            }
            @Override protected void teardown() { events.add("teardown"); }
        };
        application.run();
        List<String> expected = Arrays.asList("setup", "first", "second-close", "first-close", "next", "teardown", "application-close");
        require(events.equals(expected), "资源作用域和逆序错误: " + events);
        events.clear();
        application.run();
        require(events.equals(expected), "应用重跑遗留资源或场景状态");
    }
    private static void failureIdentity() throws Exception {
        List<String> closed = new ArrayList<>();
        AssertionError original = new AssertionError("真实场景失败");
        Exception firstClose = new Exception("场景清理失败"), secondClose = new Exception("应用清理失败");
        TestApplication app = new TestApplication() {
            @Override protected void setup() { onClose(() -> { closed.add("application"); throw secondClose; }); }
            @Override protected void runTests() throws Exception {
                scenario("failure", () -> {
                    onClose(() -> { closed.add("scene"); throw firstClose; });
                    throw original;
                });
                throw new AssertionError("失败后继续执行");
            }
        };
        Throwable caught = null;
        try { app.run(); } catch (Throwable error) { caught = error; }
        require(caught == original, "原始失败身份被覆盖");
        require(Arrays.equals(caught.getSuppressed(), new Throwable[]{firstClose, secondClose}), "清理异常丢失");
        require(closed.equals(Arrays.asList("scene", "application")), "失败时资源未释放");
    }
    private static void partialSetup() throws Exception {
        List<String> events = new ArrayList<>();
        Exception original = new Exception("初始化中途失败");
        TestApplication app = new TestApplication() {
            @Override protected void setup() throws Exception { onClose(() -> events.add("released")); throw original; }
            @Override protected void runTests() { throw new AssertionError("初始化失败仍运行"); }
        };
        Throwable caught = null;
        try { app.run(); } catch (Throwable error) { caught = error; }
        require(caught == original && events.equals(Arrays.asList("released")), "初始化失败泄漏或异常被覆盖");
    }
    private static void noFalsePass() throws Exception {
        TestApplication empty = new TestApplication() { @Override protected void runTests() { } };
        Throwable emptyError = null;
        try { empty.run(); } catch (Throwable error) { emptyError = error; }
        require(emptyError instanceof AssertionError, "空应用报告通过");
        Exception closing = new Exception("仅清理失败");
        TestApplication app = new TestApplication() {
            @Override protected void runTests() throws Exception { scenario("close", () -> onClose(() -> { throw closing; })); }
        };
        Throwable caught = null;
        try { app.run(); } catch (Throwable error) { caught = error; }
        require(caught == closing, "清理失败仍通过或丢失原异常");
    }
    @Override protected void runTests() throws Exception {
        scenario("作用域逆序释放及重跑", TestApplicationScenario::scopeOrder);
        scenario("保留场景失败和清理异常", TestApplicationScenario::failureIdentity);
        scenario("初始化中途失败也释放", TestApplicationScenario::partialSetup);
        scenario("空应用和清理失败不误报", TestApplicationScenario::noFalsePass);
    }
    public static void main(String[] args) throws Exception { new TestApplicationScenario().run(args); }
}
