package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 消息主体与次级动作独立，回调总读取同键当前值。 */
final class MessageRow {
    private MessageRow() {}
    static SceneNode create(ScenePageContext p, ReadableSignal<MailboxModel.Item> item,
                            MailboxModel model, ReadableSignal<SceneTheme> theme) {
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
        SceneNode open = SceneButton.create(p, () -> {
            MailboxModel.Message m = item.get().value.get();
            return (m.read ? "" : "未读 · ") + m.sender + "\n" + m.subject + "\n" + m.preview;
        }, () -> MailStyle.buttonTheme(theme.get(), item.get().id.equals(model.openedId.get())),
                () -> model.open(item.get().id));
        row.add(open);
        row.add(MailStyle.label(p, () -> item.get().value.get().time, theme, 0.85f, true).size(Size.natural(), Size.natural()));
        row.bind(NodeBinding.value(theme, (n,t) -> n.padding(Math.max(4,t.padding / 2))));
        return row;
    }
}
