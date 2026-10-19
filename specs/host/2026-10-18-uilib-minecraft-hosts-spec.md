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

安装变更限于 UILib 和被实际兼容故障阻塞的调用方接入产物，各自原产物保存在实例备份目录。现有世界与业务用法沿用；必要调用方适配保留相同业务请求、字段含义和使用条件。兼容故障在所属接入边界定位，不能以删除既有功能或改变核心合同通过验收。
