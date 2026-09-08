package club.heiqi.uilib.api.chat;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/** 聊天工具栏动作注册表契约（规划《聊天工具栏与HUD布局编辑》P1 最小闭环）。 */
public class ChatActionServiceTest {

    @Before
    public void setUp() {
        ChatActionService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ChatActionService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    private static ChatAction action(String id, int order) {
        return ChatAction.builder(id).label(id).order(order)
                .visible(Signal.create(Boolean.TRUE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(new Runnable() {
                    @Override
                    public void run() {
                    }
                }).build();
    }

    @Test
    public void ordersByOrderThenRegistration() {
        ChatActionService service = ChatActionService.getInstance();
        service.register(action("b", 10));
        service.register(action("a", 0));
        service.register(action("c", 10));
        List<ChatAction> actions = service.actions();
        Assert.assertEquals(3, actions.size());
        Assert.assertEquals("a", actions.get(0).getId());
        Assert.assertEquals("b", actions.get(1).getId());
        Assert.assertEquals("c", actions.get(2).getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdIsRejected() {
        ChatActionService service = ChatActionService.getInstance();
        service.register(action("dup", 0));
        service.register(action("dup", 1));
    }

    @Test
    public void closeUnregistersIdempotently() {
        ChatActionService service = ChatActionService.getInstance();
        ChatActionRegistration registration = service.register(action("x", 0));
        registration.close();
        registration.close();
        Assert.assertTrue(registration.isClosed());
        Assert.assertTrue(service.actions().isEmpty());
        // 注销后可复用同一 id 重新注册
        service.register(action("x", 0));
        Assert.assertEquals(1, service.actions().size());
    }
}
