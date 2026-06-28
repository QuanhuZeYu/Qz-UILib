# REVIEW-20260618 — Scene 输入层点击聚焦 + emoji/codepoint 文本输入修复

## 背景

Scene 输入层 I4 真机验收暴露两个问题：
1. 输入框无法点击聚焦输入文本
2. 文本输入未对接 lwjglx(lwjgl3ify) 的 codepoint 输入，无法输出 emoji 字符

经 explorer 侦察 + librarian 调研 + oracle 接口契约级设计，分两条独立 lane（核心包 / 适配层，写盘不重叠）修复。本文档归档 oracle 审查结论与遗留观察点。

## 根因

### Bug1 — 点击无法聚焦
- `SceneInputRouter.route()` 的 CLICK 合成只 `dispatchTargetAndBubble`，核心层不做任何隐式聚焦（route 全文无 requestFocus 调用）。
- demo 的 textInput 只注册 TEXT_INPUT / KEY_DOWN handler，没有任何 POINTER_DOWN/CLICK handler 调 `ctx.requestFocus()`。
- 两头都没接 → 点击文本框永不成为 focusedNode → TEXT_INPUT 因无焦点目标被丢弃。焦点此前只能靠 Tab 进入。

### Bug2 — 无法输出 emoji/codepoint
- 整条链路从 MC `keyTyped(char, int)` 起以 16 位 char 为单位，`LwjglInputSource.pushKeyTyped(char,...)` 延续瓶颈。
- lwjgl3ify 的 `MixinGuiScreenKeyTypeInput` 已用 `Character.toChars` 把 codepoint 拆成 surrogate pair 两次回调 `keyTyped`，新层把每个 surrogate 各自 `String.valueOf(char)` 包成独立 TEXT 事件 → emoji 碎成两个坏字符。
- 附带：demo BACKSPACE 用 `substring(len-1)` 是 char 级删除，会切碎 surrogate pair。

## 修复方案（用户拍板）

- **Bug1**：Router 核心层做隐式聚焦。POINTER_DOWN 时沿命中链最深向 root 找首个 focusable 并 requestFocus；点击树内非 focusable 区域保持当前焦点不变（不 blur）；点击树外焦点不变。**（注：失焦语义在第二轮已反转为「点任何非 focusable 处都失焦」，见文末「第二轮修复」。）**
- **Bug2**：对接 lwjgl3ify `InputEvents` 的 `KeyboardListener#onTextEvent`（完整 String，含 IME/补充平面），同时做降级实现：char 路径 surrogate-aware 累积组合完整 codepoint。双环境互斥靠 `externalTextMode` 开关。

## 改动范围

- **核心包 `ui.scene.input`（Bug1，守 I10）**：`FocusManager.findDeepestFocusable` + `SceneInputRouter` POINTER_DOWN 隐式聚焦块。
- **适配层 `internal/devtools/pages`（Bug2）**：`LwjglInputSource`（pushText/setExternalTextMode/surrogate 累积）、新建 `SceneLwjgl3ifyTextBridge`（反射对接 onTextEvent）、`SceneHostWidget`（透传 + BACKSPACE 改
  codepoint-aware）、`SceneDemoScreen`（bridge 生命周期）。

## oracle 审查结论：有条件 PASS（0 阻断项）

- **I7 零标脏守住**：隐式聚焦唯一写操作是 `requestFocus → writeFocused → queueWrite`，不触 node setter/markXxx/树结构；测试断言 route 后 flush 前 7 脏探针全等。
- **I10 守住**：核心包零平台 import，守护正则 4 pattern 零命中；lwjgl3ify 全走字符串反射加载。
- **I11 守住**：隐式聚焦由 Router 核心层（焦点真值机构）主动调用，比 handler 白名单更天然合规。
- **双环境互斥正确**：external 模式 pushKeyTyped 对字符键既不产 KEY 也不产 TEXT，杜绝与 onTextEvent 双份。
- **测试**：新增 `SceneRouterImplicitFocusTest`（6）+ `LwjglInputSourceTextInputTest`（10）全绿；全量 1710 测试仅 9 失败=历史预存集（字体后端/LWJGL native 缺失），零回归。

## 遗留观察点（非阻断）

1. **Bridge remove 可能为 null → 监听器泄漏隐患**：`register()` 若走非 weak `addKeyboardListener` 且解析不到 remove 方法，`unregister` 无法移除监听器，proxy 强引用链泄漏整棵 scene 树。缓解：weak 优先 + remove 优先解析；demo 单例长生命周期影响有限。**真机需确认
   lwjgl3ify 实际暴露的 add/remove API**；若只有非 weak add 且无 remove，登记为已知泄漏边界。
2. **守护正则误伤 Javadoc**：`Lwjgl3ifyInputBackendTest` 的 `\bInputEvents\s*\.` 会匹配 Javadoc 注释里的 `InputEvents.xxx`。本次把注释改成 `InputEvents#xxx` 规避。建议后续让守护正则排除注释/Javadoc 行，避免后人被迫改文档。
3. **`isCharKey` 中 `Character.isSurrogate` 是死分支**：surrogate 码位恒满足 `isPrintable`，无功能影响，保留有弱自文档价值。
4. **external 模式 + 指针捕获期间 POINTER_DOWN 仍触发隐式聚焦**：罕见、不破不变量，登记备查。

## 真机验收项（放行前提，沙箱无法验）

- lwjgl3ify 环境输入 😀 → input 显示完整 emoji，探针为单条完整串
- 按一次 BACKSPACE 整个 emoji 消失（验 `offsetByCodePoints`，非半个坏字符）
- external 模式下文本无双份（onTextEvent 与 keyTyped 不重复产 TEXT）
- 关闭 demo 后 unregister 生效：无残留监听器、SDL endTextInput 已调用
- 点击文本框即聚焦（Bug1 真机闭环，对应原始验收失败现象）

---

## 第二轮修复（首轮真机复验暴露）

首轮 emoji 真机通过，但暴露两个新问题，第二轮修复。

### 问题1 — 中文 IME 连打多字残缺（"好好好"只进一个）

- **现象**：external 模式（lwjgl3ify onTextEvent 注册成功）下连打"好好好"只进一个、"什么问题"残缺。真机日志确认每个字各到达一次（非上游丢字、非重复触发）。
- **根因（reactive 核心 latent bug）**：demo handler 用 `inputTextSignal.set(inputTextSignal.get() + text)` 累积。同帧多个 TEXT 事件在 flush 前连续调用 handler，但更深一层根因是 **`Signal.set` 的去重时机错误**——去重拿「已 flush 的旧值」比较，导致「同帧 set
  到中间值再 set 回帧初值」的第二次 set 被误判无变化丢弃。影响所有 toggle 抖动 / 计数器 +1-1 / 拖拽回弹等「终值==帧初值但中途经过别值」场景。详见 `docs/开发者文档/errors/ERROR-20260618-signal-set-dedup-stale-value.md`。
- **修复（两层，用户批准动 reactive 地基）**：
  1. **reactive 核心**：去重从 `Signal.set` 移到 `ReactiveScheduler.flush` 阶段1（`pendingWrites` 用 LinkedHashMap 按 signal 合并末值 + flush 时对比 `peek()` 帧初值 vs 合并终值，仅净变化才 apply/markDirty/记日志）。是 I9「帧末批处理合并写入」的字面正确实现，
     不改宪章条文、不引入新 API。
  2. **demo 适配层（文本模型范式）**：SceneHostWidget 引入私有 String 字段 `inputModel1/2` 作即时权威文本模型，handler 操作字段而非读 signal，signal 只作「模型→渲染」单向派生。范式：**文本模型即时可变、handler 操作模型不读 signal、signal 仅作模型→渲染单向派生**——后续所有可编辑控件的统一基线。

### 问题2 — 失焦语义反转

- 用户改变首轮决定：从「点树内非 focusable 处保持焦点不变」反转为「**点任何非 focusable 处（含树外空白与树内非 focusable 节点）都失焦**」。
- **修复**：`SceneInputRouter` 隐式聚焦块去掉 `hitTarget != null` 守卫，POINTER_DOWN 无条件进入按命中分流——命中 focusable（含祖先链）则 requestFocus，否则（命中非 focusable 或树外 null）clearFocus。去掉守卫同时修复了「树外点击 clearFocus 被守卫+continue
  跳过」的时序陷阱。clearFocus 走 writeFocused→queueWrite 零标脏，handler 内 ctx.requestFocus 仍可覆盖。

### 第二轮改动范围

- **reactive 核心 `ui.reactive`（用户批准动地基）**：`Signal.set`（去 set 层去重）+ `ReactiveScheduler`（writeQueue→pendingWrites LinkedHashMap + flush 阶段1 帧初值对比去重 + commitTransaction 简化）。
- **核心包 `ui.scene.input`**：`SceneInputRouter` 失焦反转。
- **适配层 `internal/devtools/pages`**：`SceneHostWidget` 文本模型字段范式。

### 第二轮验证

- JetBrains MCP 编译通过；1728 测试仅 9 失败=历史预存集，零回归。
- 新增测试：`ReactiveSchedulerMergeWriteTest`（7，核心合并/去重回归）+ `SceneHostWidgetTextModelTest`（7，同帧多字累积）+ `SceneRouterImplicitFocusTest`（反转至 9）+ `TransactionLogTest` 改写 2（旧测试锁定 bug 行为，按正确语义改写）。
- `SceneRouterInteractionTest.shouldMergeMultipleMovesInSameFrame` 旧断言锁定的「同帧 hover A→B→A 中间节点残留 true」瑕疵被核心修复根治，已更新断言为正确语义（B 终值 false）。
- 主 Agent 亲自实读 ReactiveScheduler + Signal 全文核验逻辑正确性（去重时机、事务日志简化、undo/redo 不受影响、阶段2 不动点保留）。

### 第二轮真机验收项（放行前提）

- external 模式连打中文"好好好" → 完整显示三个字（核心去重修复闭环）
- "什么问题"等多字词完整显示
- 点输入框外任意非 focusable 处 → 输入框失焦（边框恢复未聚焦色）
- emoji 输入 + BACKSPACE 删整字仍正常（不回归首轮）
