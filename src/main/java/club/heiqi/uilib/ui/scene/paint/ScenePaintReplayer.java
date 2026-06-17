package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 绘制命令回放器 —— 将纯数据 Display List 翻译为对 {@link UiRenderContext} 的调用。
 *
 * <h3>回放期零节点反查（宪章信条六/I6）</h3>
 * <p>每条 {@link PaintCommand} 已经是自包含的绘制操作描述（坐标、颜色、文本、样式
 * 全部在构建期固化）。回放器只读取命令字段并映射到 render API，绝不访问任何
 * SceneNode / DOM / 样式系统。这是数据层与渲染层的最终合同执行点。</p>
 *
 * <h3>命令 → Render API 映射</h3>
 * <table>
 *   <tr><th>命令类型</th><th>Render API</th><th>映射说明</th></tr>
 *   <tr><td>BACKGROUND</td><td>{@code ctx.fillRect(left, top, right, bottom, color)}</td>
 *        <td>坐标和颜色直接从命令字段取</td></tr>
 *   <tr><td>TEXT</td><td>{@code ctx.drawText(text, left, top, color, false)}</td>
 *        <td>shadow=false，Phase 1 后可扩展字体样式参数</td></tr>
 * </table>
 *
 * <h3>禁止</h3>
 * <ul>
 *   <li>禁止给 UiRenderContext 加任何方法</li>
 *   <li>禁止让 UiRenderContext 认识 SceneNode / PaintCommand</li>
 *   <li>禁止在回放期访问任何节点/样式/布局对象</li>
 * </ul>
 */
public class ScenePaintReplayer {

    /**
     * 回放 Display List 中的所有命令到渲染上下文。
     *
     * @param plan Display List（由 ScenePaintEngine.paint() 产出）
     * @param ctx  渲染上下文（提供 fillRect / drawText 等底层绘制能力）
     */
    public void replay(PaintPlan plan, UiRenderContext ctx) {
        replay(plan, ctx, 0, 0);
    }

    /**
     * 回放 Display List 中的所有命令到渲染上下文，叠加屏幕偏移。
     *
     * <p>每条 BACKGROUND 和 TEXT 命令的坐标均叠加 (offsetX, offsetY) 后再映射到 render API。
     * 偏移使用 int 类型，绝不引入 style/transform 类型——replayer 零 SceneNode 认知（I6）。</p>
     *
     * @param plan    Display List
     * @param ctx     渲染上下文
     * @param offsetX 屏幕 X 偏移（像素）
     * @param offsetY 屏幕 Y 偏移（像素）
     */
    public void replay(PaintPlan plan, UiRenderContext ctx, int offsetX, int offsetY) {
        if (plan == null || ctx == null) {
            return;
        }
        for (PaintCommand cmd : plan.getCommands()) {
            replayCommand(cmd, ctx, offsetX, offsetY);
        }
    }

    /**
     * 回放单条命令（叠加屏幕偏移）。
     *
     * @param cmd     绘制命令
     * @param ctx     渲染上下文
     * @param offsetX 屏幕 X 偏移
     * @param offsetY 屏幕 Y 偏移
     */
    private void replayCommand(PaintCommand cmd, UiRenderContext ctx, int offsetX, int offsetY) {
        switch (cmd.getType()) {
            case BACKGROUND:
                ctx.fillRect(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getColor());
                break;

            case TEXT:
                TextStyle style = cmd.getTextStyle();
                if (style != null) {
                    ctx.drawText(cmd.getText(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            style.getColor(), false);
                }
                break;

            default:
                break;
        }
    }
}
