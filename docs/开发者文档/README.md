# 开发者文档

本目录面向框架维护者和 AI 协作 Agent，记录内部架构方向、审查结论、错误教训和规格定稿。

> 本区所有文档视为内部开发资料，不面向外部接入开发者。对外接入说明见 [使用文档](../使用文档/README.md)。

## 目录结构

| 文件/目录 | 说明 |
|-----------|------|
| [项目建议.md](项目建议.md) | 协作阶段的技术方向与取舍原则 |
| [开放化调整.md](开放化调整.md) | 从内部验证转向开发者入口的开放化方案 |
| [Minecraft原版输入链路.md](Minecraft原版输入链路.md) | 原版 GuiScreen/GuiContainer 键鼠分发与 HUD 抢占内部架构分析 |
| [reviews/](reviews/README.md) | 审查报告索引与详细报告 |
| [errors/](errors/README.md) | 错误记录索引与详细问题分析 |
| [specs/](specs/README.md) | 示例页专属规格与专项方案定稿 |

## 文档写入规则

- 审查结论 → `reviews/REVIEW-YYYYMMDD-主题.md`，并在 `reviews/README.md` 添加索引条目
- 可复现错误 → `errors/ERROR-YYYYMMDD-简述.md`，并在 `errors/README.md` 添加索引条目
- 示例页规格 → `specs/` 下按功能命名
- 技术方向变更 → 更新 `项目建议.md`
- 开放化边界变更 → 更新 `开放化调整.md`

## 与其他文档区的关系

- 本区记录"为什么这样做"和"踩过什么坑"
- `使用文档/` 记录"怎么用"
- `AI记忆文档.md` 只保留跨任务长期稳定的导航指针
- 根目录 `README.md` 只面向首次访问者
