# 规划-Markdown 表格（立项定稿）

**状态：** 立项**冻结**（2026-09-07）。来源 = 用户发起「可否推进表格解析渲染」→ GPT 网页两轮审查 + 本地一手核查逐轮收口，终轮双方一致「不需要再改方案，等 C9 收口后落笔」；C9·6（`cdde849f`）落地后成文本档。**T1 未开工。**
**上游裁定：** 《规划-通用Markdown渲染器.md》§五 D4「图片/表格/任务列表/tooltip·书本划到本期范围外（可另立项）」——本文即该"另立项"；§202 C3（块模型进公共面）的预定触发器（"证明行接缝表达不了表格/多栏"）已由 T1 的 B1 最小契约承接，**不整套复活 A 案**。
**目标仓：** Qz-UILib（branch `4.0`，MC 1.7.10，本地提交不 push）。
**基线（立项时点，一手核实）：** `4118 / 0 / 0 / 6，369 类`（C9 收口后）；常驻门禁 R = commonmark-java 0.21.0（+GFM strikethrough）对 `toLayoutLines` 逐行逐 token 对拍，RECORD 恒 0；判据按 C9·6 为**二件套**——语义工件 `diff=A826A2B9E7B0EB57`、`matrix=804A42FB09D74FF5` 逐字不变（profiles 降为记录不校验）。

## 一、立项前一手核查事实清单（本档全部设计判断的地基）

1. **L1 对表格零认知**：`MarkdownBlockParser` 无任何 `|` 行分支（grep 核实），表格现状 = 普通段落字面（`MarkdownBlock` 类头明文「刻意不支持……按普通文本字面保留」）。在明确的 unsupported 分支上开表，不是修补被污染的实现。
2. **接缝冻结现状**：`MarkdownLayoutLine` 全成员恒 **20**（`MarkdownPublicSurfaceGuardTest` 逐类分解钉死），公共 10 参构造器**签名自 M7 冻结**；其类头声明「刻意不是块模型（无子树/无 children）」——二维块塞进行语义域即违约。
3. **像素归属既有事实**：`MarkdownPainter.wrapLayoutLines/toLayoutPaintCommands` 均持 `TextLayoutService`；`MarkdownLayoutLine` 全字段构造 javadoc 明言「块统一内容宽是度量事实，只有持度量服务的 L2 能算，L1 恒取定义值 0」。表格列宽同理落 L2，L1 零度量铁律不破。
4. **样式登记先例**：`MarkdownStyleTable` 类头「公共方法面不因此膨胀」+ F1（code 衬底/字号）、F3（引用色）**包内登记**先例在案——表格旋钮零公共面增量有既定机制，审查侧「样式面空白」实为"故意未公共化"。
5. **嵌套类型台账细则**：守卫细则「public 嵌套类型一律不计成员数」（`Kind` 即先例）——单外壳 + 嵌套的骨架形状台账成本最低（§二 B1）。
6. **oracle 依赖现状**：`dependencies.gradle` 已有 core + strikethrough（注释明文「testImplementation 不进生产 jar」，2026-09-06 用户裁定引入）；`GRADLE_USER_HOME=D:\Apps\.Env\Gradle\.gradle` 缓存**无** `commonmark-ext-gfm-tables` 工件且 build 一贯 `--offline` → 需一次联网（§二已写死为前置）。
7. **R 侧映射器可扩展**：门禁 R 侧 `CommonMarkReferenceSemantics` 按 commonmark Kind 映射（`BLOCK_QUOTE`/`CODE_FENCED` 等已在册），表格 AST 对拍面是它的同类旁路而非改造。
8. **关联挂账**：C9·6 第 7 条实证页面布局缺陷（markdown 页 quoteGroup 内层列 FILL 在 SHRINK 行内拿整行宽 628+8=636，字体无关）。表格列宽同样吃页面列——**T2 开工前该挂账必须收口或明确裁定**，否则表格判据被同一污染面误读。

## 二、裁定终表（两轮收口冻结，重开须新裁定）

| 项 | 裁定 |
|---|---|
| Table 是否独立语义块 | **是**：L1 包内 `MarkdownBlock.Kind.TABLE` + 包内表格结构（Table→Row→Cell；cell 持 span 流内容，经既有 `MarkdownInlineParser`，**禁**造表格版行内解析器、禁存已渲染 String） |
| 跨层接缝形态 | **B1 = 最小跨层数据契约**：单公共外壳 `MarkdownTableModel`（Row/Cell/Alignment 全嵌套，无像素字段、无度量服务引用、不出具完整块子树）+ `MarkdownDocument` +1 导出法。这是"数据契约"，**不是** L1 AST 公共化 |
| `MarkdownLayoutLine` + 表格引用成员 | **否**（语义域不匹配优先于台账成本；行不装二维块） |
| 整块模型公共化（B2 / C3 全复活） | **否** |
| L1 直接算像素（B3） | **否**（违铁律 3） |
| 首消费者 | 仅 `MarkdownPage` + headless 出图；**chat3 到 T3 另裁**（窄气泡可用性是产品决策） |
| T2 前 unsupported 降级形态 | **正锁**（L1 已识别 TABLE）+ **反锁**（`toSegments`/`toLayoutLines` 对表格输出与今日字面形状逐字节一致）+ javadoc 一句「TABLE 于 T2 前只解析、不布局」三件套；**不造** capability 协商框架（禁把暂态迁移变成永久公共概念） |
| 字面降级性质 | **产品化暂态行为**：T2 移除时是一次显式行为迁移（锁改动入提交信息），不是从测试黑洞捞伪实现 |
| 两个 oracle 面 | **彻底分离**：T1 = 新增 Table AST 对拍面（R=TablesExtension ↔ B=`MarkdownTableModel`），**现行行接缝对拍零接触**（两侧都仍视表格为段落 → 恒绿，二件套哈希逐字不变照 C8 口径）；T2 = TABLE 身份进布局接缝后，才把表格形状并入行接缝对拍 |
| 文档序交错 | 归 **T2**（T1 接缝不表达表格，"paragraph-table-paragraph" 在旧缝看仍是字面行，T1 无可验证性）；T2 三案实测（① 哨兵行+旁挂 model ② union item（破坏性，最后选）③ 平行表格 units+块锚），模板 = 仓内**两缝并存**先例（`toSegments`/`toLayoutLines`）；三案皆硬伤才升格「文档布局接缝抽象化」独立裁定 |
| 度量次序 | 宽度 pass 先于换行：measure 全 cell → intrinsic 列宽 → 按可用宽分配 → 逐 cell wrap → 行高 → 定位；禁"先按整行折再回头修表" |
| 样式面 | 表格旋钮全走包内登记（F1/F3 先例），`MarkdownStyleTable` 公共面**零增量** |
| 语法优先级 | 表头升级 vs setext H2（`---` 已被 C3b2/C4 实现并收紧惰性口径）、blockquote 内惰性行含 `|`、列数不齐、`\|` 转义——**一律以 TablesExtension 实测为唯一真相，不心算、不读规格凭感觉** |
| 缓存 | 表格布局进消费层 L3 缓存（列宽 = 全 cell 度量，比行布局贵一量级），随度量纪元整体失效；零每帧解析铁律；缓存责任在消费层（渲染器规划 §六风险 3 口径） |
| oracle 依赖 | `org.commonmark:commonmark-ext-gfm-tables:0.21.0`，testImplementation（同 parent），**一次联网拉取已获用户批准（2026-09-07）**；「运行时依赖 commonmark」的事实架构层面永不成立，不再讨论 |
| 首版语法面 | GFM 基础锁死（§五），一字符都不扩 |

## 三、批次定义

### T1 —— 语义与契约（不产一像素、不动任何消费者、行接缝零接触）

1. `MarkdownBlockParser` 识别 TABLE 块（GFM 表头+分隔行；优先级冲突用例先跑 R 侧实测再定实现）；
2. 包内表格结构 + `Kind.TABLE`（cell=span 流，样式锚点同 C6a 机制随行）；
3. 公共接缝：`MarkdownTableModel`（单外壳+嵌套，零像素）+ `MarkdownDocument` +1 导出法（含块锚信息）；
4. **新增 Table AST 独立对拍面**：语料覆盖列数/行数/三对齐/`\|`/列数不齐补空/cell 内 `** ~~ ` $ []` 行内复用/setext-vs-delimiter/惰性续行/引用内表格；
5. **现行行接缝门禁零接触**：验收 = diff/matrix 两哈希逐字不变；
6. 字面降级三件套（正/反锁 + javadoc）；
7. 守卫与台账同批：G2 新文件入零 MC 依赖扫描；G3 复生锁加反锁项「`ui/markdown`/`chat3` 内零 `|` 行检测」（带正对照地板）；五锚台账 +`MarkdownTableModel` 行（全成员+方法/构造/字段分解）与 `MarkdownDocument` 方法数续账；合计地板复核；`MarkdownStyleTable` 恒 19 不动；
8. `dependencies.gradle` +1 行，一次联网拉取（前置，可为 T1 首票）。

**T1 验收：** build 绿；行接缝二件套哈希逐字不变；Table AST 对拍全绿；反锁证明消费者可见输出与立项时点逐字节一致；javap 台账逐字入账。

### T2 —— 像素与首消费者（出图 / `MarkdownPage`）

- L2 表格布局 pass（intrinsic→分配→wrap→行高）→ `PaintCommand` 流（G1：新代码零 `GL11.`）；
- 文档序交错三案实测并裁；行接缝 oracle 于本批并入表格形状（TABLE 身份入缝的唯一时机）；
- 字面降级移除 = 显式行为迁移（锁改动逐条入提交信息）；
- 验收面沿《规划-通用Markdown渲染器.md》§五之二：headless 出图（人眼可判整页合成图 + 可机判地板）+ playground `MarkdownPage` 增表格段；
- 出图/页面判据**继承 C9 方法论**（派生形地板、双度量自证、代理可区分前置）；开工前先收口 C9·6 第 7 条挂账或明确裁定；
- 消费层 L3 缓存 + 度量纪元失效落地并有锁。

### T3 —— chat3 消费者（另裁）

- 窄气泡下表格的产品决策（压缩降单列/横滚/维持字面）届时由用户裁；缓存命中与零每帧解析复查。

## 四、硬约束重申（表格立项期间一条不破）

G1（L2 零直连 GL）/ G2（L1 零 MC·AWT）/ G3 扩展项（表格语法只活在 L1）/ G4（度量同源，样式表为唯一数值登记处）；§=普通字符宪法（cell 内同样）；门禁 RECORD 零豁免；死代码窗口——新接缝必须在同一大批次内获得真实消费者（Table AST 对拍面与出图测试计为消费者，口径同 M1-M6，窗口不跨批次收口）。

## 五、首版语法面（GFM tables，锁死）

✓：表头行+分隔行、`:---`/`:---:`/`---:`、行列数不齐补空、cell 内行内 markdown（复用 L1）、`\|` 转义、自动列宽、cell 按列宽软折。
✗：colspan/rowspan、HTML `<table>`、cell 内块级嵌套、表格嵌套、排序/交互（后者是产品能力不是 parser 问题）。

## 六、风险

1. **优先级面误判**：表头升级 vs setext/惰性续行是 CommonMark+GFM 最易踩错的接缝，唯一对策 = R 实测先行（语料取自 GFM 规范表格节与 TablesExtension 行为）。
2. **平台度量差**：列宽分配/软折是 C9 刚治完的误红重灾区，T2 判据必须构造形/派生形，禁绝对像素地板。
3. **暂态污染**：字面降级若被后续批次"顺手"扩到新消费者即失控——T2 起每消费者接入都是显式裁定，反锁不撤到 T3 收口为止。
4. 运行态：本立项全部依据 headless 与代码一手核实，**未跑真机**；观感验收归 T2/T3 既定分工（真机用户判读）。

## 七、进度表

| 步 | 内容 | 状态 |
|---|---|---|
| 立项 | 两轮收口 + 本地核查 + 本档落笔 | **完成（本笔）** |
| T1 | 语义+契约+独立 oracle 面对拍+降级三件套+台账续账 | 待开工 |
| T2 | 像素 pass+交错三案+首消费者+行接缝并表格+缓存 | 未开始 |
| T3 | chat3 消费者裁定 | 未开始 |
