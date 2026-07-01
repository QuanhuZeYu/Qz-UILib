# 决策：Scene 现代配置页先补一期地基再迁真实页面

> 【已废弃，被 DECISION-20260628-modern-config-new-mental-model 取代】

## 背景

旧现代配置模板页已经在 HTML-like / `ui.dom` 栈完成 12 个模板入口，包含 `STRING/NUMBER/BOOLEAN/CHOICE/LONG_TEXT/SIMPLE_LIST/TABLE/OBJECT/KEY_VALUE_MAP/PRESET_SELECTOR/RAW_EDITOR/ENHANCED_PICKER`，并带有分类、搜索、草稿、保存、回滚、
复杂编辑器和性能优化策略。

当前项目主线已转向 `ui.scene` 新栈。用户已明确：旧 HTML-like / `ui.dom` 栈退出实际业务接入，暂不删除，仅作为废弃参考代码；下一步不直接开写真实配置页，而是先评估并补齐 modern config 所需的新栈地基。

Scene Form demo 已证明硬编码表单状态链路可行，但它只覆盖单行文本、数值文本、开关、校验、草稿保存、取消回滚和固定布局骨架，不等价于完整现代配置页能力。

## 候选方案

1. **直接迁移完整旧现代配置页**：一次性在 scene 上复刻 12 个模板入口与所有旧复杂控件。
2. **先做 scene-native 一期地基**：只覆盖最小真实配置页闭环，再按能力缺口逐步补齐。
3. **继续复用旧 DOM 现代配置页作为业务入口**：scene 仅保留 demo，新配置页继续走旧栈。

## 最终选择

采用方案 2：先做 scene-native 一期地基，不直接迁完整旧现代配置页，也不继续扩展旧 DOM 业务入口。

一期目标只覆盖可稳定验证的最小闭环：`STRING`、`NUMBER`、`BOOLEAN`、`CHOICE`、扁平分类、字段草稿、校验、保存、取消、恢复默认和真实配置数据适配。

`Select/top-layer` 是现代配置页必须补齐的 scene 通用控件地基，不应被 inline 方案绕过。现代配置页正式路线应先按 `docs/记忆/决策/DECISION-20260623-scene-overlay-foundation.md` 补通用 top-layer/overlay 能力，再在其上实现 scene-native `CHOICE/Select`。
inline listbox 只能作为临时验证探针或降级兜底，不作为一期产品目标方案。

## 选择原因

- 旧现代配置页的 12 模板入口依赖大量旧 DOM 控件，直接搬迁会把旧栈复杂度和性能债一次性带入 scene。
- Scene 当前主干已有布局、滚动、表格、Tab、基础输入和 Form demo，但缺少字段引擎、真实配置数据适配、通用浮层控件地基、复杂编辑器和虚拟化字段列表；其中 top-layer/overlay 会直接影响 `CHOICE`、Tooltip、ContextMenu、Autocomplete 等常用控件。
- `Select/top-layer` 涉及节点提升、z-order、clip 越界、命中路由、关闭行为、滚动重定位和失效边界，是一期最大架构风险；因此它必须显式成为通用地基改造目标，而不是在真实配置页中隐式绕过或临时硬塞。
- 一期应先建立可验证的 `UI = f(state)` 配置编辑闭环，再扩展模板广度，避免为追平旧页面一次性加入过多未验证机制。

## Select/top-layer 裁决

现代配置页一期不应绕过通用 top-layer。scene 当前 hit-test、clip 和绘制顺序都依赖树内父子 bounds 与 DFS 顺序；弹层一旦超出父链 bounds，会同时遇到命中不到、被祖先裁剪和被后续兄弟覆盖的问题。这正说明 top-layer 是通用控件地基缺口，而不是可长期接受的交互取舍。

正式路线应先补 `top-layer/select` 地基，再实现 scene-native `CHOICE`：

- **少选项**：仍可用 `SceneSegmented` 或 `SceneRadioGroup`，覆盖常见 2-4 个离散值。
- **中等及以上选项**：应实现浮空 `SceneSelect`，列表经 top-layer 提升绘制，不参与原布局流，不因父级 `clipChildren` / `scrollable` 被裁掉。
- **临时 inline**：只允许作为 top-layer 施工前的测试探针或降级 fallback，不作为现代配置页一期验收目标。

top-layer/select 的状态模型仍必须保持 signal-first：`selectedIndex`、`expanded`、`anchor` 派生信息与不可变 `options`；选中、展开、关闭只写 signal，浮层显示由响应式状态驱动。`EventContext` 可承载受控关闭请求，但不得让 handler 直接改 SceneNode 属性槽或树结构。

top-layer 地基最小目标：独立 z-order 提升、跨 clip 绘制、浮层优先命中、点击外部关闭、anchor 随滚动/resize 重定位、Owner 卸载时自动清理，并保持 scene 核心包隔离与 I1/I6/I7/I10/I11。具体 P0/P1/P2 边界见 `DECISION-20260623-scene-overlay-foundation.md`。

## 一期能力边界

- **数据源**：优先接 `club.heiqi.config` / `MutableConfig` 现代配置模块；Forge Configuration 仍作为既有回退入口，不做 Forge 到 config 模块迁移工具。
- **字段类型**：`STRING` 用 scene 单行文本；`NUMBER` 有界优先走 `SceneSlider`，无界走数值文本输入与校验；`BOOLEAN` 用 scene toggle；`CHOICE` 少选项走 segmented/radio，中等及以上选项走基于 top-layer 的 `SceneSelect`。
- **结构范围**：扁平字段列表 + 扁平分类；不做嵌套树导航、深层 OBJECT 内联和跨页面子编辑。
- **状态模型**：每个字段持有 `current` 与 `draft`，dirty、error、canSave、canCancel 等派生状态通过 computed/bind 消费。
- **保存语义**：保存只在无校验错误时把 draft 写回数据源；取消回滚 draft；恢复默认只写 draft，不直接写 current。
- **布局骨架**：固定标题/摘要/操作区 + 唯一 `fillParentHeight` scrollable viewport，沿用 Form demo 验证过的页面结构。

## 暂缓能力

- `LONG_TEXT`、`SIMPLE_LIST`、`TABLE`、`OBJECT`、`KEY_VALUE_MAP`、`PRESET_SELECTOR`、`RAW_EDITOR`、`ENHANCED_PICKER`。
- autocomplete、搜索候选、资源/声音浏览器、颜色选择浮层。
- 嵌套分类树、全局搜索、只看已修改、字段虚拟化、Binding 实例池复用。
- 多行文本、源码编辑器、表格行内编辑、动态 map 编辑、复杂 picker。
- Ctrl+S/Enter 提交、Tab 焦点遍历、远程同步、Forge 与 config 双向迁移。

## 后续实施顺序

1. 设计并实现 scene 通用 `top-layer/overlay` 地基：portal/提升注册、z-order、绘制 pass、命中 pass、关闭与清理生命周期。
2. 在 top-layer 上实现 scene-native `SceneSelect`：锚点定位、滚动/resize 重定位、限高滚动、选中写回 signal。
3. 固化 scene 配置字段模型：字段 spec、类型推断最小集、draft/current、校验和 dirty 派生。
4. 接入真实 `MutableConfig` 数据适配：读当前值、写 draft、保存、恢复默认和错误反馈。
5. 做 scene modern config demo：使用真实配置模块样例覆盖一期字段类型和保存闭环。
6. 再评估复杂模板迁移顺序：按真实需求逐个补 `LONG_TEXT`、列表、表格、对象树和 raw editor。

## NORTH_STAR 自检

- **I1/I2/I9**：字段交互只写 signal，保存前 UI 变化由 draft signal 驱动，同帧写入仍由中央事务合并。
- **I3**：页面和字段组件只在挂载时建树，动态行为落在 effect/bind/on handler 中。
- **I4/I7/I8**：字段值、错误、dirty 标记只影响对应文本、颜色或可用状态；滚动视口沿用既有 fragment/cache 复用路径。
- **I6/I10**：配置数据适配不进入渲染层；scene 核心不引入 Minecraft/LWJGL/Forge 输入依赖。
- **I11**：输入 handler 只改字段 draft/caret/focus 等 signal，不直接改 SceneNode 属性槽或树结构。
- **top-layer 额外约束**：浮层提升是 scene 数据层/绘制层内部契约，不得让渲染层认识 signal/组件，也不得让 scene 输入核心引入平台 import；关闭与清理必须经 Owner 生命周期和 signal 状态收口。

## 影响范围

- 旧 `ModernConfigTemplateScreen` 及其 DOM 协作者继续作为 legacy 参考实现，不作为 scene 业务入口继续扩展。
- 对外文档中关于旧现代配置页 12 模板能力仍描述既有 DOM 实现；scene 现代配置页在一期完成前不得宣称等价覆盖。
- 后续若新增 scene 对外配置页入口，需要同步更新 `docs/使用文档/` 与长期事实文档，明确 scene 一期能力和 legacy 差距。

## 后续注意事项

- `Select/top-layer` 已被用户确认为急需补齐的通用控件地基目标，不再作为可绕过选项处理。
- top-layer 的验证锚点应包含：跨 clip 可见、浮层优先命中、外部点击关闭、anchor 滚动/resize 重定位、Owner 卸载清理、稳定兄弟不被污染、scene 包隔离不引入平台 import。
- 实现前仍应补一份更细的 top-layer 设计或测试清单，明确节点提升、命中、clip、z-order、关闭行为和滚动重定位边界。
- 若真实配置页需要超过一期边界的模板，不得临时在页面内绕过 scene 地基硬塞控件，应单独补对应控件能力。
