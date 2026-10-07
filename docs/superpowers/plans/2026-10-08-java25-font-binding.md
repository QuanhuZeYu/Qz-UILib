# Java25字体绑定启动修复实施计划

> **面向 agent 执行者：** 使用 executing-plans 按任务实施。

**状态：** 实现仍暂停于用户的架构讨论。最终裁定为目标环境实际可用优先，允许积极复用经确认的 native 后端，不强制 Java 基线；纯 Java 前置不再阻碍本计划。后续修复按当前核心与字体规格评估稳定 JNI 或上游修复路径，GTNH Java25 崩溃及复现证据保留，不把桌面结果外推 Android 支持。

**原目标：** GTNH测试实例Java25的正式UILib字体整形/光栅不再触发LWJGL3.4.2 FFM动态类生成崩溃。

**架构：** 私有字体绑定发行使用LWJGL完整Java8 JNI实现，剔除嵌套绑定的多版本覆盖；不改宿主Java/LWJGL或全局配置。已有发布包应用加入实际字体case，并在Java8与Java25运行同一个发布包。

**开发模式：** runtime-driven-development。

**Spec：** ../specs/2026-10-07-uilib-core-contract-design.md 第8与9.1节。

- [x] 用GTNH实际Java25.0.1与部署121复现FT_New_Memory_Face的Operand stack underflow；原始记录build/gt25-font-review/before.log。
- [ ] 修正packageFontBindings选择稳定JNI，保持native完整；复用PackagedUnicodeScenario新增整形及像素case，同发布包Java8/25运行，纳入check。
- [ ] 完整build、GTNH实际Java25同探针、Java8u504与GL2必要回归、只读独立审查、提交。
- [ ] 备份GTNH实例旧UILib并部署修复包，明确完整游戏启动尚待复测。
