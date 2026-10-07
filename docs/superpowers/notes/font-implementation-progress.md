# 字体与合批实施恢复点

用户已采纳并授权继续。既有refactor/uilib-core内实施，无MC启动/发布。

首增量：生产OrderedQuadCollector/Batch（相邻状态、有界段、禁止回调重入、double裁剪、固定索引模板）；TextInput（UTF8 maximal subpart/UTF16来源）；NativeFontService/Face/GlyphBitmap（Java8、上下文HB整形、FT实际em归一、灰度/mono/BGRA premultRGBA、线程专属生命周期）。

正式实例patch确认core/FT/HB/native全3.4.2，旧缓存snapshot不是当前宿主。fontBindings仅编译，fontNativeRuntime供独立测试；发布不打入LWJGL，依赖目标LWJGL3ify宿主提供同版。ICU76.1私有shadow并随附许可。不能最小化反射类：曾真实发布包缺BreakIteratorFactory，关闭minimize后隔离Java8 Bidi/ZWJ字素/泰文字典资源通过。

无平台测试机：orderedBatchScenario逐像素差0（实际委托透明draw、容量17、有序A/B/A），nativeFontScenario真实多语/彩色emoji/输入来源/40007units长上下文/.5em/内存生命周期；nativeFontVisualScenario产出build/font-machine/native/native-fonts.png；packagedUnicodeScenario隔离加载真正发布包。最新完整build通过。

两轮独立审查关闭全部发现：合批屏障证据、clip精度、回调重入；原生CharSequence64KiB栈风险改direct buffer并及时释放，FT小于1pt提升按真实尺寸归一。没有MC/GL性能证据。

下一步：共享Unicode排版/统一候选（实际脚本能力而非cmap）、TextLayout/PaintCommand不可变glyph载荷、复杂文字最终行边界；补齐其余印度脚本和选定完整RGI覆盖，系统字体入口。随后字形按需队列/后台光栅、独立GL纹理数组与二维纹理对照、scene和原版适配、原生坐标/滚动范围。当前MC仍使用原版字体，不部署本增量冒充接管完成。旧Java2D-only计划已替换。

无对应issue：本任务是用户直接授权的架构重构切片，不编造编号。
