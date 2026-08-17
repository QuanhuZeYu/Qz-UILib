# ERROR-20260817-textinput-five-node-whitebox

## 现象

SceneTextInputPrimitive 三节点结构（prefix/caret/suffix）升级为五节点
（prefix/caret/highlight/caretAfter/suffix）后，全量 build 一次性失败 27 个用例：

1. 白盒结构断言破裂：多个测试 helper 按 `children[2] == suffix` 读取输入框显示文本
   （拼接 `get(0)+get(2)` 或单点 `get(2)`），五节点后 `children[2]` 变为 highlightText，
   无选区时其文本恒空 → 列表行文本全部读成空串。
2. 结构特征查找破裂：`findTextInputRoot` 按「子节点数 == 3」定位 TextInput 根，改为 5 才命中。
3. 顺序敏感偶发：`shiftHomeExtendsFromCaretToStart`（clickLocalX 注入 DOWN 后不补 UP，
   紧接着独立 builder 注入 KEY）单测与类级均绿，全量跑偶发失败（highlight 恒空，疑似
   click 定位回退 pos=0 的跨类时序敏感）。改用例为 END+Shift+HOME 同构路径后消失。

## 根因

- 白盒测试把控件内部节点布局当契约：拼接 helper 假设「第 3 个子节点是 suffix」，
  生产代码从不这样读（无主代码白盒读取点），纯测试侧假设。
- 节点顺序本身是刻意的兼容设计（prefix/caret 保持 0/1 位），但 highlight 必须插入
  suffix 之前（文本流顺序），suffix 必然后移两位，`children[2]` 语义不可保持。

## 修复

- 白盒拼接 helper 统一改为 `get(0)+get(2)+get(4)`（prefix+highlight+suffix）；
  单点 index 读取改 `get(4)`；节点数断言 3→5；`findTextInputRoot` 特征 3→5。
- shiftHome 用例改用 END+Shift+HOME（等价语义路径），点击+Shift 交互已由
  shiftClickKeepsAnchorAndExtends 覆盖。

## 预防

- 测试断言 TextInput 显示文本时避免白盒节点位置，优先用控件语义（受控 value signal）
  或聚合所有文本子节点；必须白盒时集中封装 helper 并注释结构契约版本。
- 「DOWN 后不 UP + 后续 KEY」的多 builder 组合在跨类全量跑下不可靠，测试内指针序列
  应配对 DOWN/UP，或用同 builder 封帧。

## 关联

- 提交 28ab8b03（B2 TextInput 选区）
- SceneTextAreaPrimitive 后续同样升级行结构时，SceneTextAreaTest 的
  `rowNode(...).getChildren().get(2)` 白盒读取需同步处理。
