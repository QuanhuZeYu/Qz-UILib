# 规格文档

本目录用于存放 scene 栈的 API 规范、宿主语义、物品渲染合同与视觉规格。每份文件自己的状态标记决定它是现行规范还是历史记录。

这里的文档用于固化局部页面或专项能力的预期行为，不等同于通用接入手册；对外开发使用方式仍以 `docs/使用文档/` 为准。

## 现行规范

- `scene基础API规范.md`：Scene Primitive API 规范（primitive/wrapper 分层、Props/Result 形态、I1-I11 不变量）。
- `UI投影宿主语义.md`：统一 content/projection/host/input 与 state/intent 高层语义（scene 输入层语义母本）。
- `物品视觉渲染接缝.md`：Breaking major 的 snapshot-only ItemStack icon 合同（完整原版委托 + RenderSemantics + 分级）。
- `数据表可编辑单元格视觉规格.md`：DataTable 可编辑列视觉方案（well/inset 输入槽，色值已落 `SceneChromeTokens` token）。
- `材质配置动效.md`：Material 3 配置页与最小 Motion 产品规格。
- `现代配置实现蓝图.md`：现行 ModernConfig 三态与数据事务蓝图。
- `网络层方案.md`：网络层（内容语义 Channel + Fetch + Stream + Store + Vanilla mixin 适配器 + Forge 兼容适配器）方案。
- `网络编解码线格式.md`：网络层内容 envelope、可选 POJO codec 与分片格式。
- `网络原版Mixin注入策略.md`：vanilla custom payload early mixin 注入点与传输策略。

## 历史 / 演进记录

- `物品渲染上层替换大纲.md`：首版替换路线演进记录（2D 自绘分支已被完整原版委托取代，见其头部注记）。
- `现代配置UI设计复审.md`：配置页设计审视记录（S1 字号/M1 读数已闭环，见其头部注记）。
- `网络自检.md`：网络自检场景规格（入口已清空，保留为未来 RemoteNet 分组恢复的规格母本）。

## 已移除（git 历史保留）

2026-08-18 文档清理移除：`qzuilib测试页视觉矩阵规划.md`（旧 HTML-like 测试页已删，UiTestMatrixRegistry 硬引用不存在）、`scene浮层P0施工清单.md`（P0 已落地）、`scene装饰配色规格.md`（Slate/Blue 系色值已被 Material 3 主题取代）、`scene材质动效演进.md`（实施顺序已全部落地）、`现代配置UI设计.md`（方案已实现并被复审闭环）、`物品渲染简化方案大纲.md`（作者自标作废）。
