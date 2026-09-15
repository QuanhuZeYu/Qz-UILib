# LaTeX 数学字体命令 B2b：具体兼容方案

状态：用户已在具体接口与独立复核说明后指示“继续推进”，本方案公共 API 与可见行为已获批准，首批实现、完整构建和双字体软件验证已完成。基线提交 76c2a3d6，设计提交 873ecf48；实际验证结果见本批交付记录。

## 范围与现有能力

本批先支持 `\mathrm` / `\mathit`。复用 LatexParser → MathLayoutService/MathMetrics → MathBox/GlyphElem → DefaultFontRendererAdapter → GlyphBatchCollector/PaintCommand。FontType 当前只有 NORMAL/BOLD；数学斜体通过已有几何斜切实现，没有独立 italic face 或数学字体身份。

因此首批交付为明确的直体／斜体命令语义与既有字形斜切近似，不宣称接入真正的数学斜体字体。默认字体、字体资源、FontType、依赖和宿主数学入口不变；mathbf/operatorname/mathnormal 等另行分批。

目前 GlyphElem 只有 italic boolean，adapter 把外层 TextStyle.isItalic 与该值作 OR。这无法表达在 `<i>` 中强制直立的 mathrm。本批必须携带显式优先级，测量的斜体越量、脚本避让和最终 glyph quad 也须采用同一事实。

## 已核查参考

实际以 HTTP 200 读取 KaTeX v0.16.22 源码：

- [font.js](https://raw.githubusercontent.com/KaTeX/KaTeX/v0.16.22/src/functions/font.js)：字体命令读取一个参数，normalizeArgument 后使用 withFont 构建。
- [defineFunction.js](https://raw.githubusercontent.com/KaTeX/KaTeX/v0.16.22/src/defineFunction.js)：仅在参数 ordgroup 恰有一个子节点时去掉一层参数组。
- [ordgroup.js](https://raw.githubusercontent.com/KaTeX/KaTeX/v0.16.22/src/functions/ordgroup.js)：普通多原子组使用 mord 类，不把所有参数无条件展平。
- [buildCommon.js](https://raw.githubusercontent.com/KaTeX/KaTeX/v0.16.22/src/buildCommon.js)：普通数学原子按字体映射选字形，bin/rel/open/close 等符号使用独立符号路径；不是把整个参数中的一切符号统一斜切。

据此，本批必须区分 `a\mathrm{+}b`（保留 BIN）与 `a\mathrm{{+}}b`（额外显式组保留 ORD），`\mathrm{ab}^2` 仍以完整参数为脚本基底。参考源码核查只支持语义设计；没有运行外部引擎对拍，不声称普通字体斜切与其字形等价。

## 最小公开 API 增量

建议只为原子增加字体元数据，并为单参数命令组显式保留透明原子类别；不扩展 Kind 或全部结构节点：

```java
// club.heiqi.uilib.font.latex
public enum MathFontStyle { INHERIT, UPRIGHT, ITALIC }

// LatexAtom：保留现有全部构造器
public LatexAtom(String text, AtomClass atomClass, OperatorMode operatorMode,
                 MathStyleOverride mathStyleOverride, MathFontStyle mathFontStyle);
public MathFontStyle getMathFontStyle();

// LatexGroup：保留外层列表样式与参数局部样式两层
public LatexGroup(List<LatexNode> children, MathStyleOverride mathStyleOverride,
                  boolean transparentForAtomClass);
public boolean isTransparentForAtomClass();

// GlyphElem：保留现有全部构造器
public GlyphElem(String text, float x, float y, float sizeScale,
                 boolean italic, boolean inheritTextItalic);
public boolean isInheritTextItalic();
```

新增字段 private final。旧 LatexGroup 构造器默认 transparentForAtomClass=false，true 只允许恰有一个子节点，其他情况拒绝；普通显式组行为保留。旧 LatexAtom 构造器委托 INHERIT，新枚举参数拒绝 null；旧 GlyphElem 构造器默认 inheritTextItalic=true，延续旧外层富文本斜体叠加。INHERIT 表示沿用旧数学字形默认规则；parser 在显式命令参数内把词法字体上下文写入实际原子，最终 AST 不依赖 parser 旁表。

新增 GlyphElem boolean 专门表达是否接受宿主文本斜体，与 italic 的最终本地开关分离。数学排版决定该字形本地 italic 与继承开关后，绘制计算 `italic || (inheritTextItalic && hostItalic)`；主绘制和阴影均使用此结果。MathBox 展平、归一、移动和所有 GlyphElem 复制都必须保留该标记。内部 prepared buffer 增加对应标记，不新增公开缓存或渲染协议。

## 明确行为

- mathrm 的参数内普通字母和数字采用直体；mathit 对普通字母和数字采用既有斜切近似。明确覆盖 ASCII 字母／数字及已识别的希腊字母；其他 Unicode 数学字母和非字母 ORD 符号逐类验证，不把 Character.isLetter 覆盖到的所有语言都宣称为真正数学字体支持。
- 二元、关系、定界符和大运算符保持原有数学类别与符号形态；mathit 不把加号、括号、根号等整结构斜切。命名函数保持现有 OP 语义。仅文字 `mathit{sin}` 与命令 `mathit{\sin}` 的类别本来就不同。
- 命令只读取一个原子参数或花括号参数，参数内数学空白照常忽略，不转换成 text。命令参数为单节点时保留真实 LatexGroup 并标记透明原子类别，多节点参数及额外显式组保持普通 GROUP。透明组仍保留大小样式的外层／内层边界，分类及脚本基底识别沿透明层访问子节点，绘制布局逐层应用尺寸覆盖。不能全局把所有单节点 GROUP 都当成透明组。
- parser 保存局部字体上下文并在参数结束时恢复；嵌套字体命令以最内层为准。参数内部的脚本、分式、根体、矩阵单元按词法嵌套继承字体上下文，而字号样式仍按 B2a 独立转换。`\mathrm{x_i}^j` 中 x/i 直体，外部 j 使用原默认。
- text 是独立文本语义，保留原有转义和宿主样式规则，不把 mathit 的数学字母筛选套给 text；未知命令仍按既有字面降级，不新增宏解释器。
- 显式 mathrm/mathit 的数学原子字形按局部选择屏蔽外层富文本斜体再叠加；旧源码中未使用新命令的字形沿用原行为。该覆盖必须同时验证正常绘制与阴影。根号符号、重音符号和伸缩定界符由结构布局生成，并非带 MathFontStyle 的 LatexAtom；按本批原子承载范围，这些生成字形保留既有宿主斜体继承，不宣称整个公式结构屏蔽宿主斜体。
- 字号、cramped、分式独立样式与字体状态正交。脚本补偿和重音偏移按实际字形斜体状态，而非仅按 ASCII 字母判断；直体取消斜切补偿，斜体数字／希腊字母获得相应 ink 边界和避让。

## 大小样式叠加的关键反例

`\scriptstyle\mathrm{\displaystyle x}` 的外列表处于 SCRIPT，参数 x 显式 DISPLAY；若只删除参数组并把字体写到 x 上，B2a 的外层因子标注会覆盖 x 的局部 DISPLAY。因此本方案保留透明参数组作为真实样式边界：外组 SCRIPT → 子节点 DISPLAY。前置列表 glue 仍取外组样式，字形取内部样式。

`a\mathrm{\scriptstyle +}b` 还要求外层分类保留 BIN，局部字形可为 SCRIPT。透明单节点组直接布局其子节点，不在内部建立新的单节点数学列表，避免把 BIN 意外按行首降为 ORD；外列表统一做 BIN 降级。额外显式 `{+}` 的普通组仍按现有规则处理。

## 兼容影响

保留 Kind、旧构造器、旧方法描述符及 MathMetrics/FontType/parse/layout 接口，不要求外部子类增加抽象实现。新增方法仍存在任意外部源码同名方法冲突的通常风险，不能承诺一切未知第三方源码兼容。

手工重建 LatexGroup 时必须复制 transparentForAtomClass，外部访问者若一律把 GROUP 归为 ORD，将丢失字体命令单参数的 BIN/REL/OP 语义；Kind 与实际类型仍对应，不假借其他 Kind。手工重建 LatexAtom 时必须复制 mathFontStyle、mathStyleOverride、operatorMode 和 limitsFlag；重建 GlyphElem 时必须复制 inheritTextItalic。旧构造器仍可用，但会丢失新命令的显式状态。现有“只复制 italic”的第三方盒变换必须迁移才能保留 mathrm 在外层斜体中的效果。

新识别的命令从字面显示变成排版语义。mathit 采用已存在的斜切而非新 italic font face，这是本批近似能力的明确边界；后续字体资源方案不能把本批支持状态冒充完整数学字体支持。

## 实施与验收

解析用例：单 BIN/REL/OP 参数、双层花括号、多原子脚本基底、嵌套 mathrm/mathit、组外恢复、裸参数脚本绑定、大小样式声明混合、分式参数与矩阵单元、text 和未知命令边界。B2a 原子复制 helper 必须保留新字体字段。

几何用例：直体 x 的脚本不再添加斜切越量，显式斜体数字与希腊字母的脚本和重音使用实际状态；分式规则覆盖 ink，负 kern advance 与边界不被破坏。旧 INHERIT 构造行为等价，字号封顶和 cramped 回归继续通过。布局版本需更新。

绘制用例：`<i><latex>\mathrm{x}+y</latex></i>` 局部强制直体、mathit 数字、嵌套字体、正常／阴影一致性、不同字体命令源码交替命中缓存。复用现有生产采集与软件 testkit，两套本机字体、原尺寸 nearest 与高分辨率实际重绘分别报告，非整数起点和动态画布留边。数学数值和测试汇总用 Python 验算。

最终运行完整 offline build，检查编译产物旧接口保留，审查差异后本地增量提交。未运行的游戏客户端、GPU shader 和参考引擎对拍明确列出。

## 已批准范围

本次批准涵盖以上 MathFontStyle、LatexAtom、LatexGroup 与 GlyphElem 构造器／只读接口增量，以及新命令使用现有斜切近似和局部字体选择优先于宿主斜体的行为。直接实施上述回归、软件验证、完整构建和本地提交，不再为常规实现步骤重复确认。
