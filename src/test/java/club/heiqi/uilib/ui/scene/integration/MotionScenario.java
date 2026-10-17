package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 解析求解互证与真实挂载生命周期，不依赖墙钟 sleep。 */
public final class MotionScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new MotionScenario().run(args); }
    protected void runTests() throws Exception {
        scenario("解析弹簧同时间、不同分帧及三种阻尼", this::spring);
        scenario("有限反向缩时与显式惯性终点", this::finite);
        scenario("声明目标、动态策略、重复准备及重挂", this::binding);
        scenario("正式颜色与透明度通道", this::properties);
        scenario("标准控件默认过渡、局部覆盖与纯绘制失效", this::standardFeedback);
        scenario("自然尺寸真实分配和位置重排途中命中", this::layout);
        scenario("显式退场立即结束输入及显隐反向", this::presence);
        scenario("拥有集合退场预算、同键新身份与页面释放", this::ownedExits);
        scenario("途中减少动效同步已删除条目的平稳退场", this::reducedOwnedExit);
        scenario("拖拽释放保留位置和速度且单次意图", this::dragRelease);
        scenario("停手释放不甩出且碰壁后立即回槽", this::dragReleaseFeedback);
        scenario("碰壁按实际时刻推进且预览沿视口回槽", this::boundedRelease);
        scenario("滚动祖先优先、合法偏移与显式惯性", this::scrollMotion);
        scenario("退场滚动与窗口扩张消费当前裁剪", this::presenceClip);
        scenario("撤销回弹立即重排与旧滚动样本隔离", this::scrollCancellation);
        scenario("回槽途中键盘接管与取消", this::keyboardTakeover);
        scenario("自身裁剪外盒与退场错误清理", this::presenceOwnClip);
        scenario("无效目标结束轨迹后可卸载重挂", this::failedMount);
        scenario("退场所属层继续逃离来源局部裁剪", this::presenceLayer);
    }
    private void spring() {
        for (double damping : new double[] {.65, 1, 1.7}) {
            MotionSpec spec = MotionSpec.spring(.3, damping);
            MotionValue one = new MotionValue(), many = new MotionValue();
            one.snap(0, 0); many.snap(0, 0);
            one.retarget(100, spec, 0); many.retarget(100, spec, 0);
            one.advance(173_000_000L);
            for (long now = 1_000_000L; now <= 173_000_000L; now += 1_000_000L) many.advance(now);
            near(one.value(), many.value(), 1e-10, "分帧改变位置");
            near(one.velocity(), many.velocity(), 1e-10, "分帧改变速度");
            double x = one.value(), v = one.velocity();
            one.retarget(-30, MotionSpec.spring(.24, .8), 173_000_000L);
            near(one.value(), x, 1e-12, "反向位置跳变"); near(one.velocity(), v, 1e-12, "反向速度清零");
            one.advance(60_000_000_000L);
            require(one.value() == -30 && one.velocity() == 0 && !one.active(), "长间隔未精确停止");
        }
        // 临界阻尼的独立闭式：x(t)=1-(1+omega*t)*exp(-omega*t)。
        MotionValue critical = new MotionValue(); MotionSpec spec = MotionSpec.spring(.3, 1);
        critical.snap(0, 0); critical.retarget(1, spec, 0); critical.advance(100_000_000L);
        double t = .1, omega = 2 * Math.PI / .3;
        near(critical.value(), 1 - (1 + omega * t) * Math.exp(-omega * t), 1e-12, "临界解错误");
    }
    private void finite() {
        MotionValue value = new MotionValue(); MotionSpec linear = MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR);
        value.snap(0, 0); value.retarget(1, linear, 0); value.advance(400_000_000L);
        near(value.value(), .4, 1e-12, "有限目标");
        value.retarget(0, linear, 400_000_000L); value.advance(600_000_000L);
        near(value.value(), .2, 1e-12, "反向未缩时"); value.advance(800_000_000L);
        require(value.value() == 0 && !value.active(), "反向未精确落点");
        for (boolean middle : new boolean[] {true, false}) {
            MotionValue completed = new MotionValue(); completed.snap(0, 0); completed.retarget(1, linear, 0);
            if (middle) completed.advance(400_000_000L);
            completed.advance(1_000_000_000L); completed.retarget(0, linear, 1_000_000_000L); completed.advance(1_200_000_000L);
            near(completed.value(), .8, 1e-12, "完成后反向未恢复完整时长 middle=" + middle);
        }
        value.decay(20, 300, MotionSpec.decay(6), 1_000_000_000L);
        near(value.target(), 70, 1e-12, "惯性终点"); value.advance(1_100_000_000L);
        near(value.value(), 70 - 50 * Math.exp(-.6), 1e-10, "惯性经过时间");
    }
    private void binding() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain);
            Signal<Float> target = domain.signal(0f); Signal<MotionPolicy> policy = domain.signal(MotionPolicy.NORMAL);
            runtime.motionPolicy(policy);
            float[] shown = {0}; int[] calls = {0};
            SceneNode node = new SceneNode().bind(NodeBinding.motion(target, () -> MotionSpec.spring(.3, .8),
                    (n, value) -> { shown[0] = value; calls[0]++; }));
            MountHandle mount = runtime.attach(null, node); runtime.preparePresentation(1_000_000_000L);
            target.set(100f); runtime.preparePresentation(1_000_000_000L); runtime.preparePresentation(1_050_000_000L);
            require(shown[0] > 0 && shown[0] < 100, "挂载声明没有推进弹簧");
            float current = shown[0]; int count = calls[0]; runtime.preparePresentation(1_050_000_000L);
            require(shown[0] == current && calls[0] == count, "重复准备推进或重发映射");
            policy.set(MotionPolicy.OFF); runtime.preparePresentation(1_050_000_000L);
            require(shown[0] == 100, "关闭未落到目标"); mount.detach();
            target.set(30f); runtime.preparePresentation(2_000_000_000L);
            require(shown[0] == 100, "卸载后仍更新");
            runtime.attach(null, node); runtime.preparePresentation(2_000_000_000L);
            require(shown[0] == 30, "重挂未直接消费当前目标"); runtime.close(); node.destroy();
        });
    }
    private void properties() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain);
            Signal<Integer> color = domain.signal(0x00ff0000);
            MotionSpec linear = MotionSpec.tween(100_000_000L, MotionSpec.LINEAR);
            SceneNode node = new SceneNode().bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.background(color, () -> linear));
            runtime.attach(null, node); runtime.preparePresentation(0);
            color.set(0xff0000ff); runtime.preparePresentation(0); runtime.preparePresentation(50_000_000L);
            require(node.background() == 0x800000ff, "透明端点污染预乘颜色: " + Integer.toHexString(node.background()));
            runtime.preparePresentation(100_000_000L);
            require(node.background() == 0xff0000ff, "颜色未精确落端点");
            runtime.close(); node.destroy();
        });
    }
    private void standardFeedback() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] button = {null}; Signal<MotionSpec>[] local = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            local[0] = domain.signal(null);
            button[0] = club.heiqi.uilib.ui.scene.control.SceneButton.create(context, () -> "", context.theme(), () -> {})
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(40))
                    .bind(NodeBinding.value(local[0], SceneNode::feedbackMotion));
            context.mountOwned(button[0]);
        }));
        page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(5, 5)); page.frame();
        domain.execute(() -> {
            long start = page.runtime().inputNanos();
            club.heiqi.uilib.ui.scene.layout.SceneGeometry before = page.pipeline().current().geometry;
            page.runtime().preparePresentation(start + 70_000_000L);
            club.heiqi.uilib.ui.scene.runtime.ScenePresentation middle = page.pipeline().current();
            int color = middle.paint.commands().get(0).color;
            club.heiqi.uilib.ui.scene.control.SceneTheme theme = club.heiqi.uilib.ui.scene.control.SceneTheme.light();
            require(color != theme.button && color != theme.hover, "默认未产生中间颜色");
            require(middle.geometry == before, "仅颜色运动重建了几何");
            page.runtime().preparePresentation(start + 140_000_000L);
            require(page.pipeline().current().paint.commands().get(0).color == theme.hover, "默认未精确落到悬停色");
            local[0].set(MotionSpec.instant());
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.frame();
        domain.execute(() -> require(page.pipeline().current().paint.commands().get(0).color
                == club.heiqi.uilib.ui.scene.control.SceneTheme.light().pressed, "局部即时覆盖未生效"));
    }
    private void layout() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] pane = {null}, neighbor = {null}, a = {null}, b = {null};
        Signal<Integer>[] height = new Signal[1]; Signal<java.util.List<SceneNode>>[] order = new Signal[1];
        int[] clicks = {0}; MotionSpec spring = MotionSpec.spring(.4, 1);
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            height[0] = domain.signal(40);
            pane[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.natural())
                    .clip(true).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutSize(() -> spring))
                    .add(new SceneNode().bind(NodeBinding.value(height[0], (node, h) -> node.size(
                            club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(h)))));
            neighbor[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(20));
            a[0] = new SceneNode().background(0xff112233).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).onClick(() -> clicks[0]++);
            b[0] = new SceneNode().background(0xff445566).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(40), club.heiqi.uilib.ui.scene.layout.Size.fixed(30));
            order[0] = domain.signal(java.util.Arrays.asList(a[0], b[0]));
            SceneNode list = new SceneNode().bind(NodeBinding.children(order[0])).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutPositions(() -> spring));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.natural())
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutPositions(() -> spring)).add(pane[0]).add(neighbor[0]).add(list));
        }));
        page.frame();
        domain.execute(() -> { height[0].set(140); order[0].set(java.util.Arrays.asList(b[0], a[0])); });
        page.frame();
        int[] clickY = {0};
        domain.execute(() -> {
            long start = page.runtime().inputNanos();
            page.runtime().preparePresentation(start + 80_000_000L);
            club.heiqi.uilib.ui.scene.layout.SceneGeometry geometry = page.pipeline().current().geometry;
            int h = geometry.get(pane[0]).box.height;
            require(h > 40 && h < 140, "自然尺寸没有中间分配: " + h);
            require(geometry.get(neighbor[0]).box.y == geometry.get(pane[0]).box.y + h,
                    "邻项未随当前实际空间排列");
            int localA = geometry.get(a[0]).box.y - geometry.get(a[0].parent()).box.y;
            require(localA > 0 && localA < 30, "重排未从当前局部位置续接: " + localA);
            clickY[0] = geometry.get(a[0]).box.y + 5;
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.syntheticActivation(90, clickY[0])); page.frame();
        require(clicks[0] == 1, "可见中间位置未通过正式路由命中");
        domain.execute(() -> {
            page.runtime().preparePresentation(page.runtime().inputNanos() + 2_000_000_000L);
            club.heiqi.uilib.ui.scene.runtime.ScenePresentation settled = page.pipeline().current();
            require(settled.geometry.get(pane[0]).box.height == 140, "尺寸未落自然目标");
            require(page.pipeline().current() == settled, "静止没有复用呈现");
        });
    }
    private void presence() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<Boolean>[] visible = new Signal[1]; SceneNode[] node = {null}; int[] clicks = {0};
        club.heiqi.uilib.ui.scene.motion.PresenceOptions options = new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(200_000_000L, MotionSpec.LINEAR));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            visible[0] = domain.signal(true);
            node[0] = new SceneNode().focusable(true).modal(true).background(0xff123456)
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .onClick(() -> clicks[0]++).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(visible[0], () -> options));
            context.mountOwned(new SceneNode().add(node[0]));
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.frame();
        domain.execute(() -> visible[0].set(false)); page.frame();
        domain.execute(() -> {
            require(page.pipeline().current().geometry.activeModal() == null && page.runtime().focusRouter().focused().get() == null,
                    "退出仍持有模态或焦点");
            require(page.pipeline().current().geometry.get(node[0]) == null, "退场仍发布输入几何");
            long now = page.runtime().inputNanos(); page.runtime().preparePresentation(now + 80_000_000L);
            club.heiqi.uilib.ui.scene.paint.PaintPlan plan = page.pipeline().current().paint;
            require(plan.commands().stream().anyMatch(c -> c.color == 0x99123456), "退场未保留当前被动画面");
            visible[0].set(true); page.runtime().preparePresentation(now + 80_000_000L);
            near(page.runtime().presenceOpacity(node[0]), .6, 1e-6, "反向未沿当前透明度");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 5, 5)); page.frame();
        require(clicks[0] == 0, "显隐反向复活了旧按压");
    }
    private void ownedExits() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] items = new Signal[1]; SceneNode[] container = {null};
        java.util.List<SceneNode> built = new java.util.ArrayList<>(); int[] released = {0};
        club.heiqi.uilib.ui.scene.motion.PresenceOptions options = new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(500_000_000L, MotionSpec.LINEAR)).budget(1);
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            items[0] = domain.signal(java.util.Arrays.asList("a", "b"));
            container[0] = new SceneNode().bind(NodeBinding.repeat(items[0], value -> value, value -> {
                SceneNode child = new SceneNode().background(0xff224466).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).own(() -> released[0]++);
                built.add(child); return child;
            }, () -> options));
            context.mountOwned(container[0]);
        }));
        page.frame(); domain.execute(() -> items[0].set(java.util.Collections.singletonList("b"))); page.frame();
        domain.execute(() -> {
            require(built.get(0).runtime() == null && built.get(0).retired() && !built.get(0).destroyed(), "退出身份或保留资源错误");
            items[0].set(java.util.Arrays.asList("a", "b"));
        }); page.frame();
        require(built.size() == 3 && !built.get(0).id().equals(built.get(2).id()), "同键返回复活原身份");
        domain.execute(() -> items[0].set(java.util.Collections.singletonList("b"))); page.frame();
        require(built.get(0).destroyed() && released[0] == 1, "同键较旧代未逐出");
        domain.execute(() -> items[0].set(java.util.Collections.emptyList())); page.frame();
        require(built.get(2).destroyed() && released[0] == 2, "退场预算未逐出最早记录");
        page.close(); require(released[0] == 3, "页面结束没有释放最后保留资源");
    }
    private void reducedOwnedExit() {
        for (boolean prepared : new boolean[] {false, true}) {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] items = new Signal[1]; Signal<MotionPolicy>[] policy = new Signal[1];
        SceneNode[] row = {null}; int[] released = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            items[0] = domain.signal(java.util.Collections.singletonList("条目")); policy[0] = domain.signal(MotionPolicy.NORMAL); context.motionPolicy(policy[0]);
            context.mountOwned(new SceneNode().bind(NodeBinding.repeat(items[0], item -> item, item -> {
                row[0] = new SceneNode().background(0xff182736).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(40)).own(() -> released[0]++);
                return row[0];
            }, () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.spring(.58, .52)))));
        }));
        page.frame(); domain.execute(() -> items[0].set(java.util.Collections.emptyList())); page.frame();
        domain.execute(() -> {
            long now = page.runtime().inputNanos();
            if (prepared) { page.runtime().preparePresentation(now + 80_000_000L); page.pipeline().current(); }
            policy[0].set(MotionPolicy.REDUCED); page.runtime().preparePresentation(now + 80_000_000L);
            MotionValue reference = new MotionValue(); reference.snap(1, 0); reference.retarget(0, MotionSpec.spring(.58, .52), 0); reference.advance(80_000_000L);
            int expected = Math.round((float) reference.value() * 255);
            int current = page.pipeline().current().paint.commands().stream().filter(command -> (command.color & 0xffffff) == 0x182736)
                    .mapToInt(command -> command.color >>> 24).max().orElse(0);
            require(Math.abs(current - expected) <= 1, "减少策略回溯到上帧启动: current=" + current + " expected=" + expected);
            int previous = 255;
            for (long time = 80; time <= 700; time += 20) {
                page.runtime().preparePresentation(now + time * 1_000_000L);
                int alpha = page.pipeline().current().paint.commands().stream().filter(command -> (command.color & 0xffffff) == 0x182736)
                        .mapToInt(command -> command.color >>> 24).max().orElse(0);
                require(alpha <= previous, "减少动效后退场回闪: " + previous + "→" + alpha);
                previous = alpha;
                if (time == 200) require(row[0].destroyed() && released[0] == 1, "已删除条目仍运行原弹簧退出");
            }
        });
        }
    }
    private void dragRelease() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] content = {null}, drag = {null}; int[] dropped = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().background(0xff887766).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            drag[0] = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0],
                    club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false)
                            .releaseMotion(MotionSpec.spring(.35, .8)).onDrop(value -> dropped[0]++));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(200)).add(drag[0]));
        }));
        page.frame(); long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(55, 5).at(input + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 55, 5).at(input + 50_000_000L)); page.frame();
        domain.execute(() -> {
            int position = page.pipeline().current().geometry.get(content[0]).box.x;
            require(position == 50 && dropped[0] == 1, "释放跳回原槽或意图重复: " + position);
            long now = page.runtime().inputNanos(); page.runtime().preparePresentation(now + 10_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x > 50, "正式采样速度未续接");
            page.runtime().preparePresentation(now + 2_000_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x == 0 && dropped[0] == 1, "回槽端点或动作次数错误");
        });
    }
    private void dragReleaseFeedback() {
        for (boolean paused : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
            SceneNode[] content = {null};
            club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
                content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
                SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0],
                        club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                        new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false)
                                .bounds(club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.PARENT).releaseMotion(MotionSpec.spring(.58, .52)));
                context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(180), club.heiqi.uilib.ui.scene.layout.Size.fixed(100)).add(drag));
            }));
            page.frame(); long input = System.nanoTime();
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(75, 5).at(input + 5_000_000L));
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 75, 5).at(input + (paused ? 80_000_000L : 5_000_000L))); page.frame();
            domain.execute(() -> {
                long time = page.runtime().inputNanos();
                page.runtime().preparePresentation(time + 10_000_000L);
                int first = page.pipeline().current().geometry.get(content[0]).box.x;
                page.runtime().preparePresentation(time + 40_000_000L);
                int next = page.pipeline().current().geometry.get(content[0]).box.x;
                if (paused) require(first <= 70 && next < first, "停手松开仍按旧速度向外甩出: " + first + "/" + next);
                else require(next < first, "撞到右边界后仍贴边等待内部越界轨迹: " + first + "/" + next);
                page.runtime().preparePresentation(time + 2_000_000_000L);
                require(page.pipeline().current().geometry.get(content[0]).box.x == 0, "边界投影没有回到合法槽位");
            });
        }
    }
    private void boundedRelease() {
        MotionSpec released = MotionSpec.spring(.58, .52);
        MotionValue constrained = new MotionValue(), reference = new MotionValue();
        constrained.release(70, 0, 0, released, 0); constrained.constrain(0, 120, 0); constrained.advance(80_000_000L);
        reference.release(constrained.value(), constrained.velocity(), 0, released, 80_000_000L);
        constrained.unconstrain(); constrained.advance(300_000_000L); reference.advance(300_000_000L);
        require(constrained.value() < 0, "撤销边界后仍被旧范围截断");
        near(constrained.value(), reference.value(), .00001, "撤销约束重置了当前轨迹");
        near(constrained.velocity(), reference.velocity(), .0001, "撤销约束丢失了速度");
        for (double damping : new double[] {.52, 1, 1.8}) {
            MotionSpec spring = MotionSpec.spring(.58, damping);
            MotionValue one = new MotionValue(), many = new MotionValue();
            one.release(70, 14000, 0, spring, 0); many.release(70, 14000, 0, spring, 0);
            one.constrain(0, 120, 0); many.constrain(0, 120, 0);
            one.advance(100_000_000L);
            for (long now = 10_000_000L; now <= 100_000_000L; now += 10_000_000L) many.advance(now);
            near(one.value(), many.value(), .00001, "碰壁分帧改变位置 damping=" + damping);
            near(one.velocity(), many.velocity(), .0001, "碰壁分帧改变速度 damping=" + damping);
            require(one.value() < 120, "大帧仍停在边界未推进剩余时间");
            one.advance(1_000_000_000L); many.advance(1_000_000_000L);
            near(one.value(), many.value(), .00001, "跨第二侧边界后不一致");
        }
        for (club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds bounds : new club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds[] {
                club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.NONE, club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.PARENT}) for (boolean paused : new boolean[] {false, true}) {
            int one = overlayRelease(bounds, paused, false), many = overlayRelease(bounds, paused, true);
            near(one, many, 1, "预览PageSession单帧与连续排列改变相同经过时间的回槽位置");
        }
    }
    private int overlayRelease(club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds bounds, boolean paused, boolean partitioned) {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 180; host.height = 120;
        SceneNode[] preview = {null}; long[] releasedAt = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            preview[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode content = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content, club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false).bounds(bounds).overlay(preview[0]).releaseMotion(MotionSpec.spring(.58, .52))
                            .onDrop(ignored -> releasedAt[0] = context.runtime().inputNanos()));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(100)).add(drag));
        }));
        page.frame(); long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
        int pointer = paused ? 175 : 75;
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(pointer, 5).at(input + 5_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, pointer, 5).at(input + (paused ? 80_000_000L : 5_000_000L))); page.frame();
        int[] result = {0};
        domain.execute(() -> {
            int first = -1;
            for (long time = partitioned ? 10 : 100; time <= 100; time += 10) {
                page.runtime().preparePresentation(releasedAt[0] + time * 1_000_000L);
                int current = page.pipeline().current().geometry.get(preview[0]).box.x;
                if (time == 10) first = current;
                if (time == 40) {
                    require(current < first && first <= 120, "预览视口内部轨迹仍越界: " + first + "/" + current);
                    if (paused) {
                        MotionValue expected = new MotionValue(); expected.release(120, 0, 0, MotionSpec.spring(.58, .52), 0); expected.advance(40_000_000L);
                        near(current, Math.round(expected.value()), 1, "预览UP与MOVE坐标混用产生假速度");
                    }
                }
                result[0] = current;
            }
        });
        return result[0];
    }
    private void scrollMotion() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] inner = {null}, outer = {null}, content = {null};
        club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions options = new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.3, .8)).inertia(MotionSpec.decay(6));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().background(0xff112244).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(1000));
            inner[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> options)).add(content[0]);
            outer[0] = new SceneNode().scrollable(true).scrollOffset(40)
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(120))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> options)).add(inner[0])
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(400)));
            context.mountOwned(outer[0]);
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        domain.execute(() -> {
            require(page.runtime().scrollOffset(outer[0]) == 20 && page.runtime().scrollOffset(inner[0]) == 0,
                    "内层边界未交祖先");
            require(page.runtime().scrollPresentationOffset(inner[0]) == 0, "内层回弹抢走祖先输入");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        domain.execute(() -> require(page.runtime().scrollOffset(outer[0]) == 0 && page.runtime().scrollPresentationOffset(outer[0]) > 0, "边界暂态未保持合法偏移"));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, -1)); page.frame();
        domain.execute(() -> require(page.runtime().scrollOffset(outer[0]) == 20 && page.runtime().scrollPresentationOffset(outer[0]) == 0, "反向积存越界距离"));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(90, 100)); page.frame();
        long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, input));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, input + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, 0, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.END, input + 50_000_000L)); page.frame();
        domain.execute(() -> {
            int before = page.runtime().scrollOffset(outer[0]); long now = page.runtime().inputNanos();
            page.runtime().preparePresentation(now + 100_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(outer[0]) > before, "显式结束未合成所属视口惯性");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(90, 100, 1)); page.frame();
        domain.execute(() -> {
            int stopped = page.runtime().scrollOffset(outer[0]); page.runtime().preparePresentation(page.runtime().inputNanos() + 200_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(outer[0]) == stopped, "新输入没有接管旧惯性");
        });
    }
    private void presenceClip() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] viewport = {null}; Signal<Boolean>[] present = new Signal[1]; Signal<Integer>[] height = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            present[0] = domain.signal(true); height[0] = domain.signal(100);
            viewport[0] = new SceneNode().scrollable(true).bind(NodeBinding.value(height[0], (node, h) -> node.size(
                    club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(h))))
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(80)))
                    .add(new SceneNode().background(0xff1239ab).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                            .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(present[0], () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))))
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(300)));
            context.mountOwned(viewport[0]);
        }));
        page.frame(); domain.execute(() -> present[0].set(false)); page.frame();
        domain.execute(() -> page.runtime().scrollByPixels(viewport[0], 10)); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x1239ab).findFirst().get();
            require(ghost.box.y == 70 && ghost.clip.contains(5, 99), "滚动平移了祖先旧裁剪");
            height[0].set(140);
        }); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x1239ab).findFirst().get();
            require(ghost.clip.contains(5, 129), "窗口扩大仍受旧祖先裁剪限制");
        });
    }
    private void scrollCancellation() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] viewport = {null}, content = {null}; Signal<club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions>[] options = new Signal[1];
        club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions motion = new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.3, .8)).inertia(MotionSpec.decay(6));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            options[0] = domain.signal(motion);
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(1000));
            viewport[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(100))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(options[0])).add(content[0]); context.mountOwned(viewport[0]);
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1)); page.frame();
        domain.execute(() -> {
            require(page.pipeline().current().geometry.get(content[0]).box.y > 0, "未建立回弹反例");
            options[0].set(null);
            require(page.pipeline().current().geometry.get(content[0]).box.y == 0, "撤销回弹复用了旧偏移几何");
            options[0].set(motion);
        }); page.frame();
        long now = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, now)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 5, 5)); page.frame();
        domain.execute(() -> page.runtime().scrollToPixels(viewport[0], club.heiqi.uilib.ui.scene.node.SceneNode.Axis.COLUMN, 200)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, now + 60_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, 0, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.END, now + 60_000_000L)); page.frame();
        domain.execute(() -> {
            int offset = page.runtime().scrollOffset(viewport[0]); require(offset == 220, "新段未直接滚动");
            page.runtime().preparePresentation(page.runtime().inputNanos() + 100_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(viewport[0]) == offset, "旧段采样污染新段单个UPDATE");
        });
    }
    private void keyboardTakeover() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] content = {null}; int[] drops = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0], club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false).releaseMotion(MotionSpec.spring(.6, 1)).onDrop(value -> drops[0]++));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(200)).add(drag));
        }));
        page.frame(); long now = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(now));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(55, 5).at(now + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 55, 5).at(now + 50_000_000L)); page.frame();
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SPACE, false, null)); page.frame();
        int[] grabbed = {0}; domain.execute(() -> {
            grabbed[0] = page.pipeline().current().geometry.get(content[0]).box.x;
            require(Math.abs(grabbed[0] - 50) < 3, "键盘拾取瞬间跳回原槽: " + grabbed[0]);
        });
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.RIGHT, false, null)); page.frame();
        domain.execute(() -> require(page.pipeline().current().geometry.get(content[0]).box.x > grabbed[0], "键盘位移仍被旧回槽覆盖"));
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.ESCAPE, false, null)); page.frame();
        domain.execute(() -> {
            page.runtime().preparePresentation(page.runtime().inputNanos() + 300_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x == 0 && drops[0] == 1, "取消复活旧释放或重复业务意图");
        });
    }
    private void failedMount() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain); Signal<Float> target = domain.signal(Float.NaN);
            MotionSpec spec = MotionSpec.spring(.3, 1); float[] shown = {0};
            SceneNode node = new SceneNode().bind(NodeBinding.motion(target, () -> spec, true, "test.position", (n, value) -> shown[0] = value));
            MountHandle mount = runtime.attach(null, node);
            boolean failed = false;
            try { runtime.preparePresentation(0); } catch (IllegalArgumentException expected) { failed = true; }
            require(failed, "非法目标未按呈现错误通道暴露");
            mount.detach();
            target.set(20f); runtime.attach(null, node); runtime.preparePresentation(0);
            require(shown[0] == 20, "失败通道仍占用后续合法挂载"); runtime.close(); node.destroy();
        });
    }
    private void presenceOwnClip() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] rows = new Signal[1]; java.util.List<SceneNode> built = new java.util.ArrayList<>(); int[] released = {0};
        IllegalStateException error = new IllegalStateException("退场资源失败");
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            rows[0] = domain.signal(java.util.Arrays.asList("a", "b", "c"));
            context.mountOwned(new SceneNode().bind(NodeBinding.repeat(rows[0], value -> value, value -> {
                String key = value.get(); SceneNode child = new SceneNode().clip(true).padding(10).background(0xff765432)
                        .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                        .own(() -> { released[0]++; if (!key.equals("c")) throw error; });
                built.add(child); return child;
            }, () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))));
        }));
        page.frame(); domain.execute(() -> rows[0].set(java.util.Collections.emptyList())); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x765432).findFirst().get();
            require(ghost.clip.x == 0 && ghost.clip.y == 0 && ghost.clip.width == 100 && ghost.clip.height == 60,
                    "自身clip被错误缩到padding正文盒");
        });
        try { page.close(); } catch (IllegalStateException failure) { require(failure == error, "释放错误被替换"); }
        require(released[0] == 3 && built.stream().allMatch(SceneNode::destroyed), "一个退场资源失败阻止其他资源释放");
    }
    private void presenceLayer() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<Boolean>[] shown = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            shown[0] = domain.signal(true);
            SceneNode floating = club.heiqi.uilib.ui.scene.control.SceneLayer.create(context,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(1, club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.PASSIVE));
            floating.background(0xff987654).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(20));
            context.mountOwned(new SceneNode().clip(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(shown[0], () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))).add(floating));
        }));
        page.frame(); domain.execute(() -> shown[0].set(false)); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand layer = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x987654).findFirst().get();
            require(layer.box.y == 61 && layer.clip.contains(5, 70), "退场所属层被来源根clip截断");
            require(page.pipeline().current().geometry.activeModal() == null, "被动退场重新发布层输入范围");
        });
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        require(Math.abs(actual - expected) <= tolerance, message + ": actual=" + actual + " expected=" + expected);
    }
}
