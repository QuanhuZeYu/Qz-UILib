# 核心焦点归属与生命周期实施计划

状态：用户已认可并授权实施；核心、真实GL、完整build及独立源码审查已通过，等待交付包后的两实例实测。承接唯一权威规格第2、3、5、6节；本文件细化一个增量的方案与验收，不另立规格权威。本文公共入口与首阶段显式选择策略为本次实施范围。

## 目标与基线

页面通过受控入口把焦点交给一个当前有效的控件，并通过响应式结果观察焦点。普通内容与布局更新保留合法焦点；资格失活和挂载结束释放焦点，不隐含业务提交、取消或激活。

基线为 refactor/uilib-core 的 c8f09cfd。指针增量已通过核心自动场景、真实GL回放和完整build；用户确认1.7.10与GTNH两个实例的普通点击、移入移出、多键尾部、失焦与页面关闭场景均正常。此证据只覆盖已部署指针版本，不能当作焦点验证。

权威依据：[核心规格](../specs/2026-10-07-uilib-core-contract-design.md)第2节受控交互命令、第3节状态/呈现分离、第5节挂载生命周期与第6节交互资格。实施目录沿用当前核心worktree，不改学习分支。实施前核对实时Git，保护期间新增的协作改动。

开发方式：使用更新后的test-driven-development，先跑通最小真实路径，再依据合同与实际反馈补反例；保留正确实现，不强制先取得失败记录。核心自动验收通过后才接入真实GL场景和MC验收页。完成后部署沿用用户后续授权，由用户启动两实例实测。

## 首个切片的范围

本次交付可聚焦声明、唯一焦点归属、显式请求与清除、响应式观察和生命周期释放。它建立后续导航与编辑的基础，不宣称第6节全部完成。

Tab/Shift+Tab、键盘激活、ESC消费、文本输入、IME、选区、编辑提交与焦点可见区域自动滚动不在本次实现。初始焦点/恢复目标的页面声明、默认选择顺序、模态自动恢复与所属交互浮层是后续焦点策略专题；本次验证现有模态范围对目标资格的影响。

首阶段拟采用显式选择：新runtime起始无焦点，控件必须声明可聚焦；资格失活后清空，恢复资格不自动找回旧目标。模态关闭后可以通过新请求选择当前有效目标，本次不引入历史焦点栈。这是切片边界，不撤销规格中后续初始选择、导航和恢复能力。

窗口暂时失焦沿用已完成的宿主输入gate，本切片不把指针CANCEL解释为控件失焦或编辑提交；页面关闭由runtime生命周期释放焦点。原生窗口焦点与逻辑控件焦点分别归宿主和核心。

## 职责与拟定入口

| 实体 | 本次职责 |
| --- | --- |
| SceneNode | 声明可聚焦资格；沿现有NodeBinding和挂载写入边界动态更新 |
| SceneRuntime | 持有唯一焦点路由器；使用现有挂载token、interactive资格和生命周期通知 |
| SceneFocusRouter（新增） | 处理请求、清除、当前目标读取与失活释放；不维护第二棵树 |
| ReactiveDomain及ReadableSignal | 串行执行焦点命令；让页面绑定观察结果，复用既有依赖与释放机制 |
| SceneFramePipeline | 沿原prepare使声明生效并回放焦点可视结果；不持有第二份焦点 |
| CoreLoopScreen | 提供人工验收操作与焦点显示；不在宿主存目标或把窗口active当控件焦点 |

拟定公开接口：

- SceneNode.focusable(boolean)：声明；默认false。读取方法focusable()。
- SceneRuntime.focusRouter()：返回runtime唯一实例，构造权限与pointerRouter一致。
- SceneFocusRouter.request(MountHandle target)：域内请求，返回是否接受。检查句柄属于本runtime、仍是当前挂载、声明可聚焦且interactive；无效目标返回false，保留原有合法焦点。null属于调用错误，用clear表达清除。
- SceneFocusRouter.clear()：域内清空；重复清除不产生状态变化。
- SceneFocusRouter.focused()：只读响应来源ReadableSignal<SceneNode>，无焦点为null。写端只归路由器；读取不得推进声明、修改焦点或触发布局。

入口名称可按现有代码风格局部调整，职责与行为保持不变。请求可从输入handler调用，因为焦点是规格允许的受控交互命令；它不修改声明树，不使用禁止handler改树的command入口，也不绕过域写入约束。测量、绘制和纯派生阶段仍不能请求焦点。

焦点权威识别当前挂载代际，读取信号是该权威的响应式投影，不再保存业务或宿主目标镜像。重复请求同一有效挂载不重复通知；同节点卸载重挂载后旧句柄不能请求新挂载。

焦点变化经signal驱动声明绑定与绘制失效，默认不使布局失效；若页面根据焦点明确改变文案或尺寸，依既有属性语义正常重新布局。验收页仅使用背景/状态显示，不新增focus绘制命令或视觉框架。

## 生效与释放

焦点请求检查已生效的当前声明。Signal变化使呈现绑定变脏后，沿正常prepare生效；焦点在真实声明/树变化生效时复核当前目标，不在纯读取中暗中flush，也不声称捕获同一操作内所有未生效的中间值。

沿SceneRuntime已有invalidate及挂载释放完成点检查焦点：隐藏祖先、禁用祖先、focusable关闭、detach/destroy、move进入无资格父节点、激活隔离当前目标的模态范围时清空。普通文字、字号、窗口尺寸、布局重排和有效父节点之间move不释放合法焦点。

两个独立业务操作导致失活再恢复，即使中间没有绘制frame，已释放焦点也不能复活。复用指针增量已经建立的声明生效/挂载token路径，不能只在最后frame检查资格。

runtime.close清空焦点并释放相关观察资源，沿现有幂等生命周期。单独关闭一个pipeline不销毁共享runtime的焦点；真正页面结束仍按pipeline → runtime → business → host清理。失活释放仅改变交互权威，不直接调用业务click、提交或草稿取消。

不预建焦点事件总线、原因枚举、历史恢复栈或错误恢复框架；新机制只由本阶段实际反馈驱动。

## 任务1：跑通显式焦点的真实闭环

开发模式：test-driven-development。

涉及文件：SceneNode、SceneRuntime；新增runtime/SceneFocusRouter；新增integration/FocusInputScenario；必要的build.gradle.kts注册。

实施步骤：

1. 复用SceneRuntime、MountHandle和ReactiveDomain，实现上面的最小入口与资格声明。
2. 在既有TestApplication中使用真实SceneNode、NodeBinding、SceneFramePipeline和SoftwareSceneScenario.SoftwareHost，构造两个可聚焦控件A/B。先使页面挂载与声明生效，再取得挂载句柄。
3. 实际请求A，观察focused来源为A，绑定使页面显示“焦点A”；请求B后来源及PaintPlan更新为B；clear后显示无焦点。点击handler只发布意图或调用焦点受控入口，普通焦点请求本身不得增加业务激活计数。
4. 普通内容更新与可用尺寸变化后，仍观察B的合法焦点；绘制读取不执行焦点写入。

优先使用现有编译任务compileCoreSceneScenario输出，新增focusInputScenario运行入口并加入coreSceneCheck/check；Java8核心classpath继续排除MC、Forge、LWJGL。不再添加无main的重复软件场景入口。

任务出口：真实状态 → 绑定 → PaintPlan闭环可观察，主路径实际运行通过。此时才继续选择反例，不能因测试后写撤销正确实现。

## 任务2：围堵资格与挂载生命周期反例

开发模式：test-driven-development。与任务1共享实现，不作为独立框架另行委派。

按当前代码和合同选择这些具体反例：

| 输入序列 | 预期消费者结果 |
| --- | --- |
| 焦点A → 隐藏/禁用祖先或关闭A的focusable → 恢复资格 | 清空后不自动复活；新有效请求才成功 |
| 焦点A → 生效的模态隔离A → 另操作关闭模态，无中间frame | 仍无焦点；后台无效请求被拒，模态内有效请求可成功 |
| 焦点A → 卸载并重挂载同一节点 → 使用旧句柄请求 | 旧句柄返回false；新句柄可以取得焦点 |
| 焦点A → move到有效父；随后move到禁用父再移回 | 有效move保持焦点；失活move清空后不恢复 |
| 焦点A → 请求另一runtime句柄或未声明可聚焦目标 | 返回false，A仍保持；不混入跨域访问 |
| 焦点A → 显示纯被动非模态内容 | A保持，不因绘制层更高抢焦点 |
| 重复请求A、重复clear、runtime关闭 | 无重复状态通知；最终无目标与挂载观察残留 |

断言独立来自合同，检查焦点来源、绑定后的可见结果和业务计数。外部executor沿现有队列执行夹具核验顺序与关闭，不引入线程等待框架。只在发现具体异步缺口时扩展相应反例。

反例直接通过如实记通过；失败先核对实际路径并修根因，随后回归受影响主路径与反例。无需为每个getter、常量或源码文本添加测试。

任务出口：必要反例、coreSceneCheck与既有pointer场景通过，核心无平台依赖。

## 任务3：GL及MC验收接入与交付

开发模式：自动验证真实GL路径；MC原生交互由用户实测。

涉及文件：既有ClientFontServicesScenario、CoreLoopScreen，必要验收说明附在代码与短交接笔记。

1. 在ClientFontServicesScenario既有真实context和FramebufferSceneRenderer链加入一个焦点请求及颜色/文本结果观测，复用现有场景，不创建新GL宿主。验证请求和清除后真实回放变化；说明代码注入不能替代OS原生输入实测。
2. 核心与GL自动场景通过后，在/qzcore页添加“选择A/选择B/清除”、当前焦点标识，以及隐藏、禁用、模态操作。A/B明确focusable，验收动作通过现有合法UP的handler发受控请求；不改变普通按钮的全局自动聚焦时机。
3. 保持父GuiScreen唯一原生输入消费者、已通过实机的pointer gate与关闭次序。焦点新增不接管键盘，不为验证焦点先做Tab或文本输入。
4. 运行完整build；独立审查新焦点权威、声明生效、挂载释放与handler写入边界，依据确定反例局部修正。纯重复证据不引发重复整套运行。
5. 自动检查与审查通过后按仓库约定提交生产增量，生成manualTestJar。部署前沿用后续用户授权；不自动启动两个实例。

执行命令沿用已配置Java与Gradle wrapper，参考：

~~~text
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --offline --no-configuration-cache --console=plain coreSceneCheck
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --offline --no-configuration-cache --console=plain clientFontServicesScenario
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --offline --no-configuration-cache --console=plain build manualTestJar
~~~

运行命令使用全局agent-environment.mjs加载环境，不使用PowerShell。offline沿用当前已完整缓存环境；依赖缺失时定位原错误，不把配置或启动失败称作行为反例。

## 用户实机验收与退出

1.7.10与GTNH两个实例均由用户启动，在/qzcore观察：选择A/B与清除时标识正确；更新字号/窗口尺寸保留合法焦点；隐藏/禁用当前目标时标识清空；恢复资格不自选；模态打开隔离后台，关闭后等待新选择；关屏重开不继承旧挂载焦点。确认此前指针操作仍正常。

自动验收、源码审查和打包完成后可交付待实机版本；只有收齐两实例反馈后才称本切片的MC闭环完成。出现实际异常时围绕该序列修正，已有通过路径保持不动。通过后再规划范围进入/初始与恢复选择、Tab导航及必要滚动，不提前铺设其框架。

## 自检与交接

本计划为同一条焦点业务链按可观察反馈递进；首个切片只承诺显式选择与生命周期，不把全部第6节合同声称为已完成。新权威归runtime、身份复用挂载token、资格复用interactive、观察复用响应式系统；宿主无第二目标，绘制只读。反例来自明确合同和现有调用边界；验收成功后停止扩展。

用户已明确“开始任务”，按本计划实施、核验、审查和本地提交打包；部署遵循后续授权，两个实例由用户启动。执行证据见核心执行记录与build/focus-review/，不把自动输入等同原生实机验证。
