# Scene Text 同帧多 TEXT 事件未合并导致中文 IME 只保留末字

## 错误现象

在 `SceneControlsDemoScreen` 的 `SceneTextInput` 中，真机输入中文短句（如 `修好了`、`怎么输不进去`）时，最终只进入最后一个字符；输入单个 emoji（如 🙂）正常。

## 触发场景

- lwjgl3ify/SDL 文本桥把一次中文 IME 提交拆成同一帧内多条 TEXT 事件，例如 `修`、`好`、`了`。
- `SceneInputRouter.route()` 会在宿主 `runtime.flush()` 前逐条分发 `frame.getTextEvents()`。
- `SceneTextInput` 是受控组件，TEXT_INPUT handler 读 `props.value().get()` 再上抛 `onChange(next)`。

## 根本原因

`Signal.get()` 在 flush 前只返回帧初旧值，同帧多条 TEXT handler 都基于同一个旧文本计算新值。随后多次 `signal.set(next)` 在中央事务中按同一 signal 合并，最终只保留最后一次写入，所以中文短句只剩末字。emoji 正常是因为它通常作为单条完整 TEXT 事件进入。

## 修复方案

在 `InputFrameBuilder.drainFrame()` 封板时，把同一帧内多条 TEXT 按 push 顺序合并为一条 `SceneTextEvent`，事件时间戳取最后一条 TEXT。这样 router 和控件仍保持 `route -> flush` 的 I9 批处理时序，handler 只收到一次完整文本并只写一次 signal。

## 预防措施

- 不要为此类问题在 `SceneInputRouter` 内逐条 TEXT 后调用 `flush()`；这会破坏 I2/I9 的帧末批处理边界。
- 不要在 `SceneTextInput` 内为平台 IME 拆分行为新增本地 pending value；平台归一应发生在输入封板层。
- 新增输入源或文本桥时必须覆盖“同帧多条 TEXT 合并为完整文本”的回归测试。
