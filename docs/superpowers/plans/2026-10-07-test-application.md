# 统一测试自动化应用实施计划

> **面向 agent 执行者：** 使用 executing-plans 执行共同基础和整体验证；独立测试文件迁移可并行，文件归属不重叠。

**目标：** 测试继承统一应用基类，集中生命周期及环境初始化，新增测试只定义场景。

**架构：** 纯Java8 TestApplication在独立testSupport编译单元，运行具名场景、参数及逆序资源释放；TestResources集中读资源。真实LWJGL3环境在test域组合GlfwTestEnvironment，LWJGL2独立测试域使用自身Display宿主组件。测试main只实例化子类调用run，runTests声明场景。

**技术栈：** Java8、现有Gradle JavaExec、正式UILib生产路径。

**开发模式：** runtime-driven-development，保持既有测试真实反馈；应用生命周期自身以受控异常的行为回归校准，不按覆盖率扩张。

**Spec：** ../specs/2026-10-07-uilib-core-contract-design.md 第9.1节。

## Global Constraints

基类不依赖MC/Forge/LWJGL，测试不进入发行。生产源码不为测试改变。原版游戏不修复。无自动MC启动或实例部署。保留存量实际场景的断言与正确环境；无源码文本快照。

## Review Focus

初始化失败和close异常不得覆盖原异常；资源必须在上下文销毁前释放；核心隔离类路径和LWJGL2测试不能偷偷引入LWJGL3。

### 任务1：共同应用与运行入口

**开发模式：** runtime-driven-development。

文件：src/testSupport/java/club/heiqi/uilib/testing/TestApplication.java、TestResources.java；build.gradle.kts。
接口：protected abstract void runTests() throws Exception；protected void setup()/teardown()；final run(String...args)；protected final scenario(String, CheckedRunnable)；own(T extends AutoCloseable)/onClose(CheckedRunnable)；arguments()；protected static require(boolean,String)。scenario内own资源场景末释放，setup内own资源应用末释放，逆序且保异常；零场景失败。
- [x] 写基类、资源读取及sourceSet接入，运行实际生命周期探针，检查初始化/场景/清理失败、scope逆序与零场景。

### 任务2：无平台与字体场景迁移

**开发模式：** runtime-driven-development。

文件：src/test下所有不创建GL窗口的集成场景；SoftwareSceneScenario从SceneCoreLoopTest调用改成独立应用场景或复用显式测试方法。
消费任务1接口，拆为具名场景，集中重复require及字体读取，保真实行为断言。
- [x] 迁移coreLoop/native/fontText/vanilla/packaged等，运行check实际场景。

### 任务3：真实宿主场景迁移

**开发模式：** runtime-driven-development。

文件：src/test创建GL窗口的场景、host/client/gl/IrisProducerGlScenario；src/lwjgl2Test。
消费任务1生命周期，新增GlfwTestEnvironment提供init及core/compatibility窗口，统一失败清理；使用own/onClose保持字体worker/GPU资源在window之前释放。独立LWJGL2默认Display不使用GLFW。
- [x] 迁移并执行原CPU分支及实际GL3 core/compatibility、GL2默认Display场景，包含2d/array必要分支。

### 任务4：最终验证与提交

**开发模式：** runtime-driven-development。

- [x] 完整build和compileLwjgl2TestJava；必要实际任务原始日志保存在build/test-application-review，不clean删除探针。
- [x] 独立审查spec、scope、异常保留/清理顺序与迁移测试，修真实问题后验证受影响场景。
- [x] 检查正式发行无testing类，提交本任务规格/测试/构建/必要使用说明，说明无issue因用户直接改进请求。

验收证据：build/test-application-review/{lifecycle,final-build,gl3,lwjgl2-2d,lwjgl2-array,gl3-array}.log。最终build与原CPU场景通过；GL3全部设备入口、GL2原Java8u51默认Display的2d/array通过。独立只读review无新增确定Critical/Important/Minor。实际发现并修正了convention覆盖compileTestclasspath、显式旧Java与默认launcher冲突、测试主动关闭非幂等runtime与统一清理重复；均在测试或构建接入处理，不改生产。未启动MC、部署或宣称真实Iris/shaderpack/性能验收。
