package club.heiqi.uilib.config;

/**
 * Forge 配置模板的纯文本消息工具。
 */
final class ForgeConfigTemplateMessages {

    private ForgeConfigTemplateMessages() {}

    /**
     * 解析空状态区文本。
     *
     * @param emptyTemplateText 无可展示配置项时的默认文案
     * @param missingCategoriesMessage 缺失分类提示文案
     * @return 空状态区应展示的文本
     */
    static String resolveEmptyStateMessage(String emptyTemplateText, String missingCategoriesMessage) {
        if (missingCategoriesMessage != null && !missingCategoriesMessage.isEmpty()) {
            return missingCategoriesMessage;
        }
        return emptyTemplateText == null ? "" : emptyTemplateText;
    }
}
