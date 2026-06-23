# lwjgl3ify 解耦质量审查

## 文档元信息

- **审查日期**：2026-06-13
- **关联分支**：`refactor/decouple-lwjgl3ify`
- **执行计划**：`REVIEW-20260613-lwjgl3ify-decouple-plan.md`
- **关联决策**：`docs/记忆/决策/DECISION-20260612-lwjgl3ify-input-backend.md`

## 审查范围

- 输入后端从 `lwjgl3ify` / `lwjglx` 直接依赖中解耦的实现质量
- `LwjglInputRuntime` 对 `org.lwjglx` 与 legacy `org.lwjgl` 的反射兜底边界
- `LwjglxPollingInputBackend` fallback 语义与键盘、鼠标、滚轮事件能力
- 系统光标、时间戳、键码常量与文档边界的一致性
- 测试编译基础设施是否能支撑本轮解耦验证

## 主要结论

本轮审查确认输入后端解耦方向成立：`UiInputService` 继续作为 facade，增强路径通过 `Lwjgl3ifyInputBackend` 反射订阅 `InputEvents`，缺失或注册失败时降级到 `LwjglxPollingInputBackend`。后续修复重点集中在反射失败日志粒度、光标运行时异常处理、fallback 文档边界、事件时间戳读取、键码常量覆盖和运行时状态诊断
API。

执行计划曾提示测试编译基础设施可能存在独立问题；本轮收尾复核时 `./gradlew.bat --no-configuration-cache compileTestJava` 已通过，因此当前未创建错误立项文档。若后续再次复现，再按错误记录规范单独沉淀。

## 审查发现

### Medium：反射失败日志粒度过粗

`LwjglInputRuntime` 原先以全局状态控制反射方法/字段失败日志，首个失败可能导致后续不同方法或字段失败被静默，影响运行时诊断。

### Medium：系统光标运行时异常处理过重

系统光标操作如果因运行时状态偶发失败，可能被当成不可恢复能力缺失处理。需要区分初始化解析失败与运行时调用失败，避免临时失败永久禁用光标。

### Medium：fallback 键盘语义未充分说明

轮询 fallback 只能检测按键状态变化，无法提供操作系统级按键重复事件。该能力边界需要写入决策文档，避免控件层误以为 fallback 支持 `REPEATED`。

### Medium：输入事件时间戳只使用本地读取时间

增强输入后端应优先尝试读取事件对象携带的原生时间戳，缺失时再降级到 JDK 单调纳秒时钟。

### Low：键码常量覆盖不足

`UiKeyCodes` 原先只覆盖常用键码，仍可能诱导业务代码重新引入底层 `Keyboard` 常量。需要补齐 LWJGL2 键码表覆盖范围，保持业务层不直接依赖输入运行时类。

### Low：缺少运行时可用性检查 API

`LwjglInputRuntime` 缺少不触发原生调用的可用性检查方法，不利于诊断或包内条件逻辑判断。

### Independent：测试编译基础设施复核

执行计划中提到 `compileTestJava` / `test` 可能出现大量核心类型找不到的编译错误。当前分支收尾复核未复现：`./gradlew.bat --no-configuration-cache compileTestJava` 已通过，因此该项暂不作为已确认错误立项。

## 修复跟踪

### 批次 1：核心修复

- [x] M1：日志噪音控制（提交：`cc19738d`）
- [x] M2：光标异常处理（提交：`cc19738d`）
- [x] M3：REPEATED 文档（提交：`cc19738d`）
- [x] M4：时间戳优化（提交：`cc19738d`）

### 批次 2：完善补充

- [x] L1：键码常量补全（提交：`9499c627`）
- [x] L2：运行时检查 API（提交：`88e2157a`）
- [x] 测试基础设施复核（`compileTestJava` 当前通过，未创建错误立项）

### 最终验收

- [ ] 所有修复已合并到主分支
- [x] `compileTestJava` 当前验证通过
- [x] 文档更新完成

## 后续建议

- 如后续再次复现测试编译失败，应按 `docs/开发者文档/errors/README.md` 规范单独立项
- 合并前继续保持 `git diff --check` 与 `./gradlew.bat --no-configuration-cache compileJava` 作为最低验证门槛
- 后续输入运行时能力扩展应继续通过 `LwjglInputRuntime` 或后端抽象接入，不在业务层重新引入 `org.lwjglx.input.*`
