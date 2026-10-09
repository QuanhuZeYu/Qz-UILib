# 正式页面接入与测试迁移

依据核心/页面接入/测试应用规格，用户授权实施并迁移现有测试；不部署或游戏。复用ReactiveDomain/Owner/SceneRuntime/SceneFramePipeline，不增加焦点或输入权威。

正式能力：ScenePage为构造回调；ScenePageContext提供domain/scope/mount/own以及实际runtime供正式扩展；ScenePageSession.open在独立顶层域操作初始化及Effect结算，失败统一清理且保原Throwable。会话提供frame/pointer/navigation/escape和只读原管线观察；低层合同反例仍能取得runtime/pipeline。外部状态与host服务不默认归会话，owned资源由context.own显式登记。MC正式GuiScreen入口统一原生唯一消费/坐标/availability/history与close。CoreLoopScreen只构造状态与声明。

- [x] 先跑声明式软件页面真实闭环，再以重初始化、结束、外部状态和具体失败反例围堵；保护Error后其余资源释放，不吞主异常。
- [x] 正式MC接入+CoreLoopScreen迁移，与核心实现并行，父控制接口。
- [x] 普通pointer/focus/scope/navigation/ESC/GPU及软件页面夹具迁移；实际只验证底层runtime或多pipeline的反例保原低层机制，字体/批次/资源测试保持其生产入口。逐项盘点而非仅替换最近新增场景。
- [x] 核心check、GPU12、LWJGL2、完整build/manualTestJar；实际MC父源静态核验。不重复启动实例。
- [x] 最终独立审查与修正、记录所有测试迁移归属、本地提交供用户审阅。
