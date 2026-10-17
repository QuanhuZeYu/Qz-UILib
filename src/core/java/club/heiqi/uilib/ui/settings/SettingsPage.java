package club.heiqi.uilib.ui.settings;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 正式配置工作区；仅声明自有scene，配置权威与资源事实由SettingsConfiguration接入。
 * 视觉与操作合同见specs/config/2026-10-12-uilib-settings-page-design.md。 */
public final class SettingsPage implements ScenePage {
    private final SettingsConfiguration configuration;
    private final Runnable leave;
    private View view;

    public SettingsPage(SettingsConfiguration configuration, Runnable leave) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.leave = Objects.requireNonNull(leave, "leave");
    }
    @Override public void build(ScenePageContext context) { context.mountOwned(create(context)); }
    /** 与宿主入口共用同一声明，允许场景工作台组合本页；归属与释放仍由上下文维护。 */
    public SceneNode create(ScenePageContext context) {
        if(context.domain()!=configuration.domain())throw new IllegalArgumentException("配置页面与配置必须同域");
        if(view!=null)throw new IllegalStateException("配置页面已经打开");
        view=new View(context);
        context.own(() -> view=null);
        return view.build();
    }
    /** 原生窗口关闭与返回同走本页确认；回调只退出，不隐含保存或撤销已应用值。 */
    public void requestClose() { requestClose(leave); }
    /** 组合宿主在同一确认完成后退出窗口；页面返回沿构造时的离开动作。 */
    public void requestClose(Runnable afterConfirmed) {
        Objects.requireNonNull(afterConfirmed,"afterConfirmed");
        configuration.domain().execute(() -> {if(view!=null)view.closeRequested(afterConfirmed);});
    }
    /** 宿主快捷键与文件卡片共享显式加载确认。 */
    public void requestReload() { configuration.domain().execute(() -> { if(view!=null)view.reloadRequested(); }); }

    private enum Section {
        APPEARANCE("外观","设置界面主题、字体来源与文字预览。"),
        MINECRAFT("Minecraft 接入","管理游戏原版文字与 UILib 的接入。"),
        FILE("配置文件","查看配置来源，保存或重新加载当前设置。");
        final String title,description;
        Section(String title,String description){this.title=title;this.description=description;}
    }
    private enum Dialog { NONE, FONTS, ADD, RELOAD, CLOSE }
    private final class View {
        final ScenePageContext ctx;
        final SettingsModel model;
        final Signal<Boolean> fileSource,fontPicking;
        final Signal<Section> section;
        final Signal<Dialog> dialog;
        final Signal<String> selectedBuiltin;
        final ReadableSignal<SettingsStyle.Palette> palette;
        final SceneEditState path, sample;
        final ReadableSignal<Boolean> compact;
        final Signal<Boolean> navigationOpen;
        Runnable confirmedLeave;
        final String defaultSample="每一种语言，都值得被认真呈现。\nGood typography makes a difference.";
        View(ScenePageContext ctx) {
            this.ctx=ctx;model=new SettingsModel(configuration,ctx.scope());
            fileSource=ctx.domain().signal(false);fontPicking=ctx.domain().signal(false);
            compact=ctx.domain().computed(ctx.scope(),() -> ctx.viewportWidth().get()<SettingsLayout.COMPACT_WIDTH);
            navigationOpen=ctx.domain().signal(false);
            ctx.domain().effect(ctx.scope(),() -> {compact.get();navigationOpen.set(false);});
            section=ctx.domain().signal(Section.APPEARANCE);dialog=ctx.domain().signal(Dialog.NONE);
            List<String> builtins=configuration.builtinCandidates();
            selectedBuiltin=ctx.domain().signal(builtins.isEmpty()?"":builtins.get(0));
            palette=ctx.domain().computed(ctx.scope(),() -> new SettingsStyle.Palette(model.theme().get().isDark()));
            path=new SceneEditState(ctx.domain(),"");
            sample=new SceneEditState(ctx.domain(),defaultSample,SceneEditState.Mode.MULTI_LINE);
        }
        SceneNode text(String value,int em,boolean muted) { return text(() -> value,em,muted); }
        SceneNode text(ReadableSignal<String> value,int em,boolean muted) { return SettingsStyle.label(ctx,value,palette,em,muted); }
        SceneNode pathText(ReadableSignal<String> value,int em,boolean muted) { return SettingsStyle.pathLabel(ctx,value,palette,em,muted); }
        SceneNode button(String value,Runnable action) { return button(() -> value,action,false,false); }
        SceneNode button(ReadableSignal<String> value,Runnable action,boolean primary,boolean ghost) { return SettingsStyle.button(ctx,value,palette,action,primary,ghost).view; }
        SceneNode actions(SceneNode... nodes) { SceneNode result=SettingsStyle.column(0).control(new SettingsLayout.WrapRow(8));for(SceneNode n:nodes)result.add(n);return result; }
        SceneNode enabledButton(String value,Runnable action,ReadableSignal<Boolean> enabled) {
            return enabledButton(() -> value,action,false,false,enabled);
        }
        SceneNode enabledButton(ReadableSignal<String> value,Runnable action,boolean primary,boolean ghost,ReadableSignal<Boolean> enabled) {
            SettingsStyle.Button button=SettingsStyle.button(ctx,value,palette,action,primary,ghost);
            button.target.bind(NodeBinding.value(enabled,SceneNode::enabled));
            return button.view;
        }
        SceneNode build() {
            SceneNode toolbar=SettingsStyle.row(12).padding(16);
            toolbar.add(text("QZ  /  UILib 设置",14,false).size(Size.fill(1),Size.natural()));
            toolbar.add(button(() -> model.theme().get().isDark()?"浅色模式":"深色模式",
                    () -> model.setTheme(model.theme().get().isDark()?ThemeMode.LIGHT:ThemeMode.DARK),false,true));
            toolbar.add(button(() -> "返回",this::closeRequested,false,true));
            SceneNode heading=SettingsStyle.column(16)
                    .add(SettingsStyle.column(6).add(SettingsStyle.heading(ctx,() -> "设置",palette,30))
                    .add(text("管理字体、界面外观与当前宿主的本地配置。",15,true)))
                    .add(SettingsStyle.line(palette));
            SceneNode appearance=appearancePage(),minecraft=minecraftPage(),file=filePage();
            ctx.own(appearance::destroy);ctx.own(minecraft::destroy);ctx.own(file::destroy);
            SceneNode deck=SettingsStyle.column(0);
            deck.bind(NodeBinding.children(() -> Collections.singletonList(section.get()==Section.APPEARANCE?appearance:
                    section.get()==Section.MINECRAFT?minecraft:file)));
            SceneNode content=SettingsStyle.column(24).add(pageHeading()).add(deck);
            Signal<String> presentedMessage=ctx.domain().signal("");
            ReadableSignal<SettingsModel.Feedback> result=model.feedback();
            ReadableSignal<String> feedbackText=() -> {
                SettingsModel.Feedback value=result.get();
                return value!=null&&!value.success?value.text:presentedMessage.get();
            };
            SceneNode feedback=text(feedbackText,13,true);
            // 成功期限只控制本次挂载的呈现；配置阶段、错误和模型结果不随时间改写。
            feedback.bind(NodeBinding.transientValue(() -> {
                SettingsModel.Feedback value=result.get();return value!=null&&value.success?value:null;
            },3_000_000_000L,(n,value) -> presentedMessage.set(value==null?"":value.text)));
            feedback.bind(NodeBinding.value(feedbackText,(n,s) -> n.visible(!s.isEmpty())));
            SceneNode external=SettingsStyle.surface(SettingsStyle.column(8).padding(16)
                    .add(text("当前生效配置已变化",14,false))
                    .add(text("你的草稿已保留。可以继续编辑并明确应用，或放弃编辑以同步当前值。",13,true))
                    .add(button("同步当前值",model::discard)),palette,true);
            external.bind(NodeBinding.value(model.externalChanged(),SceneNode::visible));content.add(external);
            SceneNode body=SettingsLayout.form(content).size(Size.fill(1),Size.fill(1)).clip(true).scrollable(true);
            SceneNode shell=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1))
                    .bind(NodeBinding.value(palette,(n,v) -> n.background(v.background)))
                    .add(SettingsLayout.workspace(compact,toolbar,heading,navigation(),body,footer(feedback)));
            SceneNode fonts=fontsDialog(),add=addDialog(),reload=confirmation(Dialog.RELOAD),close=confirmation(Dialog.CLOSE);
            ctx.own(fonts::destroy);ctx.own(add::destroy);ctx.own(reload::destroy);ctx.own(close::destroy);
            SceneNode overlay=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers(false));
            overlay.bind(NodeBinding.children(() -> dialog.get()==Dialog.NONE?Collections.emptyList():Collections.singletonList(dialog.get()==Dialog.FONTS?fonts:dialog.get()==Dialog.ADD?add:dialog.get()==Dialog.RELOAD?reload:close)));
            SceneNode root=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers(false));
            root.add(shell).add(overlay).onEscape(() -> {closeRequested();return true;});
            return root;
        }
        SceneNode pageHeading() {
            return SettingsStyle.column(16).add(SettingsStyle.column(4)
                    .add(SettingsStyle.heading(ctx,() -> section.get().title,palette,20))
                    .add(text(() -> section.get().description,14,true)))
                    .add(SettingsStyle.line(palette));
        }
        SceneNode navigation() {
            SceneNode sidebar=SettingsStyle.column(24).size(Size.fill(1),Size.fill(1)).padding(20)
                    .bind(NodeBinding.value(palette,(n,colors) -> n.background(colors.panel)));
            sidebar.add(SettingsStyle.column(4).padding(8).add(SettingsStyle.heading(ctx,() -> "QZ UILib",palette,21))
                    .add(text("工作区设置",12,true)));
            SceneNode categories=SettingsStyle.column(6).size(Size.fill(1),Size.fill(1)).add(text("偏好设置",12,true));
            for(Section target:Section.values())if(target!=Section.MINECRAFT||configuration.hasMinecraft())categories.add(navButton(target));
            sidebar.add(categories).add(SettingsStyle.column(8).padding(8).add(SettingsStyle.line(palette))
                    .add(text(configuration.hasMinecraft()?"Minecraft 工作区":"Desktop 工作区",13,false))
                    .add(text("本地配置 · 显式保存",12,true)));
            sidebar.bind(NodeBinding.value(compact,(n,value) -> n.visible(!value)));
            SettingsStyle.Button trigger=SettingsStyle.button(ctx,() -> section.get().title+" · 切换分类",palette,
                    () -> navigationOpen.set(!navigationOpen.get()),false,false);
            SceneNode menu=SettingsStyle.column(4).padding(4).size(Size.fixed(216),Size.natural());
            for(Section target:Section.values())if(target!=Section.MINECRAFT||configuration.hasMinecraft())menu.add(navButton(target));
            menu.add(button("关闭分类",() -> navigationOpen.set(false)));
            SceneNode layer=club.heiqi.uilib.ui.scene.control.SceneLayer.create(ctx,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(6,
                    club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.INTERACTIVE))
                    .size(Size.fixed(218),Size.natural()).modal(true).restoreFocus(trigger.target)
                    .onEscape(() -> {navigationOpen.set(false);return true;})
                    .bind(NodeBinding.value(() -> compact.get()&&navigationOpen.get(),SceneNode::visible))
                    .add(SettingsStyle.surface(menu,palette,false));
            SceneNode selector=SettingsStyle.column(0).size(Size.natural(),Size.natural()).add(trigger.view).add(layer);
            selector.bind(NodeBinding.value(compact,SceneNode::visible));
            return SettingsStyle.column(0).bind(NodeBinding.value(compact,(n,small) -> n.size(Size.fill(1),small?Size.natural():Size.fill(1))))
                    .add(sidebar).add(selector);
        }
        SceneNode navButton(Section target) {
            club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol symbol=target==Section.APPEARANCE
                    ?club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.SLIDERS:target==Section.FILE
                    ?club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.FILE:club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.MONITOR;
            return SettingsStyle.navigationButton(ctx,() -> target.title,() -> section.get()==target,palette,symbol,
                    () -> {section.set(target);navigationOpen.set(false);});
        }
        SceneNode appearancePage() {
            SceneNode choices=club.heiqi.uilib.ui.scene.control.SceneGrid.create(ctx,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneGrid.Policy(220,20,16));
            choices.add(SettingsStyle.themeChoice(ctx,"浅色外观",false,() -> model.theme().get()==ThemeMode.LIGHT,palette,
                    () -> model.setTheme(ThemeMode.LIGHT)));
            choices.add(SettingsStyle.themeChoice(ctx,"深色外观",true,() -> model.theme().get()==ThemeMode.DARK,palette,
                    () -> model.setTheme(ThemeMode.DARK)));
            SceneNode font=SettingsStyle.column(8).add(text("字体来源",14,false));
            SceneNode summary=SettingsStyle.column(4).add(text(this::fontSummary,14,false));
            font.add(SettingsStyle.surface(SettingsLayout.sourceRow(summary,
                    actions(button("管理字体来源",() -> dialog.set(Dialog.FONTS)))),palette,false));
            font.add(text("选择内置字体或本地文件，按字形覆盖依次尝试。",13,true)).add(error(model.fontError()));
            SceneNode theme=SettingsStyle.column(12).add(text("界面主题",14,false))
                    .add(text("选择工作区的浅色或深色外观。",13,true)).add(choices);
            return SettingsStyle.column(24).add(font).add(theme).add(sampleCard());
        }
        String fontSummary() {
            List<SettingsModel.Source> sources=model.sources().get();
            if(sources.isEmpty())return "未配置字体候选";
            String first=sourceName(sources.get(0).declaration());
            return sources.size()==1?first:first+"，以及其他 "+(sources.size()-1)+" 个来源";
        }
        SceneNode fontsDialog() {
            SceneNode list=club.heiqi.uilib.ui.scene.control.SceneSortableList.create(ctx,model.sources(),SettingsModel.Source::identity,
                    this::sortableSource,this::sourcePreview,move -> model.moveBefore(move.key,move.beforeKey))
                    .size(Size.fill(1),Size.fixed(320)).scrollable(true).clip(true);
            list.bind(NodeBinding.value(() -> SettingsTypography.style(14,palette.get().accent,400),SceneNode::style));
            // 空态隐藏整个表面，避免仍参与布局的容器失去唯一可见正文。
            SceneNode sourceList=SettingsStyle.surface(list,palette,false)
                    .bind(NodeBinding.value(model.sources(),(n,items) -> n.visible(!items.isEmpty())));
            SceneNode empty=text("未配置字体候选。不会自动补入默认字体。",14,true);
            empty.bind(NodeBinding.value(model.sources(),(n,items) -> n.visible(items.isEmpty())));
            SettingsStyle.Button done=SettingsStyle.button(ctx,() -> "完成字体编辑",palette,() -> dialog.set(Dialog.NONE),true,false);
            SceneNode content=SettingsStyle.column(16).add(SettingsStyle.heading(ctx,() -> "管理字体来源",palette,20))
                    .add(text("从上到下依次匹配。拖动把手调整顺序，松手只更新草稿。",14,true))
                    .add(text("也可用上移/下移，或聚焦后按空格拾取、方向键排序、Enter 确认、Esc 取消。",12,true))
                    .add(empty).add(sourceList).add(error(model.fontError()))
                    .add(actions(button("+ 添加来源",() -> {path.reset("");fileSource.set(false);dialog.set(Dialog.ADD);}),
                            button(() -> "恢复默认",model::resetFonts,false,true)))
                    .add(text("移除的来源不会补回；空列表也不会追加默认字体。",12,true))
                    .add(done.view);
            return SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers(true,720))
                    .background(club.heiqi.uilib.ui.scene.control.SceneAppearance.MODAL_SCRIM).modal(true).onEscape(() -> {cancelDialog();return true;})
                    .add(SettingsStyle.card(content,palette)).initialFocus(done.target);
        }
        club.heiqi.uilib.ui.scene.control.SceneSortableList.Row sortableSource(ReadableSignal<SettingsModel.Source> source) {
            SceneNode handle=text("::",20,true).size(Size.fixed(28),Size.natural()).padding(4);
            SceneNode row=SettingsStyle.row(4).add(handle).add(sourceRow(source).size(Size.fill(1),Size.natural()));
            return new club.heiqi.uilib.ui.scene.control.SceneSortableList.Row(row,handle);
        }
        SceneNode sourcePreview(ReadableSignal<SettingsModel.Source> source) {
            SceneNode info=SettingsStyle.column(4).padding(12).add(text(() -> sourceName(source.get().declaration()),14,false))
                    .add(pathText(() -> source.get().declaration(),12,true));
            return SettingsStyle.surface(info,palette,false).size(Size.fill(1).bounds(0,560),Size.natural());
        }
        SceneNode sourceRow(ReadableSignal<SettingsModel.Source> source) {
            SceneNode info=SettingsStyle.column(4).add(text(() -> String.format(java.util.Locale.ROOT,"%02d",model.sources().get().indexOf(source.get())+1)
                    +"  "+sourceName(source.get().declaration()),14,false));
            SceneNode declaration=pathText(() -> source.get().declaration(),12,true)
                    .textPolicy(new club.heiqi.uilib.ui.scene.text.TextPolicy(
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.LTR,
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.EMERGENCY,"und",0,0));
            info.add(declaration);
            SceneNode buttons=actions(enabledButton(() -> "上移",() -> model.move(source.get(),-1),false,true,
                            () -> model.sources().get().indexOf(source.get())>0),
                    enabledButton(() -> "下移",() -> model.move(source.get(),1),false,true,
                            () -> model.sources().get().indexOf(source.get())<model.sources().get().size()-1),
                    button(() -> "移除",() -> model.remove(source.get()),false,true));
            return SettingsStyle.column(0).add(SettingsLayout.sourceRow(info,buttons)).add(SettingsStyle.line(palette));
        }
        SceneNode sampleCard() {
            ReadableSignal<SceneTheme> theme=() -> palette.get().theme(
                    new club.heiqi.uilib.ui.scene.text.TextStyle("default",18,palette.get().text),14,false);
            SceneNode editor=SceneTextArea.create(ctx,sample,theme,() -> false,s -> sample.accept(s,s.text),
                    SceneTextField.SubmitPolicy.CONFIRM,() -> SceneTextArea.TabMode.NAVIGATE,false)
                    .size(Size.fill(1),Size.fixed(96));
            SceneNode field=SettingsStyle.field(editor,palette);
            SceneNode unavailable=text("未配置生效字体来源，当前没有可用的文字样本。",14,true);
            ctx.own(field::destroy);ctx.own(unavailable::destroy);
            // default样本只消费真实生效来源；显式空值不能借界面字体生成伪样本。
            SceneNode preview=SettingsStyle.column(0).bind(NodeBinding.children(() -> Collections.singletonList(
                    configuration.current().candidates().isEmpty()?unavailable:field)));
            return SettingsStyle.column(8).add(SettingsStyle.row(12)
                    .add(text("文字样本",14,false).size(Size.fill(1),Size.natural()))
                    .add(button(() -> "重置",() -> sample.reset(defaultSample),false,true)))
                    .add(preview)
                    .add(text("使用当前生效字体。可编辑预览文字；样本文字不会保存。",12,true));
        }
        SceneNode minecraftPage() {
            SceneNode card=SettingsStyle.column(16).add(text("原版文字接入",16,false)).add(text("控制 Minecraft 原版文字是否使用 UILib 字体服务。",13,true));
            if(configuration.hasMinecraft()) {
                SceneNode row=SettingsStyle.row(18);
                row.add(SettingsStyle.column(6).size(Size.fill(1),Size.natural()).add(text("接管原版文字绘制",15,false)).add(text("聊天、原版界面等接入 UILib 的文字适配。",13,true)));
                row.add(SettingsStyle.row(8).size(Size.natural(),Size.natural())
                        .add(text(() -> model.replace().get()?"已开启":"已关闭",13,true))
                        .add(SettingsStyle.toggle(ctx,model.replace(),palette,() -> model.setReplace(!model.replace().get()))));card.add(row);
                card.add(SettingsStyle.line(palette));
                card.add(text("作用范围：Minecraft 原版文字适配",13,true));
                card.add(text("UILib 页面继续使用自身的字体与绘制服务。",13,true));
                card.add(text("应用后更新当前运行选项；保存后用于下次启动。",13,true));
                card.add(button(() -> "恢复默认",model::resetMinecraft,false,true));
            } else card.add(text("当前为独立桌面宿主，不提供可修改的 Minecraft 开关。",13,true));
            return SettingsStyle.column(16).add(card);
        }
        SceneNode filePage() {
            SceneNode card=SettingsStyle.column(14).add(text("配置来源",16,false))
                    .add(text("查看当前文件，以及运行状态和保存状态。",13,true));
            card.add(text("文件路径",13,true)).add(pathText(() -> configuration.path().toString(),14,false));
            card.add(SettingsStyle.line(palette)).add(text("文件格式：UTF-8 YAML",13,true));
            card.add(text(() -> model.unsaved().get()?"当前生效配置尚未保存":"当前生效配置与最近读取或保存的值一致",13,false));
            card.add(text("保存沿配置核心保留用户注释。",13,true));
            card.add(button("从文件重新加载",this::reloadRequested));
            card.add(text("重新加载使用文件中的值替换当前配置。有未应用或未保存的更改时，会先确认。",12,true));
            card.add(error(model.fileError()));
            SceneNode values=SettingsStyle.column(12).add(text("当前生效值",16,false)).add(text("只读表示；未应用草稿不显示在这里。",12,true));
            values.add(text(() -> "全局主题："+(configuration.current().theme().isDark()?"深色":"浅色")
                    +"\n\n字体候选：\n"+String.join("\n",configuration.current().candidates())
                    +(configuration.hasMinecraft()?"\n\n接管原版文字绘制："+(configuration.current().replace()?"开启":"关闭"):""),13,true));
            return SettingsStyle.column(28).add(card).add(SettingsStyle.line(palette)).add(values);
        }
        SceneNode footer(SceneNode feedback) {
            // 全局操作的短暂结果与配置阶段同处固定栏，正文滚动不遮住反馈。
            SceneNode status=SettingsStyle.column(5).add(text(this::state,13,false)).add(feedback);
            status.add(text(() -> model.dirty().get()?"应用全部分类的更改后，才能保存到文件。":model.unsaved().get()?"当前运行使用新的配置；保存后用于下次启动。":"可以继续编辑；应用与保存分别执行。",12,true));
            status.add(error(model.error()));
            SceneNode controls=actions(enabledButton(() -> "放弃编辑",model::discard,false,true,model.dirty()),
                    enabledButton("保存到文件",model::save,() -> !model.dirty().get()&&model.unsaved().get()),
                    enabledButton(() -> "应用全部更改",model::apply,true,false,model.dirty()));
            return SettingsStyle.column(14).add(SettingsStyle.line(palette))
                    .add(SettingsStyle.column(0).control(new SettingsLayout.Footer()).add(status).add(controls));
        }
        String state() { return model.dirty().get()?"有待应用修改":model.unsaved().get()?"已应用，未保存":"已保存"; }
        SceneNode error(ReadableSignal<String> value) {
            SceneNode result=SceneLabel.create(ctx,value,() -> palette.get().theme(13,palette.get().error,0,false));
            result.bind(NodeBinding.value(value,(n,s) -> n.visible(!s.isEmpty())));return result;
        }
        void closeRequested() {closeRequested(leave);}
        void closeRequested(Runnable action) {
            navigationOpen.set(false);
            if(dialog.get()!=Dialog.NONE){cancelDialog();return;}
            if(model.dirty().get()||model.unsaved().get()){confirmedLeave=action;dialog.set(Dialog.CLOSE);}
            else {confirmedLeave=null;action.run();}
        }
        void cancelDialog(){fontPicking.set(false);confirmedLeave=null;dialog.set(dialog.get()==Dialog.ADD?Dialog.FONTS:Dialog.NONE);}
        void discardAndLeave(){
            Runnable action=confirmedLeave;confirmedLeave=null;dialog.set(Dialog.NONE);
            model.discard();action.run();
        }
        void reloadRequested() {
            navigationOpen.set(false);confirmedLeave=null;
            if(model.dirty().get()||model.unsaved().get())dialog.set(Dialog.RELOAD);else model.reload();
        }
        SceneNode modal(SceneNode card) {
            return SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers(true))
                    .background(club.heiqi.uilib.ui.scene.control.SceneAppearance.MODAL_SCRIM).modal(true).onEscape(() -> {cancelDialog();return true;}).add(SettingsStyle.card(card,palette));
        }
        SceneNode confirmation(Dialog kind) {
            SceneNode content=SettingsStyle.column(16);
            content.add(text(kind==Dialog.CLOSE?"离开配置工作区？":"从文件重新加载？",20,false));
            content.add(text(kind==Dialog.CLOSE?"未应用的草稿将被放弃；已经应用的值保留在当前运行中。离开不会保存文件。":"未应用的草稿及已应用但未保存的更改，将被文件中的配置替换。",14,true));
            SettingsStyle.Button cancel=SettingsStyle.button(ctx,() -> "继续编辑",palette,this::cancelDialog,false,false);
            content.add(actions(cancel.view,button(() -> kind==Dialog.CLOSE?"不保存并离开":"放弃更改并加载",() -> {
                if(kind==Dialog.CLOSE){discardAndLeave();}else{dialog.set(Dialog.NONE);model.reload();}
            },true,false)));
            return modal(content).initialFocus(cancel.target);
        }
        SceneNode addDialog() {
            SceneNode content=SettingsStyle.column(14).add(text("添加字体来源",20,false))
                    .add(text("选择内置字体，或填写宿主可读取的字体文件路径。",13,true));
            content.add(actions(button(() -> fileSource.get()?"内置来源":"● 内置来源",() -> fileSource.set(false),false,false),
                    button(() -> fileSource.get()?"● 本地字体文件":"本地字体文件",() -> fileSource.set(true),false,false)));
            SceneNode builtins=SettingsStyle.column(5).size(Size.fill(1),Size.fixed(220)).scrollable(true).clip(true);
            for(String declaration:configuration.builtinCandidates())builtins.add(button(() -> (selectedBuiltin.get().equals(declaration)?"● ":"")+sourceName(declaration),() -> selectedBuiltin.set(declaration),false,true).size(Size.fill(1),Size.natural()));
            builtins.bind(NodeBinding.value(fileSource,(n,file) -> n.visible(!file)));content.add(builtins);
            SceneNode field=SceneTextField.create(ctx,path,() -> palette.get().theme(SettingsTypography.pathStyle(14,palette.get().text),9,false),
                    () -> false,this::addFile,SceneTextField.SubmitPolicy.CONFIRM,() -> "C:/Fonts/Example.ttf",false);
            SceneNode file=SettingsStyle.column(8).add(actions(text("文件路径",13,false),button("浏览字体文件",()->fontPicking.set(true)))).add(SettingsStyle.field(field,palette))
                    .add(text("例如 C:/Fonts/Example.ttf；应用前会检查字体来源。",12,true));
            file.bind(NodeBinding.value(fileSource,SceneNode::visible));content.add(file);
            content.add(error(() -> path.snapshot().get().error==null?model.fontError().get():path.snapshot().get().error));
            SettingsStyle.Button cancel=SettingsStyle.button(ctx,() -> "取消",palette,this::cancelDialog,false,false);
            content.add(actions(cancel.view,button(() -> "添加到末尾",() -> {
                if(fileSource.get())addFile(path.request(true));
                else if(!selectedBuiltin.get().isEmpty()){model.add(selectedBuiltin.get());if(model.fontError().get().isEmpty())dialog.set(Dialog.FONTS);}
            },true,false)));
            SceneNode result=modal(content).initialFocus(cancel.target);
            SceneNode pickerOwner=new SceneNode().size(Size.fill(1),Size.fill(1)).bind(NodeBinding.repeat(()->fontPicking.get()?java.util.Collections.singletonList(true):java.util.Collections.emptyList(),value->value,item->
                    club.heiqi.uilib.ui.scene.control.SceneFilePicker.create(ctx,fontDirectory(),()->palette.get().theme(14,palette.get().text,10,false),new club.heiqi.uilib.ui.scene.control.SceneFilePicker.Options().filter("字体文件","ttf,otf,ttc,woff,woff2"),selected->{path.reset(selected.toString());fontPicking.set(false);},()->fontPicking.set(false))));
            result.add(pickerOwner);return result;
        }
        java.nio.file.Path fontDirectory(){
            String raw=path.snapshot().get().text.trim();try{if(!raw.isEmpty()){java.nio.file.Path candidate=java.nio.file.Paths.get(raw).toAbsolutePath().normalize();if(candidate.getParent()!=null)return candidate.getParent();}}catch(IllegalArgumentException ignored){}
            return java.nio.file.Paths.get(System.getProperty("user.home",".")).toAbsolutePath().normalize();
        }
        void addFile(SceneEditState.Submission submission) {
            String value=submission.text.trim();
            if(path.snapshot().get().composing){path.reject(submission,"请先完成正在输入的文字。");return;}
            if(value.isEmpty()){path.reject(submission,"请输入字体文件路径。");return;}
            model.add("file:"+value);
            if(!model.fontError().get().isEmpty()){path.reject(submission,model.fontError().get());return;}
            path.accept(submission,value);dialog.set(Dialog.FONTS);
        }
    }
    private static String sourceName(String declaration) {
        if(declaration.equals("builtin:unifont"))return "GNU Unifont · 内置";
        if(declaration.startsWith("builtin:")) {
            String id=declaration.substring(8);
            if(id.equals("noto-color-emoji"))return "Noto Color Emoji · 内置";
            if(id.equals("noto-emoji-text"))return "Noto Emoji · 内置";
            if(id.startsWith("noto-")) {
                String script=id.substring(5);return "Noto Sans "+Character.toUpperCase(script.charAt(0))+script.substring(1)+" · 内置";
            }
            return id+" · 内置";
        }
        String path=declaration.startsWith("file:")?declaration.substring(5):declaration;
        int separator=Math.max(path.lastIndexOf('/'),path.lastIndexOf('\\'));
        return path.substring(separator+1)+" · 文件";
    }
}
