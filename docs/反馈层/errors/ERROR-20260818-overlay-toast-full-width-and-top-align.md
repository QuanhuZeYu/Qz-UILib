# ERROR-20260818-overlay-toast-full-width-and-top-align.md

**日期**：2026-08-18
**组件**：`ui.scene.control`（SceneToast / SceneDialog）+ `ui.scene.runtime`（SceneRuntime）
**状态**：已修复（布局收口 + 生命周期桥，headless 回归测试锚定位置/尺寸断言）

## 现象

浮层改进任务（动画/居中/API 化）调研时发现两个真机显示缺陷，均在 headless 测试绿的前提下存在：

1. **Toast 背景条横贯全窗口**：通知内容实际只占一小段，但深色背景被拉满整行宽，文字靠左。
2. **Toast 在窗口顶部、Dialog 卡片在窗口顶部**：SceneToastTest 注释声称「toast 在底部」，
   SceneDialog 文档声称「居中卡片」，但两者实际都贴在窗口顶部（Dialog 仅水平居中）。
   测试只断言结构（挂载/子节点数/到期）与回调，没有位置与尺寸断言，于是「底部/居中」注释漂了。

## 根因（三条叠加）

### 根因 1：column 容器纵向对齐在「高度=内容高」时静默退化

overlay 根节点布局约束 = 宿主全尺寸（SceneFramePipeline.layoutOverlays 无锚点分支），
但 toast 堆叠容器与 dialog scrim 都是 `SceneNode.column()` 且未设 `fillParentHeight`。
`SizingCalculator.computeHeight` 非 fill/grow/percent 分支直接返回内容高
（`max(内容高, 约束高)` 的 fill 分支不命中）→ 容器高 = 内容高。

`FlexLayouter.containerMainExtent` 对非 fill 容器 extent==contentExtent →
mainAvail == 内容占用 → `MainAxisAlign.END/CENTER` 的 offset 恒为 0：
**「底部堆叠」「垂直居中」静默退化为 START（顶部）**。这不是 bug 而是引擎既定语义——
纵向对齐需要容器有高度盈余，盈余来自 fillParentHeight/grow/percentHeight。

### 根因 2：column 容器子默认 FILL 被 STRETCH 拉满 cross 宽

toast 条目是 ROW 容器，默认 `SceneNode.WidthSizing.FILL`（SceneLayoutProps 默认值）。
在 column 父容器（cross=宽）里被 STRETCH 改写为父内宽 → 每条 toast 背景条横贯全窗口，
「水平居中」（容器 crossAxisAlign=CENTER）在子宽==可用宽时 offset 恒 0，形同虚设。
同款规则见 ERROR-20260818-playground-row-button-fill-and-dual-mount（ROW 行内控件同理）。

### 根因 3（顺带修复）：toast Host 资源挂在「首次 show 的页面 Owner」上

`SceneToast.Host` 构造发生在首次 `show()` 调用点（页面 build/handler 上下文），
其 portal 与到期绑定随当时的页面 Owner 走：页面卸载 → portal/bind 全部退订，
但 Host 仍被 runtime 级 WeakHashMap 缓存 → 后续页面再 show 静默失效（不显示、不到期）。
修复：`SceneRuntime` 增加 internal 桥 `__runRoot`，Host 的 portal/到期绑定显式挂 root
owner，与 runtime 同寿（页面切换不中断通知服务；runtime.dispose 统一清理）。

## 修复（浮层改进任务一并落地）

1. **Toast**：条目 `setWidthSizing(SHRINK)`（内容宽度）+ 容器 crossAxisAlign=CENTER
   （水平居中）+ 容器 `setFillParentHeight(true)`（END 底部堆叠真正生效）。
2. **Dialog**：scrim `setFillParentHeight(true)` → 遮罩铺满全屏、卡片垂直居中
   （MainAxisAlign.CENTER 有盈余可分配）；原有水平居中不变。
3. **动画**：toast/dialog 出现淡入 + 自下方 8px 上移、退场淡出（帧时间驱动，
   `__frameTimeNanos` + opacity/presentationOffsetY；opacity 是 group 合成语义，
   对子树整体生效）。toast 到期改为「展示 → 退场 → 移除」状态机；dialog 受控
   visible 桥接为内部延迟卸载，退场期间可取消并重放淡入。
4. **API 化**：`SceneToast.Type`（INFO/SUCCESS/WARNING/ERROR + 类型色点）与
   showInfo/showSuccess/showWarning/showError；`SceneDialog.alert/confirm` 命令式便捷 API。
5. **测试锚定**：SceneToastTest 新增「宽度 < 窗口宽、左距=右距、y+h=窗口高」断言与
   动画中间态断言；SceneDialogTest 新增「遮罩盒=窗口盒、卡片中心=窗口中心」断言与
   alert/confirm/退场动画测试。

## 教训

- **COLUMN 纵向对齐（END/CENTER）只在容器高度有盈余时生效**：无 fillParentHeight/grow/
  percentHeight 时静默退化为 START。写「底部堆叠/垂直居中」布局必须同时补 fill。
- **「内容宽度」在 column 容器里必须显式 SHRINK**：容器子默认 FILL 会被 STRETCH 拉满，
  cross 居中也随之失效。与 ROW 行内控件的 SHRINK 规则对称（见 20260818 前一份复盘）。
- **布局正确性必须用 cachedLayout 实测锚定**：位置/尺寸断言缺失时，「底部」「居中」之类的
  注释会在行为漂移后继续撒谎——本次两个缺陷都在全绿测试下长期存在。
- **跨页面存活的 runtime 级资源必须挂 root owner**：挂在「首次创建时的页面 owner」上，
  页面一切换资源就静默死亡，且无任何报错信号。`SceneRuntime.__runRoot` 是显式入口。
