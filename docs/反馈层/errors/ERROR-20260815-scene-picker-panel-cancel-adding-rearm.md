# ERROR-20260815-scene-picker-panel-cancel-adding-rearm.md

**日期**：2026-08-15
**组件**：`ScenePickerPanel.cancelPanel`（Phase B1 新增）与配置表单接线（Phase B2）
**状态**：已修复（cancelPanel 恒走关闭分支，回归测试锚定）

## 现象

`SearchPickerPanelWiringTest.listMembersRowTriggerPanelAddMemberUpdatesSummaryAndStaysOpen`
中：LIST_MEMBERS 面板内点「新增」后按 ESC，面板不关闭（overlay 仍常驻），且 `beginAdd`
回调被再次触发。

## 根因

`cancelPanel` 复用 `finishSelection` 收尾。`finishSelection` 对
`listMembers && addingMember` 走「新增成功重武装」分支（再次 `beginAdd`、不请求关闭）。
新增后按 ESC 时 `addingMember` 仍为 true，取消路径误入重武装分支：面板永不关闭，
且每按一次 ESC 就多武装一次新增。

## 修复

`cancelPanel` 不再复用 `finishSelection`，改为恒走关闭分支：先 `props.onCancel().run()`，
再清全部临时态（variants/activeCandidate/variantQuery/gridHighlight/
addingMember/focusIntent），最后 `closeRequest.run()`。新增回归测试
`ScenePickerPanelTest.escapeDuringAddingMemberStillCancelsAndCloses`。

> 追溯（2026-08-17）：本文描述的 pendingDelete 两步确认机制已由「删除一步直达」重构移除
>（UILib `b6a699f8`），上表临时态清单随之少一项；历史描述保留原貌供追溯。

## 教训

- 取消路径与成功路径的状态收尾语义不同（取消必须无条件关闭），共用收尾函数时
  「成功提交的特殊分支」会把取消语义改掉；特殊分支的判定条件必须只对成功路径为真。
- 接线层集成测试必须覆盖「新增进行中」这类中间状态下的取消/Escape，纯面板测试
  （只测开-关）发现不了该分支。

## 附带记录（Phase B2 接线踩坑）

- `Registry.RegisteredProvider` 是注册时固化的快照类，不实现
  `CategorizedValueEditorProvider`；若不改造，接线层 `registry.find(id)` 的结果
  恒不命中 `instanceof CategorizedValueEditorProvider`，分组透传永远退化。修复方式：
  注册时探测并快照 `categories()`/`categoryOf`，让快照类实现该接口透传。
- 虚拟网格窗口行依赖布局后的视口高度派生：测试（及宿主）在 overlay 布局后必须
  `SceneRuntime.__bridgeLayoutEpoch(epoch)` 再 `flush`，否则窗口挂载 0 行。
- 网格 cell 标签会按 cell 宽被 `TextEllipsizer` 截断（如 "Malformed key remains" →
  "Malfor…"），测试断言用前缀匹配而非全文 equals。
- 结构化列表第二行（折叠区）的 picker 触发器可能落在滚动视口裁剪区外，坐标点击
  会落空；接线层触发器支持 Enter 打开，测试改走 `requestFocus + ENTER` 键盘驱动更稳。
