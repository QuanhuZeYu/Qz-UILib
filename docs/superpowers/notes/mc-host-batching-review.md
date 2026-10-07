# MC宿主合批调查记录

本轮为源码调查和规格提案，未运行旧版本MC/GPU性能基准。主仓旧源码只读，新规格位于重构worktree。

## 已读事实

| 证据 | 事实及新设计含义 |
| --- | --- |
| [GlyphBatchCollector:165](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/render/GlyphBatchCollector.java#L165)及125—136、194—210 | 按normal/bold和页存一个batch，页首次激活登记，后续同页继续加入；提交按首次激活页。不是保持所有图元的全局顺序 |
| [FontBatchRenderer:469](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/render/FontBatchRenderer.java#L469) | flush先mark背景，再按页提交字形，再装饰；记录draw、绑定数和末色/末纹理。矩阵可显式传入或使用内部缓存 |
| [FontBatchRenderer:705](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/render/FontBatchRenderer.java#L705) | 每页将CPU顶点/索引数组拷贝到复用direct buffer |
| [FontRenderTool:71](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/render/FontRenderTool.java#L71)及104—119 | 每batch上传顶点和索引再draw；已有GPU容量复用，容量足够用SubData，不是每批总重新分配；每批末尾解绑VAO/VBO/EBO |
| [GlyphRenderBatch:115](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/render/GlyphRenderBatch.java#L115) | 每quad重写同样拓扑的索引；可比较固定EBO和一次顶点提交 |
| [DefaultFontRendererAdapter:120](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java#L120)及1640—1653 | 已有可控deferred scope，最外层结束flush、恢复状态；普通调用即时。不是新方案首次引入跨调用合批 |
| [UiRenderContext:216](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java#L216) | 已接入begin/flush/end延后文本范围 |
| [FontRendererFallbackInvoker:100](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/font/FontRendererFallbackInvoker.java#L100)及257—272 | 普通调用同步、已知TESR范围捕获延后，原版入口补alpha-test/末色/末纹理等尾状态 |
| [TesrTextReplayCoordinator:15](D:/Code/MC/Qz-UILib/src/main/java/club/heiqi/uilib/internal/font/tesr/TesrTextReplayCoordinator.java#L15) | 旧注释与代码为Angelica几何延后窗口设专用协调；调用顺序不能代替实际宿主提交顺序。具体版本条件待目标环境验证 |

旧无平台收集器虽然不调用GL，但直接使用FontBatchRenderer静态几何方法；新实现应把生产几何/合批实质放入平台无关模块，GPU实现作为消费者，不依赖重复软件算法。

## 可复现的结构反例

输入页顺序A(a1红)→B(b绿)→A(a2蓝)，全部同位置、alpha=0.5。按collector的页激活模型分组后为a1→a2→b。

用Node按src-over、黑底计算：原序RGB=[0.125,0.25,0.5]，页分组RGB=[0.125,0.5,0.25]。这是结构反例验算，证明页重排存在透明重叠风险；不是运行旧生产代码，不据此声称某MC页面已复现bug。复杂文字标记/斜体/阴影等为后续正式图像样例。

## 提案与下一步

权威提案见[宿主优化规格](../specs/2026-10-07-mc-host-render-optimization-spec.md)第7节：有序流、二维纹理基线与纹理数组对照、固定EBO及提交段一次顶点上传、自有/明确宿主提交范围。未有性能结果，不宣称新方案已经优于旧版。

字体Q10已归组件策略；原版不创建scene节点，和scene共用排版/资源/绘制载荷/backend；默认接管原版已裁定。实施仍待宿主优化稿审阅。
