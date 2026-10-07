# 原生分辨率与自有字体实施计划

> **暂停：** 用户补充必需的彩色emoji、双向/阿拉伯文/印度文字/泰文及控制字符效果。本计划基于Java2D的能力假设和首期范围已失效，不能按原任务直接实施；待专有规格审阅及完整整形/彩色字体能力验证后重写。原生坐标/滚动任务仍作为需求保留。

**目标：** 原生 framebuffer 坐标贯通、自有默认及系统字体测量/绘制，修正无限滚动。

**架构：** 字体服务加载随库 GNU Unifont 与明确系统字体，Java2D Font/GlyphVector 提供字体文件解析、整形和CPU字形光栅，UILib 自行负责候选匹配、统一行布局、不可变字形定位及图集GL回放。字体服务不依赖Minecraft；宿主只提交资源更新及回放。

**技术栈：** Java 8、Java2D headless、现有 scene/ReactiveDomain/Observation/PaintCommand、LWJGL3ify GL150。

**开发模式：** runtime-driven-development，先无平台直启真实集成，GPU由指定Prism实例短小人工批次验收；不引入方法级单元测试。

**Spec：** [字体渲染器专有规格](../specs/2026-10-07-uilib-font-renderer-spec.md)；[核心规格](../specs/2026-10-07-uilib-core-contract-design.md)第9、13.1—13.2节。字体规格待用户审阅，实施暂停；数学深入保持暂停。

## Global Constraints

- 发布不含internal/devtools；人工测试由独立附加包消费生产字体能力。
- GUI Scale不改变UILib坐标；logical px与framebuffer px一一对应。
- 不调用FontRenderer度量或绘制；系统字体与内置字体并存，候选回退不污染其他内容。
- 统一不可变字形定位供度量/绘制消费；缓存有界、失效有来源、共享资源由运行时释放。
- 世界、存档及其他Mod不改动；不启动游戏，部署后用户操作。

## Review Focus

实际字体文件能否被Java运行时加载及字形能否光栅：任务一真实文件探针。新旧排版/字形资源混用：任务二切换字体与保留旧计划验证。原生输入与裁剪/回放坐标不符：任务三软件边界验证、任务四用户实机。无限滚动反馈：任务三到末尾后逆滚立即动。

### 任务一：字体来源和CPU排版闭环

**开发模式：** runtime-driven-development。

**文件：** 新建 ui/scene/text/font 下 FontSelection（候选）、FontService（加载与资源代际）、GlyphRaster（只读CPU字形）、GlyphPlacement（位置/来源）、FontTextLayout（行与字形定位）；修改TextLayout让定位随计划封闭保存。资源放assets/qz_uilib/fonts/unifont，携带上游许可与来源。生产状态先读再修改。

**接口：** FontService.layout(String, TextStyle, int)返回TextLayout；实现SceneTextMeasurer。字体选择通过TextStyle及默认配置确定；系统字体枚举由服务提供，刷新/选择通过同一入口通知metricsRevision。GL不进入这些类型。

- [ ] 从官方取得稳定Unifont文件/许可，记录SHA256；真实Java加载并光栅中英文及补充平面样本，不以假字体替代。
- [ ] 使用Font.layoutGlyphVector定位字符运行，保持码点/组合字符边界及源文关系；按段落与合法换行边界排版，无断代理对。复杂整形适用范围以真实Java字形结果验证，不宣称全部脚本支持。
- [ ] 默认候选与系统候选共用入口；缺字替代诊断和字体加载错误分开。
- [ ] 字形光栅有界缓存按实际font/glyph/size区分，内容切换自动失效。场景同时展示同码点两字体与动态字号。

### 任务二：自有GL文字回放

**开发模式：** runtime-driven-development。

**文件：** 新建host/client字体图集与shader/VAO/VBO回放类；修改PaintCommand与MinecraftSceneHost。图集上传只在GL线程，使用FontService产出字形光栅。

**接口：** GL回放消费TextLayout保存的GlyphPlacement/GlyphRaster，不回读节点、不重新整形。宿主关闭释放GL资源；不可变CPU载荷保留有效内容。图集页数量有界，逐页绘制确保淘汰后不使用旧UV；新字体资源生成使用独立身份。

- [ ] 按字形alpha图集和定位绘制，有序批次遵守文本/矩形绘制顺序与clip。
- [ ] 恢复实际触及program/VAO/VBO/texture unit及binding/blend/scissor等状态。
- [ ] 生产字体不放devtools；开发面板换默认字号与可选系统字体，完全移除FontRenderer路径。

### 任务三：原生坐标和滚动边界

**开发模式：** runtime-driven-development。

**文件：** MinecraftSceneHost/CoreLoopScreen、SceneLayoutEngine/SceneGeometry/SceneRuntime输入滚动相关类型、SoftwareSceneScenario。

- [ ] framebuffer宽高为宿主尺寸，Mouse原生坐标在边界转换Y轴；按实际GL viewport回放/裁剪，GUI Scale不参与。
- [ ] 保存内容范围与视口，滚动请求按最终range限制；偏移来自状态也由正式滚动入口限制，不让业务继续累积虚空距离。内容缩水/宽度变化后自动归位。
- [ ] 真实软件场景验证末尾多次滚轮再反向立即动，以及字体重排后范围更新；核心状态不因绘制回写。

### 任务四：构建审查与短小人工批次

**开发模式：** runtime-driven-development。

- [ ] build manualTestJar完整通过，真实FontService CPU场景并入check；发布排除/附加包门禁仍通过。
- [ ] 独立审查字体来源、测量/绘制一致、资源生命周期、滚动边界；处理Critical/Important后提交。
- [ ] 部署指定Prism实例，第一轮只检查文字可见/字号切换/GUI Scale独立，后续单独测试系统字体切换；不把部署成功当GPU验收。
