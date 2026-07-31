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
- TextInput：outline、placeholder、error、focus。
- Select：复用现有 overlay；section 切换前先 dismiss outgoing overlay。
- Navigation：selection indicator。
- Section：active gesture 先到 UP/CANCEL，再 dismiss overlay 并立即执行单 live 切换；字段面板始终保持 `opacity=1`，只让 Navigation 选中色与非交互 section 标题做 160ms 单向进入，禁止大面积 `1→0→1` 明灭。

Motion 使用 host 每帧同一单调时间，不写 Draft/Persistence/history，不新建全局 ticker。首个 helper 保持 internal；不做 exit snapshot、layout animation、spring、Hero 或任意交互节点 transform。section 标题可做小幅 composite 位移，因为标题明确不可命中。

## 实施与验收

- M0：用户在 854x480、1280x720、1920x1080 采集当前/dirty/error/list 状态截图。
- M1：静态 theme、page frame、Navigation、Setting Row、Button/Toggle/TextInput/Select。
- M2：internal clock/sample，接 Button、Toggle、Navigation 和 section 标题低刺激进入。
- 自动化固定 max width、footer 不滚动、error-over-dirty、现有保存语义、多 occurrence motion isolation。
- 用户从 `/qzuilib modernconfig` 验证键鼠、resize、dirty/error/conflict/save/reload；Qz-Miner 配置页另做 smoke。
