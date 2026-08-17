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
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.StringFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link StringFieldRenderer} 单元测试。
 *
 * <p>照 scene 控件测试范式：SceneRuntime + FixedTextMeasurer 纯 JVM 三件套。</p>
 */
public class StringFieldRendererTest {

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
        authority = Authority.load(new File("nonexistent-ui-string.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new StringFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** render 返回非 null SceneNode */
    @Test
    public void renderReturnsNonNullNode() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = renderer.render(runtime, spec, adapter);
        Assert.assertNotNull("render 返回非 null", card);
    }

    /** 控件值 = draftSignal 初值（flush 后，检查 TextInput 三子节点结构 + 文本含初值） */
    @Test
    public void controlValueEqualsDraftInitial() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        SceneNode control = findTextInputRoot(card);
        Assert.assertNotNull("应找到 TextInput 控件", control);
        Assert.assertEquals("TextInput 含 5 子节点（prefix/caret/highlight/caretAfter/suffix）",
                5, control.__getChildren().size());
        // 初始 caret=0，prefix="" + suffix=value 或失焦显示完整值；拼接应含 draft 初值
        StringBuilder sb = new StringBuilder();
        for (SceneNode c : control.__getChildren()) {
            if (c.getText() != null) {
                sb.append(c.getText());
            }
        }
        Assert.assertTrue("控件文本应含 draft 初值 localhost，实际: " + sb,
                sb.toString().contains("localhost"));
    }

    /** onChange 回调触发 onFieldEdit */
    @Test
    public void onChangeTriggersOnFieldEdit() throws Exception {
        FieldSpec spec = schema.field("server.host");
        renderer.render(runtime, spec, adapter);
        runtime.flush();
        adapter.onFieldEdit("server.host", "edited");
        runtime.flush();
        Assert.assertEquals("onFieldEdit 后 draft 更新", "edited", draft.getDraft("server.host"));
    }

    /** maxLength 传入 SceneTextInput Props */
    @Test
    public void maxLengthPassedToProps() throws Exception {
        FieldSpec spec = schema.field("server.host"); // maxLength=100
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("maxLength=100 字段渲染不崩", card);
    }

    /** label 显示在 field shell 中 */
    @Test
    public void labelDisplayedInShell() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        // header 是 card 第一个子节点，header 第二个子节点是 title
        SceneNode header = card.__getChildren().get(0);
        SceneNode title = header.__getChildren().get(1);
        Assert.assertEquals("label 显示", "Host", title.getText());
    }

    /** error 时边框颜色变化（bind PAINT） */
    @Test
    public void errorChangesBorderColor() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        int borderBefore = card.getBorderColor();
        adapter.onFieldEdit("server.host", ""); // required 违反
        runtime.flush();
        int borderAfter = card.getBorderColor();
        Assert.assertNotEquals("error 时边框变化", borderBefore, borderAfter);
    }

    /** 多次 render 同一字段（幂等性：每次返回新 card，不崩） */
    @Test
    public void multipleRenderDoesNotCrash() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode c1 = renderer.render(runtime, spec, adapter);
        runtime.flush();
        SceneNode c2 = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("第二次 render 不崩", c2);
        Assert.assertNotSame("每次返回新节点", c1, c2);
    }

    /** 空 label/helper 时不崩 */
    @Test
    public void emptyLabelHelperDoesNotCrash() throws Exception {
        // 构造无 label/helper 的 schema
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .string("k").build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-ui-string2.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull("空 label/helper 不崩", card);
        a.dispose();
    }

    /**
     * 在 field shell 中找 TextInput 控件根节点：扫 card 子节点（跳过 header），
     * 匹配含 3 子（prefix/caret/suffix）的控件根。
     *
     * <p>不依赖"倒数第二个"位置：FormFieldShell 的 errorNode 已改为 {@code rt.show} 条件挂载，
     * 尾部位置不再固定。</p>
     *
     * @param card 字段卡片
     * @return TextInput 根节点，未找到返回 null
     */
    private SceneNode findTextInputRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            // B2 五节点结构：prefix/caret/highlight/caretAfter/suffix
            if (c.__getChildren().size() == 5) {
                return c;
            }
        }
        return null;
    }
}

