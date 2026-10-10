# UILib 内聚优化实施计划

> **面向 agent 执行者：** 必需子技能：使用 subagent-driven-development，按下列唯一文件职责实施。

**目标：** 收口整体审查中资源关闭、文字语义、宿主内容与图像外观的真实分岔。

**架构：** 沿既有 Scene/Owner/TextContent/TextLayout/PaintCommand 完成闭环；中性字符规则和样式意图归内容，平台转换止于宿主，控件定义备用外观而后端只选择。

**技术栈：** Java 8、ICU4J 76.1、既有 FT/HB、LWJGL2/3 适配、Gradle 集成场景。

**开发模式：** TDD（真实生产场景与依据合同的反例，不要求制造 RED）；游戏仍走既有双实例人工反馈。

**Spec：** [跨模块设计](<../specs/2026-10-10-uilib-cohesion-design.md>)及对应现有权威模块规格。

## Global Constraints

中文注释与文档；不扩公开 Markdown/数学/原版 UI；不新增旁路树、全局可变 Unicode 配置或重试回滚。不得并发 Gradle、擅自提交或部署；所有构建由父执行并保留原始失败。仅任务文件精确提交，不 push/tag/release，不启动游戏。

## Review Focus

已有回调错误→页面关闭后的资源残留；已有文字两套规则→删除/定位/行高分岔；GPU prepare 在 plan 冻结后→自定义占位被后端替代。分别由对应生产场景验证；不以源码快照断言或原版接口冒充效果验证。

## 任务 1：完整生命周期收尾

**开发模式：** TDD。唯一实现者核心席位，范围 SceneRuntime/必要 FocusRouter 与现 FocusInput/PageSession 反例；父不同时编辑。

- [x] 真实聚焦控件失焦抛错，观察其资源是否仍释放。
- [x] 将路由器关闭纳入统一异常合并，完整 detach/dispose。
- [x] 回归 focus/page，审查首错误与所属资源边界。

## 任务 2：共同文字事实与稳定样式内容

**开发模式：** TDD。唯一实现者文本席位，范围 text/unicode/font/format、edit、Field/AreaGeometry/RichText 及相关场景；Gradle 由父修改。LayoutEngine/SceneControl 归任务 3，文本席位提供共享布局接口建议。

- [x] 共享纯 Unicode/emoji 规则及明确自定义重载归属，围堵 LF+组合标记，规则合并后编辑及历史位置重新对齐。
- [x] 保留最终 Cluster 方向事实，复制不丢失，消除单多行重复 Bidi，采用同一 policy；Field 投影硬分隔符为可见标记，普通 NO_WRAP 保留硬段落。
- [x] 实现中性稳定 styled 内容及 DSL 源结构与样式派生，明确 FontStyle 权威；MC formatter 移宿主且唯一。
- [x] 真实字形/源映射/链接身份/主题/行高场景回归，不依赖 MC 类路径。

## 任务 3：图像提示与占位闭环

**开发模式：** TDD。唯一实现者宿主席位，范围 image/hostimage/GLImage/GLPaint/GLGlyphAtlas、PaintCommand/PaintPlan、SceneControl/FramePipeline 和图像场景；LayoutEngine 的同批测量封装由父接管，图像捕获实现保持唯一生产排列。字形图集仅按同一资源层保障主需求，不新增字体树，不改任务 2 text 文件。

- [x] 宿主物品事实接任务 2 中性内容，core 移除 palette/parser；提示 prepare 阶段缓存而 overlay 只消费布局。
- [x] IMAGE 同代分支及成功覆盖层，自定义子树使用原场景算法捕获并避免双绘；备用 overlay 在全页内容之后按父图像实际分支投影。
- [x] 图像和字形沿同一资源层准备，主内容及 ready 闭包先于备用；所有层共用单帧总预算，GL 按事实选分支，保 order/clip/state。
- [x] 上传不足/失败/item 回放异常、自定义半透明占位及备用缓存/partial 竞争的真实 GPU 场景验证；旧新代 plan 互不混用。
- [x] 同批测量/排版共享接任务 2 建议，不造控件跨帧缓存。

## 任务 4：整合与交付

**开发模式：** TDD＋必要人工实例反馈。父统筹 Gradle、规格差异、短验证、构建日志及最终交付。

- [x] ICU/Unicode 资源只加入需要的 pure core 验证路径，不带 MC/Forge/LWJGL。
- [x] 规格与质量独立审查实际实现并处理有效反馈，已证实的重要问题清零。
- [x] 完整 `build` 与人工附加包构建通过；真实设备 GPU 回归通过；操作指南已就绪。

后续交付动作与精确完成证据单独记录：按本轮文件清单本地提交，提交后干净重建正式 jar 与人工包；普通/GTNH 两实例备份替换并校验 SHA256，保持配置，不启动游戏。审查指纹、构建原始日志、备份安装指纹和实际游戏待验状态归交付证据目录，避免把预期动作记作已执行。LWJGL2 编译/checkstyle 由完整 build 覆盖，本轮未启动原 LWJGL2 宿主实机。
