package club.heiqi.uilib.api.chat;

import net.minecraft.util.IChatComponent;

/**
 * 聊天消息装饰器(公共接入 API):消息显示前对组件做变换。
 *
 * <p>注册到 {@link ChatAccess} 后,每条消息在入历史前按注册顺序应用装饰器链。
 * 典型用途:前缀/后缀注入、样式统一、消息过滤。语义契约:</p>
 * <ul>
 *   <li>返回 null 表示丢弃该消息(不显示);返回入参对象表示不修改;</li>
 *   <li>允许返回新组件(替换);实现应保持样式/事件不丢;</li>
 *   <li>装饰器执行于消息到达路径(可能非渲染线程),实现须线程安全或纯函数;</li>
 *   <li>单个装饰器抛异常由框架隔离(该装饰器失效、消息照常入链),不影响链路。</li>
 * </ul>
 */
public interface ChatMessageDecorator {

    /**
     * 装饰一条聊天消息。
     *
     * @param component 原始消息组件(不可为 null)
     * @return 变换后组件;null 表示丢弃该消息
     */
    IChatComponent decorate(IChatComponent component);
}
