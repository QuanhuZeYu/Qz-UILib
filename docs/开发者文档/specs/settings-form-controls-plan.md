# 设置页核心控件实现计划

本规格文档记录将要新增的四个表单控件（Checkbox、Radio、Slider、Tab）的实现计划与 API 形状，作为后续分步实现的依据。

## 背景

外部开发者审查发现，现有控件（按钮、文本输入、文本域、下拉、开关、分段选择、表格、物品槽位）已能覆盖列表展示与基础表单场景，但"设置页面"这类高频场景缺少 Checkbox、Radio、Slider、Tab 四个常见控件。本计划补齐这四项。

## 设计原则

- 严格遵循现有控件实现模式：`final class` + 内部 DOM + `getElement()` + 事件三件套（Event/Handler/setHandler）+ 链式 setter + `updateVisualState()` 集中刷新。
- 所有控件构造方式与现有控件一致：必传 `UiDocument`，选项类额外接收选项列表。
- 程序化修改默认不触发 change 事件，用户交互才触发。
- 所有 setter 返回 `this` 支持链式调用。
- 设置 ARIA 属性，支持键盘操作。

## 控件 1：DocumentCheckboxControl

### 定位
方框勾选 + 文字标签，适合表单字段、多选项场景。与 ToggleSwitch 并存，外观和语义有别：
- ToggleSwitch：胶囊滑块，立即生效语义（开关）
- Checkbox：方框勾选，表单提交语义（选中/未选中）

### 元素结构
```
element = document.div() role="checkbox"
├── boxElement = document.div()        // 方框
│   └── checkmarkElement = document.span() // 对勾，已选中时显示
└── labelElement = document.span()     // 标签文本（可选）
```

### 状态字段
- `boolean checked`
- `boolean indeterminate`（半选状态，用于树形勾选场景）
- `boolean enabled`
- `boolean focusVisible`
- `boolean spacePressed`

### 公开 API
```java
new DocumentCheckboxControl(UiDocument document)
new DocumentCheckboxControl(UiDocument document, String label)

setLabel(String) / getLabel()
setChecked(boolean) / setChecked(boolean, boolean notify) / isChecked()
setIndeterminate(boolean) / isIndeterminate()
setEnabled(boolean) / isEnabled()
setChangeHandler(DocumentCheckboxChangeHandler)
setBoxColors(normal, hover, checked, disabled)
setCheckmarkColor(int)
setLabelColors(normal, disabled)
setFocusBorderColor(int)
getElement() : ElementNode
```

### 事件
- `DocumentCheckboxChangeEvent`：source / element / checked / indeterminate
- 触发条件：用户点击或空格键

### 键盘交互
- Space：切换 checked
- Enter（可选）：同 Space

---

## 控件 2：DocumentRadioGroupControl

### 定位
分散式单选组（圆点 + 标签，每项可独立排列），适合选项较多、需要标签描述的场景。与 SegmentedSelector 并存，定位有别：
- SegmentedSelector：水平紧凑分段条，类似 iOS 分段控件
- RadioGroup：圆点 + 标签的垂直/水平列表，类似传统表单

### 元素结构
```
element = document.div() role="radiogroup"
└── 每个选项: optionElement = document.div() role="radio"
    ├── circleElement = document.div()      // 圆点外圈
    │   └── dotElement = document.div()     // 内点（已选中时显示）
    └── labelElement = document.span()      // 标签
```

### 状态字段
- `int selectedIndex`
- `boolean enabled`
- `int focusedIndex`（键盘焦点位置）
- `String[] options`（构造时传入并不可变）

### 公开 API
```java
new DocumentRadioGroupControl(UiDocument document, String... options)

setSelectedIndex(int) / setSelectedIndex(int, boolean notify) / getSelectedIndex()
getSelectedOption() : String
setEnabled(boolean) / isEnabled()
setOrientation(UiRadioOrientation) // VERTICAL / HORIZONTAL
setItemSpacing(UiStyleLength)
setChangeHandler(DocumentRadioChangeHandler)
setCircleColors(normal, hover, selected, disabled)
setDotColor(int)
setLabelColors(normal, disabled)
setFocusBorderColor(int)
getElement() : ElementNode
```

### 事件
- `DocumentRadioChangeEvent`：source / element / selectedIndex / selectedOption / keyboardTriggered
- 触发条件：用户点击或方向键

### 键盘交互
- ArrowUp/ArrowDown（垂直）或 ArrowLeft/ArrowRight（水平）：切换选项
- Space/Enter：确认选中（已聚焦项）

---

## 控件 3：DocumentSliderControl

### 定位
数值范围拖动控件，适合音量、亮度、阈值调节等连续/离散数值场景。

### 元素结构
```
element = document.div() role="slider"
├── trackElement = document.div()         // 轨道
│   └── fillElement = document.div()      // 已填充部分
└── thumbElement = document.div()         // 滑块
```

### 状态字段
- `double value`
- `double min` / `double max`
- `double step`（0 = 连续，>0 = 离散）
- `boolean enabled`
- `boolean dragging`
- `boolean focusVisible`
- `double dragStartValue`（拖动开始时的值，用于撤销支持）

### 公开 API
```java
new DocumentSliderControl(UiDocument document)

setRange(double min, double max) / getMin() / getMax()
setStep(double) / getStep()
setValue(double) / setValue(double, boolean notify) / getValue()
setEnabled(boolean) / isEnabled()
setOrientation(UiSliderOrientation) // HORIZONTAL（首版仅支持）
setChangeHandler(DocumentSliderChangeHandler)
setTrackColors(normal, fill, disabled)
setThumbColors(normal, hover, dragging, disabled)
setFocusBorderColor(int)
getElement() : ElementNode
```

### 事件
- `DocumentSliderChangeEvent`：source / element / value / isCommitting / userTriggered
- 派发时机：
  - 拖动中：每帧派发，`isCommitting=false`
  - 释放鼠标：派发一次 `isCommitting=true`
  - 键盘修改：派发一次 `isCommitting=true`
  - 程序化 setValue（不带 notify=false）：派发一次 `isCommitting=true`

### 键盘交互
- ArrowLeft/ArrowDown：减少 step（默认 step 为 (max-min)/100）
- ArrowRight/ArrowUp：增加 step
- Home：跳到 min
- End：跳到 max
- PageUp/PageDown：跳 10 step

### 鼠标交互
- 点击轨道：跳到该位置
- 拖动滑块：连续更新值
- 拖动时光标设为 `grabbing`

---

## 控件 4：DocumentTabControl

### 定位
标签页切换控件，适合设置页面分类、属性面板分组。

### 元素结构
```
element = document.div() role="tablist-container"
├── tabBarElement = document.div() role="tablist"
│   └── 每个标签: tabElement = document.div() role="tab"
│       └── labelElement = document.span()
└── panelElement = document.div() role="tabpanel"
    └── （动态填充：当前活动 tab 的内容）
```

### 状态字段
- `int activeIndex`
- `boolean enabled`
- `int focusedIndex`
- `List<TabEntry> tabs`：每项含 `String label` / `DocumentTabContentBuilder builder` / `ElementNode cachedContent`（懒加载缓存）

### 内容构建协议
```java
@FunctionalInterface
public interface DocumentTabContentBuilder {
    void build(ElementNode panel, UiDocument document);
}
```

### 公开 API
```java
new DocumentTabControl(UiDocument document)

addTab(String label, DocumentTabContentBuilder builder)
removeTab(int index)
clearTabs()
setActiveIndex(int) / setActiveIndex(int, boolean notify) / getActiveIndex()
getTabCount()
setEnabled(boolean) / isEnabled()
setChangeHandler(DocumentTabChangeHandler)
setTabBarColors(background, active, inactive, hover, disabled)
setTabTextColors(normal, active, disabled)
setActiveIndicatorColor(int)
setFocusBorderColor(int)
rebuildTab(int index) // 强制重新构建指定 tab 的内容（如内容数据变化时）
getElement() : ElementNode
```

### 事件
- `DocumentTabChangeEvent`：source / element / activeIndex / activeLabel / keyboardTriggered

### 内容懒加载语义
- `addTab` 时只保存 builder，不构造 DOM。
- 切到某 tab 时：若 `cachedContent` 为 null，调用 builder 构建并缓存；否则直接挂载缓存。
- 切走时：保留缓存 DOM，仅从 panelElement 卸载。
- `rebuildTab(index)`：清除缓存并立即重建（如果是当前活动 tab 则立即生效）。
- `removeTab`：销毁该 tab 的缓存 DOM。

### 键盘交互
- ArrowLeft/ArrowRight：切换 tab
- Home：跳到第一个
- End：跳到最后一个
- 切换后自动激活对应 panel

---

## 实施顺序建议

按风险递增、复用度递减排序：

1. **DocumentCheckboxControl**（最简单，参照 ToggleSwitch）
2. **DocumentRadioGroupControl**（中等，参照 SegmentedSelector）
3. **DocumentSliderControl**（中等偏复杂，新的拖动模型）
4. **DocumentTabControl**（最复杂，涉及内容懒加载与 panel 切换）

每个控件实现包含：
- 控件类本身
- Event 类
- Handler 接口
- 单元测试（构造、状态变更、事件触发）
- 在 `docs/使用文档/02-控件/` 添加使用说明

## 验收标准

每个控件完成后必须满足：

- 编译通过：`compileJava`
- 测试通过：`test`
- 与现有控件 API 风格一致（链式 setter、Event 三件套、`getElement()`）
- 暴露足够的颜色/尺寸定制点
- 设置 ARIA 角色，支持键盘操作
- `docs/使用文档/02-控件/基础控件.md` 同步说明
- 在示例页或诊断页中有最小验证

## 后续可能的扩展

本计划聚焦设置页核心控件。完成后可继续评估的方向：

- ProgressBar / Spinner（反馈类）
- Dialog / Modal（弹层类）
- Toast / Notification（瞬时反馈类）
- 虚拟滚动列表（大数据展示类）
