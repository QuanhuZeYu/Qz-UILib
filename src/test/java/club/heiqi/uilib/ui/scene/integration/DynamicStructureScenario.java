package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 正式页面、真实输入与软件矩形回放；业务期望不读取框架内部差异算法。 */
public final class DynamicStructureScenario extends TestApplication {
    private static final class Software implements SceneHost, SceneTextMeasurer {
        final SoftwareSceneScenario.SoftwareHost metrics = new SoftwareSceneScenario.SoftwareHost();
        int[] pixels = new int[100 * 80];

        @Override
        public int width() { return 100; }

        @Override
        public int height() { return 80; }

        @Override
        public long metricsRevision() { return metrics.metricsRevision(); }

        @Override
        public TextLayout layout(String text, TextStyle style, int width) {
            return metrics.layout(text, style, width);
        }

        @Override
        public void replay(PaintPlan plan) {
            Arrays.fill(pixels, 0);
            // 本场景只验收矩形颜色；文本度量沿现有软件宿主，未冒称字形光栅验收。
            for (PaintCommand command : plan.commands()) {
                if (command.kind != PaintCommand.Kind.RECT) continue;
                LayoutBox area = command.box.intersect(new LayoutBox(0, 0, width(), height()));
                if (command.clip != null) area = area.intersect(command.clip);
                for (int y = area.y; y < area.y + area.height; y++)
                    for (int x = area.x; x < area.x + area.width; x++)
                        pixels[y * width() + x] = command.color;
            }
        }
    }

    private static final class Item {
        final int key;
        final String label;

        Item(int key, String label) { this.key = key; this.label = label; }
    }

    private static SceneNode button(ScenePageContext context, Runnable action) {
        return SceneButton.create(context, context.domain().signal(""),
                context.domain().signal(SceneTheme.light()), action)
                .size(Size.fixed(40), Size.fixed(20));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("repeat当前数据重排退出与同键新生命周期", this::repeatLifecycle);
        scenario("条件拥有与调用方保留树重新挂载", this::conditionalRetention);
        scenario("折叠退出布局范围与输入且展开不恢复交互", this::collapse);
        scenario("同批先删除再明确跨父移动与新归属释放", this::transfer);
        scenario("静态子树首次同序列声明接管后退出", this::staticAdoption);
        scenario("移动到禁用祖先释放焦点并吞物理尾部", this::ineligibleTransfer);
        scenario("逻辑资源失败仍释放其余并保留suppressed", this::cleanupFailure);
        scenario("页面拥有根移动到保留树交接释放责任", this::ownedRootTransfer);
        scenario("同父移动不改变声明拥有权", this::sameParentMove);
        scenario("重复移动声明明确冲突", this::duplicateMoves);
    }

    @SuppressWarnings("unchecked")
    private void repeatLifecycle() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        Signal<List<Item>>[] items = new Signal[1];
        SceneNode[] root = new SceneNode[1];
        List<SceneNode> created = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int[] releases = {0};
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            items[0] = domain.signal(Arrays.asList(new Item(1, "甲"), new Item(2, "乙")));
            root[0] = new SceneNode().gap(5).bind(NodeBinding.repeat(items[0], item -> item.key, value -> {
                SceneNode row = button(context, () -> actions.add(value.get().label))
                        .own(() -> releases[0]++);
                created.add(row);
                return row;
            }));
            context.mountOwned(root[0]);
        }));
        page.frame();
        SceneNode first = created.get(0), second = created.get(1);
        MountHandle[] mount = new MountHandle[1];
        domain.execute(() -> mount[0] = page.runtime().captureMount(first));
        int measures = host.metrics.measures;
        page.frame();
        require(host.metrics.measures == measures && created.size() == 2, "稳定帧重建或重新测量");
        domain.execute(() -> items[0].set(Arrays.asList(new Item(2, "乙新"), new Item(1, "甲新"))));
        page.frame();
        domain.execute(() -> require(root[0].children().equals(Arrays.asList(second, first))
                && mount[0].active(), "重排丢失节点或挂载身份"));
        page.pointer(ScenePointerEvent.syntheticActivation(1, 26));
        page.frame();
        require(actions.equals(Collections.singletonList("甲新")), "条目业务操作捕获了过时数据");
        domain.execute(() -> {
            items[0].set(Collections.singletonList(new Item(2, "乙新")));
            items[0].set(Arrays.asList(new Item(2, "乙新"), new Item(1, "甲新")));
        });
        page.frame();
        require(created.size() == 2 && releases[0] == 0, "未生效的中间退出被落实");
        page.pointer(ScenePointerEvent.down(0, 1, 26));
        page.frame();
        domain.execute(() -> items[0].set(Collections.singletonList(new Item(2, "乙新"))));
        page.frame();
        page.pointer(ScenePointerEvent.up(0, 1, 26));
        page.frame();
        require(first.destroyed() && releases[0] == 1 && actions.size() == 1, "退出未销毁或物理尾部误触");
        domain.execute(() -> items[0].set(Arrays.asList(new Item(2, "乙新"), new Item(1, "甲再来"))));
        page.frame();
        require(created.size() == 3 && !created.get(2).id().equals(first.id()), "同键返回复活旧节点");
        page.close();
        require(releases[0] == 3 && second.destroyed(), "关闭未结束页面拥有树");
    }

    @SuppressWarnings("unchecked")
    private void conditionalRetention() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        Signal<List<SceneNode>>[] source = new Signal[1];
        SceneNode[] retained = new SceneNode[1];
        int[] released = {0}, updates = {0};
        Signal<String>[] text = new Signal[1];
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            text[0] = domain.signal("旧");
            retained[0] = button(context, () -> {})
                    .own(() -> released[0]++)
                    .bind(NodeBinding.value(text[0], (node, value) -> { updates[0]++; node.text(value); }));
            source[0] = domain.signal(Collections.singletonList(retained[0]));
            context.mountOwned(new SceneNode().bind(NodeBinding.children(source[0])));
        }));
        page.frame();
        domain.execute(() -> source[0].set(Collections.emptyList()));
        page.frame();
        domain.execute(() -> text[0].set("新"));
        page.frame();
        require(!retained[0].destroyed() && retained[0].runtime() == null && released[0] == 0,
                "保留条件退出销毁逻辑树");
        int before = updates[0];
        domain.execute(() -> source[0].set(Collections.singletonList(retained[0])));
        page.frame();
        require(updates[0] == before + 1 && "新".equals(retained[0].text()), "重挂载未读当前状态或叠加绑定");
        page.close();
        require(!retained[0].destroyed() && released[0] == 0, "关闭误销毁调用方保留树");
        retained[0].destroy();
        require(released[0] == 1, "调用方永久释放未生效");
        SceneNode[] owned = new SceneNode[1];
        ScenePageSession condition = own(ScenePageSession.open(domain, host, host, context -> {
            owned[0] = button(context, () -> {}).own(() -> released[0]++);
            source[0] = domain.signal(Collections.singletonList(owned[0]));
            context.mountOwned(new SceneNode().bind(NodeBinding.ownedChildren(source[0])));
        }));
        condition.frame();
        domain.execute(() -> source[0].set(Collections.emptyList()));
        condition.frame();
        require(owned[0].destroyed() && released[0] == 2, "拥有条件退出未自动销毁");
    }

    @SuppressWarnings("unchecked")
    private void collapse() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        Signal<Boolean>[] shown = new Signal[1];
        SceneNode[] rows = new SceneNode[2];
        int[] clicked = {0};
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            shown[0] = domain.signal(true);
            rows[0] = button(context, () -> clicked[0]++)
                    .bind(NodeBinding.value(shown[0], SceneNode::visible));
            rows[1] = button(context, () -> clicked[0]++);
            context.mountOwned(new SceneNode().gap(5).add(rows[0]).add(rows[1]));
        }));
        page.frame();
        domain.execute(() -> {
            require(page.pipeline().current().geometry.get(rows[1]).box.y == 25, "初始兄弟布局错误");
            page.runtime().focusRouter().request(page.runtime().captureMount(rows[0]));
        });
        page.pointer(ScenePointerEvent.down(0, 1, 1));
        page.frame();
        domain.execute(() -> shown[0].set(false));
        page.frame();
        domain.execute(() -> require(page.pipeline().current().geometry.get(rows[1]).box.y == 0
                && page.runtime().focusRouter().focused().get() == null, "折叠仍占间距或保留焦点"));
        require(host.pixels[1 + 21 * 100] == 0, "折叠仍贡献软件画面范围");
        page.pointer(ScenePointerEvent.up(0, 1, 1));
        page.frame();
        require(clicked[0] == 0, "折叠手势尾部激活后来占位条目");
        domain.execute(() -> shown[0].set(true));
        page.frame();
        domain.execute(() -> require(!rows[0].destroyed()
                && page.runtime().focusRouter().focused().get() == null, "展开销毁逻辑状态或恢复旧交互"));
    }

    @SuppressWarnings("unchecked")
    private void transfer() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        Signal<List<Integer>>[] items = new Signal[1];
        Signal<List<SceneNode>>[] containers = new Signal[1];
        Signal<SceneNode>[] destination = new Signal[1];
        SceneNode[] nodes = new SceneNode[3];
        MountHandle[] original = new MountHandle[1];
        int[] released = {0}, clicked = {0}, bindings = {0};
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            nodes[0] = new SceneNode().size(Size.fixed(40), Size.fixed(30));
            nodes[1] = new SceneNode().size(Size.fixed(40), Size.fixed(30));
            items[0] = domain.signal(Collections.singletonList(1));
            destination[0] = domain.signal(nodes[0]);
            nodes[0].bind(NodeBinding.repeat(items[0], value -> value, value -> {
                nodes[2] = button(context, () -> clicked[0]++)
                        .own(() -> released[0]++)
                        .bind(NodeBinding.move(destination[0]))
                        .bind((runtime, node, owner) -> {
                            bindings[0]++;
                            return NodeBinding.value(value, (target, current) -> {})
                                    .activate(runtime, node, owner);
                        });
                return nodes[2];
            }));
            containers[0] = domain.signal(Arrays.asList(nodes[0], nodes[1]));
            context.mountOwned(new SceneNode().axis(SceneNode.Axis.ROW)
                    .bind(NodeBinding.ownedChildren(containers[0])));
        }));
        page.frame();
        domain.execute(() -> {
            original[0] = page.runtime().captureMount(nodes[2]);
            page.runtime().focusRouter().request(original[0]);
        });
        page.pointer(ScenePointerEvent.down(0, 1, 1));
        page.frame();
        domain.execute(() -> {
            items[0].set(Collections.emptyList());
            destination[0].set(nodes[1]);
        });
        page.frame();
        domain.execute(() -> require(original[0].active() && nodes[2].parent() == nodes[1]
                && page.runtime().focusRouter().focused().get() == nodes[2]
                && bindings[0] == 1, "明确移动丢失挂载绑定或合法焦点"));
        page.pointer(ScenePointerEvent.up(0, 41, 1));
        page.frame();
        require(clicked[0] == 1, "合法移动中手势没有连续激活新位置");
        domain.execute(() -> containers[0].set(Collections.singletonList(nodes[1])));
        page.frame();
        require(nodes[0].destroyed() && !nodes[2].destroyed() && released[0] == 0,
                "旧容器误销毁已迁移条目");
        domain.execute(() -> containers[0].set(Collections.emptyList()));
        page.frame();
        require(nodes[2].destroyed() && released[0] == 1, "新作用域未承担迁移条目生命周期");
    }

    @SuppressWarnings("unchecked")
    private void staticAdoption() {
        for (boolean owning : new boolean[] {false, true}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            Software host = new Software();
            Signal<List<SceneNode>>[] source = new Signal[1];
            SceneNode[] child = new SceneNode[1];
            ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
                child[0] = button(context, () -> {});
                source[0] = domain.signal(Collections.singletonList(child[0]));
                context.mountOwned(new SceneNode().add(child[0])
                        .bind(owning ? NodeBinding.ownedChildren(source[0]) : NodeBinding.children(source[0])));
            }));
            page.frame();
            domain.execute(() -> source[0].set(Collections.emptyList()));
            page.frame();
            require(child[0].parent() == null && child[0].runtime() == null
                    && child[0].destroyed() == owning, "首次相同静态子序列没有接管声明生命周期");
            if (!owning) child[0].destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private void ineligibleTransfer() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        Signal<SceneNode>[] destination = new Signal[1];
        SceneNode[] nodes = new SceneNode[3];
        int[] clicked = {0};
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            nodes[0] = new SceneNode().size(Size.fixed(40), Size.fixed(30));
            nodes[1] = new SceneNode().size(Size.fixed(40), Size.fixed(30)).enabled(false);
            destination[0] = domain.signal(nodes[0]);
            nodes[2] = button(context, () -> clicked[0]++)
                    .bind(NodeBinding.move(destination[0]));
            nodes[0].add(nodes[2]);
            context.mountOwned(new SceneNode().axis(SceneNode.Axis.ROW).add(nodes[0]).add(nodes[1]));
        }));
        page.frame();
        page.pointer(ScenePointerEvent.down(0, 1, 1));
        page.frame();
        domain.execute(() -> destination[0].set(nodes[1]));
        page.frame();
        domain.execute(() -> require(nodes[2].parent() == nodes[1]
                && page.runtime().focusRouter().focused().get() == null,
                "新祖先失活未结束原焦点"));
        page.pointer(ScenePointerEvent.up(0, 41, 1));
        page.frame();
        require(clicked[0] == 0 && !nodes[2].destroyed(), "失活移动误激活或销毁逻辑条目");
    }

    private void ownedRootTransfer() {
        for (boolean declared : new boolean[] {false, true}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            Software host = new Software();
            SceneNode[] nodes = new SceneNode[2];
            int[] released = {0};
            ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
                nodes[1] = new SceneNode();
                context.mount(nodes[1]);
                nodes[0] = new SceneNode().own(() -> released[0]++);
                if (declared) nodes[0].bind(NodeBinding.move(domain.signal(nodes[1])));
                MountHandle handle = context.mountOwned(nodes[0]);
                if (!declared) handle.moveTo(nodes[1]);
            }));
            page.frame();
            page.close();
            require(!nodes[0].destroyed() && nodes[0].parent() == nodes[1]
                    && released[0] == 0, "旧页面拥有声明误销毁迁移到保留树的根");
            ScenePageSession reopened = own(ScenePageSession.open(domain, host, host,
                    context -> context.mount(nodes[1])));
            reopened.frame();
            reopened.close();
            nodes[1].destroy();
            require(nodes[0].destroyed() && released[0] == 1, "新保留树未承担迁移根的释放责任");
        }
    }

    @SuppressWarnings("unchecked")
    private void sameParentMove() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        SceneNode[] nodes = new SceneNode[2];
        Signal<List<SceneNode>>[] items = new Signal[1];
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            nodes[1] = new SceneNode();
            items[0] = domain.signal(Collections.singletonList(nodes[1]));
            nodes[0] = new SceneNode().bind(NodeBinding.ownedChildren(items[0]));
            context.mountOwned(nodes[0]);
        }));
        page.frame();
        domain.execute(() -> {
            page.runtime().captureMount(nodes[1]).moveTo(nodes[0]);
            items[0].set(Collections.emptyList());
        });
        page.frame();
        require(nodes[1].destroyed() && nodes[0].children().isEmpty(),
                "同父移动偷走声明拥有权，使退出条目存活");
    }

    private void duplicateMoves() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        try {
            own(ScenePageSession.open(domain, host, host, context -> {
                SceneNode parent = new SceneNode();
                context.mount(parent);
                context.mountOwned(new SceneNode()
                        .bind(NodeBinding.move(domain.signal(parent)))
                        .bind(NodeBinding.move(domain.signal(parent))));
            })).frame();
            throw new AssertionError("重复移动声明被静默接受");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("移动声明"), "重复移动声明诊断没有定位冲突");
        }
    }

    private void cleanupFailure() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Software host = new Software();
        SceneNode[] tree = new SceneNode[3];
        IllegalStateException first = new IllegalStateException("第一资源失败");
        IllegalStateException second = new IllegalStateException("第二资源失败");
        int[] released = {0};
        ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            tree[1] = new SceneNode().own(() -> { released[0]++; throw first; });
            tree[2] = new SceneNode().own(() -> { released[0]++; throw second; });
            tree[0] = new SceneNode().own(() -> released[0]++).add(tree[1]).add(tree[2]);
            context.mountOwned(tree[0]);
        });
        page.frame();
        try {
            page.close();
            throw new AssertionError("释放失败被吞掉");
        } catch (IllegalStateException failure) {
            require(failure == first && Arrays.asList(failure.getSuppressed()).contains(second),
                    "原错误被替换或后续失败未suppressed");
        }
        page.close();
        require(released[0] == 3 && tree[0].destroyed() && tree[1].destroyed()
                && tree[2].destroyed(), "失败阻断其余释放或重复关闭重复释放");
    }

    public static void main(String[] args) throws Exception {
        new DynamicStructureScenario().run(args);
    }
}
