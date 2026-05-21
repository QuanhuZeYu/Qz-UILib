# 动画能力框架层最优解审查

- 日期：2026-05-21
- 类型：动画系统能力评估与增强方案审查
- 触发：项目需要评估添加动画能力时框架层的最优解

## 一、现状评估

### 1.1 已实现能力

| 能力 | 状态 | 关键类 |
|------|------|--------|
| Transition（属性过渡） | 完整实现，12 个属性 | `DocumentAnimationTimeline` / `DocumentAnimationRuntimeState` |
| Keyframe Animation（关键帧动画） | 完整实现，多段 stop、fill mode | `DocumentKeyframes` / `DocumentAnimationRuntimeState` |
| 三级影响分层 | PAINT / EFFECT / LAYOUT | `DocumentAnimationImpact` |
| 缓动函数 | 简化版多项式近似 | `DocumentAnimationTimingFunction`（enum） |
| 事件系统 | transitionend / animationend 冒泡 | `DocumentAnimationEventDispatcher` |
| 时间线管理 | 文档级统一管理 | `DocumentAnimationTimeline` |
| Forwards Fill | 已实现 | `DocumentAnimationRuntimeState` 内部 `filledColors` / `filledFloats` |

### 1.2 当前可动画属性（12 个）

| 属性 | Impact | 值类型 |
|------|--------|--------|
| `background-color` | PAINT | COLOR |
| `border-color` | PAINT | COLOR |
| `text-color` | PAINT | COLOR |
| `border-radius` | PAINT | FLOAT |
| `opacity` | EFFECT | FLOAT |
| `backdrop-blur-radius` | EFFECT | FLOAT |
| `width` | LAYOUT | FLOAT |
| `height` | LAYOUT | FLOAT |
| `margin-left` | LAYOUT | FLOAT |
| `margin-right` | LAYOUT | FLOAT |
| `padding-left` | LAYOUT | FLOAT |
| `padding-right` | LAYOUT | FLOAT |

### 1.3 架构设计优点

- 动画值不修改 inline style，作为运行时覆盖层注入 paint/layout 阶段，避免样式循环依赖和不必要的级联重算
- 三级影响分层（PAINT/EFFECT/LAYOUT）精确控制动画对渲染管线的影响范围
- LAYOUT 级动画通过 `LayoutRuntimeValueResolver` 接口注入二次布局，与布局引擎解耦
- PAINT/EFFECT 级动画在 `DocumentPaintEngine` 构建 paint command 时实时查询 timeline
- 动画完成后通过 `DocumentAnimationEventDispatcher` 派发 DOM 事件，支持冒泡

### 1.4 当前缺口

| 缺口 | 影响 |
|------|------|
| 无 `transform` 概念 | 缺少浏览器动画最核心的性能优化手段 |
| 缓动函数精度不足 | 多项式近似代替标准 cubic-bezier，无法支持自定义曲线 |
| 可动画属性覆盖面窄 | 缺少 `top/left/right/bottom`、`gap`、`font-size`、`transform` 子属性 |
| 无 `animation-direction` | 不支持 reverse / alternate |
| 无 infinite iteration | 强制 >= 1 |
| 无 per-property timing function | keyframe 内各段不能独立指定缓动 |
| 无 `animationstart` / `animationiteration` 事件 | 事件覆盖不完整 |
| 无 `transitionstart` / `transitioncancel` 事件 | 事件覆盖不完整 |

## 二、框架层最优解方案

基于当前架构（浏览器语义 UI 框架、惰性布局、paint command 管线），最优解是**分三层递进增强**。

### 第一层：核心缺失补齐（最高投入产出比）

#### 2.1 引入 `transform` 属性（PAINT 级，不触发 reflow）

**优先级：最高。** 浏览器中 90% 的高性能动画都依赖 transform。

设计要点：
- 新增值对象 `UiTransform`，承载 `translateX`/`translateY`/`scaleX`/`scaleY`/`rotate` 与 `transform-origin`
- `ComputedStyle` 新增 `UiTransform getTransform()`
- `DocumentAnimationProperty` 新增 `TRANSLATE_X`/`TRANSLATE_Y`/`SCALE_X`/`SCALE_Y`/`ROTATE`（全部 PAINT 级 FLOAT 类型）
- `DocumentPaintEngine` 在生成 paint command 时，对有 transform 的元素包裹矩阵变换命令
- `DocumentPaintRenderer` 回放时 push/pop GL 矩阵

关键优势：transform 动画**不触发 layout**，只在 paint 阶段应用矩阵，性能最优。

实现路径：
1. `PropertyRuntimeSemantics` 补齐 `resolveBaseFloat` 和 `normalizeDeclaredKeyframeFloat`
2. `DocumentPaintEngine` 新增 `TRANSFORM` paint command 类型
3. `DocumentPaintRenderer` 实现矩阵 push/pop 回放
4. 命中测试需要反向变换坐标

#### 2.2 标准 cubic-bezier 缓动

设计要点：
- 将 `DocumentAnimationTimingFunction` 从 enum 改为接口 + 预定义常量，保持向后兼容
- 接口定义 `float apply(float progress)`
- 预定义常量：`LINEAR`、`EASE`、`EASE_IN`、`EASE_OUT`、`EASE_IN_OUT`
- 新增 `cubicBezier(float x1, float y1, float x2, float y2)` 工厂方法
- 内部使用 Newton-Raphson 迭代求解 t → x 的反函数

向后兼容策略：
- 现有 enum 值保留为接口常量
- `ComputedStyle` 中的 getter 返回类型改为接口
- 所有消费方已通过 `.apply(progress)` 调用，无需修改

#### 2.3 `animation-direction` 和 infinite iteration

设计要点：
- `DocumentAnimationRuntimeState` 的 keyframe 状态机中：
  - `iterationCount = 0` 或 `Integer.MAX_VALUE` 表示 infinite
  - direction 枚举：`NORMAL` / `REVERSE` / `ALTERNATE` / `ALTERNATE_REVERSE`
  - 在 `resolve()` 中对 progress 做 reverse/alternate 映射
- `ComputedStyle` 新增 `getAnimationDirection()`
- `UiStyleProperty` 新增 `ANIMATION_DIRECTION`

### 第二层：扩展可动画属性集

按影响范围分批补齐：

| 属性 | Impact | 优先级 | 说明 |
|------|--------|--------|------|
| `translate-x` / `translate-y` | PAINT | 高 | transform 子属性 |
| `scale-x` / `scale-y` | PAINT | 高 | transform 子属性 |
| `rotate` | PAINT | 高 | transform 子属性 |
| `box-shadow` (spread/blur/offset) | PAINT | 中 | 阴影动画 |
| `top` / `left` / `right` / `bottom` | LAYOUT | 中 | 定位动画 |
| `gap` | LAYOUT | 低 | flex/grid 间距动画 |
| `font-size` | LAYOUT | 低 | 文本尺寸动画 |

扩展方式：在 `DocumentAnimationProperty` 枚举中新增条目，在 `PropertyRuntimeSemantics` 中补齐语义。现有架构已为此预留扩展点。

### 第三层：高级能力（按需）

| 能力 | 说明 | 优先级 |
|------|------|--------|
| per-property transition timing | `transition` 声明支持四元组列表 | 中 |
| `steps()` 缓动 | 精灵动画 | 低 |
| per-stop timing | keyframe 每段 stop 独立缓动 | 低 |
| Web Animations API 风格命令式接口 | `element.animate(keyframes, options)` 返回 `Animation` 对象 | 低 |
| `animationstart` / `animationiteration` 事件 | 完善事件覆盖 | 低 |

## 三、架构决策

### 3.1 不引入合成层（compositor layer）

理由：
- 项目场景是 Minecraft GUI，元素数量有限，已在 GL 上下文中直接绘制
- 引入合成层会大幅增加复杂度（层提升策略、层间裁剪、内存管理），收益不大
- transform/opacity 动画标记为 PAINT 级（不触发 layout），paint command 生成时直接注入矩阵/透明度覆盖
- 保持现有"每帧惰性重建 paint commands"策略

### 3.2 保持动画值不修改 inline style 的原则

理由：
- 避免样式循环依赖
- 避免不必要的级联重算
- `LayoutContext.styleCache` 在单次 pass 内缓存 `ComputedStyle`，动画值通过 `LayoutRuntimeValueResolver` 在消费端注入，不破坏缓存一致性

### 3.3 transform 走 paint command 矩阵注入

理由：
- 与现有 PAINT 级动画属性（background-color、border-color 等）的消费方式一致
- 不需要引入新的抽象层
- 命中测试通过反向矩阵变换坐标即可

## 四、布局引擎优化兼容性评估

### 4.1 优化内容（2026-05-21）

1. **`LayoutContext` 单次 pass 上下文**（`a737fae`）
   - `styleCache`：`IdentityHashMap<ElementNode, ComputedStyle>` — 避免同一 pass 重复计算样式
   - `generatedChildNodesCache`：伪元素子节点缓存
   - `visibleElementChildrenCache`：可见子元素分桶缓存
   - `intrinsicContentWidthCache` / `intrinsicOuterWidthCache`：固有宽度测量缓存
   - 新增 `UiStyleResolver.computeWithParentStyle(element, parentStyle)` 复用父级样式

2. **减少 positioned 元素重复测量**（`9d0fd0d`）
   - 只有 `right` 或 `bottom` 单边锚定时才做预测量
   - 其余 positioned 元素直接执行最终布局

### 4.2 兼容性结论

**布局优化与动画方案完全兼容，不需要变更计划。**

- `LayoutContext` 是 pass-local 的，每次 `layout()` 调用创建新实例。动画二次布局（`resolveRuntimeLayoutBox`）会创建全新 `LayoutContext`，不复用第一次布局缓存
- `LayoutRuntimeValueResolver` 接口未变，动画注入方式不受影响
- transform 是 PAINT 级属性，不进入布局引擎，与 `LayoutContext` 无关
- 新增 `DocumentAnimationProperty` 只需在 `PropertyRuntimeSemantics` 中补齐语义，不需要感知 `LayoutContext`

### 4.3 后续实现建议

- 如果 `DocumentPaintEngine` 中查询动画值频繁，可考虑在 paint command 生成阶段引入类似的 pass-local 缓存，与布局引擎优化思路保持一致
- 保持"动画值通过 `LayoutRuntimeValueResolver` 在消费端注入，不修改 `ComputedStyle`"的设计原则，确保 `LayoutContext.styleCache` 安全

## 五、推进计划

### Phase 1：transform + cubic-bezier（建议首批）

1. 实现 `UiTransform` 值对象
2. `DocumentAnimationTimingFunction` 从 enum 重构为接口
3. 实现标准 cubic-bezier 缓动
4. `DocumentAnimationProperty` 新增 transform 子属性
5. `DocumentPaintEngine` 新增 TRANSFORM paint command
6. `DocumentPaintRenderer` 实现矩阵回放
7. 命中测试反向变换
8. 验证：编写动画测试用例

### Phase 2：animation-direction + infinite + 属性扩展

1. 新增 `animation-direction` 样式属性与枚举
2. 支持 infinite iteration
3. 补齐 `top`/`left`/`right`/`bottom` 可动画属性
4. 补齐 `box-shadow` 动画子属性

### Phase 3：高级能力（按需求驱动）

1. per-property transition timing
2. `steps()` 缓动
3. 命令式动画 API
4. 补齐事件覆盖

## 六、风险与约束

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| transform 命中测试复杂度 | 旋转/缩放后坐标反算 | 首版只支持 translate，scale/rotate 后续补齐 |
| cubic-bezier 从 enum 改接口 | 序列化/反序列化兼容 | 预定义常量保持原名，消费方只用 `.apply()` |
| LAYOUT 级属性扩展 | 二次布局性能 | 严格控制 LAYOUT 级属性数量，优先用 PAINT 级 transform 替代 |
| infinite animation 内存 | 长时间运行不清理 | `pruneFinishedAnimations` 对 infinite 不触发清理，但 element 移除时随 states map 清理 |
