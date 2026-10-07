# 字体与合批实施恢复点

用户暂停后已明确要求“继续任务”，并补充宿主边界优先LWJGL而非MC包装。已恢复实际施工；运行时update_goal(resume)拒绝“the model cannot resume a paused goal; the user must resume it”，因此自动目标仍paused/disarmed（revision2），不另建目标绕过；实际工作依用户最新授权继续。既有refactor/uilib-core内实施，无MC启动/发布。

当前基线为350a7c90（原生scene/共享client owner），之前fa6e60b2与5c7a1327。第四增量已实施：多消费者PresentationEpoch共享需求并集与总上传预算；生产owner在Display.update调用前结束呈现帧；AttributedText/token双侧来源/整簇样式、logicalEm与rasterEm分离；原生COPY粗体/基线斜体；GL回放采样比例、实际颜色及装饰。新增LWJGL上下文捕获与透视采样量化目录已完成独立审查，偏轴透视Jacobian数值微分核验通过。当前冻结增量完整build及全部五个正式设备场景已通过，可保存本地增量提交，不能外推尚未实现的原版接管。

本轮独立Java8/native/GL证据：epoch partial跨consumer跨帧最终ready、每epoch1024bytes/1call上限、已驻留需求拒绝不能绕pin；原生样式含sub1em和彩色strike包络与premult像素；属性文本3953RGI/371VS15回归、密度1/1.5/2/3/4保持逻辑定位。独立review修复alias缓存重绑异常、控制诊断吞token、空行活动字号。富文本GL与独立软件参考原始最大通道差1/1/128/2；2倍采样精确quad边界137channels另计，其余最大4/255，clip外检查无排除。父正式执行build + presentationEpochScenario + clientFontServicesScenario + glyphPipelineScenario + attributedGlyphGlScenario + fontContextGlScenario，最终BUILD SUCCESSFUL in 40s；Checkstyle违规及原生任务字体/输出argv混淆均已修正。正式设备运行Java8 1.8.0_492/LWJGL3.4.2/AMD610M，context core3.3与compat4.6通过。原版默认替代、trim/混淆动画、TESR/Iris/列表生产接管仍未完成，不部署冒充全接管。

首增量：生产OrderedQuadCollector/Batch（相邻状态、有界段、禁止回调重入、double裁剪、固定索引模板）；TextInput（UTF8 maximal subpart/UTF16来源）；NativeFontService/Face/GlyphBitmap（Java8、上下文HB整形、FT实际em归一、灰度/mono/BGRA premultRGBA、线程专属生命周期）。

正式实例patch确认core/FT/HB/native全3.4.2，旧缓存snapshot不是当前宿主。fontBindings仅编译，fontNativeRuntime供独立测试；发布不打入LWJGL，依赖目标LWJGL3ify宿主提供同版。ICU76.1私有shadow并随附许可。不能最小化反射类：曾真实发布包缺BreakIteratorFactory，关闭minimize后隔离Java8 Bidi/ZWJ字素/泰文字典资源通过。

无平台测试机：orderedBatchScenario逐像素差0（实际委托透明draw、容量17、有序A/B/A），nativeFontScenario真实多语/彩色emoji/输入来源/40007units长上下文/.5em/内存生命周期；nativeFontVisualScenario产出build/font-machine/native/native-fonts.png；packagedUnicodeScenario隔离加载真正发布包。首增量5c7a1327完整build通过；第二增量最终冻结后已在父会话执行build+glBatchScenario+glyphPipelineScenario，完整成功（33秒）。高层/GL/滚动及新增字形管线独立review均关闭实际发现；本次共享定位与GPU管线增量可本地提交，MC接管仍未实施。

两轮独立审查关闭全部发现：合批屏障证据、clip精度、回调重入；原生CharSequence64KiB栈风险改direct buffer并及时释放，FT小于1pt提升按真实尺寸归一。没有MC性能证据。独立正式GLFW3.4.2已在AMD610M建立GL3.2Core，2D/array相对软件最大通道差1/255、两路线完全相同，固定EBO一次/容量分段上传真实验证。Compatibility alpha test与unit0外部sampler实际干扰均修复并独立复核退出恢复（含异常）。初步wall-time波动不足裁定数组胜出。

正在做：共享Unicode/Bidi排版与真实默认15候选、10印度脚本资源、Emoji17完整RGI3953及标准VS15全部371已独立运行；FontSource懒加载实际字节代际，Glyph不可变source/size/glyphId/来源/位置及真实FT墨迹保守界。独立审查的WJ断点、Arabic tatweel连接断裂、损坏资源原因丢失、长文二次整形与后续GL/LB12a回归已全部修正并独立复核。8192字符窄宽排版原20.43秒，最终同一独立probe约59.76毫秒；非MC性能门槛证据。最后FontTextScenario及独立135最终Bidi行、faceLimit1淘汰、来源/cache代际、296字形实际墨迹界复测全部通过。

滚动真实范围已Runtime管理，不由业务Signal累计像素；Geometry发布offset/maximum，末尾逆向即移动，内容缩短和窗口变更夹取；独立default/custom控件含padding、同帧输入/旧plan/卸载重挂载实际通过，完整build待高层与GPU施工收尾。GlyphKey/AsyncGlyphRasterizer/GlGlyphAtlas/GlPaintPlanRenderer已实施并独立review关闭。正式glyphPipelineScenario真实AMD610M/Java8/GL3.2：2D/array首次blank、分帧partial、最终多语+emoji与软件原nativebitmap最大通道差2/255，两条路线字节完全相同。按CPU/GPUbudget和visible真实inkbounds请求，clip外10000字形不启动worker；worker-ownednative、去重/pressure/旧source、uploadState与delegate异常恢复实际通过。UploadBudget整行最小单位，不承诺低于一行的正bytes会累计；CPU bitmap budget限持有cache，最多额外一份inflight，不承诺限制native单次瞬时分配。GPU当帧pin，host已绑定纹理不删除。正式设备任务不加入无显示check。

下一步仍需共享服务scene和原版默认适配、原生framebuffer输入/坐标、原版格式及Angelica/TESR尾状态合同、系统字体自动能力探针、省略/软连字符、MC实测并部署短包。第二增量fa6e60b2已本地提交，tree当时干净。第三增量已接入生产ClientFontServices/MinecraftFontServices及自有scene：原生display像素/Mouse事件/outer scissor/viewport、代际资源包、screen仅关quad、主线程退出关闭共享owner；client-only Mixin实际refmap已确认shutdownMinecraftApplet→func_71405_e。父build+clientFontServicesScenario成功（45秒），独立Java8/AMD GL3.2 review无确定剩余缺陷。真实MC资源回调/硬件输入/注入退出仍未启动验证。原版字体标准入口替代尚未实现，不能把scene接入宣称全接管；不部署冒充接管完成。旧Java2D-only计划已替换。

无对应issue：本任务是用户直接授权的架构重构切片，不编造编号。
