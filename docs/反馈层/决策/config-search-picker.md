# Config SearchPicker 决策

## 能力边界

- SearchPicker 是配置展示/编辑适配层，不改变 canonical 值、YAML 格式或网络语义。
- Provider、Codec、Presentation 与 SearchFunction 在注册时冻结为不可变快照；Registry 不保留调用方后续可变策略。
- Codec 写回使用无状态双参 `encode(current, selection)`；不得共享或缓存可变 `current`。
- 搜索结果预算上限为 64；当前不做虚拟化，NEI 不纳入首版。
- scene 图片只承载候选/变体展示，图片失败不得影响选择与写回。

## 产品投影

- beta API 直接收敛为 `ALL/SELECTED`；`ALL` 的 keys 必为空，`SELECTED` 必须包含 1..N 个唯一 key，不保留旧模式别名。
- 界面只投影“全部状态”和“指定状态”。SELECTED 草稿可暂为空但禁确认；ALL/SELECTED 往返保留浮层草稿，确认 ALL 始终提交空 keys。
- 当前候选未枚举的已选 key 必须以通用失效项展示，默认保留且允许移除；确认排序不得丢弃未知 key。
- 候选与已选成员优先展示领域 label；方块业务目标格式为“本地化名 + registry + canonical”，内部 key 不作为主标签。
- Picker 是“添加方块”入口；已选成员需要可编辑、可移除，并在提交前给出最终规则摘要。
- StructuredList 通过业务 member label 元数据或领域 label 提供语义化标题；raw 编辑可作为兼容入口保留，但不主导产品流程。
- StructuredList 列表视口使用独立 320px 首选高度，不复用 SimpleList 主题高度；对象卡片标题槽最多 260px，可 grow/shrink/clip，按钮紧随标题且宽屏空白留在右侧。

## 列表成员绑定

- `SearchPickerSpec.BindingMode.LIST_MEMBERS` 是面向 `List<String>` member 的显式绑定模式；原有默认 `SINGLE_VALUE` 与 `Codec` 整值转换路径保持兼容，列表成员模式必须显式提供 `ListMemberCodec`。
- 关闭态只展示当前规则摘要与 `Manage` 入口；raw 列表默认折叠为高级修正/删除入口，不再主导日常成员管理。
- 管理 portal 的目标宽度为 480px、最小宽度为 360px，并在视口四周保留至少 8px safe inset；高度受可用视口 cap，内容超出后在 portal 内滚动。搜索框固定在 portal 顶部。
- portal 中“当前成员”按可用高度动态展示、最多 3 行，“搜索结果”最多 5 行；两区超过 cap 后各自保持可滚动，不以无界列表撑高 portal。
- 每个 raw 列表项以 `ListItem.id` 作为列表内稳定身份，candidate key 不是成员身份。编辑确认时按稳定 id 重新定位并读取最新 raw，只替换目标项；新增只追加一项。Picker 删除采用“发起删除 → 二次确认”的两步流程，确认后仍按稳定 id 删除目标；目标已不存在、codec 异常或返回非法值时零写。raw 高级入口仍可直接修正或删除。
- 未枚举 candidate 保留其 selection。malformed raw 与 duplicate candidate 只显示通用提示，不回显原始坏值，也不自动合并重复项；原列表顺序和成员身份保持不变。只有用户明确编辑、两步确认删除，或通过 raw 高级入口修正/删除目标项时，该项才变化；操作其它项不得改写它。
- active overlay 打开时，Tab/Shift+Tab 焦点范围收口在当前顶层 portal；关闭、取消或确认后恢复到打开前的触发控件。Escape 或点击外部关闭仍零写。

## 验证纪律

- 布局必须在完整生产 scene 树中覆盖宽/窄视口；孤立控件测试不能替代宿主验证。
- 自动化测试通过与消费方升级不等于真机通过；运行态结论以消费方日志和用户实机反馈为准。
- 每次变更 beta 坐标必须升版本、重新发布 Maven Local，并核对消费制品 SHA。
