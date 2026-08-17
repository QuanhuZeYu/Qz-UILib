# Scene Primitive API 规范

> 类型：架构规范文档
> 范围：scene 新栈所有 primitive 的 API 形态约定
> 已落地范例：`SceneTextInputPrimitive`、`SceneSelectPrimitive`

---

## 1. 核心原则

primitive 只提供**行为、状态、结构、输入处理**，不设置任何 chrome（背景色、边框、圆角、padding、cursor、文本色）。chrome 由上层 wrapper 或高级控件自行挂载。

**分层契约**：
- `Primitive.create(rt, props)` → 返回 `Result`（暴露结构节点 + 派生态 signal）
- `Wrapper.create(rt, props)` → 返回 `Supplier<SceneNode>`（保持原 public API 兼容），内部调 primitive + 挂默认 chrome

---

## 2. Props 规范

### 2.1 字段内容
- 只含**行为/数据契约**：受控值 signal、enabled、onChange/onSelect 回调、配置参数
- **禁止**：颜色常量、padding、borderWidth、cornerRadius、cursor、flat 等任何 chrome 字段
- **例外**：节点延迟/条件创建时，Props 可含 chrome 装配回调（见第 4 节）

### 2.2 注解
- record 必须 `@Desugar`

### 2.3 构造
- 主构造含全部字段（canonical）
- 可选兼容构造用于简化调用（省略 enabled 等默认值字段）
- 可变集合字段做不可变拷贝 + null 校验

---

## 3. Result 规范

### 3.1 字准
- **结构节点**：root（主树常驻根）+ 子节点引用（供 wrapper 挂 PAINT 绑定）
- **派生态 signal**：wrapper 做 chrome 决策所需的行为状态（如 caretVisible、isPlaceholder、expanded、highlightedIndex）
- **注解**：record 必须 `@Desugar`

### 3.2 节点引用 vs 派生态 signal 的取舍
- wrapper 需要直接上色的节点 → 暴露节点引用
- wrapper 需要判断状态来选色/切样式 → 暴露派生态 signal（Boolean/Integer 等）
- 不暴露内部 handler、不暴露可写 signal（wrapper 不应改 primitive 内部状态）

---

## 4. 节点暴露 vs chrome 回调的分叉规则

这是 primitive API 的核心设计决策，分两种模式：

### 模式 A：Result 暴露节点（默认）
**适用条件**：所有结构节点在 `create` 时全部确定（静态节点）。
**做法**：Result 直接暴露节点引用，wrapper 在 `create` 后拿到引用挂 PAINT 绑定。
**范例**：`SceneTextInputPrimitive.Result{root, prefixText, caret, suffixText, ...}`

### 模式 B：chrome 回调注入
**适用条件**：部分节点在 `create` 后才创建（overlay 延迟创建、show 条件创建、动态列表）。
**做法**：Props 含 chrome 装配回调接口，primitive 在节点创建时同步回调，wrapper 在回调内挂 PAINT 绑定。
**范例**：`SceneSelectPrimitive.ListboxChrome{decorateListbox, decorateItem}` + `ItemHandle`
**关键约束**：回调必须在 primitive 调用栈内**同步执行**，确保绑定登记到正确的 Owner 作用域。

### 判定流程
```
节点在 create 时全部确定？
  是 → 模式 A：Result 暴露节点
  否 → 模式 B：chrome 回调注入（节点延迟/条件创建的那些）
  混合 → 两者并存：静态节点走模式 A，动态节点走模式 B
```

---

## 5. create 方法规范

```java
public static Result create(SceneRuntime rt, Props props)
```
- 返回 `Result`（非 `Supplier<SceneNode>`）
- 内部创建所有结构节点、挂行为 handler、注册 focusable、挂 LAYOUT 级文本绑定
- **不挂 PAINT 颜色绑定**（留给 wrapper）
- **不设 chrome 属性**（padding/borderWidth/cornerRadius/cursor/backgroundColor/textColor）

---

## 6. 不变量约束

| 不变量 | 约束 |
|---|---|
| I1 | primitive handler 只 `signal.set()` 或调 `props.onChange()/onSelect()`，不直接改节点属性 |
| I4 | primitive 文本绑定走 `Invalidation.LAYOUT`；wrapper 颜色绑定走 `Invalidation.PAINT` |
| I7 | **不得新增 wrapper 节点层**——wrapper 在 primitive 返回的 root 上挂 chrome，不包新层（守 cell.children 索引 + 测试结构断言） |
| I10 | primitive 用 `SceneKey` 枚举，不碰 lwjgl/GLFW/minecraft import |
| I11 | handler 只 `signal.set` + `stopPropagation`（逃生舱②）；只读几何测量走逃生舱① |

---

## 7. wrapper 规范

### 7.1 API 兼容
- `create(SceneRuntime, Props)` 签名不变，返回 `Supplier<SceneNode>`
- 原 `Props` 字段顺序 + 兼容构造保持（消费者零改动）
- 原 chrome 常量保留（统一 chrome 时再改）

### 7.2 create 内部流程
1. 构造 `Primitive.Props`（从 `this.Props` 去掉 chrome 字段）
2. 调 `Primitive.create(rt, primitiveProps)` 拿 `Result`
3. 在 `Result` 节点上挂 PAINT 颜色绑定 + 设静态 chrome 属性（padding/borderWidth/cornerRadius/cursor）
4. 返回 `() -> Result.root()`（或 `() -> Result.trigger()` 等）

### 7.3 子节点顺序
- wrapper 不得改变 primitive 建立的子节点顺序（旧测试可能靠 `__getChildren().get(n)` 取）

---

## 8. 各控件 primitive 化批次

| 批次 | 控件 | primitive 名 | 模式 | 状态 |
|---|---|---|---|---|
| 已完成 | TextInput | SceneTextInputPrimitive | 模式 A | ✅ |
| 已完成 | Select | SceneSelectPrimitive | 模式 B（ListboxChrome 回调） | ✅ |
| 批 A | Checkbox/Toggle | SceneToggleablePrimitive | 模式 A | 待做 |
| 批 B | Button | SceneButtonPrimitive | 模式 A | 待做 |
| 批 C | RadioGroup/Segmented | SceneSingleSelectPrimitive | 模式 A（Result 暴露 `List<ItemHandle>`） | 待做 |
| 批 D | Slider | SceneSliderPrimitive | 模式 A | ✅ |
| 批 E | Tab | 复用 SceneSingleSelectPrimitive | 消费者 | 待做（依赖批 C） |

---

## 9. 硬约束清单（传给 fixer）

1. primitive Props 无 chrome 字段（颜色/padding/border/radius/cursor/flat）
2. primitive create 返回 Result，不返回 Supplier
3. primitive 不挂 PAINT 颜色绑定
4. wrapper 不新增节点层（复用 primitive 返回的 root）
5. wrapper 保持原 Props 兼容 + create 签名不变
6. handler 只写 signal + 调 onChange/onSelect + stopPropagation
7. 文本走 LAYOUT，颜色走 PAINT
8. 每个 primitive 单独 commit、单独 test、单独 review
