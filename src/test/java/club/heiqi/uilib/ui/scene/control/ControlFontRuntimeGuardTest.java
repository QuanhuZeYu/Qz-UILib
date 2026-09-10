package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 控件字号运行期守卫（字号动态化 I-4，守卫 1b）：挂载式控件在真实帧里必须跟随句柄字号，
 * 浮层族必须与句柄字号保持单值一致。
 *
 * <p>断言只读真实帧产物（{@link PaintCommand#getTextStyle()} 的字号），不读节点内部字段：
 * 字号没传到绘制上时断言必须失败。夹具与 SceneControlFontSizeEntryTest 完全同构
 * （SceneInteractionHarness + SceneFramePipeline + 只记录 super.paint 产物的 CapturingPaintEngine）。</p>
 *
 * <p>装饰字面值（Checkbox 勾选标记、DragReorder 把手）按 {@link ControlTextInventoryTest#EXEMPT_TEXTS}
 * 的字面值跳过——该表本身被计数钉死（恰 2），不允许就地扩容。</p>
 *
 * <p>未挂载控件登记在 {@link #PENDING} 并给出理由，由 {@link #runtimeRegistryMatchesInventory}
 * 保证覆盖度不缩水（清单 28 = 挂载式 16 + 浮层族 3 + 待挂载 9）。</p>
 */
public class ControlFontRuntimeGuardTest {

    private static final int SCOPE_PX = 24;
    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 960;
    private static final String[] LABELS = {"One", "Two", "Three"};
    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");
    private static final Runnable NOOP = new Runnable() {
        public void run() {
        }
    };
    private static final List<Supplier<SceneNode>> PANELS = Arrays.<Supplier<SceneNode>>asList(
            () -> new SceneNode(), () -> new SceneNode(), () -> new SceneNode());

    /** 当前已接线的控件：任何时刻都必须绿（防回退）。 */
    private static final Set<String> WIRED = new LinkedHashSet<String>(Arrays.asList(
            "SceneLabel", "SceneButton", "SceneTextInput", "SceneTextArea", "SceneBreadcrumb",
            "SceneSegmented", "SceneTab", "SceneSimpleList", "SceneDataTable", "SceneKeyValueMap",
            "SceneObjectField"));

    private static final Map<String, InlineMounter> INLINE = inlineMounters();
    private static final Set<String> PENDING = pending();
    private static final Set<String> OVERLAY = overlayControls();

    private final List<Fixture> fixtures = new ArrayList<Fixture>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        for (Fixture fixture : fixtures) {
            fixture.harness.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    /** 防回退：已接线控件必须一直跟随句柄字号（当前预期绿）。 */
    @Test
    public void wiredControlsFollowScopeFontSize() {
        for (String name : WIRED) {
            InlineMounter mounter = INLINE.get(name);
            Assert.assertNotNull("已接线控件必须有挂载器：" + name, mounter);
            assertFollowsScope(name, mounter);
        }
    }

    /** P0-1：Checkbox 标签必须跟随控件字号（勾选标记按豁免单独处理）。 */
    @Test
    public void checkboxLabelFollowsScopeFontSize() {
        assertFollowsScope("SceneCheckbox", INLINE.get("SceneCheckbox"));
    }

    /** P0-1：Toggle 标签必须跟随控件字号。 */
    @Test
    public void toggleLabelFollowsScopeFontSize() {
        assertFollowsScope("SceneToggle", INLINE.get("SceneToggle"));
    }

    /** P0-1：RadioGroup 各选项标签必须跟随控件字号。 */
    @Test
    public void radioGroupLabelsFollowScopeFontSize() {
        assertFollowsScope("SceneRadioGroup", INLINE.get("SceneRadioGroup"));
    }

    /** P0-1：NavList 各导航项标签必须跟随控件字号。 */
    @Test
    public void navListItemsFollowScopeFontSize() {
        assertFollowsScope("SceneNavList", INLINE.get("SceneNavList"));
    }

    /** P0-1：Select 触发器值文本必须跟随控件字号（下拉项随展开路径另测）。 */
    @Test
    public void selectTriggerValueFollowsScopeFontSize() {
        assertFollowsScope("SceneSelect", INLINE.get("SceneSelect"));
    }

    /** 同一控件内不得出现两套字号：挂载式全清单的绘制字号集合必须单值。 */
    @Test
    public void everyInlineControlPaintsSingleResolvedFontSize() {
        StringBuilder mixed = new StringBuilder();
        for (Map.Entry<String, InlineMounter> entry : INLINE.entrySet()) {
            Fixture fixture = fixture();
            MountHandle handle = entry.getValue().mount(fixture.runtime, fixture.parent);
            fixture.frame();
            handle.fontSize(SCOPE_PX);
            fixture.frame();
            Set<Integer> sizes = paintedFontSizes(fixture);
            if (sizes.size() != 1 || !sizes.contains(Integer.valueOf(SCOPE_PX))) {
                mixed.append(entry.getKey()).append('=').append(sizes).append("; ");
            }
        }
        Assert.assertEquals("控件内文字必须与控件字号单值一致（混合字号即缺陷）",
                "", mixed.toString());
    }

    /** P0-1：Dialog 内标题/正文/按钮必须与浮层句柄字号一致（当前按钮为 16）。 */
    @Test
    public void dialogWordsAreConsistentWithHandleFontSize() {
        Fixture fixture = fixture();
        final ScenePortalHandle[] portal = new ScenePortalHandle[1];
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        fixture.runtime.mount(fixture.parent, new Supplier<SceneNode>() {
            public SceneNode get() {
                portal[0] = SceneDialog.create(fixture.runtime, new SceneDialog.Props(
                        visible, "标题", "正文",
                        Arrays.asList(SceneDialog.Button.of("取消", NOOP),
                                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, NOOP)),
                        NOOP));
                return new SceneNode();
            }
        });
        Assert.assertNotNull("Dialog portal 句柄必须创建成功", portal[0]);
        portal[0].fontSize(SCOPE_PX);
        fixture.frameOverlay();

        Set<Integer> sizes = paintedFontSizes(fixture);
        Assert.assertEquals("对话框内文字必须与句柄字号单值一致（标题/正文/按钮）",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(SCOPE_PX))), sizes);
        Assert.assertTrue("必须真的绘制到按钮文字（反向假绿守卫）",
                fixture.paintedTexts.contains("确定") && fixture.paintedTexts.contains("标题"));
    }

    /** 浮层族（第三轮成果）：ContextMenu 菜单项必须跟随句柄字号（当前预期绿）。 */
    @Test
    public void contextMenuItemsFollowHandleFontSize() {
        Fixture fixture = fixture();
        SceneContextMenu.Handle handle = SceneContextMenu.open(fixture.runtime, 10, 10,
                Arrays.asList(SceneContextMenu.MenuItem.of("复制", NOOP),
                        SceneContextMenu.MenuItem.of("粘贴", NOOP)));
        handle.fontSize(SCOPE_PX);
        fixture.frameOverlay();
        assertPaintedSizes("SceneContextMenu", fixture, SCOPE_PX);
    }

    /** 浮层族：Toast 必须跟随 runtime 级默认字号（当前预期绿）。 */
    @Test
    public void toastMessagesFollowDefaultFontSize() {
        Fixture fixture = fixture();
        SceneToast.defaultFontSize(fixture.runtime, SCOPE_PX);
        SceneToast.show(fixture.runtime, "通知", 5_000_000_000L);
        fixture.frameOverlay();
        assertPaintedSizes("SceneToast", fixture, SCOPE_PX);
    }

    /** 浮层族单值：Dialog/ContextMenu/Toast 三者的绘制字号集合都必须单值。 */
    @Test
    public void overlayFamilyPaintsSingleResolvedFontSize() {
        StringBuilder mixed = new StringBuilder();
        mixed.append(checkDialogFontSet());
        mixed.append(checkContextMenuFontSet());
        mixed.append(checkToastFontSet());
        Assert.assertEquals("浮层族内文字必须与浮层字号单值一致", "", mixed.toString());
    }

    /** 计数钉：挂载式 + 浮层族 + 待挂载必须恰好覆盖清单全集（防「漏登记 = 静默不测」）。 */
    @Test
    public void runtimeRegistryMatchesInventory() {
        Set<String> covered = new LinkedHashSet<String>(INLINE.keySet());
        covered.addAll(OVERLAY);
        covered.addAll(PENDING);
        Assert.assertEquals("运行期覆盖必须等于清单全集（多/少都红）",
                new LinkedHashSet<String>(ControlTextInventoryTest.TEXT_CONTROLS), covered);
        for (String name : INLINE.keySet()) {
            Assert.assertFalse("已挂载控件不得又出现在 PENDING：" + name, PENDING.contains(name));
        }
    }

    /** 反假绿：每个挂载式控件都必须真的产出 TEXT 命令（否则断言空转）。 */
    @Test
    public void everyInlineControlPaintsText() {
        StringBuilder silent = new StringBuilder();
        for (Map.Entry<String, InlineMounter> entry : INLINE.entrySet()) {
            Fixture fixture = fixture();
            entry.getValue().mount(fixture.runtime, fixture.parent);
            fixture.frame();
            if (fixture.paintedTexts.isEmpty()) {
                silent.append(entry.getKey()).append(' ');
            }
        }
        Assert.assertEquals("控件必须绘制出文字（无 TEXT 说明夹具失效或控件被裁）",
                "", silent.toString());
    }

    /** P1-1：runtime 级默认字号重复设置不得累积 effect（裁决 4 过渡态：单槽单绑定、幂等）。 */
    @Test
    public void toastDefaultFontSizeTenCallsDoNotAccumulateEffects() {
        Fixture fixture = fixture();
        Signal<Integer> size = Signal.create(Integer.valueOf(16));
        // 首次调用允许 Host 初始化的固有效应；只钉「重复调用是否累积」。
        SceneToast.defaultFontSize(fixture.runtime, size);
        fixture.runtime.flush();
        int afterFirst = ReactiveTestProbe.registeredEffectCount();
        for (int i = 0; i < 10; i++) {
            SceneToast.defaultFontSize(fixture.runtime, size);
        }
        fixture.runtime.flush();
        int delta = ReactiveTestProbe.registeredEffectCount() - afterFirst;
        Assert.assertTrue("重复设置默认字号不得累积 effect（10 次重复实测增量 " + delta + "）", delta <= 1);
        size.set(Integer.valueOf(SCOPE_PX));
        fixture.runtime.flush();
        SceneToast.show(fixture.runtime, "通知", 5_000_000_000L);
        fixture.frameOverlay();
        assertPaintedSizes("SceneToast", fixture, SCOPE_PX);
    }

    /** 装饰字面值保持独立：勾选标记/把手不被强拉成控件字号，但必须登记豁免。 */
    @Test
    public void decoratedGlyphsStayExemptAndRegistered() {
        Fixture fixture = fixture();
        MountHandle handle = INLINE.get("SceneCheckbox").mount(fixture.runtime, fixture.parent);
        fixture.frame();
        handle.fontSize(SCOPE_PX);
        fixture.frame();
        for (PaintCommand command : fixture.textCommands()) {
            if (ControlTextInventoryTest.EXEMPT_TEXTS.containsKey(command.getText())) {
                continue;
            }
            Assert.assertEquals("SceneCheckbox 的非豁免文字必须跟随字号：" + command.getText(),
                    SCOPE_PX, command.getTextStyle().getFontSize());
        }
        Assert.assertTrue("勾选标记必须被绘制（豁免不能变成空头支票）",
                fixture.paintedTexts.contains("\u2713"));
    }

    private void assertFollowsScope(String name, InlineMounter mounter) {
        Assert.assertNotNull(name + " 必须有挂载器", mounter);
        Fixture fixture = fixture();
        MountHandle handle = mounter.mount(fixture.runtime, fixture.parent);
        Assert.assertNotNull(name + " 的挂载器必须返回 MountHandle（字号入口）", handle);
        fixture.frame();
        handle.fontSize(SCOPE_PX);
        fixture.frame();
        assertPaintedSizes(name, fixture, SCOPE_PX);
    }

    private static void assertPaintedSizes(String name, Fixture fixture, int expected) {
        Set<Integer> sizes = paintedFontSizes(fixture);
        Assert.assertFalse(name + " 必须绘制出文字（反向假绿守卫）", sizes.isEmpty());
        Assert.assertEquals("控件 " + name + " 的绘制字号必须与控件字号一致（集合）",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(expected))), sizes);
    }

    private static Set<Integer> paintedFontSizes(Fixture fixture) {
        Set<Integer> sizes = new LinkedHashSet<Integer>();
        for (PaintCommand command : fixture.textCommands()) {
            if (ControlTextInventoryTest.EXEMPT_TEXTS.containsKey(command.getText())) {
                continue;
            }
            sizes.add(Integer.valueOf(command.getTextStyle().getFontSize()));
        }
        return sizes;
    }

    private String checkDialogFontSet() {
        Fixture fixture = fixture();
        final ScenePortalHandle[] portal = new ScenePortalHandle[1];
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        fixture.runtime.mount(fixture.parent, new Supplier<SceneNode>() {
            public SceneNode get() {
                portal[0] = SceneDialog.create(fixture.runtime, new SceneDialog.Props(
                        visible, "标题", "正文",
                        Arrays.asList(new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, NOOP)),
                        NOOP));
                return new SceneNode();
            }
        });
        portal[0].fontSize(SCOPE_PX);
        fixture.frameOverlay();
        Set<Integer> sizes = paintedFontSizes(fixture);
        return sizes.equals(new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(SCOPE_PX))))
                ? "" : "SceneDialog=" + sizes + "; ";
    }

    private String checkContextMenuFontSet() {
        Fixture fixture = fixture();
        SceneContextMenu.Handle handle = SceneContextMenu.open(fixture.runtime, 10, 10,
                Arrays.asList(SceneContextMenu.MenuItem.of("复制", NOOP)));
        handle.fontSize(SCOPE_PX);
        fixture.frameOverlay();
        Set<Integer> sizes = paintedFontSizes(fixture);
        return sizes.equals(new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(SCOPE_PX))))
                ? "" : "SceneContextMenu=" + sizes + "; ";
    }

    private String checkToastFontSet() {
        Fixture fixture = fixture();
        SceneToast.defaultFontSize(fixture.runtime, SCOPE_PX);
        SceneToast.show(fixture.runtime, "通知", 5_000_000_000L);
        fixture.frameOverlay();
        Set<Integer> sizes = paintedFontSizes(fixture);
        return sizes.equals(new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(SCOPE_PX))))
                ? "" : "SceneToast=" + sizes + "; ";
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixtures.add(fixture);
        return fixture;
    }

    /** 挂载式控件挂载器：负责建控件并把**控件自己的**挂载句柄返回（字号写在它上面）。 */
    interface InlineMounter {
        MountHandle mount(SceneRuntime rt, SceneNode parent);
    }

    private static Map<String, InlineMounter> inlineMounters() {
        Map<String, InlineMounter> map = new LinkedHashMap<String, InlineMounter>();
        map.put("SceneLabel", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneLabel.create(rt,
                        SceneLabel.Props.builder(Signal.create("Label")).build()));
            }
        });
        map.put("SceneButton", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneButton.create(rt, new SceneButton.Props(
                        Signal.create("OK"), Signal.create(Boolean.TRUE), NOOP)));
            }
        });
        map.put("SceneTextInput", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<String> value = Signal.create("abc");
                return rt.mount(parent, SceneTextInput.create(rt,
                        SceneTextInput.Props.builder(value).onChange(value::set).build()));
            }
        });
        map.put("SceneTextArea", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<String> value = Signal.create("abc");
                return rt.mount(parent, SceneTextArea.create(rt,
                        SceneTextArea.Props.builder(value).onChange(value::set).build()));
            }
        });
        map.put("SceneBreadcrumb", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneBreadcrumb.create(rt, new SceneBreadcrumb.Props(
                        Arrays.asList(new SceneBreadcrumb.Segment("/", "Home"),
                                new SceneBreadcrumb.Segment("/docs", "Docs")),
                        Signal.create(Boolean.TRUE), path -> { })));
            }
        });
        map.put("SceneSegmented", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneSegmented.create(rt, new SceneSegmented.Props(
                        Signal.create(Integer.valueOf(0)), Arrays.asList(LABELS),
                        Signal.create(Boolean.TRUE), index -> { }, null)));
            }
        });
        map.put("SceneTab", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneTab.create(rt, new SceneTab.Props(
                        Signal.create(Integer.valueOf(0)), Arrays.asList(LABELS), PANELS,
                        Signal.create(Boolean.TRUE), index -> { }, false, null)));
            }
        });
        map.put("SceneSimpleList", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                        Arrays.<SceneSimpleList.ListItem>asList(
                                new SceneSimpleList.ListItem("alpha"),
                                new SceneSimpleList.ListItem("beta")));
                return rt.mount(parent, SceneSimpleList.create(rt,
                        SceneSimpleList.Props.builder(items).label("列表").build()));
            }
        });
        map.put("SceneDataTable", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<List<SceneDataTable.Row>> rows = Signal.create(
                        Arrays.<SceneDataTable.Row>asList(
                                new SceneDataTable.Row(Arrays.asList("A", "B")),
                                new SceneDataTable.Row(Arrays.asList("C", "D"))));
                return rt.mount(parent, SceneDataTable.create(rt, SceneDataTable.Props.builder(rows)
                        .columns(Arrays.asList(SceneDataTable.Column.text("列一", 80),
                                SceneDataTable.Column.text("列二", 80)))
                        .rowHeight(28)
                        .viewportHeight(160)
                        .build()));
            }
        });
        map.put("SceneKeyValueMap", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<List<KeyValueRow>> rows = Signal.create(
                        Arrays.<KeyValueRow>asList(new KeyValueRow("name", "qz", ValueType.STRING)));
                return rt.mount(parent, SceneKeyValueMap.create(rt,
                        SceneKeyValueMap.Props.builder(rows).label("属性").build()));
            }
        });
        map.put("SceneObjectField", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                Signal<Map<String, Object>> value = Signal.create(new LinkedHashMap<String, Object>());
                return rt.mount(parent, SceneObjectField.create(rt,
                        SceneObjectField.Props.builder(value).label("对象").showScrollbar(true).build()));
            }
        });
        map.put("SceneCheckbox", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneCheckbox.create(rt, new SceneCheckbox.Props(
                        Signal.create(Boolean.TRUE), Signal.create("选项"),
                        Signal.create(Boolean.TRUE), next -> { })));
            }
        });
        map.put("SceneToggle", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneToggle.create(rt, new SceneToggle.Props(
                        Signal.create(Boolean.TRUE), Signal.create("开关"),
                        Signal.create(Boolean.TRUE), next -> { })));
            }
        });
        map.put("SceneRadioGroup", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneRadioGroup.create(rt, new SceneRadioGroup.Props(
                        Signal.create(Integer.valueOf(0)), OPTIONS,
                        Signal.create(Boolean.TRUE), next -> { })));
            }
        });
        map.put("SceneSelect", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneSelect.create(rt, new SceneSelect.Props(
                        Signal.create(Integer.valueOf(0)), OPTIONS,
                        Signal.create(Boolean.TRUE), next -> { })));
            }
        });
        map.put("SceneNavList", new InlineMounter() {
            public MountHandle mount(SceneRuntime rt, SceneNode parent) {
                return rt.mount(parent, SceneNavList.create(rt, new SceneNavList.Props(
                        Signal.create(Integer.valueOf(0)), OPTIONS, Signal.create(Boolean.TRUE),
                        next -> { }, Integer.valueOf(120))));
            }
        });
        return map;
    }

    /**
     * 待挂载控件（S0 登记，非「已覆盖」）：每一项都必须写明未挂载的具体原因与转正条件。
     *
     * <p>S3 前随对应 API/夹具落地而迁入 INLINE 或专属用例，迁出后本集合必须缩小，
     * 由 runtimeRegistryMatchesInventory 保证覆盖总数不缩水。</p>
     */
    private static Set<String> pending() {
        return new LinkedHashSet<String>(Arrays.asList(
                // 无字号入口（P1-1，attach 返回 void）：S2 补入口后迁入 INLINE
                "SceneTooltip",
                // 需 SearchPickerData / VisualAdapter / 宿主面板装配（跨包夹具），S3 随 search 收口补挂载
                "ScenePickerPanel", "SceneVirtualGrid", "SceneAutocomplete",
                "CategoryNavPane", "MemberGrid", "PickerInfoBar", "SearchResultList", "VariantChooser"));
    }

    private static Set<String> overlayControls() {
        return new LinkedHashSet<String>(Arrays.asList(
                "SceneDialog", "SceneContextMenu", "SceneToast"));
    }

    /** 只观察 super.paint 的产物：FramePipeline 仍执行真实 paint/replay（同 SceneControlFontSizeEntryTest）。 */
    private static final class CapturingPaintEngine extends ScenePaintEngine {

        List<PaintCommand> commands = new ArrayList<PaintCommand>();

        CapturingPaintEngine(SceneTextMeasurer measurer) {
            super(measurer);
        }

        @Override
        public PaintResult paint(SceneNode root) {
            PaintResult result = super.paint(root);
            commands = new ArrayList<PaintCommand>(result.getPlan().getCommands());
            return result;
        }
    }

    /** 度量随字号变化：字号没传到绘制上时断言必须失败（同 SceneControlFontSizeEntryTest:379-395）。 */
    static final class FontMeasurer implements SceneTextMeasurer {

        public int measureWidth(String text, int fontSizePx) {
            return text == null ? 0 : text.codePointCount(0, text.length()) * fontSizePx / 2;
        }

        public int lineHeight(int fontSizePx) {
            return fontSizePx;
        }

        public int epoch() {
            return 0;
        }
    }

    private static final class Fixture {

        final FontMeasurer measurer = new FontMeasurer();
        final SceneInteractionHarness harness;
        final SceneRuntime runtime;
        final SceneNode scene = SceneNode.column();
        final SceneNode parent = SceneNode.column();
        final SceneLayoutEngine layout;
        final CapturingPaintEngine paint;
        final SceneFramePipeline pipeline;
        final MockPlatformInputSource input;
        final int width = CANVAS_WIDTH;
        final int height = CANVAS_HEIGHT;
        final List<PaintCommand> commands = new ArrayList<PaintCommand>();
        final List<String> paintedTexts = new ArrayList<String>();

        Fixture() {
            harness = SceneInteractionHarness.create(measurer);
            runtime = harness.getRuntime();
            scene.appendChild(parent);
            harness.mountRoot(scene, width, height);
            layout = new SceneLayoutEngine(measurer);
            paint = new CapturingPaintEngine(measurer);
            input = new MockPlatformInputSource(width, height);
            pipeline = new SceneFramePipeline(runtime, layout, paint, new ScenePaintReplayer(),
                    measurer, input);
        }

        /** 跑两帧真实管线：首帧物化信号/懒建，次帧取稳定产物。 */
        void frame() {
            captureScene();
            captureScene();
        }

        /**
         * 场景帧 + 浮层帧：浮层根由 overlay host 提供，独立布局后逐个 paint。
         *
         * <p>Toast/Dialog 的浮层根是零高的容器（内容在自己的卡片子树上），只 paint 根取不到
         * 文字命令；故再对每个「文本非空」的子孙节点逐个 paint（同 SceneToastTest 的
         * cached-fragment 取证口径，SceneToastTest.ownCommands），把 TEXT 命令取全。</p>
         */
        void frameOverlay() {
            frame();
            List<SceneOverlayHost.Entry> entries = runtime.getOverlayHost().bottomFirst();
            for (SceneOverlayHost.Entry entry : entries) {
                layout.layout(entry.getRoot(), new Constraints(width, height));
            }
            commands.clear();
            for (SceneOverlayHost.Entry entry : entries) {
                paint.paint(entry.getRoot());
                commands.addAll(paint.commands);
                for (SceneNode text : textLeaves(entry.getRoot())) {
                    paint.paint(text);
                    commands.addAll(paint.commands);
                }
            }
            refreshPaintedTexts();
        }

        private static List<SceneNode> textLeaves(SceneNode root) {
            List<SceneNode> found = new ArrayList<SceneNode>();
            collectTexts(root, found);
            return found;
        }

        private static void collectTexts(SceneNode node, List<SceneNode> out) {
            String text = node.getText();
            if (text != null && !text.isEmpty()) {
                out.add(node);
            }
            for (SceneNode child : node.__getChildren()) {
                collectTexts(child, out);
            }
        }

        private void captureScene() {
            pipeline.run(scene, width, height, new RecordingRenderBackend(), 0, 0, 1000L);
            commands.clear();
            commands.addAll(paint.commands);
            refreshPaintedTexts();
        }

        List<PaintCommand> textCommands() {
            List<PaintCommand> texts = new ArrayList<PaintCommand>();
            for (PaintCommand command : commands) {
                if (command.getType() == PaintCommandType.TEXT && !command.getText().isEmpty()) {
                    texts.add(command);
                }
            }
            return texts;
        }

        private void refreshPaintedTexts() {
            paintedTexts.clear();
            for (PaintCommand command : textCommands()) {
                paintedTexts.add(command.getText());
            }
        }
    }
}
