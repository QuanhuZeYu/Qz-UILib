package club.heiqi.uilib.ui.config;

import java.util.Properties;

/** 配置类型的存储与不可变快照边界。owns 必须声明键自身；标量仅占用自身，
 * 复合类型占用自身和完整的点分隔子树，decode 再检查其中的合法结构。
 * decode 在命名空间完全缺失时返回 null；显式空值必须返回相应的有效值。
 * 自定义复合类型必须在 snapshot 中复制并冻结所有向外发布的可变内容。 */
public interface ConfigCodec<T> {
    T decode(Properties properties, String name);
    void encode(Properties properties, String name, T value);
    T snapshot(T value);
    boolean owns(String name, String propertyName);
}
