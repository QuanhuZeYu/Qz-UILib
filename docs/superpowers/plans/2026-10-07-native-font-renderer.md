# 字体、宿主合批与无平台测试机实施计划

状态：用户已采纳规格并要求继续。替换旧Java2D-only计划，运行反馈模式内联实施，不启动MC、不发布。工作区为既有refactor/uilib-core；基线完整build通过。

## 目标与合同

依据[字体规格](../specs/2026-10-07-uilib-font-renderer-spec.md)、[宿主优化规格](../specs/2026-10-07-mc-host-render-optimization-spec.md)。字体候选统一优先级；复杂Unicode/彩色emoji必需；组件决定排版策略；原版默认接管且不创建scene；有序合批、固定EBO、有界顶点提交。先在无MC环境使用真实生产流程，后短小MC部署。

## 任务一：依赖与平台无关合批闭环

- 核验ICU Unicode、LWJGL HarfBuzz/FreeType的实际版本、Java字节码和native加载；与目标lwjgl3ify核心版本对齐，区分Java8产物和Java17/21MC宿主。不把未经验证的native混入发布包。
- ui/render/batch新增有序图元/批次/容量切片，平台无关，资源身份与裁剪决定相邻合并；输出封闭载荷，固定四边形索引可复用。
- internal/devtools字体测试机及test直启集成驱动生产收集器，输出PNG与统计；覆盖A/B/A透明重叠、裁剪屏障、容量边界，不新增方法级JUnit。
- 验收：真实生产收集结果保持次序，合批与逐图元软件像素一致；完整build后提交。

## 任务二：真实字体服务与Unicode排版

- ui/scene/text扩展不可变TextStyle/TextLayout，保留既有简易调用；新增font服务及资源代际、UTF8输入/诊断、候选列表、实际face/glyph身份。
- ICU执行Bidi/字素/语言断行，HarfBuzz整形，FreeType单色及彩色光栅；候选匹配保留脚本上下文。字号em与实际行高，颜色仅绘制失效；GPU等待为空白。
- 内置Unifont、Noto脚本与彩色资源从官方来源取得、记录SHA/许可证；脚本覆盖和RGI集用真实字体检查，不以cmap覆盖代替序列整形。
- FontService实现SceneTextMeasurer，共享定位供scene和原版入口使用，组件策略作为入参。
- 验收：真实多语言PNG/来源映射/候选变更/最终行整形/UTF8异常，正式流程直启纳入check；完整build后提交。

## 任务三：按需字形与独立GL合批

- 稀疏实际glyph缓存、重复需求合并、可见优先、有界CPU与GPU预算、后台光栅及主线程发布。上传就绪只使相关paint失效。
- 生产host/client/backend消费同一有序批次；固定EBO、一个有界段一次顶点上传、状态范围内绑定。
- 无MC GL上下文探针：二维纹理与纹理数组保持相同次序对照；检查版本/层数/采样/显存，比较CPU/GPU与慢帧。依据实测选用，不预设通过。
- 验收：截图透明颜色/裁剪与软件一致、预算/资源换代无旧UV，记录实测而非只比较draw call；完整build后提交。

## 任务四：scene与MC适配

- MinecraftSceneHost改正式字体/backend，原生framebuffer尺寸/鼠标/裁剪一致；CoreLoopScreen仅消费正式能力。
- Scene滚动正式入口按内容range钳制，缩内容/重排更新；末尾反向立即移动，用真实scene闭环覆盖。
- 恢复正式Mixin接管标准FontRenderer的度量/绘制/截取/换行/高度和设置；默认开启。即时调用与已知Angelica宿主延后范围区分，保留原版坐标/返回/尾状态语义，不从旧仓整树恢复。
- 验收：无平台使用原版方法等价输入的适配场景，完整build与manualTestJar；实机注入只能最后目标实例验收。

## 任务五：证据与交付

- 独立审查共享服务边界、完整Unicode、请求/资源生命周期、有序合批、原版适配与动态坐标；解决重要问题再提交。
- 无平台生成图像、异步GPU计时和机器可读统计，性能按已审定门槛比较；原版同机基线留到MC采集，不伪造相对原版通过。
- 部署用户已授权实例，每次少量简单操作；不启动游戏，第一批文字与GUI Scale，后续复杂脚本/原版宿主及性能。

生产代码每个可交付增量完整build通过再提交。无issue的增量说明理由，不push/tag/release。保留未跟踪字体资源，经过来源与完整构建后才纳入字体增量。
