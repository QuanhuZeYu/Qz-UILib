package club.heiqi.uilib.host.client.localization;

import java.util.Map;

/** MC加载完成边界的正式读取合同；返回原版与Forge已合并表，由服务立即复制。 */
public interface LanguageManagerAccess {
    String qz$languageCode();
    Map<String, String> qz$translations();
}
