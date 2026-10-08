# 焦点范围进入与恢复策略实施计划

> **面向 agent 执行者：** 使用executing-plans在同一会话内实施，最终一次独立审查。用户已明确“开始任务”，沿既有worktree推进，不重复授权。

**目标：** 页面/模态进入有初始选择，返回外层可恢复合法目标，失效记录不复活。
**架构：** SceneFocusRouter复用runtime的有效模态、树、挂载token与响应式投影，保存被覆盖范围的恢复记录；真实变化重验，声明结算结束只执行待进入/返回的选择，不每帧扫描。SceneNode声明initialFocus/restoreFocus，不增加业务或宿主目标镜像。
**技术栈：** Java8核心、ReactiveDomain、scene/NodeBinding/PaintPlan，现有LWJGL3实际GL与LWJGL2 MC验收页。
**开发模式：** test-driven-development：最小闭环先实际运行，再合同反例与修正；OS交互由用户自行双实例实测。
**Spec：** [唯一权威规格](../specs/2026-10-07-uilib-core-contract-design.md)5与6.3.1。

## Global Constraints

保留6e555d8f指针/焦点已验收结果；用户确认上一焦点测试通过。
核心无MC/Forge/LWJGL依赖；唯一原生输入消费者及pointer gate保持现有边界。
页面初始/返回选择与一般资格失活区分；清除不自动补选。
默认顺序为逻辑树前序，UUID及几何排序不参与。空范围无焦点，不创建新的输入处理器。
当前页面范围覆盖非模态根，进入页面时按根声明顺序取政策；模态政策取该模态节点，目标必须属于当前有效范围。
部署沿后续用户授权，两实例由用户启动；不push/tag/release。

## Review Focus

绑定可能同时改变显隐、目标资格与子序列：即时释放/记录取消，但结算结束才选择，避免暂态目标进入可观察结果。嵌套模态外层结束、保存目标禁用后恢复及旧token重挂载均在任务2核验。MC原生只通过真实用户反馈收齐，不外推GPU自动注入。

### 任务1：最小进入与返回闭环

**开发模式：** test-driven-development。
**文件：** 修改SceneNode、SceneRuntime、SceneFocusRouter；新增integration/FocusScopeScenario与build.gradle.kts运行入口。
**接口：** SceneNode.initialFocus(SceneNode)/restoreFocus(SceneNode)可空，读取同名getter；经writable声明边界与NodeBinding更新。runtime包内共享activeModal/local资格查询。FocusRouter保留request/clear/focused；新增包内prepareScopes，在preparePresentation结算后执行待选择。

- [x] 页面A/B声明初始B，绑定焦点标识；首帧B，打开模态初始M，关闭恢复B。检查真实PaintPlan输出及业务计数。
- [x] 页无初始目标取逻辑第一个有效目标；模态内空范围无焦点。
- [x] 局部选择保存挂载身份，焦点命令仍先checkWrite；默认遍历只在进入/返回，不从focused读取触发。

### 任务2：围堵具体生命周期反例

**开发模式：** test-driven-development。
**文件：** 同上；旧FocusInputScenario按新进入合同更新预期，不删除旧token、域保护、失活与指针场景。

- [x] 保存目标在模态期间禁用/隐藏/focusable关闭后恢复：先前记录已取消，返回取其他初始/首项。
- [x] 保存目标卸载重挂载、跨runtime/越模态目标声明被拒；当前声明可明确指向新挂载，不能冒充旧代际自动恢复。
- [x] 内层返回外层先前目标；外层隐藏/卸载时所属内层不恢复，范围记录释放。生效attach/detach无绘制frame不丢原返回目标。
- [x] 初始/恢复目标绑定变化、动态children、无frame范围切换、被动非模态、主动clear、添加/改标题/字号与resize：仅范围进入返回选目标，平常保持合法焦点或主动无焦点。
- [x] 核对外部executor、重复关闭；coreSceneCheck与Pointer28回归通过。

### 任务3：渲染、验收与交付

**开发模式：** 自动GL + 用户MC实测。
**文件：** ClientFontServicesScenario、CoreLoopScreen及短执行记录。

- [x] 复用实际FBO链验证模态进入颜色/关闭恢复颜色，不创建GL测试宿主。
- [x] 核心/GL通过后在/qzcore明确展示默认初始与模态M、内层N、恢复失败选择；原点击/字号/标题操作不抢焦点。
- [x] 完整build与最终独立审查通过；两项Important已修复并同代理复核关闭，必要核心/GL回归通过。
交付执行：本地提交、manualTestJar、核验哈希与双实例部署；实际版本与结果记录在build/focus-scope-review/，不自动启动实例。
- [ ] 两实例由用户观察初始、模态进入/恢复、内层返回、失活恢复失败及普通更新保持；自动通过后交付待实机版本。

运行沿agent-environment.mjs的execWithEnvironment，Gradle wrapper使用--offline --no-configuration-cache --console=plain。核心入口focusScopeScenario/coreSceneCheck、真实GPUclientFontServicesScenario、完整build与manualTestJar。日志放build/focus-scope-review/，配置/编译失败不作行为RED。
