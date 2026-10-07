# UILib 全库规格覆盖工作表

状态：**工作盘点，非规格**。行为要求仅以 [唯一 spec](<../specs/2026-10-07-uilib-core-contract-design.md>) 为准。本表用于发现遗漏，不把当前源码或旧指南提升为要求。

## 完成判据

每项能力在 spec 中具备目标场景、职责、不变量、主流程、已知失败归属及可观察验收。所有生产包均能关联能力；算法、字段和内部工具不逐类立法。核心合同必要选择逐项裁决，已有边界内的设计直接完成。完整覆盖不表示实现已满足规格。

## 补充顺序与当前差距

先收束当前核心合同，再补其依赖的字体/文本/绘制和通用控件，随后配置、网络、聊天与 HUD，最后核对宿主、资源、诊断和构建产物。

| 能力 | 实现入口（现状定位） | 规格覆盖状态 |
| --- | --- | --- |
| 状态与呈现 | ui/reactive、ui/scene/runtime、ui/env | 直接状态/派生读取与渲染批次职责已确认；Effect 与生命周期资源归属明确，执行器及合批属于设计 |
| 布局与扩展 | ui/scene/node、layout、control、form | 尺寸意图、自定义接入和可见溢出命中已明确；分配/扩展接口属于设计 |
| 输入与生命周期 | ui/scene/input、overlay、host；ui/input、screen | 范围、归属、非模态参与与焦点声明职责已明确；宿主实现与统一默认策略属于设计 |
| 通用控件与编辑器 | ui/scene/control、form；config/ui/editor、field | 完整文本编辑已确认；其他控件逐项覆盖待完成 |
| 绘制与材质动效 | ui/scene/paint、theme；ui/render、base；gl | 平台及坐标边界已确认；能力与失败语义待补 |
| 文本与 Markdown | ui/text、markdown、scene/text；font/layout | 第 13 节补齐模式、排版、测量/链接共同几何；语法范围及降级待裁决 |
| 数学排版 | font/latex、glyph | 第 13 节明确数学混排职责；命令范围、字形能力及降级待补 |
| 字体与资源 | font/api、page、render；resource、i18n | 第 13 节补齐度量/绘制一致、变化及资源归属；字体选择与失败策略待补 |
| 图像与物品 | ui/image、scene/image | 适配边界已确认；图像来源、状态、缩放及物品语义待补 |
| 配置数据与事务 | club/heiqi/config；runtime、schema | 仅通用业务边界已有；配置系统自己的权威、持久化与冲突合同待补 |
| 配置 UI 与桥接 | config/ui；uilib/config/modern | 编辑协调可复用；schema 驱动与保存呈现待补 |
| 网络 | net/api、core、codec、transport、store | Channel、Fetch、Stream、Store、Realtime 及连接生命周期待补 |
| 聊天 | api/chat、internal/chat3 | 消息/玩家名、动作、装饰、历史与滚动待补 |
| 被动 HUD | ui/hud、client/hud | 被动输入原则已有；注册、显示、位置缩放及编辑布局待补 |
| 诊断与开发工具 | ui/diagnostic、internal/devtools、client/command | 反馈原则已有；工具能力、headless 与打包范围待补 |
| 平台启动与集成 | ClientProxy、CommonProxy、MyMod；client、mixin、util | 平台边界已有；启动、双端环境与退出清理待补 |

## 核心收束及后续补充

核心职责规格已收束至可交接实现设计；不以执行器、算法或接口签名未选定要求继续逐项裁决。第 10.2 节保留技术设计交接。全库能力覆盖仍未完成。下一组核对字体、富文本、Markdown、数学排版和绘制。

### 已确认补充

派生读取当前结果已获用户认可并写入 spec 第 3.1 节：首次及后续读取基于当前来源，允许按需计算/缓存，读取不执行副作用。后续继续收束 Effect、结果发布及核心布局/生命周期合同。

用户裁定：布局与命中区域归框架自动管理，在渲染批次统一更新，画面与命中保持一致；已写入 spec 第 3.4、4.1 节。“采用最后已呈现布局”的独立合同提议撤回，不冻结快照或具体时机算法。继续按职责边界收束核心规格，既有边界内的可逆技术选择留给设计，不重复细节裁决。

结果发布调查：SceneFramePipeline 位于 ui/scene/host，当前 run 在 ROUTE 前执行 LAYOUT_PRE_ROUTE，在路由后继续布局并生成及回放画面；SceneInputRouter 调用 SceneHitTester，后者直接读取节点 cachedLayout 并在父盒外提前返回。静态源码说明当前路径，不证明新旧画面/命中一致性已满足；父盒外可见且未裁剪部分可交互已获用户认可，写入 spec 第 4.2 节；现有父盒外提前返回路径需迁移。旧阶段顺序和缓存形状不冻结为新规格。

环境服务原文已核对：ResourceReloadService、LanguageEpochService、UiEnvironment、LocaleEnvironment、ResourceEnvironment、ProcessUiEnvironment。spec 第 3.3 节按既有动态更新边界细化职责；未继承旧单例、计数或按帧读取约定。

用户允许按新 spec 直接重构至 5.0；生产重构不属于本轮规格补充。

## 生产包覆盖基线

基线分支 docs/uilib-spec，HEAD 570479b1。依据 glob 对 src/main/java 的实际盘点，包含 779 个 Java 文件、94 个源码目录；数量只用于本次遗漏核验，不是长期指标。

| 源码目录 | Java 文件数 |
| --- | ---: |
| [club/heiqi/config](<../../../src/main/java/club/heiqi/config>) | 22 |
| [club/heiqi/config/runtime](<../../../src/main/java/club/heiqi/config/runtime>) | 16 |
| [club/heiqi/config/schema](<../../../src/main/java/club/heiqi/config/schema>) | 16 |
| [club/heiqi/config/ui](<../../../src/main/java/club/heiqi/config/ui>) | 6 |
| [club/heiqi/config/ui/editor](<../../../src/main/java/club/heiqi/config/ui/editor>) | 17 |
| [club/heiqi/config/ui/field](<../../../src/main/java/club/heiqi/config/ui/field>) | 25 |
| [club/heiqi/config/ui/theme](<../../../src/main/java/club/heiqi/config/ui/theme>) | 2 |
| [club/heiqi/uilib](<../../../src/main/java/club/heiqi/uilib>) | 4 |
| [club/heiqi/uilib/api/chat](<../../../src/main/java/club/heiqi/uilib/api/chat>) | 6 |
| [club/heiqi/uilib/client](<../../../src/main/java/club/heiqi/uilib/client>) | 5 |
| [club/heiqi/uilib/client/command](<../../../src/main/java/club/heiqi/uilib/client/command>) | 2 |
| [club/heiqi/uilib/client/hud](<../../../src/main/java/club/heiqi/uilib/client/hud>) | 10 |
| [club/heiqi/uilib/config](<../../../src/main/java/club/heiqi/uilib/config>) | 2 |
| [club/heiqi/uilib/config/modern](<../../../src/main/java/club/heiqi/uilib/config/modern>) | 11 |
| [club/heiqi/uilib/font](<../../../src/main/java/club/heiqi/uilib/font>) | 20 |
| [club/heiqi/uilib/font/api](<../../../src/main/java/club/heiqi/uilib/font/api>) | 2 |
| [club/heiqi/uilib/font/config](<../../../src/main/java/club/heiqi/uilib/font/config>) | 3 |
| [club/heiqi/uilib/font/event](<../../../src/main/java/club/heiqi/uilib/font/event>) | 1 |
| [club/heiqi/uilib/font/glyph](<../../../src/main/java/club/heiqi/uilib/font/glyph>) | 12 |
| [club/heiqi/uilib/font/internal](<../../../src/main/java/club/heiqi/uilib/font/internal>) | 1 |
| [club/heiqi/uilib/font/latex](<../../../src/main/java/club/heiqi/uilib/font/latex>) | 6 |
| [club/heiqi/uilib/font/latex/layout](<../../../src/main/java/club/heiqi/uilib/font/latex/layout>) | 17 |
| [club/heiqi/uilib/font/latex/node](<../../../src/main/java/club/heiqi/uilib/font/latex/node>) | 11 |
| [club/heiqi/uilib/font/layout](<../../../src/main/java/club/heiqi/uilib/font/layout>) | 7 |
| [club/heiqi/uilib/font/layout/markdown](<../../../src/main/java/club/heiqi/uilib/font/layout/markdown>) | 10 |
| [club/heiqi/uilib/font/page](<../../../src/main/java/club/heiqi/uilib/font/page>) | 14 |
| [club/heiqi/uilib/font/render](<../../../src/main/java/club/heiqi/uilib/font/render>) | 10 |
| [club/heiqi/uilib/font/shader](<../../../src/main/java/club/heiqi/uilib/font/shader>) | 1 |
| [club/heiqi/uilib/font/util](<../../../src/main/java/club/heiqi/uilib/font/util>) | 11 |
| [club/heiqi/uilib/gl/shader](<../../../src/main/java/club/heiqi/uilib/gl/shader>) | 1 |
| [club/heiqi/uilib/i18n](<../../../src/main/java/club/heiqi/uilib/i18n>) | 1 |
| [club/heiqi/uilib/internal/chat3](<../../../src/main/java/club/heiqi/uilib/internal/chat3>) | 1 |
| [club/heiqi/uilib/internal/chat3/data](<../../../src/main/java/club/heiqi/uilib/internal/chat3/data>) | 2 |
| [club/heiqi/uilib/internal/chat3/input](<../../../src/main/java/club/heiqi/uilib/internal/chat3/input>) | 16 |
| [club/heiqi/uilib/internal/chat3/view](<../../../src/main/java/club/heiqi/uilib/internal/chat3/view>) | 13 |
| [club/heiqi/uilib/internal/chat3/viewmodel](<../../../src/main/java/club/heiqi/uilib/internal/chat3/viewmodel>) | 12 |
| [club/heiqi/uilib/internal/chat3/wiring](<../../../src/main/java/club/heiqi/uilib/internal/chat3/wiring>) | 3 |
| [club/heiqi/uilib/internal/devtools](<../../../src/main/java/club/heiqi/uilib/internal/devtools>) | 5 |
| [club/heiqi/uilib/internal/devtools/glass](<../../../src/main/java/club/heiqi/uilib/internal/devtools/glass>) | 3 |
| [club/heiqi/uilib/internal/devtools/headless](<../../../src/main/java/club/heiqi/uilib/internal/devtools/headless>) | 23 |
| [club/heiqi/uilib/internal/devtools/playground](<../../../src/main/java/club/heiqi/uilib/internal/devtools/playground>) | 6 |
| [club/heiqi/uilib/internal/devtools/playground/pages](<../../../src/main/java/club/heiqi/uilib/internal/devtools/playground/pages>) | 10 |
| [club/heiqi/uilib/internal/font](<../../../src/main/java/club/heiqi/uilib/internal/font>) | 1 |
| [club/heiqi/uilib/internal/font/angelica](<../../../src/main/java/club/heiqi/uilib/internal/font/angelica>) | 1 |
| [club/heiqi/uilib/internal/font/tesr](<../../../src/main/java/club/heiqi/uilib/internal/font/tesr>) | 1 |
| [club/heiqi/uilib/internal/font/tesr/angelica](<../../../src/main/java/club/heiqi/uilib/internal/font/tesr/angelica>) | 1 |
| [club/heiqi/uilib/mixin/early](<../../../src/main/java/club/heiqi/uilib/mixin/early>) | 4 |
| [club/heiqi/uilib/mixin/early/nametag](<../../../src/main/java/club/heiqi/uilib/mixin/early/nametag>) | 4 |
| [club/heiqi/uilib/mixin/early/network](<../../../src/main/java/club/heiqi/uilib/mixin/early/network>) | 2 |
| [club/heiqi/uilib/mixin/early/tesr](<../../../src/main/java/club/heiqi/uilib/mixin/early/tesr>) | 1 |
| [club/heiqi/uilib/mixin/late](<../../../src/main/java/club/heiqi/uilib/mixin/late>) | 1 |
| [club/heiqi/uilib/mixin/late/ae2](<../../../src/main/java/club/heiqi/uilib/mixin/late/ae2>) | 1 |
| [club/heiqi/uilib/net/api](<../../../src/main/java/club/heiqi/uilib/net/api>) | 34 |
| [club/heiqi/uilib/net/client](<../../../src/main/java/club/heiqi/uilib/net/client>) | 1 |
| [club/heiqi/uilib/net/codec](<../../../src/main/java/club/heiqi/uilib/net/codec>) | 8 |
| [club/heiqi/uilib/net/core](<../../../src/main/java/club/heiqi/uilib/net/core>) | 7 |
| [club/heiqi/uilib/net/store](<../../../src/main/java/club/heiqi/uilib/net/store>) | 3 |
| [club/heiqi/uilib/net/transport](<../../../src/main/java/club/heiqi/uilib/net/transport>) | 5 |
| [club/heiqi/uilib/net/transport/forge](<../../../src/main/java/club/heiqi/uilib/net/transport/forge>) | 3 |
| [club/heiqi/uilib/net/transport/vanilla](<../../../src/main/java/club/heiqi/uilib/net/transport/vanilla>) | 4 |
| [club/heiqi/uilib/resource](<../../../src/main/java/club/heiqi/uilib/resource>) | 1 |
| [club/heiqi/uilib/ui/base/cascade](<../../../src/main/java/club/heiqi/uilib/ui/base/cascade>) | 1 |
| [club/heiqi/uilib/ui/base/props](<../../../src/main/java/club/heiqi/uilib/ui/base/props>) | 3 |
| [club/heiqi/uilib/ui/base/values](<../../../src/main/java/club/heiqi/uilib/ui/base/values>) | 3 |
| [club/heiqi/uilib/ui/diagnostic](<../../../src/main/java/club/heiqi/uilib/ui/diagnostic>) | 4 |
| [club/heiqi/uilib/ui/env](<../../../src/main/java/club/heiqi/uilib/ui/env>) | 6 |
| [club/heiqi/uilib/ui/event](<../../../src/main/java/club/heiqi/uilib/ui/event>) | 4 |
| [club/heiqi/uilib/ui/host](<../../../src/main/java/club/heiqi/uilib/ui/host>) | 5 |
| [club/heiqi/uilib/ui/hud/api](<../../../src/main/java/club/heiqi/uilib/ui/hud/api>) | 24 |
| [club/heiqi/uilib/ui/image](<../../../src/main/java/club/heiqi/uilib/ui/image>) | 8 |
| [club/heiqi/uilib/ui/input](<../../../src/main/java/club/heiqi/uilib/ui/input>) | 11 |
| [club/heiqi/uilib/ui/markdown](<../../../src/main/java/club/heiqi/uilib/ui/markdown>) | 3 |
| [club/heiqi/uilib/ui/reactive](<../../../src/main/java/club/heiqi/uilib/ui/reactive>) | 8 |
| [club/heiqi/uilib/ui/render](<../../../src/main/java/club/heiqi/uilib/ui/render>) | 40 |
| [club/heiqi/uilib/ui/runtime](<../../../src/main/java/club/heiqi/uilib/ui/runtime>) | 1 |
| [club/heiqi/uilib/ui/scene](<../../../src/main/java/club/heiqi/uilib/ui/scene>) | 1 |
| [club/heiqi/uilib/ui/scene/control](<../../../src/main/java/club/heiqi/uilib/ui/scene/control>) | 58 |
| [club/heiqi/uilib/ui/scene/control/search](<../../../src/main/java/club/heiqi/uilib/ui/scene/control/search>) | 14 |
| [club/heiqi/uilib/ui/scene/form](<../../../src/main/java/club/heiqi/uilib/ui/scene/form>) | 7 |
| [club/heiqi/uilib/ui/scene/host](<../../../src/main/java/club/heiqi/uilib/ui/scene/host>) | 4 |
| [club/heiqi/uilib/ui/scene/host/lwjgl](<../../../src/main/java/club/heiqi/uilib/ui/scene/host/lwjgl>) | 8 |
| [club/heiqi/uilib/ui/scene/image](<../../../src/main/java/club/heiqi/uilib/ui/scene/image>) | 3 |
| [club/heiqi/uilib/ui/scene/input](<../../../src/main/java/club/heiqi/uilib/ui/scene/input>) | 29 |
| [club/heiqi/uilib/ui/scene/layout](<../../../src/main/java/club/heiqi/uilib/ui/scene/layout>) | 15 |
| [club/heiqi/uilib/ui/scene/node](<../../../src/main/java/club/heiqi/uilib/ui/scene/node>) | 10 |
| [club/heiqi/uilib/ui/scene/overlay](<../../../src/main/java/club/heiqi/uilib/ui/scene/overlay>) | 7 |
| [club/heiqi/uilib/ui/scene/paint](<../../../src/main/java/club/heiqi/uilib/ui/scene/paint>) | 12 |
| [club/heiqi/uilib/ui/scene/runtime](<../../../src/main/java/club/heiqi/uilib/ui/scene/runtime>) | 12 |
| [club/heiqi/uilib/ui/scene/text](<../../../src/main/java/club/heiqi/uilib/ui/scene/text>) | 7 |
| [club/heiqi/uilib/ui/scene/theme](<../../../src/main/java/club/heiqi/uilib/ui/scene/theme>) | 4 |
| [club/heiqi/uilib/ui/screen](<../../../src/main/java/club/heiqi/uilib/ui/screen>) | 3 |
| [club/heiqi/uilib/ui/text](<../../../src/main/java/club/heiqi/uilib/ui/text>) | 6 |
| [club/heiqi/uilib/ui/text/layout](<../../../src/main/java/club/heiqi/uilib/ui/text/layout>) | 4 |
| [club/heiqi/uilib/util](<../../../src/main/java/club/heiqi/uilib/util>) | 5 |
