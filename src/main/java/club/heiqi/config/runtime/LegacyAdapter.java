package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSerializer;

/**
 * 旧式字符串透传适配器，桥接需要原始 YAML/JSON 文本的遗留调用方。
 *
 * <p>方法名保留 {@code getRawJson}/{@code setRawJson} 以维持"原始字符串透传"语义，
 * 但实际格式统一为 YAML（与 {@link Persistence} 默认格式一致）。</p>
 *
 * <p>读路径：{@link Authority#getRaw(String)} 取 {@link ConfigNode} 子树，
 * {@link ConfigSerializer#toString(ConfigNode, ConfigFormat)} 序列化为 YAML 文本。</p>
 *
 * <p>写路径：{@link ConfigSerializer#parse(String, ConfigFormat)} 解析文本为 {@link ConfigNode}，
 * 经 {@link Authority#putRaw(String, Object)} 受控写回。写盘需由 {@link ConfigManager#flushRaw()}
 * 显式触发，{@code setRawJson} 本身不立即持久化。</p>
 *
 * <p>BATCH_SAVE / RELOAD 通知期间 {@code setRawJson} 经 Authority mutation guard
 * fail-closed，抛 {@link ConfigConflictException}（兼容 {@link ConfigException} 签名），
 * 内存 Authority 零变化。</p>
 *
 * <p>所有 Authority 读写与 ConfigManager 保存事务共享同一锁域；构造器包级私有，
 * 仅由 {@link Authority} 创建。本类零依赖 uilib。</p>
 */
public final class LegacyAdapter {

    private final Authority authority;

    /**
     * 包级私有构造器。
     *
     * @param authority 关联的权威快照
     */
    LegacyAdapter(Authority authority) {
        this.authority = authority;
    }

    /**
     * 取指定路径的原始 YAML 文本。
     *
     * @param path 字段路径
     * @return YAML 文本，路径不存在返回空串
     */
    public String getRawJson(String path) {
        ConfigNode node = authority.getRaw(path);
        if (node == null || node.isNull()) {
            return "";
        }
        return ConfigSerializer.toString(node, ConfigFormat.YAML);
    }

    /**
     * 写回原始 YAML 文本到指定路径。
     *
     * <p>仅修改内存权威态，不立即写盘。需调用 {@link ConfigManager#flushRaw()} 持久化。
     * 通知期内抛 {@link ConfigConflictException}，不改内存。</p>
     *
     * @param path 字段路径
     * @param json YAML 文本
     * @throws ConfigException 文本解析失败或通知期封锁
     */
    public void setRawJson(String path, String json) throws ConfigException {
        ConfigNode parsed = ConfigSerializer.parse(json, ConfigFormat.YAML);
        authority.putRaw(path, parsed);
    }
}
