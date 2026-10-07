# Java25字体绑定启动修复实施计划

> **面向 agent 执行者：** 使用 executing-plans 按任务实施。

**状态：** 用户已要求继续修复。采用现有官方JNI/native后端，仅在私有字体绑定发行中去除多版本覆盖；保留目标环境兼容优先和Android潜在目标，桌面验证不外推手机支持。

**原目标：** GTNH测试实例Java25的正式UILib字体整形/光栅不再触发LWJGL3.4.2 FFM动态类生成崩溃。

**架构：** 私有字体绑定发行使用LWJGL完整Java8 JNI实现，剔除嵌套绑定的多版本覆盖；不改宿主Java/LWJGL或全局配置。已有发布包应用加入实际字体case，并在Java8与Java25运行同一个发布包。

**开发模式：** runtime-driven-development。

**Spec：** ../specs/2026-10-07-uilib-core-contract-design.md 第8与9.1节。

- [x] 用GTNH实际Java25.0.1与部署121复现FT_New_Memory_Face的Operand stack underflow；原始记录build/gt25-font-review/before.log。
- [x] 修正packageFontBindings选择稳定JNI，保持native完整；复用TestApplication和TestResources新增PackagedFontScenario真实整形及像素case，同发布包Java8/25运行，纳入check。
- [x] 完整build、GTNH实际Java25同探针、Java8u504与GL2必要回归、只读独立审查。最终日志build/gt25-font-review/final-build-device.log、java25-reobf.log、zip-asm5.log；独立审查无Critical/Important，17嵌套jar保留entry与官方逐字节一致。Minor测试名称收窄为实际整形与像素观测。
- [ ] 本地提交后完整build与正式包复验。
- [ ] 备份GTNH实例旧UILib并部署修复包，明确完整游戏启动尚待复测。
