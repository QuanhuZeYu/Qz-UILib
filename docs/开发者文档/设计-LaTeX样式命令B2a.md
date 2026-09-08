# LaTeX 样式命令 B2a：待裁定的接口方案

状态：具体设计待用户裁定，尚未修改生产 API 或实现命令。基于 B1 提交 d88bd9c0。

## 交付行为

支持 `\dfrac`、`\tfrac`、`\displaystyle`、`\textstyle`、`\scriptstyle`、`\scriptscriptstyle`。复用 LatexParser → MathLayoutService/MathStyle → MathBox → 既有字体批次/PaintCommand，不新增渲染器、缓存、字体资源或依赖。

声明从当前位置作用到当前数学列表作用域结束，后续声明覆盖前者。显式声明选取根字号对应的级别并重置为非 cramped；随后分母、下标等结构仍按 B1 转换。分式命令只覆盖分式自身样式。现有 parse/layout 签名、Kind 值、旧构造器、入口默认 text、limits 默认和美元语义均保留。新识别的命令从原先字面降级变成实际排版，这是本批预期的可见变化。

## 建议公开 API

在 `club.heiqi.uilib.font.latex` 新增：

```java
public enum MathStyleOverride {
    INHERIT, DISPLAY, TEXT, SCRIPT, SCRIPTSCRIPT
}
```

在 `LatexNode` 保留原构造器，并新增：

```java
protected LatexNode(Kind kind, MathStyleOverride mathStyleOverride);
public MathStyleOverride getMathStyleOverride();
```

新增字段为 private final，null 拒绝，旧构造器委托 INHERIT。不新增抽象方法，不要求外部子类实现复制协议。B1 内部 MathStyle 继续保存根字号和 cramped，不直接暴露给调用者。这里仅承诺新增样式字段不可变；现有 LatexAtom.setLimitsFlag 仍可变，不在本批改其兼容约定。

下列节点各在当前最完整构造器末尾增加一个 `MathStyleOverride mathStyleOverride` 参数的重载，保留所有旧构造器：

| 节点 | 重载保留的原参数 |
| --- | --- |
| LatexAtom | String text, AtomClass atomClass, OperatorMode operatorMode |
| LatexSupSub | LatexNode base, LatexNode sup, LatexNode sub |
| LatexSqrt | LatexNode index, LatexNode radicand |
| LatexGroup | List<LatexNode> children |
| LatexBinom | LatexNode upper, LatexNode lower |
| LatexSpace | double emWidth |
| LatexAccent | String accentText, LatexNode base, boolean stretchable, boolean below |
| LatexLeftRight | String leftDelimiter, List<LatexNode> parts, List<String> middleDelimiters, String rightDelimiter |
| LatexMatrix | Fence fence, List<List<List<LatexNode>>> rows, List<Character> columnAligns |

LatexFrac 另新增独立枚举与完整构造器，限制分式覆盖到合法的两种命令样式：

```java
public enum FractionStyle { INHERIT, DISPLAY, TEXT } // LatexFrac 内嵌
public LatexFrac(LatexNode numerator, LatexNode denominator,
                 MathStyleOverride mathStyleOverride, FractionStyle fractionStyle);
public FractionStyle getFractionStyle();
```

旧 `LatexFrac(numerator, denominator)` 委托两个 INHERIT。新增构造器的枚举参数不接受 null。普通声明作用于整个当前因子；FractionStyle 仅在分式布局时应用，不影响包围它的 LatexSupSub 的脚本级别。

## 解析与布局规则

- 每个数学列表只记录本作用域实际出现的局部声明。进入组、分子、分母、脚本等子列表时从局部 INHERIT 开始，运行时通过父结构继承经过转换的样式；不得把外部声明重复写入所有后代，否则分母和脚本会被错误放大或解除 cramped。
- 声明元数据附到完整因子上，包括外层 LatexSupSub；其 base 默认继承，保留 base 内真正局部的覆盖。裸命令参数延续现有 parseAtom 的绑定边界。
- 声明本身不产生可见原子，也不把后续序列包装成普通 GROUP。全列表统一计算 BIN 降级；跨声明仍保持原子邻接。前置自动间距使用右节点的当前列表声明样式，分式独立覆盖不参与外层 glue，并验证声明前后两侧；显式 kern 根据其自身有效样式度量。
- 样式改变后的字形倍率必须在 layoutNode 返回边界换算回传入样式的尺度；坐标和规则线已是逻辑像素，不重复缩放。子盒的 advance 与视觉边界分别保留。layoutSupSub 的原子基底快路径也必须遵守手工构造的 base 覆盖，但不能把该覆盖当成整个 SupSub 的样式。
- 矩阵单元进入既有 TEXT 基线，各单元内可显式覆盖，列与行边界结束局部声明。left/middle/right 的 middle 不应凭实现分段意外结束同一个定界数学列表的声明。
- text 内容和其他未知命令仍按现有字面规则处理。命令源码已经在 LatexCache 键中，仅更新布局版本，不增加第二套缓存。

## 兼容影响

旧构造器和方法描述符保留；Kind 不扩展，现有 switch 与具体类型强转无需增加分支。旧构造器生成的 AST 延续原样式行为。新增基类方法对通常的旧子类不提出实现要求，但无法保证任意第三方子类恰好声明同名且不兼容返回类型的源码仍能编译。

外部 AST 访问者若忽略新字段，将丢失显式样式语义；重建 AST 时必须把 mathStyleOverride 以及 fractionStyle 一并传给新重载。直接通过旧构造器重建有样式节点会回到 INHERIT。这是本批需要明确批准的公开承载扩展。

## 实施验收

- 解析：`a+\scriptstyle b` 的完整原子邻接；`{\scriptstyle a}b` 作用域；连续声明；声明后无内容；脚本内显式 display；分子覆盖不影响分母；矩阵单元和 left/middle/right 作用域。
- 绑定：`\dfrac ab^2` 保持脚本附着整个分式；`\scriptstyle\dfrac ab^2` 分式采用 DISPLAY，但外层脚本仍从 SCRIPT 转换；带 limits 的原子不能在节点重建时丢标记。
- 几何：dfrac 与 tfrac 的子级字号、clearance 和整体高度确有差异；深脚本封顶、cramped、混合样式边界间距、负 kern、规则线与 ink 包围盒、旧构造器等价行为。
- 绘制：真实字体的生产采集链路中测量和绘制保持一致；软件样张使用足够包围画布，覆盖原尺寸与高分辨率重绘，避免沿用 B1 固定起点混排裁切作为完整性证据。
- 更新支持／近似／字面降级命令清单；完整 offline build 成功并审查 diff 后，本地提交本批生产增量。数学数值与测试汇总使用 Python 验算。未实际执行的 GPU、游戏客户端和参考引擎对拍不列为通过。

## 确认事项

批准上述新增枚举、基类只读字段接口、节点构造器重载与分式独立覆盖；批准这些显式命令从字面降级变成样式语义。批准后直接实施、验证并本地提交 B2a，无需再次确认常规实现步骤。
