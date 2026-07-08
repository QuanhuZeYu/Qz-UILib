package club.heiqi.config.ui.field;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link FieldShellBinder} 单元测试：薄 helper 装配 FormFieldShell 外壳的契约。
 *
 * <p>验证 binder 正确把 spec/adapter 拆解为 FormFieldShell 需要的参数：
 * 标题回退（label → path）、helper 透传、error/dirty signal 桥接、controlFn 注入、控件高度入参。</p>
 */
public class FieldShellBinderTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FormTheme theme;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("server")
                    .string("host").label("Host").helper("server host").build()
                .endSection()
                .build();
        authority = Authority.load(new File("nonexistent-binder.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        theme = ConfigTheme.asFormTheme();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 默认重载（inputHeight）：返回非 null card，标题显示 label。 */
    @Test
    public void defaultOverloadReturnsCardWithLabel() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, adapter,
                SceneNode::new, theme);
        runtime.flush();
        Assert.assertNotNull("返回非 null card", card);
        SceneNode header = card.__getChildren().get(0);
        SceneNode title = header.__getChildren().get(1);
        Assert.assertEquals("title 显示 label 'Host'", "Host", title.getText());
    }

    /** 显式 controlHeight 重载：行为与默认一致，仅控件根 preferredHeight 不同。 */
    @Test
    public void explicitControlHeightOverloadReturnsCard() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, adapter,
                SceneNode::new, theme, theme.listHeight());
        runtime.flush();
        Assert.assertNotNull("返回非 null card", card);
        Assert.assertEquals("title 仍显示 label", "Host",
                card.__getChildren().get(0).__getChildren().get(1).getText());
    }

    /** 空输入时显示：label=null 时回退 path，helper=空时不崩。 */
    @Test
    public void labelNullFallsBackToPath() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .string("k").build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-binder2.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = FieldShellBinder.build(runtime, spec, a, SceneNode::new, theme);
        runtime.flush();
        Assert.assertNotNull("空 schema 渲染不崩", card);
        Assert.assertEquals("label null 回退 path", "a.k",
                card.__getChildren().get(0).__getChildren().get(1).getText());
        a.dispose();
    }

    /** error signal 桥接：触发 required 违反后 card 边框色变化。 */
    @Test
    public void errorSignalBridgedToBorderColor() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("server")
                    .string("host").defaultValue("v").required().maxLength(2).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-binder3.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, a, SceneNode::new, theme);
        runtime.flush();
        int borderBefore = card.getBorderColor();
        a.onFieldEdit("server.host", "abc"); // maxLength 违反
        runtime.flush();
        int borderAfter = card.getBorderColor();
        Assert.assertNotEquals("error 时边框色变化", borderBefore, borderAfter);
        a.dispose();
    }
}