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
- [x] 本地提交557c35f5后完整build与正式125包复验，committed-build.log、committed-java25.log。
- [x] 备份GTNH实例旧121并部署正式125，deployment.json记录sha256和原Java配置未改；Prism实际启动，UILibinit/postinit与324mods加载成功。
- [ ] 处理实机第二个错误：00:09:36主菜单Rendering screen，Lwjgl3GlApi.capabilities链接org/lwjglx/opengl/GL缺失。官方LWJGL3ify3.0.31将org/lwjgl/引用映射至org/lwjglx/；此直接GL3适配添加既有依赖中的官方Lwjgl3Aware逐类标记，不标记整jar，保旧LWJGL2与宿主GLSM转换。已完整build/GL2/GL3及独立审查后提交00d7ca19并部署126，RFB类名错误已消失；主菜单继续暴露Angelica2.2.10矩阵重载映射错误，不能判定游戏启动完成。
- [x] 修正Lwjgl3GlApi矩阵上传为复用direct FloatBuffer签名，保GLSM接管，核查当前API其他转换后的方法签名。审计捕获实际OptionalIrisProgramScope可达的glGetActiveAttrib便捷重载missing，已改为共同六参buffer签名按真实最大名字长度读取UTF8；完整build+GL3array已通过，glsm-common-signatures-build.log。审计green与独立审查通过，73个GLSM重写目标全部存在，提交bb8c1148并完整build生成正式127。已备份旧121/125/126，部署127后Prism实际启动到GTNH主菜单，中文与324/324mods可见，进程23440持续运行；原错误无再现。证据：delivery-build.log、delivery-java25.log、delivery-deployment.json、delivery-game-latest.log、delivery-game-fml.log、gtnh-main-menu.png、result.json均在build/gt25-font-review。未进入存档，未验证Android。
