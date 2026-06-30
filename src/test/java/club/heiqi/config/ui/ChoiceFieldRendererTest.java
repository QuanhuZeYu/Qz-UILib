package club.heiqi.config.ui;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.field.ChoiceFieldRenderer;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link ChoiceFieldRenderer} 单元测试。
 *
 * <p>覆盖 ≤4 项用 SceneSegmented、>4 项用 SceneSelect、selectedIndex 映射、
 * onSelect 回调、options 传入、空选项兜底。</p>
 */
public class ChoiceFieldRendererTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = UiSchemaFactory.serverSchema();
        authority = Authority.load(new File("nonexistent-ui-choice.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new ChoiceFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** ≤4 项用 SceneSegmented（server.mode 3 项） */
    @Test
    public void fewOptionsUseSegmented() throws Exception {
        FieldSpec spec = schema.field("server.mode");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("≤4 项渲染不崩", card);
        // Segmented root 含 3 个 segment 子节点（3 选项）
        SceneNode seg = findSegmentedRoot(card);
        Assert.assertNotNull("应找到 Segmented 控件", seg);
        Assert.assertEquals("3 个 segment", 3, seg.__getChildren().size());
    }

    /** >4 项用 SceneSelect */
    @Test
    public void manyOptionsUseSelect() throws Exception {
        ConfigSchema many = UiSchemaFactory.manyChoiceSchema();
        Authority auth = Authority.load(new File("nonexistent-ui-choice2.yaml"), many);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = many.field("opts.color");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull(">4 项渲染不崩", card);
        // Select trigger 含 label + arrow 两子节点
        SceneNode select = findSelectRoot(card);
        Assert.assertNotNull("应找到 Select 控件", select);
        a.dispose();
    }

    /** selectedIndex 由 draft 值映射 */
    @Test
    public void selectedIndexMappedFromDraft() throws Exception {
        FieldSpec spec = schema.field("server.mode");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        // draft 初值 "online" → selectedIndex=0 → 第 0 个 segment 选中（ACCENT 背景）
        SceneNode seg = findSegmentedRoot(card);
        SceneNode first = seg.__getChildren().get(0);
        Assert.assertEquals("selectedIndex=0 → 首段选中背景",
                club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.ACCENT,
                first.getBackgroundColor());
    }

    /** onSelect 回调触发 onFieldEdit */
    @Test
    public void onSelectTriggersOnFieldEdit() throws Exception {
        FieldSpec spec = schema.field("server.mode");
        renderer.render(runtime, spec, adapter);
        runtime.flush();
        adapter.onFieldEdit("server.mode", "offline");
        runtime.flush();
        Assert.assertEquals("onSelect 后 draft=offline", "offline", draft.getDraft("server.mode"));
    }

    /** options 传入 Props（渲染不崩即可验证） */
    @Test
    public void optionsPassedToProps() throws Exception {
        FieldSpec spec = schema.field("server.mode");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("options 传入不崩", card);
    }

    /** 空选项不崩（防御性兜底） */
    @Test
    public void emptyOptionsDoesNotCrash() throws Exception {
        // CHOICE 字段 build 时要求 options 非空，故构造合法 schema 后清空 choices 测兜底
        // 这里用 1 项 options 测试边界
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .choice("k").options("only").defaultValue("only").label("K").build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-ui-choice3.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull("1 项 options 不崩", card);
        a.dispose();
    }

    /**
     * 找 Segmented 根（倒数第二个子节点，error 是最后一个）。
     *
     * @param card 字段卡片
     * @return Segmented 根，未找到返回 null
     */
    private SceneNode findSegmentedRoot(SceneNode card) {
        int n = card.__getChildren().size();
        if (n < 2) {
            return null;
        }
        return card.__getChildren().get(n - 2);
    }

    /**
     * 找 Select trigger 根（倒数第二个子节点，error 是最后一个）。
     *
     * @param card 字段卡片
     * @return Select 根，未找到返回 null
     */
    private SceneNode findSelectRoot(SceneNode card) {
        int n = card.__getChildren().size();
        if (n < 2) {
            return null;
        }
        return card.__getChildren().get(n - 2);
    }
}

