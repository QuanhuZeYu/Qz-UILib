package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.render.UiRenderBackend;

/**
 * 绘制命令回放器 —— 将纯数据 Display List 翻译为对 {@link UiRenderBackend} 的调用。
 *
 * <p>回放器只通过 scene 渲染出口接口 {@link UiRenderBackend} 认识渲染层，不持有任何
 * 具体后端类（守宪章信条六，scene 核心可脱 MC 移植）。Minecraft 平台下，该接口的
 * 实现是 {@code club.heiqi.uilib.ui.render.UiRenderContext}（焊 Tessellator + LWJGL GL）。</p>
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
 *   <tr><td>TEXT</td><td>{@code ctx.drawText(text, left, top, color, false, fontSizePx)}</td>
 *        <td>shadow=false，字号从 TextStyle 取纯数值传递</td></tr>
 *   <tr><td>PUSH_OPACITY</td><td>{@code ctx.pushGroupOpacity(left, top, right, bottom, opacity)}</td>
 *        <td>Phase 3B：进入 group opacity 合成作用域，传该层局部 opacity（嵌套相乘由渲染层离屏层栈完成）</td></tr>
 *   <tr><td>POP_OPACITY</td><td>{@code ctx.popGroupOpacity()}</td>
 *        <td>Phase 3B：退出 group opacity 作用域，与 PUSH_OPACITY 严格配对</td></tr>
 * </table>
 *
 * <p>Phase 4C 方案甲：transform 走 PUSH_TRANSFORM/POP_TRANSFORM 边界命令，回放器从命令 getter
 * 取 7 个浮点分量喂给 {@link UiRenderBackend} 的纯数值 pushTransform 重载（全 primitive，守 I6）。
 * 纯数值重载与 opacity 的 pushGroupOpacity 同构，渲染层零 scene/DOM 认知。</p>
 *
 * <h3>禁止</h3>
 * <ul>
 *   <li>禁止给 UiRenderBackend 加带 scene/DOM 概念的方法；纯数值重载（全 primitive）允许</li>
 *   <li>禁止让 UiRenderBackend 认识 SceneNode / PaintCommand</li>
 *   <li>禁止在回放期访问任何节点/样式/布局对象</li>
 *   <li>禁止 import Transform / UiTransform / SceneNode</li>
 * </ul>
 */
public class ScenePaintReplayer {

    /**
     * 回放 Display List 中的所有命令到渲染上下文。
     *
     * @param plan Display List（由 ScenePaintEngine.paint() 产出）
     * @param ctx  渲染上下文（提供 fillRect / drawText 等底层绘制能力）
     */
    public void replay(PaintPlan plan, UiRenderBackend ctx) {
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
    public void replay(PaintPlan plan, UiRenderBackend ctx, int offsetX, int offsetY) {
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
    private void replayCommand(PaintCommand cmd, UiRenderBackend ctx, int offsetX, int offsetY) {
        switch (cmd.getType()) {
            case BACKGROUND:
                if (cmd.getCornerRadius() <= 0) {
                    // 直角背景：走现有 fillRect 快速路径（零回归）
                    ctx.fillRect(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getColor());
                } else {
                    // 圆角背景：走 drawSurface（fillColor=背景色，borderColor=0 仅填充不描边）
                    ctx.drawSurface(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY,
                            cmd.getColor(), 0, cmd.getCornerRadius());
                }
                break;

            case BORDER:
                if (cmd.getCornerRadius() <= 0) {
                    // 直角边框：走现有 drawBorder（fillColor 无关，只画边框）
                    ctx.drawBorder(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getColor());
                } else {
                    // 圆角边框：走 drawSurface 传 fillColor=0 只描边（drawSurface 内部 fillColor!=0 才填充）
                    ctx.drawSurface(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY,
                            0, cmd.getColor(), cmd.getCornerRadius());
                }
                break;

            case CLIP_PUSH:
                // Phase 4：进入裁剪作用域。区域叠加屏幕偏移后传给渲染层剪切栈，cornerRadius=0 退化为矩形裁剪。
                ctx.pushClip(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getCornerRadius());
                break;

            case CLIP_POP:
                // Phase 4：退出裁剪作用域，与 CLIP_PUSH 严格配对。
                ctx.popClip();
                break;

            case TEXT:
                TextStyle style = cmd.getTextStyle();
                if (style != null) {
                    ctx.drawText(cmd.getText(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            style.getColor(), false, style.getFontSize());
                }
                break;

            case IMAGE:
                try {
                    ctx.drawImage(cmd.getImageSource(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY);
                } catch (RuntimeException ignored) {
                    // 单张宿主图片失败不得中断后续 Display List 回放。
                } catch (LinkageError ignored) {
                    // 可选宿主类型链接失败时同样隔离。
                }
                break;

            case PUSH_OPACITY:
                // Phase 3B：进入 group opacity 合成作用域。区域叠加屏幕偏移后传给渲染层离屏层栈。
                // opacity 传该层局部值（绘制引擎已保证传局部值非累计值），嵌套相乘由渲染层离屏层栈天然完成。
                ctx.pushGroupOpacity(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getOpacity());
                break;

            case POP_OPACITY:
                // Phase 3B：退出 group opacity 合成作用域，与 PUSH_OPACITY 严格配对。
                ctx.popGroupOpacity();
                break;

            case PUSH_TRANSFORM:
                // 方案甲：进入 transform 顶点变换作用域。7 个浮点分量从命令 getter 取，
                // 喂给纯数值 pushTransform 重载（全 primitive，守 I6），GL 矩阵栈完成 origin 三明治。
                ctx.pushTransform(cmd.getTranslateX(), cmd.getTranslateY(), cmd.getRotateDegrees(),
                        cmd.getScaleX(), cmd.getScaleY(), cmd.getOriginXRatio(), cmd.getOriginYRatio(),
                        cmd.getLeft(), cmd.getTop(), cmd.getRight(), cmd.getBottom());
                break;

            case POP_TRANSFORM:
                // 方案甲：退出 transform 顶点变换作用域，与 PUSH_TRANSFORM 严格配对（glPopMatrix）。
                ctx.popTransform();
                break;

            case PUSH_TRANSFORM_LAYER:
                // B6 FBO 方案：进入 transform 离屏图层作用域（transform+clip 叠加正确处理）。
                // 7 个浮点分量从命令 getter 取，喂给 pushTransformLayer（全 primitive，守 I6）。
                // 内部借 FBO 离屏层 + MODELVIEW 归 I + 重建父 clip，段内 scissor 在未变换坐标系下正确裁剪。
                ctx.pushTransformLayer(cmd.getTranslateX(), cmd.getTranslateY(), cmd.getRotateDegrees(),
                        cmd.getScaleX(), cmd.getScaleY(), cmd.getOriginXRatio(), cmd.getOriginYRatio(),
                        cmd.getLeft(), cmd.getTop(), cmd.getRight(), cmd.getBottom());
                break;

            case POP_TRANSFORM_LAYER:
                // B6 FBO 方案：退出 transform 离屏图层作用域，与 PUSH_TRANSFORM_LAYER 严格配对。
                // 内部 end + applyClipSnapshot(父) + pushTransform(T) + composite 回贴 + popTransform。
                ctx.popTransformLayer();
                break;

            default:
                break;
        }
    }
}
