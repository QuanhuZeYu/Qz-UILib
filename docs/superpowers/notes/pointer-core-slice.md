# 核心指针切片设计与交接

基线：refactor/uilib-core，4a5454a9。按已授权规格第2、3.4、5、6.1、9节实现；先完成无MC核心自动门禁，再真实GPU自动验收，然后接入MC开发验收页面。未启动或部署游戏实例。

核心输入复用SceneRuntime、ReactiveDomain、SceneHitTester与统一SceneGeometry。router归runtime持有，整段物理手势只保留首次目标的MountHandle，不使用UUID恢复挂载；移动保留token但重新检查可见、启用、模态及几何存在资格。按下不激活，移动维护位置，左键0同目标有效位置释放才发业务意图。离开位置不迁移目标，回原目标可释放激活；资格失活则永久取消本段。

任意按钮参与物理集合，只有左键0起手可建owner；第二键与空白起手吞尾。第一键释放后集合仍非空时不建立新owner。CANCEL用于失焦/窗口关闭，取消owner保留物理集合；宿主必须继续递交UP，或在确认全部物理按钮释放时递交allButtonsReleased同步，不能用它跳过仍按下按钮。runtime.close释放router并终止输入。

每事件domain.execute内prepare→资格检查→route；最终无事件帧同样prepare和检查。runtime的invalidate在已生效声明或挂载变化时仅复核当前owner（不重建几何），防止失活后在两帧之间恢复而复活旧手势。测量与paint沿现readOnly路径，handler仅发布业务意图。滚轮沿现HitTester和dispatchScroll，pipeline.click和runtime.dispatchClick均为完整synthetic activation，任何物理按钮仍按时吞掉。

实施顺序（TDD）：既有旧click/DOWN行为探针真实RED → typed正式scene场景 → 集中实现事件/router和挂载token查询 → coreSceneCheck隔离GREEN → 冻结交接父review/fullbuild/MC阶段。旧探针与typed正式测试不同，red.log只证明旧宿主按下行为提前激活，不声称typed全部边界曾在baseline失败。

## 冻结交接

最终核心门禁：Java8的coreSceneCheck --offline --console=plain，BUILD SUCCESSFUL；PointerInputScenario具名28场景，既有SceneCoreLoopTest（含SoftwareSceneScenario）、CoreFontEngineScenario和TestApplicationScenario全部通过。coreSources执行classpath只有scene/reactive、字体Unicode依赖和TestApplication输出；未执行compileJava/MC/fullbuild。git diff --check通过，无提交。

原始证据在build/pointer-review/：red.log（旧pipeline按下提前激活）；review-red.log（无中间frame的模态激活/关闭误点）；close-review-red.log（close丢第二键，跨管线误点）；effect-review-red.log（业务Effect结算失败丢本批第二键）。三个typed评审反例均在最终green.log同场景通过。配置网络manifest失败与新增重复SoftwareSceneScenario缺main的启动失败另存environment-failure.log与task-entry-failure.log，不计RED；后者已删除重复启动，复用既有SceneCoreLoopTest嵌入入口。

正式API：ScenePointerEvent.down(button,x,y)、up(button,x,y)、move(x,y)、cancel()、allButtonsReleased()；pipeline.pointer(event)。仅按钮0能激活普通按钮，宿主必须采集所有按钮。失焦CANCEL保留占位，宿主确认Mouse全部物理按钮释放后才allButtonsReleased。页面关闭pipeline.close（不replay，记录pending队列与已提交批次的物理尾部），随后domain.execute(runtime::close)。同runtime其他管线沿唯一router继续吞尾。

ReactiveDomain.execute(action, onFailure)是操作完成失败的窄观察口：包括action与结束时Effect结算，在当前域通知清理者；清理失败suppressed附到原Throwable，保原异常身份。不重试/吞错，也不增加executor或中间件。pipeline每事件使用此口取消owner并仅记录剩余物理尾部；直接和外部executor均真实验证。

核心源码冻结后完成独立复审，三个Important均以实际typed反例RED→GREEN关闭。父代理把真实GL ClientFontServicesScenario改为DOWN0先断言0、UP0再断言1，8场景全PASS（AMD Radeon 610M、Java8）；GPU代码注入事件不是OS原生输入实测。证据gpu-green.log。

之后才编辑CoreLoopScreen：父GuiScreen.handleInput仍是唯一Mouse.next消费者，删除mouseClicked/不再super.handleMouseInput，历史getEvent快照按原framebuffer坐标递交全部按钮与move/scroll；宿主只保存原生focus输入可用性gate，不持目标/token/按钮集合。初始与失焦gate直到active且handleInput调用父类消费完全部历史事件、确认所有物理按钮释放才解除；gate期间仅递交UP吞旧尾，丢新DOWN/move/scroll，避免临时新owner。当前物理快照不能在历史事件消费中途或draw清owner：MC的elapsedTicks可为0但仍render，draw不代表Mouse.next已消费。该原生tick/render时序以MC源码核对，不冒称OS输入链自动实测。close先pipeline.close（无replay）再runtime/business/host关闭，finally清所有引用。

MC接入后完整build --offline --no-configuration-cache --console=plain通过（30秒，57任务），包含pointer28与既有状态/scene/字体/产物排除/类路径隔离。证据full-build.log。focus/IME/键盘/拖放不在本切片；MC源码已编译，不把此项称为MC实机通过。
