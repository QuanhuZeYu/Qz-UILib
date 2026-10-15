package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 搜索表面只包正式单行字段；输入、组字、选区和提交不在案例中另实现。 */
final class MailSearch {
    private MailSearch() {}
    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette, MailboxModel model) {
        SceneEditState query=new SceneEditState(p.domain(),model.query.get());
        Owner scope=p.domain().owner();String[] lastQuery={model.query.get()};
        // 外部查询意图明确重置草稿；用户INPUT提交相同值不重置光标或组字。
        p.domain().effect(scope,() -> {
            String current=model.query.get();
            if(!current.equals(lastQuery[0])) {
                lastQuery[0]=current;
                if(!current.equals(query.text().get()))query.reset(current);
            }
        });
        SceneNode field=SceneTextField.create(p,query,theme,() -> false,submission -> {
            if(query.accept(submission,submission.text))model.setQuery(submission.text);
        },SceneTextField.SubmitPolicy.INPUT,() -> "搜索邮件",false);
        SceneNode search=MailStyle.surface(theme,() -> field.runtime()!=null&&field.runtime().focusRouter().focused().get()==field
                ?palette.get().surface:palette.get().search,1.6f).axis(SceneNode.Axis.ROW)
                .size(Size.fill(3).bounds(0,720),Size.natural()).gap(4).padding(4).own(scope);
        search.control(new MailRaisedSurface(theme,() -> field.runtime()!=null&&field.runtime().focusRouter().focused().get()==field
                ?palette.get().surface:palette.get().search,1.6f,
                () -> field.runtime()!=null&&field.runtime().focusRouter().focused().get()==field,100000000L));
        search.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,4)).gap(MailStyle.px(t,4))
                .size(Size.fill(3).bounds(0,MailStyle.px(t,720)),Size.natural().bounds(MailStyle.px(t,48),Integer.MAX_VALUE))));
        search.add(SceneIconButton.graphic(p,() -> SceneIconButton.Symbol.SEARCH,
                () -> theme.get().withTextStyle(theme.get().textStyle.withColor(palette.get().mutedText))).padding(8));
        field.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,4)).style(t.textStyle.withEm(t.textStyle.logicalEm*16f/14f))));search.add(field);
        SceneNode clear=SceneIconButton.create(p,SceneIconButton.Symbol.CLOSE,() -> "清空搜索",theme,
                () -> {query.reset("");model.setQuery("");});
        clear.bind(NodeBinding.value(() -> !query.text().get().isEmpty(),SceneNode::visible));search.add(clear);
        return search;
    }
}
