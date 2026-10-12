package club.heiqi.uilib.ui.settings;

import java.util.Arrays;
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
 * 视觉与操作合同见specs/2026-10-12-uilib-settings-page-design.md。 */
public final class SettingsPage implements ScenePage {
    private final SettingsConfiguration configuration;
    private final Runnable leave;
    private View view;

    public SettingsPage(SettingsConfiguration configuration, Runnable leave) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.leave = Objects.requireNonNull(leave, "leave");
    }
    @Override public void build(ScenePageContext context) {
        if(context.domain()!=configuration.domain())throw new IllegalArgumentException("配置页面与配置必须同域");
        if(view!=null)throw new IllegalStateException("配置页面已经打开");
        view=new View(context);
        context.own(() -> view=null);
        view.build();
    }
    /** 原生窗口关闭与返回同走本页确认；回调只退出，不隐含保存或撤销已应用值。 */
    public void requestClose() { configuration.domain().execute(() -> { if(view!=null)view.closeRequested(); }); }
    /** 宿主快捷键与文件卡片共享显式加载确认。 */
    public void requestReload() { configuration.domain().execute(() -> { if(view!=null)view.reloadRequested(); }); }

    private enum Section { FONT, MINECRAFT, FILE }
    private enum Dialog { NONE, ADD, RELOAD, CLOSE }
    private final class View {
        final ScenePageContext ctx;
        final SettingsModel model;
        final Signal<Boolean> fileSource;
        final Signal<Section> section;
        final Signal<Dialog> dialog;
        final Signal<String> selectedBuiltin;
        final ReadableSignal<SettingsStyle.Palette> palette;
        final SceneEditState path, sample;
        final SettingsStyle.NavigationLayout navigationLayout = new SettingsStyle.NavigationLayout();
        final String defaultSample="每一种语言，都值得被认真呈现。\nGood typography makes a difference.";
        View(ScenePageContext ctx) {
            this.ctx=ctx;model=new SettingsModel(configuration,ctx.scope());
            fileSource=ctx.domain().signal(false);
            section=ctx.domain().signal(Section.FONT);dialog=ctx.domain().signal(Dialog.NONE);
            List<String> builtins=configuration.builtinCandidates();
            selectedBuiltin=ctx.domain().signal(builtins.isEmpty()?"":builtins.get(0));
            palette=ctx.domain().computed(ctx.scope(),() -> new SettingsStyle.Palette(model.theme().get().isDark()));
            path=new SceneEditState(ctx.domain(),"");
            sample=new SceneEditState(ctx.domain(),defaultSample,SceneEditState.Mode.MULTI_LINE);
        }
        SceneNode text(String value,int em,boolean muted) { return text(() -> value,em,muted); }
        SceneNode text(ReadableSignal<String> value,int em,boolean muted) { return SettingsStyle.label(ctx,value,palette,em,muted); }
        SceneNode button(String value,Runnable action) { return button(() -> value,action,false,false); }
        SceneNode button(ReadableSignal<String> value,Runnable action,boolean primary,boolean ghost) { return SettingsStyle.button(ctx,value,palette,action,primary,ghost).view; }
        SceneNode actions(SceneNode... nodes) { SceneNode result=SettingsStyle.column(0).control(new SettingsStyle.WrapRow(8));for(SceneNode n:nodes)result.add(n);return result; }
        SceneNode enabledButton(String value,Runnable action,ReadableSignal<Boolean> enabled) {
            return enabledButton(() -> value,action,false,false,enabled);
        }
        SceneNode enabledButton(ReadableSignal<String> value,Runnable action,boolean primary,boolean ghost,ReadableSignal<Boolean> enabled) {
            SettingsStyle.Button button=SettingsStyle.button(ctx,value,palette,action,primary,ghost);
            button.target.bind(NodeBinding.value(enabled,SceneNode::enabled));
            return button.view;
        }
        void build() {
            SceneNode shell=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1))
                    .bind(NodeBinding.value(palette,(n,p) -> n.background(p.background)));
            SceneNode toolbar=SettingsStyle.row(12).padding(14);
            toolbar.add(text("QZ  /  UILib 设置",15,false).size(Size.fill(1),Size.natural()));
            toolbar.add(SettingsStyle.column(4)
                    .add(button(() -> model.theme().get().isDark()?"浅色模式":"深色模式",
                            () -> model.setTheme(model.theme().get().isDark()?ThemeMode.LIGHT:ThemeMode.DARK),false,true))
                    .add(text("应用后全局生效，保存后重启保留",11,true)));
            toolbar.add(button(() -> "返回",this::closeRequested,false,true));
            shell.add(toolbar).add(SettingsStyle.line(palette));
            SceneNode workspace=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsStyle.Workspace(navigationLayout));
            workspace.add(navigation());
            SceneNode body=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1));
            SceneNode content=SettingsStyle.column(20).padding(28).size(Size.fill(1),Size.fill(1)).clip(true).scrollable(true);
            content.add(pageHeading());
            SceneNode fonts=fontPage(),minecraft=minecraftPage(),file=filePage();
            ctx.own(fonts::destroy);ctx.own(minecraft::destroy);ctx.own(file::destroy);
            SceneNode deck=SettingsStyle.column(0);
            deck.bind(NodeBinding.children(() -> Collections.singletonList(section.get()==Section.FONT?fonts:section.get()==Section.MINECRAFT?minecraft:file)));
            content.add(deck);
            SceneNode feedback=text(() -> model.message().get(),13,true);
            feedback.bind(NodeBinding.value(model.message(),(n,s) -> n.visible(!s.isEmpty())));content.add(feedback);
            SceneNode external=SettingsStyle.surface(SettingsStyle.column(8)
                    .add(text("当前生效配置已变化",14,false))
                    .add(text("你的草稿已保留。可以继续编辑并明确应用，或放弃编辑以同步当前值。",13,true))
                    .add(button("同步当前值",model::discard)).padding(16),palette,true);
            external.bind(NodeBinding.value(model.externalChanged(),SceneNode::visible));content.add(external);
            body.add(content).add(SettingsStyle.line(palette)).add(footer());
            workspace.add(body);shell.add(workspace);
            SceneNode add=addDialog(),reload=confirmation(Dialog.RELOAD),close=confirmation(Dialog.CLOSE);
            ctx.own(add::destroy);ctx.own(reload::destroy);ctx.own(close::destroy);
            SceneNode overlay=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsStyle.Layers(false));
            overlay.bind(NodeBinding.children(() -> dialog.get()==Dialog.NONE?Collections.emptyList():Collections.singletonList(dialog.get()==Dialog.ADD?add:dialog.get()==Dialog.RELOAD?reload:close)));
            SceneNode root=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsStyle.Layers(false));
            root.add(shell).add(overlay).onEscape(() -> {closeRequested();return true;});
            ctx.mountOwned(root);
        }
        SceneNode pageHeading() {
            SceneNode h=SettingsStyle.column(8);
            h.add(text(() -> section.get()==Section.FONT?"字体与排版":section.get()==Section.MINECRAFT?"Minecraft 接入":"配置文件",26,false));
            h.add(text(() -> section.get()==Section.FONT?"管理字体来源与优先顺序，让每一种文字都有合适的呈现。":section.get()==Section.MINECRAFT?"选择原版文字接入方式，保持游戏与 UILib 页面职责清晰。":"查看配置来源，显式保存或从文件重新加载。",13,true));
            SceneNode reset=button(() -> "恢复默认",() -> {if(section.get()==Section.FONT)model.resetFonts();else model.resetMinecraft();},false,true);
            reset.bind(NodeBinding.value(section,(n,s) -> n.visible(s!=Section.FILE)));
            h.add(actions(text(this::state,12,true),reset));
            return h;
        }
        SceneNode navigation() {
            SceneNode nav=SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsStyle.Navigation(navigationLayout))
                    .bind(NodeBinding.value(palette,(n,p) -> n.background(p.panel)));
            SceneNode brand=SettingsStyle.column(10).clip(true).add(text("配置工作区",13,true));
            SceneNode buttons=SettingsStyle.column(0).control(new SettingsStyle.NavigationButtons(navigationLayout));
            buttons.add(navButton(Section.FONT,"字体与排版"));
            if(configuration.hasMinecraft())buttons.add(navButton(Section.MINECRAFT,"Minecraft 接入"));
            buttons.add(navButton(Section.FILE,"配置文件"));
            SceneNode info=SettingsStyle.column(8).clip(true).add(SettingsStyle.line(palette)).add(text("本地配置",12,false))
                    .add(text(configuration.hasMinecraft()?"Qz UILib · Minecraft":"Qz UILib · Desktop",11,true));
            nav.add(brand).add(buttons).add(info);return nav;
        }
        SceneNode navButton(Section target,String label) {
            return SettingsStyle.navigationButton(ctx,() -> label,() -> section.get()==target,palette,() -> section.set(target));
        }
        SceneNode fontPage() {
            SceneNode content=SettingsStyle.column(12);
            SceneNode heading=SettingsStyle.row(12).add(text("字体候选顺序",16,false).size(Size.fill(1),Size.natural()))
                    .add(button("+ 添加来源",() -> {path.reset("");fileSource.set(false);dialog.set(Dialog.ADD);}));
            content.add(heading).add(text("从上到下尝试来源，按字形覆盖选择合适的候选。",13,true));
            content.add(text(() -> model.sources().get().size()+" 个来源 · 优先级从高到低",12,true));
            SceneNode list=SettingsStyle.column(0).size(Size.fill(1),Size.fixed(340)).scrollable(true).clip(true);
            list.bind(NodeBinding.repeat(model.sources(),SettingsModel.Source::identity,source -> sourceRow(source)));
            SceneNode empty=text("未配置字体候选。这是明确的空列表，不会自动补入默认字体。",14,true);
            empty.bind(NodeBinding.value(model.sources(),(n,s) -> n.visible(s.isEmpty())));content.add(empty);
            list.bind(NodeBinding.value(model.sources(),(n,s) -> n.visible(!s.isEmpty())));
            content.add(SettingsStyle.surface(list,palette,false));
            content.add(text("仅使用列表中声明的来源。移除的来源不会自动补回，空列表也不会追加默认字体。",12,true));
            content.add(error(model.fontError()));content.add(SettingsStyle.line(palette));
            content.add(text("内置字体与本地文件可混合排序",12,true));
            SceneNode left=SettingsStyle.column(16).add(SettingsStyle.card(content,palette))
                    .add(text("编辑不会立即写入配置文件。先应用更改，再保存到文件。",12,true));
            SceneNode right=SettingsStyle.column(16).add(sampleCard())
                    .add(SettingsStyle.surface(SettingsStyle.column(8).padding(16).add(text("字体如何生效？",14,false))
                    .add(text("更改列表后，点击“应用全部更改”。当前运行使用新的字体来源；保存到文件后，下次启动继续使用。",12,true)),palette,true));
            return SettingsStyle.column(0).control(new SettingsStyle.Columns()).add(left).add(right);
        }
        SceneNode sourceRow(ReadableSignal<SettingsModel.Source> source) {
            SceneNode info=SettingsStyle.column(6).size(Size.fill(1),Size.natural());
            info.add(text(() -> String.format(java.util.Locale.ROOT,"%02d",model.sources().get().indexOf(source.get())+1)+"  "+sourceName(source.get().declaration()),14,false));
            SceneNode declaration=SceneLabel.create(ctx,() -> source.get().declaration(),
                    () -> palette.get().theme(11,palette.get().muted,0,false),SceneTextPolicy.WRAP)
                    .textPolicy(new club.heiqi.uilib.ui.scene.text.TextPolicy(
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.LTR,
                            club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.EMERGENCY,"und",0,0));
            info.add(declaration);
            SceneNode buttons=SettingsStyle.row(2).size(Size.natural(),Size.natural());
            buttons.add(enabledButton("上移",() -> model.move(source.get(),-1),() -> model.sources().get().indexOf(source.get())>0));
            buttons.add(enabledButton("下移",() -> model.move(source.get(),1),() -> model.sources().get().indexOf(source.get())<model.sources().get().size()-1));
            buttons.add(button("移除",() -> model.remove(source.get())));
            return SettingsStyle.column(0).add(SettingsStyle.row(10).padding(10).add(info).add(buttons)).add(SettingsStyle.line(palette));
        }
        SceneNode sampleCard() {
            SceneNode card=SettingsStyle.column(12).add(text("文字样本",16,false)).add(text("当前生效配置",12,true));
            SceneNode samples=SettingsStyle.column(10).padding(16);
            samples.add(text("让文字清晰可读。",23,false)).add(text("The quick brown fox.",22,false))
                    .add(text("0123456789 · ± ∞ → 🎨",14,true)).add(SettingsStyle.line(palette)).add(text(sample.text(),14,false));
            card.add(SettingsStyle.surface(samples,palette,true));
            card.add(actions(text("自定义样本",13,false),button(() -> "重置",() -> sample.reset(defaultSample),false,true)));
            ReadableSignal<SceneTheme> theme=() -> palette.get().theme(14,palette.get().text,10,false);
            SceneNode editor=SceneTextArea.create(ctx,sample,theme,() -> false,s -> sample.accept(s,s.text));
            editor.size(Size.fill(1),Size.fixed(90));card.add(SettingsStyle.surface(editor,palette,false));
            card.add(text("样本文字仅供查看，不作为配置保存。",12,true));return SettingsStyle.card(card,palette);
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
            } else card.add(text("当前为独立桌面宿主，不提供可修改的 Minecraft 开关。",13,true));
            return SettingsStyle.column(16).add(SettingsStyle.card(card,palette));
        }
        SceneNode filePage() {
            SceneNode card=SettingsStyle.column(14).add(text("配置来源",16,false))
                    .add(text("查看当前文件，以及运行状态和保存状态。",13,true));
            card.add(text("文件路径",13,true)).add(text(configuration.path().toString(),14,false));
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
            return SettingsStyle.column(18).add(SettingsStyle.card(card,palette)).add(SettingsStyle.card(values,palette));
        }
        SceneNode footer() {
            SceneNode status=SettingsStyle.column(5).add(text(this::footerState,13,false));
            status.add(text(() -> model.dirty().get()?"应用全部分类的更改后，才能保存到文件。":model.unsaved().get()?"当前运行使用新的配置；保存后用于下次启动。":"可以继续编辑；应用与保存分别执行。",12,true));
            status.add(error(model.error()));
            SceneNode controls=actions(enabledButton(() -> "放弃编辑",model::discard,false,true,model.dirty()),
                    enabledButton("保存到文件",model::save,() -> !model.dirty().get()&&model.unsaved().get()),
                    enabledButton(() -> "应用全部更改",model::apply,true,false,model.dirty()));
            return SettingsStyle.column(0).padding(14).add(SettingsStyle.column(0).control(new SettingsStyle.Footer()).add(status).add(controls));
        }
        String state() { return model.dirty().get()?"有待应用修改":model.unsaved().get()?"已应用，未保存":"已保存"; }
        String footerState() { return model.dirty().get()?"更改尚未应用":model.unsaved().get()?"更改已生效，尚未保存到文件":"当前配置与保存值一致"; }
        SceneNode error(ReadableSignal<String> value) {
            SceneNode result=SceneLabel.create(ctx,value,() -> palette.get().theme(13,palette.get().error,0,false));
            result.bind(NodeBinding.value(value,(n,s) -> n.visible(!s.isEmpty())));return result;
        }
        void closeRequested() {
            if(dialog.get()!=Dialog.NONE){dialog.set(Dialog.NONE);return;}
            if(model.dirty().get()||model.unsaved().get())dialog.set(Dialog.CLOSE);else leave.run();
        }
        void reloadRequested() {
            if(model.dirty().get()||model.unsaved().get())dialog.set(Dialog.RELOAD);else model.reload();
        }
        SceneNode modal(SceneNode card) {
            return SettingsStyle.column(0).size(Size.fill(1),Size.fill(1)).control(new SettingsStyle.Layers(true))
                    .background(0x66000000).modal(true).onEscape(() -> {dialog.set(Dialog.NONE);return true;}).add(SettingsStyle.card(card,palette));
        }
        SceneNode confirmation(Dialog kind) {
            SceneNode content=SettingsStyle.column(16);
            content.add(text(kind==Dialog.CLOSE?"离开配置工作区？":"从文件重新加载？",20,false));
            content.add(text(kind==Dialog.CLOSE?"未应用的草稿将被放弃；已经应用的值保留在当前运行中。离开不会保存文件。":"未应用的草稿及已应用但未保存的更改，将被文件中的配置替换。",14,true));
            SettingsStyle.Button cancel=SettingsStyle.button(ctx,() -> "继续编辑",palette,() -> dialog.set(Dialog.NONE),false,false);
            content.add(actions(cancel.view,button(() -> kind==Dialog.CLOSE?"不保存并离开":"放弃更改并加载",() -> {
                if(kind==Dialog.CLOSE){model.discard();leave.run();}else{dialog.set(Dialog.NONE);model.reload();}
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
            SceneNode field=SceneTextField.create(ctx,path,() -> palette.get().theme(14,palette.get().text,10,false),() -> false,this::addFile);
            SceneNode file=SettingsStyle.column(8).add(text("文件路径",13,false)).add(SettingsStyle.surface(field,palette,false))
                    .add(text("例如 C:/Fonts/Example.ttf；应用前会检查字体来源。",12,true));
            file.bind(NodeBinding.value(fileSource,SceneNode::visible));content.add(file);
            content.add(error(() -> path.snapshot().get().error==null?model.fontError().get():path.snapshot().get().error));
            SettingsStyle.Button cancel=SettingsStyle.button(ctx,() -> "取消",palette,() -> dialog.set(Dialog.NONE),false,false);
            content.add(actions(cancel.view,button(() -> "添加到末尾",() -> {
                if(fileSource.get())addFile(path.request(true));
                else if(!selectedBuiltin.get().isEmpty()){model.add(selectedBuiltin.get());if(model.fontError().get().isEmpty())dialog.set(Dialog.NONE);}
            },true,false)));
            return modal(content).initialFocus(cancel.target);
        }
        void addFile(SceneEditState.Submission submission) {
            String value=submission.text.trim();
            if(path.snapshot().get().composing){path.reject(submission,"请先完成正在输入的文字。");return;}
            if(value.isEmpty()){path.reject(submission,"请输入字体文件路径。");return;}
            model.add("file:"+value);
            if(!model.fontError().get().isEmpty()){path.reject(submission,model.fontError().get());return;}
            path.accept(submission,value);dialog.set(Dialog.NONE);
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
