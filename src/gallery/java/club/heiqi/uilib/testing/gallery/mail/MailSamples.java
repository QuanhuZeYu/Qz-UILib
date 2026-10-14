package club.heiqi.uilib.testing.gallery.mail;

import java.util.List;
import java.util.ArrayList;
import club.heiqi.uilib.testing.gallery.mail.MailboxModel.Message;

/** 确定的本地交接邮件，数据事件与页面实现分离。 */
public final class MailSamples {
    private MailSamples() {}
    public static List<Message> messages(int count) {
        String[] senders = {"林晓 · 产品设计", "陈默 · 工程协作", "Workshop Weekly", "资源维护团队", "周宁 · 项目运营"};
        String[] subjects = {"周五的设计评审：收件箱与阅读体验", "构建已完成，等待交接确认", "Café / 本周工坊精选", "字体与语言资料更新通知", "项目排期与下周安排"};
        String[] previews = {"请查看新版信息层级与交互说明，我们希望每个操作都有清晰的反馈。", "正式产物与验证资料已准备好，欢迎在会前阅读。", "一封简短周报：新内容、活动和社区分享。", "同语言资源内容也会更新，请核对本轮文案。", "下周的工作分为设计、实现和验收，详细安排见正文。"};
        List<Message> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int n = i % subjects.length;
            result.add(new Message("mail-" + i, senders[n], subjects[n] + (i < subjects.length ? "" : " · " + (i + 1)),
                    previews[n], "你好，\n\n" + previews[n] + "\n\n这封邮件记录了本次协作的背景与预期。请先阅读说明，再在收件箱中标记需要继续跟进的内容。\n\n需要核对：\n· 页面在不同窗口下的可读性\n· 操作与当前业务对象的一致性\n· 变化后的内容是否及时呈现\n\n谢谢！\n" + senders[n],
                    i < 3 ? "10:" + (28 - i * 7) : "昨天", i % 7 == 2, i % 4 == 1, true));
        }
        return result;
    }
}
