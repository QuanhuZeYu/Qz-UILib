# 字体批渲染守卫尾状态收口

> 日期：2026-08-13 · 关联提交：`ad07fcd9` · 关联 issue：#49

## 现象

原版 `FontRenderer` 完成一次 drawString 后会遗留三项调用链可见的尾状态：`ALPHA_TEST` 处于 ENABLED、颜色为末字形色 `glColor4f`、绑定当前字体页纹理。UILib 接管原版字体绘制路径时，守卫在还原到进入态后只补回了 `ALPHA_TEST`，颜色与纹理绑定两项与原版尾状态不一致，下游按原版尾状态续画的代码（含其他 mod）会继承到错值。issue #49「光影下世界字体导致箱子变白」疑与此类状态污染相关。

## 根因

- 批渲染 flush 内 `blendFuncSeparate` 的 dst-alpha 使用 `771`（`GL_ONE_MINUS_SRC_ALPHA`），与原版世界路径 `(770, 771, 1, 0)` 不一致，且该混合状态在 flush 后残留给后续调用。
- `glPushAttrib` 只会恢复 pop 时 active unit 的 `TEXTURE_2D` enable；flush 内部先 `glActiveTexture(TEXTURE0)` 切换活动纹理单元再恢复属性，导致各 unit 的 `TEXTURE_2D` enable 恢复错位。
- 以上状态偏差叠加接管路径只补 `ALPHA_TEST` 的尾状态补丁，使 UILib 路径结束态既不同于进入态，也不同于原版尾状态，形成三套互不一致的合同。

## 修复

提交 `ad07fcd9` 收口如下：

- 原时机接管路径（`FontRendererFallbackInvoker.applyVanillaDrawStringTailState`）在守卫 pop 后按原版语义补回三项尾状态：`ALPHA_TEST` ENABLED、末字形色 `glColor4f`、字体页纹理绑定。
- 位移时机路径（延迟标签回放、HUD、deferred flush）不模拟原版尾状态，保持守卫还原进入态不补，避免把原版合同外推给脱离原调用时机的路径。
- `FontRenderStateGuard` push/pop 显式按 unit 快照与恢复 `TEXTURE_2D` enable，不再依赖 `glPushAttrib` 只覆盖 active unit 的语义。
- flush 内 `blendFunc` 改为 `(770, 771, 1, 0)`，与 `fontF.frag` 输出 straight alpha 的语义一致，不再依赖 dst-alpha。

## 预防

- 新增守卫或改动批渲染时必须同时覆盖「push 时 active unit ≠ unit0」单测与尾状态合同测试（`FontRenderStateGuardTest`、`FontRenderTailStateContractTest`），任一不满足不得交付。
- 混合函数改动前先核对 shader 输出是预乘 alpha 还是 straight alpha；dst-alpha 依赖只在预乘语义下成立。
- 尾状态补丁只能施加在原版调用时机被完整接管的路径；延后、回放或 deferred flush 等位移时机路径不得假扮原版尾状态。

## 验证状态

- 静态与自动化：2636 JUnit + checkstyle + build 全绿，含新增 `FontRenderStateGuardTest`、`FontRenderTailStateContractTest` 与 `FontBatchRendererTailStateTest`。
- 运行态：真机场景（光影下世界字体、延迟标签回放、HUD）未验证；issue #49 待用户运行态复测确认箱子变白是否消除。
