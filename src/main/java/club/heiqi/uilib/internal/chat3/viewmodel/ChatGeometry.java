package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 几何排版(L2 视图模型,纯函数):合成组 → 定位组(气泡矩形/组头/消息矩形)。
 *
 * <p>两种排版: </p>
 * <ul>
 *   <li>HUD:自底向上紧密堆叠(最新组在底,组间距小);</li>
 *   <li>容器:自上而下(旧在上),内容坐标从 0 起,滚动平移与可视裁剪由渲染层完成;</li>
 *   <li>对齐:自己组右缘钉右边界 / 他人组左缘钉左边界 / 系统组居中;</li>
 *   <li>命中检测:点 → 消息记录(事件链经记录组件回投)。</li>
 * </ul>
 */
public final class ChatGeometry {

    /** 度量注入(与 Layouter/Composer 同源;渲染侧接 TextLayoutService.advance)。 */
    public interface Measure {

        /** 文本宽度(UI px,指定字号口径;§ 格式码对零宽)。 */
        float advance(String text, int fontSizePx);
    }

    /** 消息气泡矩形(含内边距;系统消息 = 纯文本矩形)。 */
    public static final class PositionedMessage {

        private final ChatLineRecord record;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private PositionedMessage(ChatLineRecord record, int x, int y, int width, int height) {
            this.record = record;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public ChatLineRecord getRecord() {
            return record;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    /** 定位后的组:组矩形(含组头)+ 消息矩形列表(绝对坐标)。 */
    public static final class PositionedGroup {

        private final ChatCardComposer.ComposedGroup group;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int headerX;
        private final int headerY;
        private final List<PositionedMessage> messages;

        private PositionedGroup(ChatCardComposer.ComposedGroup group, int x, int y, int width, int height,
                int headerX, int headerY, List<PositionedMessage> messages) {
            this.group = group;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.headerX = headerX;
            this.headerY = headerY;
            this.messages = messages;
        }

        public ChatCardComposer.ComposedGroup getGroup() {
            return group;
        }

        /** @return 组左上角 x(系统组 = 文本左缘) */
        public int getX() {
            return x;
        }

        /** @return 组左上角 y(组顶,含组头) */
        public int getY() {
            return y;
        }

        /** @return 组宽(系统组 = 文本宽,无气泡) */
        public int getWidth() {
            return width;
        }

        /** @return 组高(含组头与内边距) */
        public int getHeight() {
            return height;
        }

        /** @return 组头文本 x(系统组无组头,= x) */
        public int getHeaderX() {
            return headerX;
        }

        /** @return 组头文本 y */
        public int getHeaderY() {
            return headerY;
        }

        /** @return 消息气泡矩形列表(时间正序,绝对坐标) */
        public List<PositionedMessage> getMessages() {
            return Collections.unmodifiableList(messages);
        }
    }

    private ChatGeometry() {
    }

    /**
     * HUD 形态排版:自底向上紧密堆叠(最新组在底)。
     *
     * @return 定位组(时间正序,坐标已按 HUD 自底向上排好)
     */
    public static List<PositionedGroup> layoutHud(List<ChatCardComposer.ComposedGroup> groupsTimeAsc, Measure measure,
            int windowWidth, int windowHeight, int margin, int paddingX, int paddingY,
            int fontSize, int lineHeight, int headerFontSize, int groupInnerGap, int groupGap) {
        List<PositionedGroup> measured = measureGroups(groupsTimeAsc, measure, margin, windowWidth - margin,
                paddingX, paddingY, fontSize, lineHeight, headerFontSize, groupInnerGap);
        List<PositionedGroup> result = new ArrayList<PositionedGroup>();
        int bottom = windowHeight - margin;
        for (int i = measured.size() - 1; i >= 0; i--) {
            PositionedGroup group = measured.get(i);
            int y = bottom - group.height;
            result.add(0, translate(group, y));
            bottom = y - groupGap;
        }
        return result;
    }

    /**
     * 容器形态排版:自上而下(旧在上),内容坐标从 0 起。
     *
     * @return 定位组(时间正序,组 y 从 0 递增;滚动平移与裁剪由渲染层完成)
     */
    public static List<PositionedGroup> layoutContainer(List<ChatCardComposer.ComposedGroup> groupsTimeAsc,
            Measure measure, int containerWidth, int paddingX, int paddingY,
            int fontSize, int lineHeight, int headerFontSize, int groupInnerGap, int groupGap) {
        List<PositionedGroup> measured = measureGroups(groupsTimeAsc, measure, 0, containerWidth,
                paddingX, paddingY, fontSize, lineHeight, headerFontSize, groupInnerGap);
        List<PositionedGroup> result = new ArrayList<PositionedGroup>();
        int top = 0;
        for (PositionedGroup group : measured) {
            result.add(translate(group, top));
            top += group.height + groupGap;
        }
        return result;
    }

    /**
     * 命中检测:点 → 消息记录(事件链经记录组件回投原版)。
     *
     * @return 命中的消息记录;未命中返回 null
     */
    public static ChatLineRecord hitTest(List<PositionedGroup> groups, int x, int y) {
        for (PositionedGroup group : groups) {
            for (PositionedMessage message : group.getMessages()) {
                if (x >= message.getX() && x < message.getX() + message.getWidth()
                        && y >= message.getY() && y < message.getY() + message.getHeight()) {
                    return message.getRecord();
                }
            }
        }
        return null;
    }

    /** 量尺寸 + 水平对齐;垂直坐标为组相对值(y = 0 起),由 translate 平移为绝对坐标。 */
    private static List<PositionedGroup> measureGroups(List<ChatCardComposer.ComposedGroup> groupsTimeAsc,
            Measure measure, int leftBase, int rightBase, int paddingX, int paddingY,
            int fontSize, int lineHeight, int headerFontSize, int groupInnerGap) {
        List<PositionedGroup> positioned = new ArrayList<PositionedGroup>();
        for (ChatCardComposer.ComposedGroup group : groupsTimeAsc) {
            boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
            String headerName = group.getHeaderName();
            String headerTime = group.getHeaderTime();
            int headerWidth = 0;
            if (!headerName.isEmpty() || !headerTime.isEmpty()) {
                // 组头 row 双节点(名字 + gap 4 + 时间;名字可为空——自己组默认 showSelfName=false)
                int nameWidth = headerName.isEmpty() ? 0
                        : (int) Math.ceil(measure.advance(headerName, headerFontSize));
                int timeWidth = headerTime.isEmpty() ? 0
                        : (int) Math.ceil(measure.advance(headerTime, headerFontSize));
                headerWidth = nameWidth + (nameWidth > 0 && timeWidth > 0 ? 4 : 0) + timeWidth;
            }
            int contentWidth = 0;
            int linesCount = 0;
            for (ChatCardComposer.MessageLines message : group.getMessages()) {
                contentWidth = Math.max(contentWidth, (int) Math.ceil(message.getMaxLineWidth()));
                linesCount += message.getDisplayLines().size();
            }
            int messageCount = group.getMessages().size();
            int width;
            int height;
            if (system) {
                width = Math.max(contentWidth, 1);
                height = linesCount * lineHeight;
            } else {
                width = Math.max(contentWidth, headerWidth) + 2 * paddingX;
                width = Math.min(width, rightBase - leftBase);
                height = headerFontSize + 2 * paddingY + linesCount * lineHeight
                        + Math.max(0, messageCount - 1) * groupInnerGap;
            }
            int x;
            switch (group.getAlignment()) {
                case SELF_RIGHT:
                    x = rightBase - width;
                    break;
                case SYSTEM_CENTER:
                    x = (leftBase + rightBase - width) / 2;
                    break;
                default:
                    x = leftBase;
                    break;
            }
            int headerX = x + (group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT
                    ? width - paddingX - headerWidth : paddingX);
            int headerY = system ? 0 : paddingY;
            List<PositionedMessage> messages = new ArrayList<PositionedMessage>();
            int cursorY = system ? 0 : paddingY + headerFontSize;
            for (ChatCardComposer.MessageLines message : group.getMessages()) {
                int lineW = (int) Math.ceil(message.getMaxLineWidth());
                int messageWidth = system ? lineW : lineW + 2 * paddingX;
                int messageX;
                if (system) {
                    messageX = x + (width - lineW) / 2;
                } else if (group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT) {
                    messageX = x + width - paddingX - lineW;
                } else {
                    messageX = x + paddingX;
                }
                int messageHeight = message.getDisplayLines().size() * lineHeight;
                messages.add(new PositionedMessage(message.getRecord(), messageX, cursorY,
                        Math.max(messageWidth, 1), messageHeight));
                cursorY += messageHeight + groupInnerGap;
            }
            positioned.add(new PositionedGroup(group, x, 0, width, height, headerX, headerY, messages));
        }
        return positioned;
    }

    /** 组相对坐标(measureGroups 输出,y = 0 起)平移为绝对坐标。 */
    private static PositionedGroup translate(PositionedGroup group, int newY) {
        List<PositionedMessage> shifted = new ArrayList<PositionedMessage>();
        for (PositionedMessage message : group.messages) {
            shifted.add(new PositionedMessage(message.record, message.x, newY + message.y,
                    message.width, message.height));
        }
        return new PositionedGroup(group.group, group.x, newY, group.width, group.height,
                group.headerX, newY + group.headerY, shifted);
    }
}
