package club.heiqi.uilib.ui.host.minecraft;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 直接验证MC正式持有者与真实session，不注入screen私有字段或复制关闭算法。 */
public final class MinecraftScreenCleanupScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("session首错与输入桥附加Error仍全部释放且幂等", () ->
                closeFailures(new IllegalStateException("session清理失败"), new AssertionError("输入桥清理失败"), false));
        scenario("session首个Error不被后续异常覆盖", () ->
                closeFailures(new AssertionError("session原始Error"), new IllegalArgumentException("输入桥异常"), false));
        scenario("域内session实际释放先于输入桥且保首错", () ->
                closeFailures(new IllegalStateException("域内session清理失败"), new AssertionError("域内输入桥清理失败"), true));
        scenario("域内首个Error与输入桥异常顺序保持", () ->
                closeFailures(new AssertionError("域内session原始Error"), new IllegalArgumentException("域内输入桥异常"), true));
    }

    private void closeFailures(Throwable first, Throwable second, boolean inDomain) {
        ReactiveDomain domain = ReactiveDomain.direct();
        int[] closed = {0, 0, 0};
        java.util.List<String> order = new java.util.ArrayList<>();
        SceneHost host = new SceneHost() {
            public int width() { return 80; }
            public int height() { return 60; }
            public long metricsRevision() { return 1; }
            public void replay(PaintPlan plan) {}
        };
        ScenePageSession session = ScenePageSession.open(domain, host, (text, style, width) -> { throw new AssertionError("空页面不应请求文字度量"); }, context -> {
            context.own(() -> closed[0]++);
            context.own(() -> { closed[1]++; order.add("session"); fail(first); });
        });
        MinecraftScreenResources resources = new MinecraftScreenResources(session, () -> {
            require(session.ended(), "输入桥先于session结束");
            order.add("inputs");
            closed[2]++;
            fail(second);
        });
        try {
            if (inDomain) domain.execute(resources::close); else resources.close();
            throw new AssertionError("双关闭没有失败");
        }
        catch (RuntimeException | Error actual) {
            require(actual == first, "首错identity被覆盖：actual=" + actual + " order=" + order);
            require(actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == second,
                    "输入桥错误未作为首错的suppressed");
        }
        require(order.equals(java.util.Arrays.asList("session", "inputs")), "实际所属资源释放顺序错误：" + order);
        require(closed[0] == 1 && closed[1] == 1 && closed[2] == 1, "释放失败阻断后续所属资源");
        resources.close();
        session.close();
        require(closed[0] == 1 && closed[1] == 1 && closed[2] == 1, "重复结束重复释放");
    }

    private static void fail(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        throw (RuntimeException) failure;
    }

    public static void main(String[] args) throws Exception { new MinecraftScreenCleanupScenario().run(args); }
}
