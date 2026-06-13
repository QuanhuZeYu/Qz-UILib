# lwjgl3ify 解耦优化修复计划

## 文档元信息

- **创建时间**：2026-06-13
- **关联分支**：`refactor/decouple-lwjgl3ify`
- **关联审查**：REVIEW-20260613-lwjgl3ify-decouple（审查报告）
- **执行策略**：渐进式修复（分两批次）
- **总预估时间**：批次 1 约 2.5 小时，批次 2 约 1.5 小时

## 决策确认

基于代码审查报告，用户已确认以下决策：

- **Q1（测试编译问题）**：C - 测试基础设施问题单独立项，不阻塞本次解耦合并
- **Q2（Medium 问题）**：需要处理 M1-M4
- **Q3（键码补全范围）**：C - 补充完整 LWJGL2 键码表（256 个常量）
- **执行策略**：接受渐进式修复建议

## 第一批次：核心修复（优先完成）

### 任务 1.1：测试基础设施确认（15 分钟）

**目标**：确认测试编译问题为既有问题，不是本轮引入

**执行步骤**：
1. 运行 `git stash` 暂存当前未提交修改
2. 运行 `./gradlew clean compileTestJava` 验证问题持续性
3. 切换到主分支 `git checkout 4.0` 运行相同命令对比
4. 恢复分支 `git checkout refactor/decouple-lwjgl3ify; git stash pop`
5. 如果确认是既有问题，创建立项文档 `docs/开发者文档/errors/ERROR-20260613-test-compile-infrastructure.md`

**验收标准**：
- [ ] 确认测试问题不是本轮引入
- [ ] 如果是既有问题，已创建立项文档

---

### 任务 1.2：M1 修复 - 日志噪音控制（30 分钟）

**目标**：每个反射方法/字段失败独立记录一次日志

**文件**：`src/main/java/club/heiqi/uilib/ui/input/LwjglInputRuntime.java`

**修改内容**：

**替换代码块 1（第 23-24 行）**：
```java
// 删除
private static final AtomicBoolean METHOD_INVOCATION_LOGGED = new AtomicBoolean(false);
private static final AtomicBoolean FIELD_INVOCATION_LOGGED = new AtomicBoolean(false);

// 替换为
private static final ConcurrentHashMap<String, Boolean> METHOD_LOG_REGISTRY = new ConcurrentHashMap<String, Boolean>();
private static final ConcurrentHashMap<String, Boolean> FIELD_LOG_REGISTRY = new ConcurrentHashMap<String, Boolean>();
```

**修改方法 1（约 149 行）**：
```java
private static void logMethodInvocationFailureOnce(String methodName, Throwable throwable) {
    if (METHOD_LOG_REGISTRY.putIfAbsent(methodName, Boolean.TRUE) == null) {
        LOG.debug("UILib 原生输入方法反射调用失败，已按无事件处理：methodName={}", methodName, throwable);
    }
}
```

**修改方法 2（约 155 行）**：
```java
private static void logFieldInvocationFailureOnce(String fieldName, Throwable throwable) {
    if (FIELD_LOG_REGISTRY.putIfAbsent(fieldName, Boolean.TRUE) == null) {
        LOG.debug("UILib 原生输入字段反射读取失败，已按无扩展字段处理：fieldName={}", fieldName, throwable);
    }
}
```

**验收标准**：
- [ ] `./gradlew compileJava` 通过
- [ ] 代码审查：确认所有调用点传递正确的方法/字段名
- [ ] 导入语句包含 `java.util.concurrent.ConcurrentHashMap`

---

### 任务 1.3：M2 修复 - 光标异常处理优化（45 分钟）

**目标**：区分初始化失败与运行时失败，避免偶发错误永久禁用系统光标

**文件**：`src/main/java/club/heiqi/uilib/ui/host/SystemDocumentCursorHost.java`

**修改策略**：保守方案 - 只在 `applyResolvedCursor()` 中区分异常类型

**修改方法（约 47-54 行）**：
```java
private void applyResolvedCursor(ResolvedCursorKind resolvedCursor) {
    appliedCursor = resolvedCursor;
    if (resolvedCursor == ResolvedCursorKind.HIDDEN) {
        try {
            backend.hideCursor();
            runtimeCursorSynchronized = true;
        } catch (IllegalStateException exception) {
            // 运行时调用失败（如 Display 未创建），记录日志但不永久禁用
            MyMod.LOG.debug("UILib 系统光标隐藏失败，本次操作跳过。", exception);
            runtimeCursorSynchronized = false;
        }
        return;
    }
    try {
        backend.showCursor();
        if (resolvedCursor == ResolvedCursorKind.DEFAULT) {
            backend.applyDefaultCursor();
            runtimeCursorSynchronized = true;
            return;
        }
        backend.applySystemCursor(resolvedCursor);
        runtimeCursorSynchronized = true;
    } catch (IllegalStateException exception) {
        // 运行时调用失败，记录日志但不永久禁用
        MyMod.LOG.debug("UILib 系统光标应用失败，本次操作跳过：cursor={}", resolvedCursor, exception);
        runtimeCursorSynchronized = false;
    }
}
```

**关键变更说明**：
- 移除外层 `catch (RuntimeException)` / `catch (LinkageError)` 对 `applyResolvedCursor()` 的包裹
- 只在内部捕获 `IllegalStateException`（SdlReflectionBridge.invoke() 抛出的运行时错误）
- 保留 `isRuntimeAvailable()` 的异常捕获，用于探测阶段

**验收标准**：
- [ ] `./gradlew compileJava` 通过
- [ ] 逻辑推演：Display 未创建时光标操作失败不会永久禁用
- [ ] 逻辑推演：反射解析失败（构造函数阶段）仍会永久禁用

---

### 任务 1.4：M3 文档补充 - REPEATED 语义说明（10 分钟）

**目标**：明确记录 fallback 模式功能边界

**文件**：`docs/记忆/决策/DECISION-20260612-lwjgl3ify-input-backend.md`

**修改位置**：第 37 行之后插入

**新增内容**：
```markdown
- `LwjglxPollingInputBackend` fallback 只承诺基础按键、鼠标与滚轮；复杂文本输入和 IME 明确降级。`InputEvents` 注册失败时键盘可兜底到轮询，文本事件仍不会由轮询后端合成。
- **键盘事件语义降级**：fallback 模式不支持 `UiKeyEvent.Action.REPEATED`；`LwjglxPollingInputBackend` 只能检测按键状态变化（`PRESSED` / `RELEASED`），无法识别操作系统级别的按键重复事件。需要长按重复输入的控件（如文本框光标移动、数值调节）应在应用层自行实现定时器逻辑，或明确依赖 `InputEvents` 可用环境。
- `SystemDocumentCursorHost` 移除 `Display` 静态 import；SDL 系统光标仍依赖 lwjgl3ify / LWJGLX 光标桥，缺失时降级为 no-op。
```

**验收标准**：
- [ ] 文件可正常保存
- [ ] 新增内容符合 Markdown 格式

---

### 任务 1.5：M4 优化 - 时间戳读取优化（20 分钟）

**目标**：尝试从 InputEvents 读取原生时间戳

**文件**：`src/main/java/club/heiqi/uilib/ui/input/Lwjgl3ifyInputBackend.java`

**修改方法 1（约 172 行）**：
```java
private static void handleKeyEvent(UiInputService inputService, Object event) {
    long timeNanos = readEventTimestampNanos(event);
    handleKeyEvent(inputService, event, timeNanos);
}
```

**新增方法（插入约 174 行后）**：
```java
private static long readEventTimestampNanos(Object event) {
    // 尝试读取 InputEvents 提供的时间戳字段（如果存在）
    Long timestamp = readLongField(event, "timestampNanos");
    if (timestamp != null) {
        return timestamp.longValue();
    }
    // Fallback 到单调时钟
    return LwjglInputRuntime.getNanoTime();
}

private static Long readLongField(Object instance, String fieldName) {
    Field field = findField(instance, fieldName);
    if (field == null) {
        return null;
    }
    try {
        return Long.valueOf(field.getLong(instance));
    } catch (IllegalAccessException exception) {
        logInputFieldReflectionFailureOnce(fieldName, exception);
        return null;
    } catch (IllegalArgumentException exception) {
        logInputFieldReflectionFailureOnce(fieldName, exception);
        return null;
    }
}
```

**验收标准**：
- [ ] `./gradlew compileJava` 通过
- [ ] 如果 InputEvents 无 `timestampNanos` 字段，自动 fallback

---

### 任务 1.6：提交与验证（20 分钟）

**提交信息**：
```
[Refactor]: 优化 lwjglx 解耦质量

## 修改内容

- **M1 修复**：LwjglInputRuntime 日志噪音控制
  - 改用 ConcurrentHashMap 独立记录每个反射方法/字段的日志状态
  - 避免首次失败后静默所有后续失败

- **M2 修复**：SystemDocumentCursorHost 异常处理优化
  - 区分初始化失败（永久降级）与运行时失败（临时跳过）
  - 避免偶发的 Display 未创建导致光标永久禁用

- **M3 补充**：决策文档明确 fallback 模式功能边界
  - 记录 LwjglxPollingInputBackend 不支持 REPEATED 键盘事件
  - 明确控件层需自行实现长按重复逻辑

- **M4 优化**：Lwjgl3ifyInputBackend 时间戳读取
  - 优先尝试从 InputEvents 反射读取原生时间戳
  - 失败时 fallback 到 System.nanoTime()

## 验证

- git diff --check 通过
- ./gradlew compileJava 通过
- 代码审查确认符合编码规范

## 关联

- 基于代码审查报告：REVIEW-20260613-lwjgl3ify-decouple
- 执行计划：REVIEW-20260613-lwjgl3ify-decouple-plan.md
```

**验证清单**：
- [ ] `git diff --check` 通过
- [ ] `./gradlew compileJava` 通过
- [ ] 无新增编译警告
- [ ] 代码符合 `AGENTS.md` 编码规范
- [ ] 提交信息符合 Git 规范

**提交后任务**：
- [ ] 更新 `docs/记忆/当前态/交接记录.md` 记录本批次完成
- [ ] 标记批次 1 完成

---

## 第二批次：完善补充（后续会话）

### 任务 2.1：L1 修复 - 补充完整键码常量（60 分钟）

**目标**：补充完整 LWJGL2 键码表（256 个常量）

**文件**：`src/main/java/club/heiqi/uilib/ui/event/UiKeyCodes.java`

**实现要求**：

1. **参考源**：LWJGL2 `org.lwjgl.input.Keyboard` 源码
2. **覆盖范围**：所有 KEY_* 常量（键码 0-255）
3. **代码结构**：按功能区分类，添加注释分隔
4. **分类体系**：
   - 特殊控制键（NONE, ESCAPE）
   - 数字键区 - 主键盘（1-0）
   - 字母键区（A-Z）
   - 功能键区（F1-F15）
   - 修饰键区（SHIFT, CONTROL, ALT, META）
   - 导航键区（UP, DOWN, LEFT, RIGHT, HOME, END, PRIOR, NEXT）
   - 编辑键区（INSERT, DELETE, BACK）
   - 小键盘区（NUMPAD0-9, NUMPADENTER, 运算符）
   - 符号键区（逗号、分号、括号等）
   - 其他常用键（SPACE, TAB, RETURN, PAUSE 等）

**示例结构**：
```java
public final class UiKeyCodes {

    // ===== 特殊控制键 =====
    public static final int KEY_NONE = 0;
    public static final int KEY_ESCAPE = 1;
    
    // ===== 数字键区 (主键盘) =====
    public static final int KEY_1 = 2;
    public static final int KEY_2 = 3;
    // ... 完整补充
    
    // ===== 字母键区 =====
    public static final int KEY_Q = 16;
    // ... 完整 A-Z
    
    // [继续补充所有区域]
    
    private UiKeyCodes() {}
}
```

**验收标准**：
- [ ] 所有 256 个键码常量已定义
- [ ] 对比 LWJGL2 源码确认数值正确
- [ ] 搜索主源码确认无遗漏的 `import org.lwjglx.input.Keyboard`（仅用于键码常量）
- [ ] `./gradlew compileJava` 通过

**提交信息**：
```
[Refactor]: 补充完整 LWJGL2 键码常量表

- 将 UiKeyCodes 从 23 个常量扩展到 256 个完整键码表
- 按功能区分类并添加注释，提高可读性
- 覆盖主键盘、功能键、导航键、小键盘、修饰键等所有区域
- 确保业务代码完全不需要 import org.lwjglx.input.Keyboard

验证：compileJava 通过，数值与 LWJGL2 源码一致
```

---

### 任务 2.2：L2 优化 - 运行时可用性检查 API（20 分钟）

**目标**：提供主动检查 API，避免触发运行时反射调用

**文件**：`src/main/java/club/heiqi/uilib/ui/input/LwjglInputRuntime.java`

**新增方法（约第 55 行后插入）**：
```java
/**
 * 检查键盘运行时是否在初始化时成功解析。
 * 
 * <p>该方法只检查反射解析是否成功，不调用原生方法。可用于诊断或条件逻辑。</p>
 * 
 * @return true 如果至少有一个键盘类（org.lwjglx 或 org.lwjgl）成功加载
 */
static boolean isKeyboardRuntimeAvailable() {
    return KEYBOARD.isCreatedMethod != null;
}

/**
 * 检查鼠标运行时是否在初始化时成功解析。
 * 
 * <p>该方法只检查反射解析是否成功，不调用原生方法。可用于诊断或条件逻辑。</p>
 * 
 * @return true 如果至少有一个鼠标类（org.lwjglx 或 org.lwjgl）成功加载
 */
static boolean isMouseRuntimeAvailable() {
    return MOUSE.isCreatedMethod != null;
}
```

**可选增强（INFO 日志）**：在 `LwjglxPollingInputBackend.initialize()` 中记录运行时状态
```java
@Override
public void initialize() {
    snapshotKeyboardState();
    previousTotalScrollAmount = mouseRuntime.readTotalScrollAmount();
    
    if (collectKeyboardState) {
        LOG.info("UILib LwjglxPollingInputBackend 已启用键盘状态收集，运行时可用性：键盘={}, 鼠标={}",
                LwjglInputRuntime.isKeyboardRuntimeAvailable(),
                LwjglInputRuntime.isMouseRuntimeAvailable());
    }
}
```

**验收标准**：
- [ ] 新增方法符合 Javadoc 注释规范
- [ ] `./gradlew compileJava` 通过
- [ ] 方法可见性为 package-private（`static` 无 `public`）

**提交信息**：
```
[Refactor]: 新增 LwjglInputRuntime 可用性检查 API

- 新增 isKeyboardRuntimeAvailable() / isMouseRuntimeAvailable()
- 允许外部检查反射解析状态，无需触发运行时调用
- 可选：LwjglxPollingInputBackend 初始化时记录运行时状态

验证：compileJava 通过
```

---

### 任务 2.3：测试基础设施修复（独立任务）

**立项文档**：`docs/开发者文档/errors/ERROR-20260613-test-compile-infrastructure.md`

**文档内容**：
```markdown
# 测试编译基础设施问题

## 问题现象

运行 `./gradlew compileTestJava` 或 `test` 时出现 100+ 编译错误，主要是找不到核心类型符号：
- `UiDocument`、`HtmlLikeDocumentWidget`
- `TextMeasureService`、`UiInputFrame`
- `Widget`、`UiRuntimeAdapters`
- `DocumentNode`、`TextNode`、`ElementNode`

## 触发条件

- 任何触发测试编译的 Gradle 任务
- 影响所有测试源码

## 影响范围

- **阻断自动化测试**：无法运行任何单元测试
- **阻断验证覆盖**：解耦质量关键验证点无法执行
- **不阻塞生产代码**：主源码编译正常（`compileJava` 通过）

## 根本原因

**待诊断**。可能原因：
1. Gradle 测试依赖配置问题
2. 增量编译状态损坏
3. 测试源码集路径配置错误
4. IDE 与 Gradle 状态不一致

## 诊断步骤

1. 运行 `./gradlew clean compileTestJava` 排除增量编译影响
2. 检查 `build.gradle` 中测试依赖配置
3. 对比主分支 `4.0` 是否存在同样问题
4. 检查 IDEA 项目结构设置（如果使用 IDEA）

## 修复计划

- **优先级**：High
- **责任人**：待分配
- **预估时间**：1-2 小时（诊断 + 修复）
- **阻塞项**：无（不阻塞 `refactor/decouple-lwjgl3ify` 分支合并）

## 修复策略

1. 先尝试 `./gradlew clean build --refresh-dependencies`
2. 如果无效，逐步排查 Gradle 配置
3. 必要时回滚近期 Gradle 配置变更

## 后续跟踪

- [ ] 问题根因已确认
- [ ] 修复方案已实施
- [ ] 测试编译通过
- [ ] 关键测试验证通过
```

**验收标准**：
- [ ] 立项文档已创建
- [ ] 文档格式符合错误记录规范
- [ ] 已在 `docs/开发者文档/errors/README.md` 中添加索引

---

### 任务 2.4：审查报告归档（10 分钟）

**文件**：`docs/开发者文档/reviews/REVIEW-20260613-lwjgl3ify-decouple.md`

**内容**：复制完整审查报告，添加修复跟踪章节

**新增章节（文档末尾）**：
```markdown
## 修复跟踪

### 批次 1：核心修复
- [x] M1：日志噪音控制（提交：[commit hash]）
- [x] M2：光标异常处理（提交：[commit hash]）
- [x] M3：REPEATED 文档（提交：[commit hash]）
- [x] M4：时间戳优化（提交：[commit hash]）

### 批次 2：完善补充
- [ ] L1：键码常量补全
- [ ] L2：运行时检查 API
- [ ] 测试基础设施修复（独立任务）

### 最终验收
- [ ] 所有修复已合并到主分支
- [ ] 测试验证通过
- [ ] 文档更新完成
```

**验收标准**：
- [ ] 审查报告已归档
- [ ] 已在 `docs/开发者文档/reviews/README.md` 中添加索引

---

## 验收标准总览

### 批次 1 完成标准
- [ ] 任务 1.1-1.6 全部完成
- [ ] 所有修改通过 `git diff --check`
- [ ] `./gradlew compileJava` 通过
- [ ] 代码审查确认符合 `AGENTS.md` 规范
- [ ] M1-M4 修改已提交（单次提交）
- [ ] 交接记录已更新
- [ ] 无新增编译警告

### 批次 2 完成标准
- [ ] 任务 2.1-2.4 全部完成
- [ ] 键码常量表补全（256 个）
- [ ] 运行时检查 API 已实现
- [ ] 测试基础设施立项文档已创建
- [ ] 审查报告已归档

### 最终完成标准
- [ ] 两批次所有任务完成
- [ ] 测试基础设施修复完成（独立任务）
- [ ] 所有相关文档更新
- [ ] 分支已合并到主分支（如果适用）

---

## 执行时间线

```
批次 1（本次会话或下次会话）
├─ 任务 1.1：测试确认         15 分钟
├─ 任务 1.2：M1 日志          30 分钟
├─ 任务 1.3：M2 光标          45 分钟
├─ 任务 1.4：M3 文档          10 分钟
├─ 任务 1.5：M4 时间戳        20 分钟
└─ 任务 1.6：提交验证         20 分钟
   总计：约 2.5 小时

批次 2（后续会话）
├─ 任务 2.1：键码补全         60 分钟
├─ 任务 2.2：运行时 API       20 分钟
├─ 任务 2.3：测试立项         10 分钟
└─ 任务 2.4：报告归档         10 分钟
   总计：约 1.5 小时

独立任务（单独会话）
└─ 测试基础设施修复           1-2 小时
```

---

## 风险提示

1. **M2 光标异常处理**是批次 1 中最复杂的修改，建议仔细测试
2. **测试编译问题**虽不阻塞合并，但会影响长期验证能力
3. **键码常量补全**工作量较大，需耐心核对数值正确性

---

## 参考文档

- 审查报告：`REVIEW-20260613-lwjgl3ify-decouple.md`（待创建）
- 协作规范：`AGENTS.md`
- 决策记录：`docs/记忆/决策/DECISION-20260612-lwjgl3ify-input-backend.md`
- 架构边界：`docs/记忆/长期事实/架构边界.md`
