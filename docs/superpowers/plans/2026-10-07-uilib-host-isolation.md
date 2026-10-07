# 无平台核心与双LWJGL宿主实施计划

> **面向 agent 执行者：** 使用 subagent-driven-development，分离文件范围并集成验证。

**目标：** 同一UILib核心与字体服务在原Java8/LWJGL2的纯Forge1.7.10和既有LWJGL3ify实例中运行，不补装宿主Mod。

**架构：** 核心仅依赖纯Java字体与绘制合同；CPU native绑定由隔离加载的适配实现，GPU算法通过注入GlApi复用，LWJGL2/3只实现具体调用。Minecraft负责资源、生命周期、原版语义及可选Angelica/Iris桥。

**技术栈：** Java8、ICU4J76.1、FreeType/HarfBuzz、LWJGL2/3宿主，Forge1614。

**开发模式：** runtime-driven-development；先实际Java8/native共存与GL反馈，再完整build和正式包装检查。

**Spec：** 核心规格第9节、字体规格第1/5.1节、MC宿主优化验收表，commit4e6a99e8。

## Global Constraints

不升级第二实例Java、不安装LWJGL3ify/Angelica、不退回原版字体、不删复杂脚本/emoji能力。用户后续明确允许该实例加装UniMixins，复用其启动能力，不自行实现Mixin启动器。
核心无Minecraft/Forge/LWJGL类依赖或隐式native/GL初始化。
字体布局、格式、需求缓存与GPU资源/回放算法保持单份，宿主仅适配能力与调用。
保护主仓协作者内容；不merge/push/tag/release，不自动启动MC。

## Review Focus

官方JNI导出固定类名，不能仅Shadow改包；私有classloader必须实证parent LWJGL2 sealed package与同名native库隔离。
LWJGL2状态查询要求至少16元素，适配内部缓冲不得让viewport/normal/颜色查询越界；有界staging不得改成每行直接内存泄漏。
纯Forge未装Mixin runtime，按用户后续授权加装固定UniMixins；不自建Mixin启动链。

### 任务1：CPU字体合同（680）

**开发模式：** runtime-driven-development。
**范围：** ui/scene/text/font、TextLayout、对应CPU场景；实现迁host/font/freetype。
**接口：** FontEngine.openSession；FontSession.openFace；FontFace度量/整形/墨迹/光栅，公共数据只JDK与核心类型。
- [ ] 拆GlyphBitmap纯数据与FT转换，InkBounds独立；核心布局/worker注入引擎。
- [ ] 保留原Unicode、完整context/UTF16 cluster、样式及worker预算场景。

### 任务2：GPU双宿主（0038）

**开发模式：** runtime-driven-development。
**范围：** host/client/gl及LwjglFontContextCapture；LWJGL2实现单独sourceSet。
**接口：** 注入GlApi、纯GlTokens/Capabilities；ProgramScope/provider注入MC可选程序桥。
- [ ] 单份atlas/renderer/state算法去绑定与MCcompat具体依赖。
- [ ] 实际LWJGL2 ABI适配query/uniform/log/introspection/buffer提交，保全部GL合同。

### 任务3：发行与外层接线（父、abc探针）

**开发模式：** runtime-driven-development。
**范围：** build配置、native加载器、ClientFontServices/MC入口、Mixin启动发行、MCcompat ProgramScope桥。
- [ ] Java8_51真实FT/HB与parentLWJGL2共存探针，验证后实现隔离CPU加载器。
- [ ] 分别编译2/3bindings并打包，宿主检测不触发另一实现初始化。
- [ ] 第二实例加装固定UniMixins并验证真实LaunchWrapper变换，复用已有Mixin发行，不自建启动器。

### 任务4：集成与部署

**开发模式：** runtime-driven-development。
- [ ] 不含MC/Forge/LWJGL的核心加载与逻辑路径，Java8_51真实native及LWJGL2 GL场景。
- [ ] 完整build，既有CPU/native/GL和Mixin双profile回归，独立审查。
- [ ] 本地增量提交后审计正式包，备份并部署两个指定实例；不自动启动MC，人工视觉/性能结果仍单独归属。
