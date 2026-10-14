package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 阅读面直接消费当前消息事实；动作回调读取当前id，不捕获旧Message。 */
final class MailReader {
    private MailReader() {}
    static SceneNode create(ScenePageContext p,ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette,MailboxModel model) {
        ReadableSignal<Boolean> available=() -> model.opened.get()!=null;
        SceneNode reader=MailStyle.surface(theme,() -> palette.get().surface,0).size(Size.fill(3),Size.fill(1));
        reader.bind(NodeBinding.value(model.reading,SceneNode::visible));
        SceneNode tools=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW)
                .size(Size.fill(1),Size.natural()).padding(8).gap(8);
        tools.bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,48),Integer.MAX_VALUE))));
        tools.add(SceneIconButton.create(p,SceneIconButton.Symbol.CHEVRON_LEFT,() -> "返回列表",theme,model::back));
        tools.add(SceneIconButton.create(p,SceneIconButton.Symbol.ARCHIVE,() -> "归档当前邮件",theme,
                () -> model.archive(model.opened.get().id)).bind(NodeBinding.value(available,SceneNode::enabled)));
        tools.add(SceneIconButton.create(p,() -> available.get()&&model.opened.get().read?SceneIconButton.Symbol.MAIL_OPEN:SceneIconButton.Symbol.MAIL,
                () -> "切换当前邮件已读状态",theme,() -> model.setRead(model.opened.get().id,!model.opened.get().read))
                .bind(NodeBinding.value(available,SceneNode::enabled)));
        tools.add(new SceneNode().size(Size.fill(1),Size.natural()));
        tools.add(MailStyle.label(p,() -> available.get()?(model.opened.get().inbox?"收件箱":"已归档"):"邮件已不存在",theme,.8f,true)
                .size(Size.natural(),Size.natural()));reader.add(tools);
        SceneNode message=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).clip(true).gap(18);
        message.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,28)).gap(MailStyle.px(t,20))));
        message.add(MailStyle.label(p,() -> available.get()?model.opened.get().subject:"邮件已不存在",theme,1.6f,false));
        SceneNode sender=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(12).size(Size.fill(1),Size.natural());
        sender.add(MailStyle.surface(theme,() -> palette.get().selected,2).padding(10).size(Size.natural(),Size.natural())
                .add(SceneIconButton.graphic(p,() -> SceneIconButton.Symbol.MAIL_OPEN,theme)));
        SceneNode details=new SceneNode().size(Size.fill(1),Size.natural()).gap(3);
        details.add(MailStyle.label(p,() -> available.get()?model.opened.get().sender:"",() -> theme.get().withTextStyle(
                theme.get().textStyle.withFontStyle(new FontStyle(600,FontStyle.Posture.NORMAL))),1f,false));
        details.add(MailStyle.label(p,() -> available.get()?"发送给团队  ·  "+model.opened.get().time:"",theme,.85f,true));sender.add(details);
        sender.add(SceneIconButton.create(p,() -> available.get()&&model.opened.get().starred?SceneIconButton.Symbol.STAR_FILLED:SceneIconButton.Symbol.STAR,
                () -> "切换当前邮件星标",() -> theme.get().withTextStyle(theme.get().textStyle.withColor(
                        available.get()&&model.opened.get().starred?0xfff4b400:palette.get().mutedText)),
                () -> model.toggleStar(model.opened.get().id)).bind(NodeBinding.value(available,SceneNode::enabled)));
        message.add(sender);
        SceneNode body=MailStyle.label(p,() -> available.get()?model.opened.get().body:"请选择其他邮件继续阅读。",theme,1f,false);
        body.bind(NodeBinding.value(theme,(n,t) -> n.textPolicy(new TextPolicy(TextPolicy.Direction.AUTO,TextPolicy.Wrap.NORMAL,
                n.textPolicy().language,0,t.textStyle.logicalEm*1.75f))));message.add(body);reader.add(message);
        return reader;
    }
}
