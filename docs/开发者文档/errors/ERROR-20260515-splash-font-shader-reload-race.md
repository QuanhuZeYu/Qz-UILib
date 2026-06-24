# 2026-05-15 Splash 字体 shader 重载竞态

## 错误现象

- 客户端启动阶段崩溃，崩溃报告显示 `SplashProgress` 的 Splash 线程抛出异常。
- 根因栈为 `club.heiqi.uilib.gl.shader.ShaderProgramSupport.linkAndValidateProgram` 抛出 `IllegalStateException: 字体着色器链接失败`。
- 触发路径是 Angelica 在 SplashProgress 内存条中调用原版 `FontRenderer.drawString`，被 UILib 的 `MixinFontRenderer` 接管后进入 `DefaultFontRendererAdapter`。

## 触发场景

- `FontConfig.replaceOrigin=true`。
- Splash 加载线程正在绘制文字，同时客户端主线程执行 `FontRenderer.onResourceManagerReload`，触发 UILib 字体系统 `reload()`。
- `reload()` 会关闭并重建 shader、批渲染器、字符页和调度器；Splash 线程可能同时在 `drawString` 中使用同一套运行时对象。

## 根本原因

- 字体资源重载与 `drawString` 没有共享同一把运行时锁，导致 Splash 线程和客户端主线程并发访问字体 GL 资源。
- shader 初始化失败后 `FontShaderProgram.initialized` 会停留在已初始化状态，后续调用可能继续使用半初始化状态。
- Mixin 接管路径没有保护运行时异常，字体管线一次失败会直接把原版启动线程带崩。
- 更深层的数据一致性问题是异步字形生成链路缺少运行时版本隔离：旧 worker 可能在资源重载后迟到，把旧字体排序、旧度量或旧图像结果写入新运行时的待上传队列和字符页。
- 字符缓存键、字体匹配缓存和宽度缓存若只按 `(codepoint, FontType)` 分桶，会把字体排序变化前后的同码点视为同一语义，导致宽度与实际绘制字体不一致，或新页混入旧排序下生成的字形。
- 运行时版本隔离只能拒绝旧结果，不能自动恢复旧 generation 中仍在飞行的字符需求；如果 reload 发生在 `tryMarkGenerating()` 之后、worker 结果回写或上传刷新之前，该字符可能只被旧 generation 记录为 `GENERATING` / `UPLOAD_PENDING`，旧结果随后被丢弃，新 generation 又没有收到重新生成请求，
  表现为重载后有时完全不出字或部分字符长期缺失。
- 仅用 `(runtimeVersion, codepoint, FontType)` 判断当前结果仍不够：同一 runtime 内若字符被取消后又重新提交，旧 pending 与新 pending 具有相同键；如果刷新上传只检查当前状态是否为 `UPLOAD_PENDING`，旧 pending 可能被误当作新请求写入字符页，导致字符页槽位、ready 状态和实际图像不一致。
- 每帧文本绘制都会重新请求缺失字形，但这不能抵消高频 reload：如果资源重载或配置装载在短时间内反复触发，每次 reload 都会重建字符页、取消 worker 并清空 pending，字形生成和上传会被持续饿死，表现为“请求一直存在，但字符始终没有稳定窗口进入 ready”。

## 修复方案

- `DefaultFontRendererAdapter` 在进入字体运行时绘制和测量时同步锁定 `FontService` 实例，与 `FontService.reload()` 的同步边界对齐。
- `FontShaderProgram.initialize()` 在失败时关闭已创建资源并把初始化状态回滚为 false。
- `MixinFontRenderer` 对 UILib 字体管线异常做单次日志记录并放行原版 `FontRenderer`，避免启动线程因自定义字体失败直接崩溃。
- `GlyphGenerationTask`、`GlyphGenerationResult`、`PendingGlyphUpload` 与 `GlyphCacheKey` 携带 runtimeVersion；调度器提交、worker 回调、结果入队与上传前都校验版本，旧运行时结果直接丢弃。
- `FontMatcher` 与 `TextLayoutService` 的缓存键纳入 runtimeVersion，字体排序变化后同一 `(codepoint, FontType)` 会重新匹配和重新测量，不复用旧排序语义。
- `FontCatalog` 改为不可变快照替换，避免 reload 原地清空/追加字体列表时与旧 worker 遍历同一列表发生并发污染。
- `GlyphPageManager` 维护当前 runtime 内已请求但尚未上传完成的可恢复字符集合；`GlyphGenerationDispatcher` 跟踪 in-flight 任务，在 reset、迟到丢弃或提交拒绝时显式释放生成中状态；`FontService.reload()` 在清空旧页前快照这些需求，并在新 runtime 初始化后按新版本重新提交，确保旧
  generation 被取消时字符不会静默丢失。
- 每次 `tryMarkGenerating()` 分配单调 `generationId`，并让 `GlyphGenerationTask`、`GlyphGenerationResult` 与 `PendingGlyphUpload` 携带该编号；`queueUpload()` 只接受仍处于 `GENERATING` 且编号匹配的结果，`flushPendingUploads()` 只上传仍处于
  `UPLOAD_PENDING` 且编号匹配的记录，避免同码点旧 pending 污染新字符页。
- 字体 reload 入口增加请求合并：首个 reload 立即执行，短时间内重复 reload 只记录为 pending；pending 在安静窗口后执行，若 reload 持续抖动则按最大延迟强制执行一次。这样快速连续 reload 不会每帧摧毁字符页，字形生成和上传能获得稳定完成窗口。
- Splash 阶段保留自定义字体接管和统一批渲染路径，不再为非客户端主线程单独切换 immediate path；同时在 `FontRenderer.onResourceManagerReload` 入口检测 SplashProgress，Splash 绘制期间跳过 UILib 字体资源重载请求。

## 预防措施

- 不要用“非客户端主线程回落原版”作为默认修复，因为 SplashProgress 里仍有自定义字体渲染需求。
- 不要为 Splash/非客户端主线程维护第二套 immediate 字体渲染路径；Splash 字体绘制应继续走批渲染路径，差异只体现在运行时锁、异常保护和 Splash 阶段跳过资源重载。
- 字体 GL 资源、批渲染器、字符页和 shader 的重载/绘制必须处于同一运行时互斥边界内。
- 字体资源重载不仅要重建 GL 对象，还要让异步生成任务、待上传结果、字符缓存、字体匹配缓存和宽度缓存共同跨越运行时版本边界；连续多次 reload 时，旧 generation 的失败状态和迟到结果都不能写入当前 generation。
- runtimeVersion 隔离必须配套 generation handoff：旧 generation 中 `GENERATING` / `UPLOAD_PENDING` 的字符需求要么完成上传，要么被显式取消并迁移到新 generation；不能只丢弃旧 worker 结果，否则会形成“没有 ready glyph，也没有新任务”的空洞。
- 字符页状态机必须按“请求编号”而不只是按字符键验证结果归属：同一字符的旧 pending、新 pending、取消和重提交可能同处一个 runtime，缺少 per-request token 会把错误图像写成当前 ready glyph。
- 对资源重载、配置加载、Splash 绘制等早期链路，不能假设“下一帧还会提交字符”就足够安全；如果 reload 本身也按帧级频率发生，必须对 reload 做 debounce/coalesce，否则会形成生成链路饥饿。
- 字符页首次 `glTexImage2D` 不能依赖驱动对未初始化纹理内容的默认值；若后续会生成 mipmap，整张纹理必须先显式填充透明像素，否则新上传字形周围会把旧显存内容混进低层 mip，表现为纯黑块或脏边。
- 早期启动线程、Splash 线程、资源重载线程相关问题需要优先查 `run/client/logs/fml-client-latest.log` 中 `Splash thread Exception` 的上下文，而不是只看最终 crash report 的 `SplashProgress.finish` 包装异常。
