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
 *   <tr><td>PUSH_OPACITY</td><td>{@code ctx.pushPaintContext(left, top, right, bottom, opacity)}</td>
 *        <td>Phase 3B：进入 group opacity 合成作用域，传该层局部 opacity（嵌套相乘由渲染层离屏层栈完成）</td></tr>
 *   <tr><td>POP_OPACITY</td><td>{@code ctx.popPaintContext()}</td>
 *        <td>Phase 3B：退出 group opacity 作用域，与 PUSH_OPACITY 严格配对</td></tr>
 * </table>
 *
 * <p>Phase 3B 的 opacity group 栈与 transform 所需的 {@code pushPaintContext/popPaintContext}
 * 全部已存在于 {@link UiRenderContext}，未触碰「禁止给 UiRenderContext 加方法」红线。
 * transform（只 translate）在绘制引擎侧已编入命令绝对坐标，回放器对 transform 完全无感知（守 D2/I6）。</p>
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

            case PUSH_OPACITY:
                // Phase 3B：进入 group opacity 合成作用域。区域叠加屏幕偏移后传给渲染层离屏层栈。
                // opacity 传该层局部值（绘制引擎已保证传局部值非累计值），嵌套相乘由渲染层离屏层栈天然完成。
                ctx.pushPaintContext(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getOpacity());
                break;

            case POP_OPACITY:
                // Phase 3B：退出 group opacity 合成作用域，与 PUSH_OPACITY 严格配对。
                ctx.popPaintContext();
                break;

            default:
                break;
        }
    }
}
