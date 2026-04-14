package club.heiqi.uilib.ui.screen;

/**
 * `UiTestDocumentPageController` 的页面局部高频变更探针状态。
 */
final class UiTestMutationProbeState {

    private String actionStateText = "尚未操作";
    private long lastMutationUpdateNanos;
    private int mutationSequence;
    private int mutationSetTextCount;
    private String lastMutationMode = "";
    private String lastMutationText = "";

    /**
     * 响应换行开关变更。
     *
     * @param enabled 是否启用
     */
    void onWrapToggleChanged(boolean enabled) {
        actionStateText = enabled ? "已开启自动换行提示" : "已关闭自动换行提示";
    }

    /**
     * 响应宽度档位变更。
     *
     * @param widthPreset 宽度档位文案
     */
    void onWidthPresetChanged(String widthPreset) {
        actionStateText = "已切换宽度档位到 " + valueOrEmpty(widthPreset);
    }

    /**
     * 响应探针开关变更。
     *
     * @param enabled 是否启用
     * @param selectedMode 当前模式
     * @return 需要写回的样本文本
     */
    MutationTextUpdate onMutationToggleChanged(boolean enabled, String selectedMode) {
        actionStateText = enabled ? "已启用高频字符变更探针" : "已停止高频字符变更探针";
        return resetMutationProbeState(true, enabled, selectedMode);
    }

    /**
     * 响应探针模式变更。
     *
     * @param enabled 当前是否启用
     * @param selectedMode 当前模式
     * @return 需要写回的样本文本
     */
    MutationTextUpdate onMutationModeChanged(boolean enabled, String selectedMode) {
        actionStateText = "已切换变更模式到 " + valueOrEmpty(selectedMode);
        return resetMutationProbeState(true, enabled, selectedMode);
    }

    /**
     * 响应探针频率变更。
     *
     * @param selectedRate 当前频率文案
     * @return 需要写回的样本文本
     */
    MutationTextUpdate onMutationRateChanged(String selectedRate) {
        actionStateText = "已切换探针频率到 " + valueOrEmpty(selectedRate);
        return resetMutationProbeState(false, false, lastMutationMode);
    }

    /**
     * 响应手动刷新动作。
     */
    void onManualRefresh() {
        actionStateText = "已刷新当前诊断文本";
    }

    /**
     * 重置探针内部状态。
     *
     * @param resetText 是否恢复样本文本
     * @param enabled 当前是否启用
     * @param selectedMode 当前模式
     * @return 需要写回的样本文本
     */
    MutationTextUpdate resetMutationProbeState(boolean resetText, boolean enabled, String selectedMode) {
        lastMutationMode = valueOrEmpty(selectedMode);
        lastMutationUpdateNanos = 0L;
        mutationSequence = 0;
        mutationSetTextCount = 0;
        lastMutationText = "";
        if (!resetText) {
            return MutationTextUpdate.noChange();
        }
        return MutationTextUpdate.of(enabled
                ? "探针已重置，等待下一次文本变更。"
                : "探针未启用。开启后可以直接观察 `§k` 混淆文本、同长度替换和长文重排在当前容器中的表现。");
    }

    /**
     * 按当前配置推进一次变更探针。
     *
     * @param enabled 是否启用
     * @param selectedMode 当前模式
     * @param selectedRateIndex 当前频率索引
     * @param nowNanos 当前时间
     * @return 需要写回的样本文本
     */
    MutationTextUpdate tickMutation(boolean enabled, String selectedMode, int selectedRateIndex, long nowNanos) {
        if (!enabled) {
            return MutationTextUpdate.noChange();
        }

        String currentMode = valueOrEmpty(selectedMode);
        if (!currentMode.equals(lastMutationMode)) {
            resetMutationProbeState(false, true, currentMode);
        }

        if ("§k渲染".equals(currentMode)) {
            if (lastMutationText.isEmpty()) {
                return applyMutationProbeText(buildObfuscatedProbeText());
            }
            return MutationTextUpdate.noChange();
        }

        long intervalNanos = resolveMutationIntervalNanos(selectedRateIndex);
        if (intervalNanos > 0L && nowNanos - lastMutationUpdateNanos < intervalNanos) {
            return MutationTextUpdate.noChange();
        }

        lastMutationUpdateNanos = nowNanos;
        mutationSequence++;
        if ("同长替换".equals(currentMode)) {
            return applyMutationProbeText(buildStableWidthProbeText(mutationSequence));
        }
        return applyMutationProbeText(buildLongReflowProbeText(mutationSequence));
    }

    /**
     * 获取最近操作文案。
     *
     * @return 最近操作文案
     */
    String getActionStateText() {
        return actionStateText;
    }

    /**
     * 获取真实的 `setText()` 次数。
     *
     * @return 次数
     */
    int getMutationSetTextCount() {
        return mutationSetTextCount;
    }

    /**
     * 获取当前模式。
     *
     * @return 当前模式
     */
    String getLastMutationMode() {
        return lastMutationMode;
    }

    /**
     * 获取最近一次探针文本。
     *
     * @return 最近文本
     */
    String getLastMutationText() {
        return lastMutationText;
    }

    /**
     * 应用新的探针文本，并统计真实的 `setText()` 次数。
     *
     * @param text 新文本
     * @return 需要写回的样本文本
     */
    private MutationTextUpdate applyMutationProbeText(String text) {
        String normalizedText = text == null ? "" : text;
        if (normalizedText.equals(lastMutationText)) {
            return MutationTextUpdate.noChange();
        }
        lastMutationText = normalizedText;
        mutationSetTextCount++;
        return MutationTextUpdate.of(normalizedText);
    }

    /**
     * 解析当前探针频率。
     *
     * @param selectedRateIndex 频率索引
     * @return 纳秒间隔；0 表示每帧
     */
    private long resolveMutationIntervalNanos(int selectedRateIndex) {
        if (selectedRateIndex == 1) {
            return 50_000_000L;
        }
        if (selectedRateIndex == 2) {
            return 200_000_000L;
        }
        return 0L;
    }

    /**
     * 构造只依赖 `§k` 绘制随机字符的样本文本。
     *
     * @return 混淆样本
     */
    private String buildObfuscatedProbeText() {
        return "§7§kQZUILIB-DIAGNOSTIC-STREAM-00000000-ABCDEFGHIJKLMNOPQRSTUVWXYZ§r\n"
                + "§7这个模式不会持续调用 setText，而是依赖 §k 在绘制阶段随机替换字符。若它本身也出现明显尖峰，更像是字体绘制或 glyph 准备在放大耗时。§r";
    }

    /**
     * 构造同长度高频替换样本。
     *
     * @param sequence 当前序号
     * @return 同长度样本
     */
    private String buildStableWidthProbeText(int sequence) {
        return "同长替换样本 " + formatCounter(sequence) + " / token=" + buildRollingToken(sequence, 24)
                + " / mirror=" + buildRollingToken(sequence * 3 + 7, 24)
                + "。这一模式会高频 setText，但尽量保持字符总长度稳定，用来观察文本替换本身是否会显著拖慢容器。";
    }

    /**
     * 构造会触发换行与布局变化的长文本样本。
     *
     * @param sequence 当前序号
     * @return 长文本样本
     */
    private String buildLongReflowProbeText(int sequence) {
        int extraLength = 6 + Math.abs(sequence % 20);
        return "长文重排样本 " + formatCounter(sequence)
                + "：当前路径片段为 assets/qz_uilib/ui/diagnostic/" + buildRollingToken(sequence, extraLength)
                + "，描述串为 `" + buildRollingToken(sequence * 5 + 11, extraLength + 10)
                + "`。这一模式会持续改变长文本长度和断行位置，用来观察容器换行、最小宽度传播和布局失效是否出现明显放大。";
    }

    /**
     * 构造滚动字母数字串。
     *
     * @param seed 种子
     * @param length 长度
     * @return 结果文本
     */
    private String buildRollingToken(int seed, int length) {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            int alphabetIndex = Math.abs(seed + index * 7) % alphabet.length();
            builder.append(alphabet.charAt(alphabetIndex));
        }
        return builder.toString();
    }

    /**
     * 格式化固定宽度计数器。
     *
     * @param value 计数值
     * @return 补零字符串
     */
    private String formatCounter(int value) {
        String raw = Integer.toString(Math.max(0, value));
        StringBuilder builder = new StringBuilder();
        for (int index = raw.length(); index < 6; index++) {
            builder.append('0');
        }
        builder.append(raw);
        return builder.toString();
    }

    /**
     * 规范化可选文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 表示一次样本文本写回决策。
     */
    static final class MutationTextUpdate {

        static final MutationTextUpdate NO_CHANGE = new MutationTextUpdate(false, "");

        final boolean shouldApplyText;
        final String text;

        private MutationTextUpdate(boolean shouldApplyText, String text) {
            this.shouldApplyText = shouldApplyText;
            this.text = text;
        }

        static MutationTextUpdate noChange() {
            return NO_CHANGE;
        }

        static MutationTextUpdate of(String text) {
            return new MutationTextUpdate(true, text == null ? "" : text);
        }
    }
}
