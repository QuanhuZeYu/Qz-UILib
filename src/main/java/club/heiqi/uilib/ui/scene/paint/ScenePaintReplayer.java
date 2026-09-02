package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.render.UiRenderBackend;

/**
 * 绘制命令回放器 —— 将纯数据 Display List 翻译为对 {@link UiRenderBackend} 的调用。
 *
 * <p>回放器只通过 scene 渲染出口接口 {@link UiRenderBackend} 认识渲染层，不持有任何
 * 具体后端类（守宪章信条六，scene 核心可脱 MC 移植）。Minecraft 平台下，该接口的
 * 实现是 {@code club.heiqi.uilib.ui.render.UiRenderContext}（直接 LWJGL GL，架构禁令禁用原版包装类）。</p>
 *
 * <h3>回放期零节点反查（宪章信条六/I6）</h3>
 * <p>每条 {@link PaintCommand} 已经是自包含的绘制操作描述（坐标、颜色、文本、样式
 * 全部在构建期固化）。回放器只读取命令字段并映射到 render API，绝不访问任何
 * SceneNode / DOM / 样式系统。这是数据层与渲染层的最终合同执行点。</p>
 *
 * <h3>命令 → Render API 映射</h3>
 * <table>
 *   <tr><th>命令类型</th><th>Render API</th><th>映射说明</th></tr>
 *   <tr><td>BACKGROUND</td><td>{@code ctx.fillRect(left, top, right, bottom, color)} /
 *        {@code ctx.drawSurface(..., fillColor, 0, cornerRadius)} /
 *        {@code ctx.drawSurface(..., fillColor, 0, tl, tr, br, bl)}</td>
 *        <td>直角走 fillRect 快速路径；uniform 圆角走 7 参 drawSurface；四角独立（T4a）走 10 参 drawSurface</td></tr>
 *   <tr><td>TEXT</td><td>{@code ctx.drawText(text, left, top, color, false, fontSizePx)}</td>
 *        <td>shadow=false，字号从 TextStyle 取纯数值传递</td></tr>
 *   <tr><td>PUSH_OPACITY</td><td>{@code ctx.pushGroupOpacity(left, top, right, bottom, opacity)}</td>
 *        <td>Phase 3B：进入 group opacity 合成作用域，传该层局部 opacity（嵌套相乘由渲染层离屏层栈完成）</td></tr>
 *   <tr><td>POP_OPACITY</td><td>{@code ctx.popGroupOpacity()}</td>
 *        <td>Phase 3B：退出 group opacity 作用域，与 PUSH_OPACITY 严格配对</td></tr>
 * </table>
 *
 * <p>T4a：BACKGROUND/BORDER 命令携带四角独立圆角时，回放器把四角以 4 个 {@code int}
 * 纯数值传给 {@link UiRenderBackend} 的四角 drawSurface 重载（后端内部转
 * {@code ResolvedCornerRadii}），scene 层零分角类型依赖（守 I6）。CLIP 本轮仍 uniform。</p>
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

    private static final Logger LOG = LogManager.getLogger("QzUiLib/PaintReplay");

    /** IMAGE 隔离失败一次性告警位（本类设计为回放期零状态，故用类级 static 而非实例字段）。 */
    private static final AtomicBoolean IMAGE_FAILURE_WARNED = new AtomicBoolean();
    private static final AtomicBoolean IMAGE_LINKAGE_WARNED = new AtomicBoolean();

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
        List<String> visibleTexts = new ArrayList<String>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT && command.getTextStyle() != null
                    && command.getText() != null && !command.getText().isEmpty()) {
                visibleTexts.add(command.getText());
            }
        }
        if (!visibleTexts.isEmpty()) {
            ctx.publishTextDemand(Collections.unmodifiableList(visibleTexts));
        }
        Deque<Scope> openScopes = new ArrayDeque<Scope>();
        // 一次 replay = 一个 backdrop 批次：本棵树里所有兄弟玻璃共享同一份背景采样
        // （语义对齐 iOS 同一 visual effect 层级互不透过；性能上 N 次快照捕获降为 1 次）。
        club.heiqi.uilib.ui.render.UiRenderBackends.beginBackdropBatch(ctx);
        try {
            try {
                for (PaintCommand cmd : plan.getCommands()) {
                    replayCommand(cmd, ctx, offsetX, offsetY, openScopes);
                }
            } catch (RuntimeException exception) {
                IllegalStateException cleanupFailure = unwind(ctx, openScopes, exception);
                if (cleanupFailure != null) throw cleanupFailure;
                throw exception;
            } catch (LinkageError error) {
                IllegalStateException cleanupFailure = unwind(ctx, openScopes, error);
                if (cleanupFailure != null) throw cleanupFailure;
                throw error;
            }
        } finally {
            // 批次必须无条件收尾：中途抛异常若漏掉 end，冻结的 revision 会让后续帧
            // 永远看不到新内容（玻璃停在旧背景上）。
            club.heiqi.uilib.ui.render.UiRenderBackends.endBackdropBatch(ctx);
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
    private void replayCommand(PaintCommand cmd, UiRenderBackend ctx, int offsetX, int offsetY,
            Deque<Scope> openScopes) {
        switch (cmd.getType()) {
            case BACKDROP: {
                // 声明式玻璃：坐标与 fragment 偏移同域（logical px），换算与 scaled 穿透
                // 由门面统一负责——replayer 不碰 GL、不猜后端类型（宪章信条六）。
                // 四角圆角复用 BORDER 的口径：无分角时退化为 uniform。
                club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver.ResolvedCornerRadii radii =
                        cmd.hasPerCornerRadii()
                                ? club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver.ResolvedCornerRadii.of(
                                        cmd.getCornerRadiusTopLeft(), cmd.getCornerRadiusTopRight(),
                                        cmd.getCornerRadiusBottomRight(), cmd.getCornerRadiusBottomLeft())
                                : club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver.ResolvedCornerRadii
                                        .uniform(cmd.getCornerRadius());
                club.heiqi.uilib.ui.render.UiRenderBackends.backdropFilter(ctx,
                        cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY,
                        cmd.getBackdrop(), radii);
                break;
            }

            case BACKGROUND:
                if (cmd.hasPerCornerRadii()) {
                    // 四角独立圆角背景（T4a）：四角数值直接喂后端四角重载（后端内部转 ResolvedCornerRadii）
                    ctx.drawSurface(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY,
                            cmd.getColor(), 0,
                            cmd.getCornerRadiusTopLeft(), cmd.getCornerRadiusTopRight(),
                            cmd.getCornerRadiusBottomRight(), cmd.getCornerRadiusBottomLeft());
                } else if (cmd.getCornerRadius() <= 0) {
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
                if (cmd.hasPerCornerRadii()) {
                    // 四角独立圆角边框（T4a）：fillColor=0 只描边（drawSurface 内部 fillColor!=0 才填充）
                    ctx.drawSurface(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY,
                            0, cmd.getColor(),
                            cmd.getCornerRadiusTopLeft(), cmd.getCornerRadiusTopRight(),
                            cmd.getCornerRadiusBottomRight(), cmd.getCornerRadiusBottomLeft());
                } else if (cmd.getCornerRadius() <= 0) {
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
                openScopes.push(Scope.CLIP);
                break;

            case CLIP_POP:
                // Phase 4：退出裁剪作用域，与 CLIP_PUSH 严格配对。
                ctx.popClip();
                removeTop(openScopes, Scope.CLIP);
                break;

            case TEXT:
                TextStyle style = cmd.getTextStyle();
                if (style != null) {
                    ctx.drawText(cmd.getText(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            style.getColor(), false, style.getFontSize(), style.getMode());
                }
                break;

            case SEGMENTS:
                TextStyle segmentsStyle = cmd.getTextStyle();
                if (cmd.getSegments() != null && segmentsStyle != null) {
                    ctx.drawSegments(cmd.getSegments(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            segmentsStyle.getFontSize());
                }
                break;

            case IMAGE:
                try {
                    ctx.drawImage(cmd.getImageSource(), cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                            cmd.getRight() + offsetX, cmd.getBottom() + offsetY);
                } catch (RuntimeException isolated) {
                    // 单张宿主图片失败不得中断后续 Display List 回放（隔离语义不变）；
                    // 首次留 WARN，避免"图片静默消失"无痕迹（对齐 5d-D5 / GlAttribDepth 先例）。
                    if (IMAGE_FAILURE_WARNED.compareAndSet(false, true)) {
                        LOG.warn("IMAGE 命令绘制失败（后续同类不再告警，回放继续）：{}", isolated.toString());
                    }
                } catch (LinkageError isolated) {
                    // 可选宿主类型链接失败时同样隔离。
                    if (IMAGE_LINKAGE_WARNED.compareAndSet(false, true)) {
                        LOG.warn("IMAGE 命令宿主类型链接失败（后续同类不再告警，回放继续）：{}", isolated.toString());
                    }
                }
                break;

            case PUSH_OPACITY:
                // Phase 3B：进入 group opacity 合成作用域。区域叠加屏幕偏移后传给渲染层离屏层栈。
                // opacity 传该层局部值（绘制引擎已保证传局部值非累计值），嵌套相乘由渲染层离屏层栈天然完成。
                ctx.pushGroupOpacity(cmd.getLeft() + offsetX, cmd.getTop() + offsetY,
                        cmd.getRight() + offsetX, cmd.getBottom() + offsetY, cmd.getOpacity());
                openScopes.push(Scope.OPACITY);
                break;

            case POP_OPACITY:
                // Phase 3B：退出 group opacity 合成作用域，与 PUSH_OPACITY 严格配对。
                ctx.popGroupOpacity();
                removeTop(openScopes, Scope.OPACITY);
                break;

            case PUSH_TRANSFORM:
                // 方案甲：进入 transform 顶点变换作用域。7 个浮点分量从命令 getter 取，
                // 喂给纯数值 pushTransform 重载（全 primitive，守 I6），GL 矩阵栈完成 origin 三明治。
                ctx.pushTransform(cmd.getTranslateX(), cmd.getTranslateY(), cmd.getRotateDegrees(),
                        cmd.getScaleX(), cmd.getScaleY(), cmd.getOriginXRatio(), cmd.getOriginYRatio(),
                        cmd.getLeft(), cmd.getTop(), cmd.getRight(), cmd.getBottom());
                openScopes.push(Scope.TRANSFORM);
                break;

            case POP_TRANSFORM:
                // 方案甲：退出 transform 顶点变换作用域，与 PUSH_TRANSFORM 严格配对（glPopMatrix）。
                ctx.popTransform();
                removeTop(openScopes, Scope.TRANSFORM);
                break;

            case PUSH_TRANSFORM_LAYER:
                // B6 FBO 方案：进入 transform 离屏图层作用域（transform+clip 叠加正确处理）。
                // 7 个浮点分量从命令 getter 取，喂给 pushTransformLayer（全 primitive，守 I6）。
                // 内部借 FBO 离屏层 + MODELVIEW 归 I + 重建父 clip，段内 scissor 在未变换坐标系下正确裁剪。
                ctx.pushTransformLayer(cmd.getTranslateX(), cmd.getTranslateY(), cmd.getRotateDegrees(),
                        cmd.getScaleX(), cmd.getScaleY(), cmd.getOriginXRatio(), cmd.getOriginYRatio(),
                        cmd.getLeft(), cmd.getTop(), cmd.getRight(), cmd.getBottom());
                openScopes.push(Scope.TRANSFORM_LAYER);
                break;

            case POP_TRANSFORM_LAYER:
                // B6 FBO 方案：退出 transform 离屏图层作用域，与 PUSH_TRANSFORM_LAYER 严格配对。
                // 内部 end + applyClipSnapshot(父) + pushTransform(T) + composite 回贴 + popTransform。
                ctx.popTransformLayer();
                removeTop(openScopes, Scope.TRANSFORM_LAYER);
                break;

            default:
                break;
        }
    }

    /** 成功执行显式 POP 后，从跟踪栈移除对应作用域。 */
    private static void removeTop(Deque<Scope> openScopes, Scope expected) {
        if (!openScopes.isEmpty() && openScopes.peek() == expected) openScopes.pop();
    }

    /**
     * 异常退出时按真实进入顺序的逆序尽力关闭全部作用域。
     *
     * @return 清理全部成功时为 {@code null}；否则返回不会被帧边界消费的普通异常
     */
    private static IllegalStateException unwind(UiRenderBackend ctx, Deque<Scope> openScopes,
            Throwable originalFailure) {
        IllegalStateException failure = null;
        while (!openScopes.isEmpty()) {
            Scope scope = openScopes.pop();
            try {
                scope.close(ctx);
            } catch (RuntimeException cleanupFailure) {
                if (failure == null) {
                    failure = new IllegalStateException("Display List scope unwind failed", originalFailure);
                }
                failure.addSuppressed(cleanupFailure);
            } catch (LinkageError cleanupFailure) {
                if (failure == null) {
                    failure = new IllegalStateException("Display List scope unwind failed", originalFailure);
                }
                failure.addSuppressed(cleanupFailure);
            }
        }
        return failure;
    }

    /** 回放器需要异常回滚的四类作用域。 */
    private enum Scope {
        CLIP { @Override void close(UiRenderBackend ctx) { ctx.popClip(); } },
        OPACITY { @Override void close(UiRenderBackend ctx) { ctx.popGroupOpacity(); } },
        TRANSFORM { @Override void close(UiRenderBackend ctx) { ctx.popTransform(); } },
        TRANSFORM_LAYER { @Override void close(UiRenderBackend ctx) { ctx.popTransformLayer(); } };

        abstract void close(UiRenderBackend ctx);
    }
}