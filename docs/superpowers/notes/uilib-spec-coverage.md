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
| 文本与 Markdown | ui/text、markdown、scene/text；font/layout | 第 13 节补齐模式、排版及共同几何；CommonMark 基础+表格/数学扩展已获认可，图片统一系统已确认；VS Code 内置预览语义参照已确认；常用 HTML 内容子集已认可并记录第 13.5 节；HTML 降级及管道表格语义已认可；美元定界方向已认可；歧义规则按固定参照补齐，其余扩展词法待补 |
| 数学排版 | font/latex、glyph | 第 13.4 节数学常用结构及失败源文呈现已认可；具体命令表及字形边界待补 |
| 字体与资源 | font/api、page、render；resource、i18n | 第 13.3 节同字符多字体共存、度量/绘制一致及资源归属已明确；有序候选/主题默认/缺字替代已确认；加载/重载使用统一错误诊断，不增加失败分支策略；字体核心职责可交接设计 |
| 图像与物品 | ui/image、scene/image | 第 14 节统一图像职责及纹理/区域、位图、HTTP/HTTPS、物品来源已确认；第 14.6 节自然尺寸与适配已确认；第 14.4 节默认加载/失败占位与正式重载已确认；第 14.7 节物品外观/效果及可选覆盖层/提示已确认；首期合同可交接设计 |
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

富文本调查：已读取 RichTextTagParser 的能力说明及样式属性处理。现状支持 color、b/i/u/s、mark、sup/sub、size、spacing、a、br、latex 及 serialize；isKnownTagName 未列出 reset/img，不能据统一图像目标推断当前标签解析器已经支持图片。旧越界截断、未闭合宽容及错配闭合处理不自动作为新合同。第 13.5 节已按既有职责细化显式模式、局部样式及共享数学/图像能力，用户进一步明确 DSL 独立规范来源及标签分类，已写入第 13.5 节；具体集合和属性仍待明确，不把示例 value 写法自动定为接口，未增加修复或失败回退机制。

用户明确要求：能力决策不能隐含附加失败分支及复杂度。已写入 spec 第 1.1 节；诊断/清理不自动授权恢复/重试/补偿，不能仅转移到框架内部后添加。既有明确认可合同保留，新增额外行为须显式说明范围与复杂度并单独认可。

用户已认可同字符多字体共存的目标，写入第 13.3 节；有序候选回退、主题默认候选及全部缺字替代已认可，写入第 13.3 节；用户明确不增加失败分支策略，加载/重载统一诊断，不要求“首次失败/保留旧资源”策略分支，写入第 13.3 节。用户指出的当前普通字符单字体限制不作为新规格边界。只读核对普通路径 GlyphRuntimeTables、FontMatcher：字体匹配、宽度及字形槽按 codepoint + FontType 存储，match 不接收局部字体族意图，因此同一运行时代际/FontType 的同码点不能独立缓存不同字体结果。数学路径存在 MathGlyphKey 独立身份，说明普通码点直索引不是全部字体资源不可改变的物理限制。按已确认目标设计字体身份/字形资源/缓存，缺字回退及统一资源错误职责已确定。

数学定界方向已认可并写入第 13.5 节：`$…$` 行内、独立 `$$…$$` 公式块，代码内容保留字面。转义、空白/跨行、不配对及金额歧义由固定 VS Code 版本对照明确，不继承旧出口词法。常用数学结构与未知命令/排版失败保留源文并提示的方向已认可，写入第 13.4 节；具体命令表及字形支持边界继续细化。

管道表格方向已认可并写入第 13.5 节：表头/分隔行与列对齐、缺列补空、超列不增加显示列，默认自动列宽/换行/行高，仍超宽时表格内横向滚动。具体不规则行处理核对 [markdown-it 13.0.2 表格规则](<https://github.com/markdown-it/markdown-it/blob/13.0.2/lib/rules_block/table.js>)；该版本仅为本次证据，不取代将来固定的 VS Code 验收版本。上游 master/main 的读取返回 404，改用固定版本取得实际规则。

物品渲染修正：为最大兼容性不采用平台无关物品策略，宿主适配器直接调用原版 API，允许 ItemStack 声明，保留 Forge/模组绘制接缝；区域、输入及宿主状态恢复仍由 UILib 协调。

VS Code 对齐调查：官方说明以 CommonMark 为目标并使用 markdown-it；公开 main 分支 markdownEngine.ts 开启 html，并从设置提供 breaks/linkify/typographer。用户已认可对齐内置预览的文档解释及主要阅读行为，实际渲染仍使用 UILib，已写入 spec 第 13.5 节；固定稳定版本/默认设置/无第三方扩展，具体版本由设计记录。HTML/CSS 等差异必须明确，不承诺完整浏览器，HTML 全部字面显示提议撤回。常用内容标签及必要属性已获认可，记录第 13.5 节：强调、段落/换行、标题、列表/引用、代码、链接、图片、表格；转换到 UILib 结构，不承诺任意 CSS/脚本/浏览器交互。HTML 降级已认可：未知普通容器保留内部正文，未支持属性忽略，无法解释内容字面显示并诊断；script/style 等非正文不执行也不显示为正文。已写入第 13.5 节。

Markdown 采用 CommonMark 基础，明确表格与数学扩展，不承诺浏览器 HTML 和全部扩展。已记录到 spec 第 13.5 节；旧解析器不同输出路径、兼容词法及聊天特例不作为新实现必须保持的合同。用户已认可 Markdown 图片和内嵌显示，并明确由 UILib 专有图像系统管理以便业务轻量声明，已写入 spec 第 13.5、14 节。来源范围已认可：资源纹理及区域、运行时位图、HTTP/HTTPS 和 Minecraft 物品图标；写入第 14.5 节。自然尺寸、单边按比例推导、四种适配及 Markdown 默认缩小策略已认可，写入第 14.6 节；加载/失败默认占位、业务覆盖与正式重新加载已认可，写入第 14.4 节；后台重试有结束边界，具体算法由设计承担。

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
