# 富文本标签与 SceneLabel

Qz-UILib 的通用文本显示组件 **SceneLabel** 原生支持现代富文本标签语法（RICH_TAGS 模式），
与 Minecraft 原版 § 格式码完全无关（§ 模式仅作为兼容遗产保留，新代码一律不宣传、不使用）。

## 快速上手

```java
// 富文本标签：contentMode 传 TextStyle.TEXT_MODE_RICH_TAGS
SceneLabel.Props props = new SceneLabel.Props(
        textSignal,                         // ReadableSignal<String>，可含标签，响应式更新
        0xFFE6E1E5,                         // ARGB 默认文字色
        16,                                 // 基准字号（UI 像素）
        TextStyle.TEXT_MODE_RICH_TAGS,      // 内容模式：富文本
        320);                               // wrapWidth：<=0 不换行

SceneNode label = SceneLabel.create(runtime, props).get();
parent.appendChild(label);
```

组件形态与 scene 栈其它控件一致：纯静态工厂 + record Props + 组件函数（契约 R1/R2/I3），
文本经 signal 驱动；节点不可命中（`hitTestable=false`），不拦截任何输入。

## 标签语法

| 标签 | 语义 | 示例 |
|---|---|---|
| `<color=#RRGGBB>` | 24 位颜色 | `<color=#FF5533>红</color>` |
| `<color=#AARRGGBB>` | 8 位 ARGB（含透明度） | `<color=#80FF5533>半透明</color>` |
| `<color=名字>` | CSS 16 基础色名（black/silver/gray/white/maroon/red/purple/fuchsia/green/lime/olive/yellow/navy/blue/teal/aqua） | `<color=gold>金</color>` |
| `<b>` | 粗体 | `<b>粗</b>` |
| `<i>` | 斜体 | `<i>斜</i>` |
| `<u>` | 下划线 | `<u>线</u>` |
| `<s>` | 删除线 | `<s>删</s>` |
| `<size=N>` | 绝对像素字号（1..256，越界截断） | `<size=24>大</size>` |
| `<br>` / `<br/>` | 硬换行 | `第一行<br>第二行` |

**通用规则**

- 任意嵌套，样式继承父级；闭合标签（`</color>`、`</b>` 等）后回退父样式；通用闭合 `</>` 关闭最近一层。
- 转义实体：`&lt;` → `<`、`&gt;` → `>`、`&amp;` → `&`。
- 颜色与字号支持 `<color=值>` 与 `<color 值>` 两种写法。

**宽容解析（宽容失败）**

| 输入 | 行为 |
|---|---|
| 未知标签（如 `<foo>`） | 原样保留为字面文本（含尖括号） |
| 未闭合标签 | 自动闭合到文本末尾 |
| 多余/错配闭合标签 | 忽略（错配时吞掉中间已开样式） |
| 坏属性（`<color=不是颜色>`、`<size=abc>`） | 忽略该属性，文本继承父样式 |

## 能力与语义

- **测量一致**：标签不占任何测量宽度；宽度/裁剪/自动换行/对齐全部走同一解析器。
- **换行续传**：换行切在样式片段中间时，行尾显式闭合、行首自动重开标签，跨行样式零特判。
- **字号混排**：同一样式行内不同字号共享同一基线（baseline-aligned）；advance 按各段字号推进。
  字号变化走位图缩放（同一光栅纹理按目标字号缩放渲染），与引擎既有 charSize 机制同质。
- **`<br>` 与裸换行符**：富文本模式下统一折叠为换行标记（零宽，不产生字形）。

## 限制（首版）

- 行高按组件基准字号计算，混排极端字号差时行距可能偏紧（由基线对齐保证正确显示）。
- 不做链接与 span 级交互（悬停/点击命中）；不做 `<font>` 字体族切换。
- 富文本只覆盖**显示侧**；编辑器（SceneTextInput/TextArea）仍按原文本模式工作。

## 相关测试场地

真机测试场地（devtools playground）新增「富文本」页：样式标签、字号混排、wrapWidth 自动换行、
宽容解析四组演示 + SceneLabel signal 交互切换。
