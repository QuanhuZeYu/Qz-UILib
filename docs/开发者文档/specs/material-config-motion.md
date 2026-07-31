# Material Config 与 Motion 规格

> 状态：当前功能分支产品规格。目标是 Material 3 桌面设置页风格，不是完整设计系统。

## 首批页面

- 固定左侧 section navigation、右侧内容、顶部状态和底部保存操作区。
- 页面居中并限制最大宽度；854 logical px 是首批最小目标宽度。
- 世界内页面保持完整 framebuffer 布局，根背景使用 80% 不透明暗色遮罩，让游戏画面从整个背景连续透出；禁止用缩短 surface 的方式只露底部条带。
- 普通标量使用低噪声 Setting Row；dirty 使用小 indicator，error 优先。
- 复杂字段使用单层 tonal surface，不在内部重复 card chrome。
- Config Authority、Draft、Persistence、校验、冲突、reload、renderer 与 editor SPI 保持现有语义。

## 最小 Dark Theme

- colors：primary/onPrimary、primaryContainer/onPrimaryContainer、surface/surfaceContainer/surfaceContainerHigh、onSurface/onSurfaceMuted、outline、error/onError。
- shape：small/medium/large。
- spacing：xs/sm/md/lg/xl。
- motion：fast 90ms、standard 160ms、emphasized 240ms，真实客户端再校准。
- 首版不做 light/dynamic theme、shadow、theme extension 或通用 state resolver。

## 组件与 Motion

- Button：primary/secondary/text chrome 与 state-layer opacity。
- Toggle：thumb position/track color，不移动 hit root。
- TextInput：outline、placeholder、error、focus；enabled 内凹背景不随 hover/focus 明暗跳变，透明 caret 与选中层只衰减 alpha，不把可见端点 RGB 同步压暗。
- Select：复用现有 overlay；section 切换前先 dismiss outgoing overlay。
- Navigation：背景/文字色插值，并以非交互指示条伸缩与标签 4px 横移强化 selection feedback。
- Section：active gesture 先到 UP/CANCEL，再 dismiss overlay 并立即执行严格单 live 切换；incoming 标题与字段 presentation shell 始终保持 `opacity=1`，完整布局发布后从 `-22px` 以 emphasized 240ms 归位，相邻项延迟 36ms、最大启动延迟 252ms。旧 section Owner 立即销毁，快速反选不等待 outgoing completion，禁止大面积 `1→0→1` 明灭。
- Scroll：滚轮/scrollbar handler 只合并 intent signal，由同帧 flush effect 启动或取消 Motion，不直接写节点。配置主视口滚轮目标可在同帧与跨帧连续累计，以 160ms keyed ease-out Motion 从当前显示 offset 重定向收敛；持续输入时旧段先推进到本帧显示值再重定向，禁止每次重定向重新 ease-in 并零速起步。内容与 scrollbar thumb 同步，反向滚轮立即截断旧方向；scrollbar 拖动从当前可见 offset 取消 Motion 并直接接管，track 点击保持直接定位。

Motion 使用 host 每帧同一单调时间，不写 Draft/Persistence/history，不新建全局 ticker。stagger helper 保持 internal；不做 exit snapshot、layout animation、spring 或 Hero。不得在输入开放时 transform 交互子树；section presentation shell 位移期间必须关闭整棵子树的 hit-test 与 Tab traversal，逐项 identity 归位或 Owner 卸载后恢复原门禁并请求 hover 重算。

Host 每帧在 post-flush 主树与 active overlay 都完成布局后发布最终 layout epoch。layout-ready observer 必须先观察到晚于安装时的 publication，且全部 target 已取得 `LayoutBox`，才可启动轨道；observer 造成的布局写入最多同帧 settle 三轮，未收敛部分留到下一帧。该边界只容忍短暂延迟的 publication，仍是同线程 pipeline；在 `PaintPlan` 冻结、topology/resource generation 和命中一致性协议完成前，不启用跨线程 layout/paint。

## 实施与验收

- M0：用户在 854x480、1280x720、1920x1080 采集当前/dirty/error/list 状态截图。
- M1：静态 theme、page frame、Navigation、Setting Row、Button/Toggle/TextInput/Select。
- M2：internal clock/sample，接 Button、Toggle、Navigation，以及 layout-ready 的 section 标题/字段卡片级联进入。
- 自动化固定 max width、footer 不滚动、error-over-dirty、现有保存语义、多 occurrence motion isolation、透明色 alpha-safe 插值、TextInput 背景稳定、同帧/逐帧滚轮累计与 ease-out 轨迹、反向滚轮、scrollbar 中途接管、跨帧 hover 几何同步、主树/overlay 首帧布局发布、三轮 settle 上限，以及 reveal 期间 hit-test/Tab 门禁与 Owner 恢复。
- 用户从 `/qzuilib modernconfig` 验证键鼠、resize、dirty/error/conflict/save/reload；Qz-Miner 配置页另做 smoke。
