package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 导航声明只消费内容与动作；内部过渡、悬停和裁剪不依赖邮箱业务模型。 */
final class MailNavigation {
    private final ScenePageContext p;
    private final ReadableSignal<SceneTheme> theme,mutedTheme;
    private final ReadableSignal<MailPalette> palette;
    private final ReadableSignal<Boolean> wide,labeled;
    private final Signal<Float> expansion;
    final SceneNode node;

    MailNavigation(ScenePageContext p,MailViewState view) {
        this.p=p;theme=view.theme;palette=view.palette;wide=view.wide;
        ReadableSignal<Boolean> collapsed=view.collapsed;
        expansion=p.domain().signal(1f);Signal<Float> hoverWidth=p.domain().signal(0f);
        Signal<Boolean> hovered=p.domain().signal(false);
        ReadableSignal<Boolean> preview=() -> wide.get()&&collapsed.get()&&hovered.get();
        labeled=() -> !wide.get()||!collapsed.get()||preview.get();
        mutedTheme=() -> theme.get().withTextStyle(theme.get().textStyle.withColor(theme.get().disabledText));
        // 导航覆盖区自身接住空白点击和滚轮，正式路由不会继续落到下面的邮件列表。
        node=new SceneNode().clip(true).onClick(() -> {}).onScroll(delta -> {});
        node.control(new MailSurface(theme,() -> MailMotion.alpha(palette.get().canvas,hoverWidth.get()),0,false) {
            @Override public boolean passiveHover() {return true;}
        });
        node.bind(NodeBinding.hoverWithin(() -> {
            p.viewportWidth().get();p.viewportHeight().get();theme.get();
            return wide.get()&&collapsed.get();
        },350000000L,(n,value) -> hovered.set(value)));
        node.bind(NodeBinding.transition(() -> preview.get()?1f:0f,150000000L,MailMotion::standard,(n,value) -> hoverWidth.set(value)));
        node.bind(NodeBinding.transition(() -> labeled.get()?1f:0f,150000000L,MailMotion::standard,(n,value) -> expansion.set(value)));
        node.bind(NodeBinding.value(theme,(n,t) -> n.axis(wide.get()?SceneNode.Axis.COLUMN:SceneNode.Axis.ROW).padding(0).gap(0)
                .size(wide.get()?Size.fixed(MailStyle.px(t,collapsed.get()?72+184*hoverWidth.get():256)):Size.fill(1),
                        wide.get()?Size.fill(1):Size.natural())));
    }

    MailNavigation primary(String label,SceneIconButton.Symbol icon,Runnable action) {
        return primary(() -> label,() -> icon,action);
    }
    MailNavigation primary(ReadableSignal<String> label,ReadableSignal<SceneIconButton.Symbol> icon,Runnable action) {
        SceneNode button=MailStyle.surface(theme,() -> palette.get().receive,1.1f)
                .focusable(true).clickFocus(true).onClick(action)
                .bind(NodeBinding.value(label,SceneNode::text));
        button.control(new MailReceiveSurface(theme,() -> palette.get().receive,expansion));
        button.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,16))
                .size(Size.natural(),Size.natural().bounds(MailStyle.px(t,56),Integer.MAX_VALUE))));
        SceneNode content=MailStyle.surface(theme,() -> 0,0,false).axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).clip(true);
        content.bind(NodeBinding.value(theme,(n,t) -> n.gap(MailStyle.px(t,12))));
        content.add(SceneIconButton.graphic(p,icon,theme)
                .bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,24)),Size.fixed(MailStyle.px(t,24))))));
        content.add(MailStyle.single(p,label,theme).bind(NodeBinding.value(labeled,SceneNode::visible)));
        button.axis(SceneNode.Axis.ROW).add(content);
        SceneNode slot=new SceneNode().add(button).bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,8))))
                .bind(NodeBinding.value(wide,SceneNode::visible));
        node.add(slot).add(new SceneNode().bind(NodeBinding.value(theme,(n,t) -> n.size(Size.natural(),Size.fixed(MailStyle.px(t,8)))))
                .bind(NodeBinding.value(wide,SceneNode::visible)));
        return this;
    }

    MailNavigation item(String label,SceneIconButton.Symbol icon,ReadableSignal<Boolean> selected,
                        ReadableSignal<Integer> count,Runnable action) {
        return item(() -> label,() -> icon,selected,count,action);
    }
    MailNavigation item(ReadableSignal<String> label,ReadableSignal<SceneIconButton.Symbol> icon,ReadableSignal<Boolean> selected,
                        ReadableSignal<Integer> count,Runnable action) {
        SceneNode entry=MailStyle.surface(theme,() -> selected.get()?palette.get().navigation:0,16f/14f)
                .axis(SceneNode.Axis.ROW).focusable(true).clickFocus(true).onClick(action)
                .bind(NodeBinding.value(label,SceneNode::text));
        entry.control(new MailNavigationSurface(theme,() -> selected.get()?palette.get().navigation:0,expansion));
        entry.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,4)).gap(0)
                .size(wide.get()?Size.fixed(MailStyle.px(t,32+208*expansion.get())):Size.fill(1),
                        Size.natural().bounds(MailStyle.px(t,32),Integer.MAX_VALUE))));
        entry.add(spacer(() -> wide.get()?2+20*expansion.get():12));
        entry.add(SceneIconButton.graphic(p,icon,() -> theme.get().withTextStyle(theme.get().textStyle.withColor(palette.get().mutedText)))
                .bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,wide.get()?20:28)),Size.fixed(MailStyle.px(t,24))))));
        entry.add(spacer(() -> wide.get()?18f:20f));
        ReadableSignal<SceneTheme> textTheme=() -> theme.get().withTextStyle(theme.get().textStyle.withFontStyle(
                new club.heiqi.uilib.ui.scene.text.FontStyle(selected.get()?700:400,club.heiqi.uilib.ui.scene.text.FontStyle.Posture.NORMAL)));
        SceneNode text=MailStyle.surface(theme,() -> 0,0,false).axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).clip(true);
        text.add(MailStyle.single(p,label,textTheme).size(Size.fill(1),Size.natural()));
        if(count!=null) {
            SceneNode badge=MailStyle.surface(theme,() -> 0,0,false).axis(SceneNode.Axis.ROW).size(Size.natural(),Size.natural());
            badge.add(spacer(() -> 16f)).add(MailStyle.label(p,() -> Integer.toString(count.get()),mutedTheme,.8f,false));
            text.add(badge);
        }
        text.bind(NodeBinding.value(labeled,SceneNode::visible));entry.add(text);
        SceneNode slot=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).clip(true);
        slot.add(spacer(() -> wide.get()?20*(1-expansion.get()):0f)).add(entry);node.add(slot);
        return this;
    }

    MailNavigation footer(String text) {
        node.add(new SceneNode().size(Size.fill(1),Size.fill(1)).bind(NodeBinding.value(wide,SceneNode::visible)));
        node.add(MailStyle.label(p,() -> text,mutedTheme,.8f,false).padding(12)
                .bind(NodeBinding.value(() -> wide.get()&&labeled.get(),SceneNode::visible)));
        return this;
    }
    private SceneNode spacer(ReadableSignal<Float> width) {
        return new SceneNode().bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,width.get())),Size.natural())));
    }
}
