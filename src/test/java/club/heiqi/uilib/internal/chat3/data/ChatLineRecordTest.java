package club.heiqi.uilib.internal.chat3.data;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * ChatLineRecord 契约测试:字段不可变、纯文本惰性缓存(首次调用后不再触碰组件)。
 */
public class ChatLineRecordTest {

    @Test
    public void shouldExposeImmutableFields() {
        ChatComponentText component = new ChatComponentText("hello");
        ChatLineRecord record = new ChatLineRecord(component, 42, 123456789L);

        Assert.assertSame(component, record.getComponent());
        Assert.assertEquals(42, record.getMessageId());
        Assert.assertEquals(123456789L, record.getArrivedWallMillis());
    }

    @Test
    public void shouldRejectNullComponent() {
        try {
            new ChatLineRecord(null, 1, 0L);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void shouldCachePlainTextLazily() {
        CountingComponent component = new CountingComponent("cached-text");
        ChatLineRecord record = new ChatLineRecord(component, 7, 0L);

        Assert.assertEquals(0, component.unformattedCalls);

        Assert.assertEquals("cached-text", record.getPlainText());
        Assert.assertEquals(1, component.unformattedCalls);

        // 第二次走缓存,不再触碰组件
        Assert.assertEquals("cached-text", record.getPlainText());
        Assert.assertEquals(1, component.unformattedCalls);
    }

    /** 计数 mock:验证惰性缓存只调一次 getUnformattedText。 */
    private static final class CountingComponent implements IChatComponent {

        private final String text;
        private int unformattedCalls = 0;

        CountingComponent(String text) {
            this.text = text;
        }

        @Override
        public IChatComponent setChatStyle(ChatStyle style) {
            return this;
        }

        @Override
        public ChatStyle getChatStyle() {
            return null;
        }

        @Override
        public IChatComponent appendText(String text) {
            return this;
        }

        @Override
        public IChatComponent appendSibling(IChatComponent component) {
            return this;
        }

        @Override
        public String getUnformattedTextForChat() {
            return text;
        }

        @Override
        public String getUnformattedText() {
            unformattedCalls++;
            return text;
        }

        @Override
        public String getFormattedText() {
            return text;
        }

        @Override
        public List<IChatComponent> getSiblings() {
            return Collections.emptyList();
        }

        @Override
        public IChatComponent createCopy() {
            return new CountingComponent(text);
        }

        @Override
        public Iterator<IChatComponent> iterator() {
            return Collections.<IChatComponent>emptyList().iterator();
        }
    }
}
