# Minecraft 多版本宿主接入

状态：已授权的多版本宿主接入、安装与实机验收范围。

## 目标与运行组合

用户在 Prism Launcher 中打开实例后，能打开同一测试广场，运行邮箱、正式配置、拖放排序、滚动与媒体案例。测试内容继续由无平台核心及共享广场定义；Minecraft 只提供窗口、原生输入、资源和运行生命周期。

目标为下列现有实例，版本与加载器保留现状：

| 实例 | Minecraft | 加载器或整合包版本 | Java 与窗口/输入宿主 |
| --- | --- | --- | --- |
| Forge 1.7.10 | 1.7.10 | Forge 10.13.4.1614 | Java8 / LWJGL2 |
| GTNH | 1.7.10 | GTNH 2.9.0-beta-3 | 既有 Java 组合 / LWJGL3ify 与 Angelica |
| Forge 1.12.2 | 1.12.2 | Forge 14.23.5.2864 | Java8 / LWJGL2 |
| Forge 1.16.5 | 1.16.5 | Forge 36.2.42 | Java8 / LWJGL3 |
| NeoForge 26.3 | 26.3 | NeoForge 26.3.0.86 | Java25 / SDL |

既有原版字体替代能力的范围保持原合同；新接入以 UILib 页面为目标。

## 归属与主流程

核心、坐标和产物边界遵循[核心规格第 9 节](<../../../Qz-UILib-Core/specs/2026-10-07-uilib-core-contract-design.md#9-平台边界与验收>)；开屏持键、失焦、ESC、组字、重初始化与释放遵循[页面宿主规格](<../../../Qz-UILib-Core/specs/host/2026-10-07-uilib-page-host-spec.md>)。

嵌入式页面服务组合既有场景、共享 GL、CPU 字体、图像、媒体与配置能力。各版本宿主只负责加载器入口、页面打开关闭、资源变更通知、framebuffer 尺寸与原生事件转换，不重新实现布局、绘制或输入权威，也不通过全局替换游戏输入回调接管其他页面。

宿主按[Core 命令与范围快捷键规格](<../../../Qz-UILib-Core/specs/scene/2026-10-18-uilib-command-spec.md>)只递交命名键 DOWN/UP、当次修饰键与重复事实，字符和组字经独立文本入口递交。按压与焦点交互参考 [React Aria usePress](https://react-aria.adobe.com/usePress) 和 [useFocus](https://react-aria.adobe.com/useFocus) 案例：Space 的原目标联系、完整释放后的单次激活及离开焦点取消由 Core 维护，Enter 在 DOWN 激活。`native-only` 回归验证生产输入转换与路由，窗口可用性和物理状态由隔离夹具提供，不替代真实设备与窗口运行。

语义消费遵循[Core 无障碍语义与宿主读取规格](<../../../Qz-UILib-Core/specs/scene/2026-10-18-uilib-semantics-spec.md>)，参考 [React Aria TextField 标签与值示例](https://react-aria.adobe.com/TextField)和 [FocusScope 焦点范围示例](https://react-aria.adobe.com/FocusScope)。各版本页面以 `semantics()` 读取同页 Host 回调保存的最新纯值快照，以 `semantic(request)` 将挂载 ID 定向动作交给原页面会话；名称和值、焦点与动作资格由 Core 的当前事实表达。未开屏或关闭后读取为空，动作返回 `CLOSED`；既有页面使用不增加原生无障碍桥前提。`native-only` 通过同一页面端口观察快照与请求结果，仍沿前述隔离夹具边界验收。

新版提取式渲染宿主在真实 GPU 提交阶段消费不可变 PaintPlan，提取阶段不执行 GL。页面结束后不得回放旧页计划；资源结束遵循其所有权。宿主提交必须保持有效的目标 framebuffer 与宿主 GL 状态；宿主虚拟化状态仍从实际有效状态取得。

[测试广场](<../../../Qz-UILib-Core/specs/verification/2026-10-07-uilib-testing-spec.md#2-共享测试广场>)保持共享声明、资源及独立附加产物。人工附加包包含共享广场及其独立核心验证服务和样本，“核心验证”页与无窗口入口使用同一检查目录；正式产物不含测试代码。人工附加包仅登记显式客户端命令 `/qzcore`，不向游戏主菜单注入测试入口或兼容其他模组的菜单实现。生产入口可打开任意 ScenePage。正式跨仓依赖与产物边界遵循核心第 9 节，实际坐标、版本与发布策略由各仓构建维护。

## 装配与验收

所有 Minecraft 版本在同一独立仓维护，各加载器保留独立构建入口与各自工具链、映射和元数据；旧版主构建保留现有工具链。各入口通过正式 Maven 坐标、版本及依赖元数据消费 Core 与所需 Platform 的对应 Java 目标产物，不读取相邻仓生产源码或根项目 build 输出。相同 Java 目标内复用同一核心、共享 GL 与字体输出，版本源码只含各自宿主职责。Java8 与 Java21+ 输出分别装配，不复制核心实现。

逐实例验证真实发行安装组合：启动成功、从正式入口打开广场目录、浏览案例、运行一次关键交互、调整窗口或缩放后画面与命中对齐、关闭并重开无旧输入或资源残留。媒体使用随附示例验证解码与可见播放。安装与运行记录绑定实际源码和产物版本；独立桌面或编译成功不替代游戏实机。

各宿主的 `installManifest` 任务从既有安装任务与实际 Maven 解析结果生成 `build/install-manifest.json`：协议版本、宿主、精确 UILib 版本、安装 Jar 的 source/name/SHA256，以及组合依赖的 Maven 坐标、classifier、来源与 SHA256。集合包含本次人工验收所需的宿主、人工附加包与媒体包；内嵌的 GL/binding、gallery 与核心验证依赖同样记录来源身份。构建端负责装配和包装验证，安装端只消费冻结集合；本次指定 Maven 仓中的对应产物必须与构建时身份一致，不依据文件名、ZIP 条目或目录枚举另行推断组合。

安装变更限于 UILib 和被实际兼容故障阻塞的调用方接入产物。部署先对全部目标完成清单、来源 SHA256、Maven 身份与目标预检，再直接覆盖，全部新包 SHA256 通过后仅删除明确 allowlist 内的旧 UILib；不自动构建，也不创建备份、staging 或 rollback。现有世界与业务用法沿用；必要调用方适配保留相同业务请求、字段含义和使用条件。兼容故障在所属接入边界定位，不能以删除既有功能或改变核心合同通过验收。

## 可选 Mod 扩展边界

原版宿主只保留中性 MinecraftHostExtension SPI，外部实现通过 ServiceLoader 按需提供字体提交屏障、有效矩阵/光栅状态、program、物品状态和增强输入事实。扩展缺席时使用原版即时字体、原始固定管线及普通输入，不解析目标 Mod 类或安装其 Mixin。扩展仍复用同一 scene、信号、PaintPlan、字体与 GPU 资源所有权。非原版版本选择、Iris/Angelica、lwjgl3ify/RFB 和 AE2 接缝归独立 Qz-UILib-Compat。

原版文字入口可消费扩展提供的 `textMapping()` 不可变码点映射快照，缺席时恒等。转换在共同 `MinecraftFormat` 入口消费 § 格式后执行，每个原码点转换为一个 Unicode 标量；保留原始文本及完整 UTF16/byte 来源范围，裁剪与换行返回原源切片。测量、单字符宽度和绘制使用同一映射；解析、布局及阴影缓存包含映射快照身份。映射事实改变时扩展发布不同稳定实例，配置读取只发生在启动或显式资源重载，不能每字符访问文件。具体字符表与开关归独立兼容仓，不进入 Core 或通用字体匹配。

独立扩展单向消费 `asia.quanhuzeyu.uilib:minecraft-host:<宿主版本>:development-java8` 正式 Maven 开发接口；该 Jar 只包含本仓编译输出，不携带邻仓源码或复制平台算法。宿主提供中性光栅捕获及quad索引复用入口。`fontGeneration()` 返回已发布 FontTextService 排版代际，同来源候选调整与资源重新发布均使外部布局缓存失效，不能以来源资源 revision 替代。`uilibDependencyVersion` 可明确选择上游 Maven 组合；未提供时沿用宿主版本，安装清单逐项记录实际坐标与 SHA256。
