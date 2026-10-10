package club.heiqi.uilib.internal.devtools;

import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;

/** MC 验收入口 /qzcore；只声明业务树，运行与宿主接线由正式入口管理。 */
public final class CoreLoopScreen extends MinecraftSceneScreen {
    private static final ReactiveDomain EDIT_DOMAIN=ReactiveDomain.direct();
    private static club.heiqi.uilib.ui.scene.edit.SceneEditState editState;
    private static Signal<String> acceptedValue;
    private static club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission delayed;
    private static club.heiqi.uilib.ui.scene.edit.SceneEditState multilineState;
    private static Signal<String> acceptedDocument;
    private static club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission delayedDocument;
    public CoreLoopScreen() { super(EDIT_DOMAIN,CoreLoopScreen::buildPage); }

    private static void buildPage(ScenePageContext ctx) {
        ReactiveDomain domain = ctx.domain();
        SceneRuntime runtime = ctx.runtime();
        Owner business = ctx.scope();
        List<Integer> initialRows = new ArrayList<>();
        for (int item = 1; item <= 60; item++) initialRows.add(item);
        Signal<List<Integer>> rows = domain.signal(initialRows);
        Signal<Integer> count = domain.signal(0), fontSize = domain.signal(20);
        Signal<Boolean> expanded = domain.signal(false), modal = domain.signal(false);
        Signal<Boolean> light = domain.signal(false);
        if(editState==null){editState=new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain,"12");acceptedValue=domain.signal("12");}
        Signal<Boolean> readOnly=domain.signal(false),editEnabled=domain.signal(true);
        if(multilineState==null){String initial="多行正文：中文与 Emoji 🙂\n第二行：Enter 换行，Ctrl+Enter 提交\n第三行：上下移动与跨行选择\n第四行：滚轮查看，缩窗软换行\n第五行：末尾空行也可输入\n";multilineState=new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain,initial,club.heiqi.uilib.ui.scene.edit.SceneEditState.Mode.MULTI_LINE);acceptedDocument=domain.signal(initial);}
        Signal<Boolean> areaReadOnly=domain.signal(false),areaEnabled=domain.signal(true);
        java.util.function.Consumer<club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission> submitDocument=request->{
            if(request.text.contains("拒绝")){multilineState.reject(request,"正文包含拒绝，草稿与选区保留");}
            else if(multilineState.accept(request,request.text))acceptedDocument.set(request.text);
        };
        java.util.function.Consumer<club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission> submit=request->{
            try{String normalized=String.valueOf(Integer.parseInt(request.text.trim()));if(editState.accept(request,normalized))acceptedValue.set(normalized);}
            catch(NumberFormatException invalid){editState.reject(request,"请输入完整整数，草稿可暂时为负号或空串");}
        };
        Computed<SceneTheme> theme = domain.computed(business, () -> {
            SceneTheme base = light.get() ? SceneTheme.light() : SceneTheme.dark();
            return base.withTextStyle(new TextStyle("default", fontSize.get(), base.textStyle.color));
        });
        Computed<String> heading = domain.computed(business, () -> (expanded.get() ? "核心闭环：标题加长时自然换行，正文自动获得剩余空间。累计业务操作：" : "核心闭环：") + count.get());
        SceneNode title = SceneLabel.create(ctx, heading, theme).size(Size.fill(1), Size.natural());
        SceneNode controls = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2);
        controls.add(SceneButton.create(ctx, () -> "添加", theme, () -> { List<Integer> next = new ArrayList<>(rows.get()); next.add(next.isEmpty() ? 1 : next.get(next.size() - 1) + 1); rows.set(next); count.set(count.get() + 1); }));
        controls.add(SceneButton.create(ctx, () -> "标题", theme, () -> expanded.set(!expanded.get())));
        controls.add(SceneButton.create(ctx, () -> "语言", theme, () -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new CoreLocalizationPage())));
        controls.add(SceneButton.create(ctx, () -> "富文本", theme, () -> net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new CoreRichTextPage())));
        controls.add(SceneButton.create(ctx, () -> "字号", theme, () -> fontSize.set(fontSize.get() == 20 ? 28 : 20)));
        controls.add(SceneButton.create(ctx, () -> "模态", theme, () -> modal.set(true)));
        controls.add(SceneButton.create(ctx, () -> light.get() ? "深色" : "浅色", theme, () -> light.set(!light.get())));
        SceneNode body = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).gap(2)
            .scrollStep(32)
            .bind(NodeBinding.repeat(rows, value -> value, value -> {
                return SceneButton.create(ctx, () -> "条目 " + value.get() + "：Tab导航自动显露，点击累计业务操作。", theme, () -> count.set(count.get() + 1))
                    .bind(NodeBinding.value(theme, (node, colors) -> node.background(colors.panel))).size(Size.fill(1), Size.natural());
            }));
        Signal<Boolean> focusVisible = domain.signal(true), focusEnabled = domain.signal(true);
        Signal<Boolean> focusAEnabled = domain.signal(true), innerModal = domain.signal(false), restoreA = domain.signal(false);
        // 明确的焦点操作不改变普通按钮策略；目标与token始终由runtime管理。
        SceneNode focusA = SceneButton.create(ctx, () -> "选择A", theme, () -> { });
        SceneNode focusB = SceneButton.create(ctx, () -> "选择B", theme, () -> { });
        SceneNode focusM = SceneButton.create(ctx, () -> "选择M", theme, () -> { });
        SceneNode focusN = SceneButton.create(ctx, () -> "选择N", theme, () -> { });
        focusM.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusM)));
        focusN.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusN)));
        focusA.bind(NodeBinding.value(focusAEnabled, SceneNode::enabled));
        focusA.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusA)));
        focusB.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusB)));
        SceneNode focusTargets = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2)
            .bind(NodeBinding.value(focusVisible, SceneNode::visible)).bind(NodeBinding.value(focusEnabled, SceneNode::enabled))
            .add(focusA).add(focusB);
        Computed<String> focusStatus = domain.computed(business, () -> {
            SceneNode target = runtime.focusRouter().focused().get();
            return "焦点：" + (target == focusA ? "A" : target == focusB ? "B" : target == focusM ? "M" : target == focusN ? "N" : "无")
                + "；目标" + (focusVisible.get() ? "显示" : "隐藏") + "/" + (focusEnabled.get() ? "启用" : "禁用")
                + "；A" + (focusAEnabled.get() ? "启用" : "禁用") + "；恢复：" + (restoreA.get() ? "指定A" : "原焦点");
        });
        SceneNode focusActions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2)
            .add(SceneButton.create(ctx, () -> "清除焦点", theme, () -> runtime.focusRouter().clear()))
            .add(SceneButton.create(ctx, () -> "显隐目标", theme, () -> focusVisible.set(!focusVisible.get())))
            .add(SceneButton.create(ctx, () -> "启禁目标", theme, () -> focusEnabled.set(!focusEnabled.get())))
            .add(SceneButton.create(ctx, () -> "恢复策略", theme, () -> restoreA.set(!restoreA.get())));
        SceneNode panel = new SceneNode().initialFocus(focusA)
            .bind(NodeBinding.value(restoreA, (node,useA) -> node.restoreFocus(useA ? focusA : null)))
            .size(Size.fill(1), Size.fill(1)).padding(8).gap(4).bind(NodeBinding.value(theme, (node,value) -> node.background(value.panel)))
            .add(title).add(controls).add(SceneLabel.create(ctx, focusStatus, theme)).add(focusTargets).add(focusActions);
        SceneNode longButton = SceneButton.create(ctx, () -> "限高长文案：运行时浅色与深色主题切换后，这段文字应保持自然换行；高度不足只显示完整行及省略，悬停或键盘焦点查看全文。", theme, () -> count.set(count.get() + 1))
            .size(Size.fill(1), Size.fixed(56));
        SceneNode policyButton = SceneButton.create(ctx, () -> "显式单行省略策略：这一行长文案不会换行", theme, () -> count.set(count.get() + 1), SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
        SceneNode disabledButton = SceneButton.create(ctx, () -> "禁用按钮：不能点击、Tab聚焦或键盘激活", theme, () -> count.set(count.get() + 100)).enabled(false);
        panel.add(longButton).add(policyButton).add(disabledButton);
        SceneNode editPanel=new SceneNode().size(Size.fill(1),Size.natural()).gap(2);
        editPanel.add(SceneLabel.create(ctx,()->"整数业务值："+acceptedValue.get()+"；编辑会话："+editState.snapshot().get().active+"；错误："+editState.snapshot().get().error,theme));
        editPanel.add(club.heiqi.uilib.ui.scene.control.SceneTextField.create(ctx,editState,theme,readOnly,submit).bind(NodeBinding.value(editEnabled,SceneNode::enabled)));
        SceneNode editActions=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(2);
        editActions.add(SceneButton.create(ctx,()->"确认整数",theme,()->submit.accept(editState.request(true))));
        editActions.add(SceneButton.create(ctx,()->"暂存请求",theme,()->delayed=editState.request(true)));
        editActions.add(SceneButton.create(ctx,()->"完成旧请求",theme,()->{if(delayed!=null){if(editState.accept(delayed,delayed.text))acceptedValue.set(delayed.text);delayed=null;}}));
        editActions.add(SceneButton.create(ctx,()->readOnly.get()?"可编辑":"只读",theme,()->readOnly.set(!readOnly.get())));
        editActions.add(SceneButton.create(ctx,()->editEnabled.get()?"禁用字段":"启用字段",theme,()->editEnabled.set(!editEnabled.get())));
        editPanel.add(editActions).add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(2)
            .add(SceneButton.create(ctx,()->"重置为12",theme,()->{editState.reset("12");acceptedValue.set("12");delayed=null;}))
            .add(SceneButton.create(ctx,()->"外部值99",theme,()->{acceptedValue.set("99");editState.synchronizeExternal("99");}))
            .add(SceneButton.create(ctx,()->"取消草稿",theme,editState::cancel)));
        SceneNode areaPanel=new SceneNode().size(Size.fill(1),Size.natural()).gap(2);
        areaPanel.add(SceneLabel.create(ctx,()->"多行正文：Enter 换行；Ctrl+Enter 或按钮提交；Home/End 为视觉行；Ctrl+Home/End 为全文",theme));
        areaPanel.add(club.heiqi.uilib.ui.scene.control.SceneTextArea.create(ctx,multilineState,theme,areaReadOnly,submitDocument).size(Size.fill(1),Size.fixed(160)).bind(NodeBinding.value(areaEnabled,SceneNode::enabled)));
        areaPanel.add(SceneLabel.create(ctx,()->"已接受正文："+acceptedDocument.get().replace("\n"," ⏎ ")+"；错误："+multilineState.snapshot().get().error,theme));
        areaPanel.add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(2)
            .add(SceneButton.create(ctx,()->"提交正文",theme,()->{if(areaEnabled.get()&&!areaReadOnly.get())submitDocument.accept(multilineState.request(true));}))
            .add(SceneButton.create(ctx,()->"暂存正文请求",theme,()->{if(areaEnabled.get()&&!areaReadOnly.get())delayedDocument=multilineState.request(true);}))
            .add(SceneButton.create(ctx,()->"完成旧正文请求",theme,()->{if(delayedDocument!=null){if(multilineState.accept(delayedDocument,delayedDocument.text))acceptedDocument.set(delayedDocument.text);delayedDocument=null;}}))
            .add(SceneButton.create(ctx,()->areaReadOnly.get()?"正文可编辑":"正文只读",theme,()->areaReadOnly.set(!areaReadOnly.get())))
            .add(SceneButton.create(ctx,()->areaEnabled.get()?"禁用正文":"启用正文",theme,()->areaEnabled.set(!areaEnabled.get()))));
        areaPanel.add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(2)
            .add(SceneButton.create(ctx,()->"清空正文",theme,()->{if(areaEnabled.get()&&!areaReadOnly.get()){multilineState.reset("");acceptedDocument.set("");delayedDocument=null;}}))
            .add(SceneButton.create(ctx,()->"外部正文",theme,()->{String next="外部更新正文\n仅在空闲时同步\n";acceptedDocument.set(next);multilineState.synchronizeExternal(next);}))
            .add(SceneButton.create(ctx,()->"恢复样例",theme,()->{if(areaEnabled.get()&&!areaReadOnly.get()){String next="中文🙂与 é\n第二行：ABCDEFGHIJKLMN 软换行长文 ABCDEFGHIJKLMN\n第三行\n第四行\n第五行\n第六行\n第七行\n第八行\n";multilineState.reset(next);acceptedDocument.set(next);delayedDocument=null;}})));
        // 字段验收放在滚动正文之前，字号/窗口变化由已有布局处理。
        SceneNode scrollContent=new SceneNode().size(Size.fill(1),Size.natural()).gap(4).add(editPanel).add(areaPanel).add(CoreImagePage.create(ctx,theme)).add(body.size(Size.fill(1),Size.fixed(220)));
        // 页内自然内容共用外层滚动，小窗口/大字号不把字段正文压成零高度。
        SceneNode editorPage=new SceneNode().initialFocus(focusA).size(Size.fill(1),Size.fill(1)).scrollable(true).padding(8).gap(4)
            .bind(NodeBinding.value(theme,(node,value)->node.background(value.panel)));
        panel.size(Size.fill(1),Size.natural());
        editorPage.add(panel).add(scrollContent);
        ctx.mount(editorPage);
        SceneNode inner = new SceneNode().size(Size.fill(1), Size.natural()).padding(6).gap(3).bind(NodeBinding.value(theme, (node,value) -> node.background(value.panel))).modal(true).initialFocus(focusN).onEscape(() -> { innerModal.set(false); return true; })
            .bind(NodeBinding.value(innerModal, SceneNode::visible))
            .add(SceneLabel.create(ctx, focusStatus, theme)).add(focusN)
            .add(SceneButton.create(ctx, () -> "返回外层M", theme, () -> innerModal.set(false)))
            .add(SceneButton.create(ctx, () -> "关闭全部", theme, () -> { innerModal.set(false); modal.set(false); }));
        SceneNode dialog = new SceneNode().size(Size.fixed(360), Size.natural()).padding(10).gap(5).bind(NodeBinding.value(theme, (node,value) -> node.background(value.panel))).modal(true).initialFocus(focusM).onEscape(() -> { innerModal.set(false); modal.set(false); return true; })
            .bind(NodeBinding.value(modal, SceneNode::visible))
            .add(SceneLabel.create(ctx, focusStatus, theme)).add(focusM)
            .add(SceneButton.create(ctx, () -> "内层模态", theme, () -> innerModal.set(true)))
            .add(SceneButton.create(ctx, () -> "启禁A", theme, () -> focusAEnabled.set(!focusAEnabled.get())))
            .add(SceneButton.create(ctx, () -> "关闭", theme, () -> { innerModal.set(false); modal.set(false); }))
            .add(inner);
        ctx.mount(dialog);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
