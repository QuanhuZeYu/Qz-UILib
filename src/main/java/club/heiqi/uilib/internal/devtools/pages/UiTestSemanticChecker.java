package club.heiqi.uilib.internal.devtools.pages;

/**
 * `/qzuilib test` 浏览器语义 checker 边界描述器。
 */
final class UiTestSemanticChecker {

    /**
     * 为样例创建初始双维度结果。
     *
     * @param testCase 样例规格
     * @return 初始结果
     */
    UiTestCaseResult createInitialResult(UiTestCaseSpec testCase) {
        UiTestSemanticStatus semanticStatus = testCase.requiresManualConfirmation()
                ? UiTestSemanticStatus.MANUAL_PENDING : UiTestSemanticStatus.NOT_ASSERTED;
        return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, semanticStatus, UiTestSummaryStatus.PENDING,
                "视觉样例已展示，尚未运行语义检查。", "");
    }

    /**
     * 描述指定分组的自动/人工语义边界。
     *
     * @param group 分组规格
     * @return 语义边界文本
     */
    String describeGroupBoundary(UiTestGroupSpec group) {
        String code = group.getCode();
        if ("DOM".equals(code)) {
            return "自动边界：节点归属、返回值、子节点顺序、textContent、classList、selector 结果；人工边界：暂无计划人工项。";
        }
        if ("CSS".equals(code)) {
            return "自动边界：computed style、继承结果、specificity 结果、可见性与 pointer-events 状态；人工边界：暂无计划人工项。";
        }
        if ("LAYOUT".equals(code)) {
            return "自动边界：布局盒尺寸、位置、margin collapse、flex/table 分配、scroll 范围；人工边界：暂无计划人工项。";
        }
        if ("PAINT".equals(code)) {
            return "自动边界：绘制命令顺序、stacking phase、clip/transform/top-layer 命中；人工边界：重叠层级、host image fallback 和滚动条视觉需截图确认。";
        }
        if ("INPUT".equals(code)) {
            return "自动边界：事件日志、传播顺序、默认行为、focus/focus-visible、wheel 滚动结果；人工边界：游戏内键鼠输入手感需人工确认。";
        }
        if ("CTRL".equals(code)) {
            return "自动边界：value、checked、selection、caret、disabled、change 日志；人工边界：caret 可见性、overlay 位置和交互手感需人工确认。";
        }
        if ("TEXT".equals(code)) {
            return "自动边界：测量宽度、line-height、wrap/trim 摘要、字体 epoch；人工边界：fallback 观感和 obfuscated 可读性需截图确认。";
        }
        if ("ANIM".equals(code)) {
            return "自动边界：timeline 状态、start/end/cancel 日志、最终样式；人工边界：过渡流畅度和视觉节奏需人工确认。";
        }
        if ("HOST".equals(code)) {
            return "自动边界：入口状态、窗口尺寸和 runtime stats 摘要；人工边界：HUD/container 输入、开屏时序和异常面板需游戏内确认。";
        }
        if ("NET".equals(code)) {
            return "自动边界：transport mode 和本地状态摘要；人工边界：服务端往返、远程页面、HUD、配置保存需网络 smoke 确认。";
        }
        return "自动边界：未登记；人工边界：未登记。";
    }

    /**
     * 构建分组诊断摘要。
     *
     * @param group 分组规格
     * @param state 分组状态
     * @return 诊断摘要
     */
    String buildGroupDiagnosticSummary(UiTestGroupSpec group, UiTestGroupState state) {
        return group.getCode() + " 诊断摘要：P0 已建立 registry / builder / checker / state 边界；计划 "
                + group.getPlannedCaseCount() + " 张，已接入 " + state.getImplementedCaseCount()
                + " 张，剩余缺口 " + state.getGapCount() + " 张。";
    }
}
