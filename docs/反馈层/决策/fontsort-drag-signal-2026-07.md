# fontSort 拖拽预览与终态收敛决策

## 决策

允许被拖行浮起偏移 signal 化，用于跟手等纯视觉反馈。排序 preview 仍使用受控顺序 signal
驱动 keyed reconcile。排序落点采用主流列表的中线插槽语义：被拖行中心越过相邻行中心即换位，
不再要求越过整行外边缘。

## 原因

用户已批准 fontsort 拖拽全套手感升级。浮起跟随需要从 POINTER_MOVE handler 写入偏移，
再经 signal -> bind -> transform 链驱动视觉移动；该链路符合 UI = f(state)，且 transform 属
COMPOSITE 级，不触发布局或绘制重算。

## 约束

- 落点 index 与终止顺序由当前事件坐标和 handler 内即时顺序计算，不从待 flush 的 preview 或
  `dragOffsetSig` 回读；受控顺序 authority 仍可在 DOWN、激活和终止时同步采样。
- 单个手势持有即时可见顺序；`UP` 必须用自身坐标重新计算落点并直接提交该顺序，不读取待 flush
  的 preview signal；若 UP 落点不同于最后一次 MOVE，还必须发布最终 preview，即使 authority 提交为 no-op。
  `CANCEL` 必须显式把当前有效基线排回 signal，覆盖同帧待写预览。
- 顺序与抓取几何在达到激活阈值时重新采样；`DOWN` 到激活之间由失焦提交或受控源更新产生的 keyed
  位移必须成为真实起点，不能回写按下瞬间的旧顺序。
- fontSort 拖拽期间若收到外部 draft 更新，外部真值优先；UP/CANCEL 都采用 deferred draft，
  并同步比对 adapter 已写入 `DraftBuffer`、但尚未 flush 到 draft signal 的真值，不得用拖拽开始时
  的旧快照覆盖 reload/reset。
- scene Router 在焦点 authority 切换时同步派发 `FOCUS_LOST/FOCUS_GAINED`，但既有 focused signal 仍延迟
  到 flush。fontSort 索引据此在 action CLICK、拖拽 DOWN 或 section 卸载前先处理旧文本；事件帧内另持
  即时文本，使同帧 `TEXT_INPUT→Enter/Tab` 不回读旧 signal。焦点进入时冻结 presentation order 与
  `DraftBuffer` draft/current；程序化外部 authority 已变化时，Enter/失焦放弃旧文本。row Owner 最后
  登记 cleanup 兜底，在 handler/focusable 注销前收口未决编辑。
- `ConfigScreen` 将 `RESTORE/CANCEL/SAVE` 的 CLICK 按事件顺序逐次入 FIFO 队列，经三阶段 signal
  收敛后执行；执行时再按 `DraftBuffer` / `canSaveNow()` 即时状态门禁，不满足条件则 no-op。
  视觉 enabled 仍由既有 Computed 驱动。
- SimpleList 在 preview signal 外单独冻结 `props.items` authority 起点；拖拽期间外部 authority 优先，
  即使它恰好等于当前 preview，也不得在 UP/CANCEL 时恢复为更旧顺序或伪装成控件提交。终态判定
  通过两阶段 signal binding 延后，使 `onReorder -> reset bridge -> props.items` 的单跳回流先兑现。
  两只 terminal binding 归属 runtime root Owner，跨过列表行 Owner 的 keyed 卸载边界后主动释放；
  runtime dispose 则直接取消未决结算。
- 每个把手的浮起偏移只使用 owner-scoped `dragOffsetSig`，随行卸载退订；它只驱动 COMPOSITE
  transform，不触 layout/paint。排序 preview 使用既有顺序 signal 和 keyed diff，可触发布局。
- 换位帧的 transform 必须以目标槽位的预测 LayoutBox 为基准，使 keyed diff 完成布局后抓取点仍贴住指针。
- auto-scroll 边缘区按短 viewport 收缩；同帧 MOVE 与捕获期间 SCROLL 共用即时滚动目标，transform
  与 MOVE/UP 落点都计入该目标相对 viewport 已应用 offset 的完整差值；active drag 在内层边界也
  消费 SCROLL，禁止热区重叠、旧 signal 回拨、抓取点落后或带动外层配置视口。
- 拖拽结束（UP/CANCEL）先清手势闭包并向 `dragOffsetSig` 排入 0，transform 在后续 flush 后归零，
  避免预览态泄漏。

## 影响

- `SceneDragReorder` 以 `dragOffsetSig` 驱动被拖行 transform，并在 handler 闭包内维护手势即时顺序。
- `FontSortPresentation` 的筛选完整顺序映射以拖拽起始 full/visible 快照和回调传入的最终 visible
  顺序计算，隐藏项槽位与相对顺序保持不变；fontSort drag handle 读取其事件帧即时 visible rows，
  使同帧索引 `FOCUS_LOST` 产生的新顺序成为真实拖拽起点。
- 当时 `docs/设定值层/硬约束总目录.md` §5 曾把“拖拽瞬态 signal 只写不读”细化为业务真值与
  视觉偏移分层；该目录现已精简为风险导航，具体契约以本文、源码 Javadoc 与测试为准。
