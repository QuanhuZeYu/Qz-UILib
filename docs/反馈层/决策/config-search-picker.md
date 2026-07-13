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
- 当前 raw 成员显示在 `CANDIDATES` portal。每个 raw 列表项以 `ListItem.id` 作为列表内稳定身份，独立展示和编辑；candidate key 不是成员身份，重复 candidate 不自动合并。
- 确认时按稳定 id 重新定位并读取最新 raw：编辑只替换目标项，新增只追加一项；删除仍由 raw 列表控件处理。目标已删除、codec 异常或返回非法值时零写。
- 未枚举 candidate 保留其 selection。无法解码的 malformed raw 仍占一个稳定成员行，portal 只显示不可读取/通用占位，不回显原始坏值；原值由外层 raw 列表原样保留，只有用户通过 raw 删除/修正，或在 Picker 中明确替换该项时才变化。编辑、追加或删除其它目标项时，不改写其余成员。

## 验证纪律

- 布局必须在完整生产 scene 树中覆盖宽/窄视口；孤立控件测试不能替代宿主验证。
- 自动化测试通过与消费方升级不等于真机通过；运行态结论以消费方日志和用户实机反馈为准。
- 每次变更 beta 坐标必须升版本、重新发布 Maven Local，并核对消费制品 SHA。
