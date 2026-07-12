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

## 验证纪律

- 布局必须在完整生产 scene 树中覆盖宽/窄视口；孤立控件测试不能替代宿主验证。
- 自动化测试通过与消费方升级不等于真机通过；运行态结论以消费方日志和用户实机反馈为准。
- 每次变更 beta 坐标必须升版本、重新发布 Maven Local，并核对消费制品 SHA。
