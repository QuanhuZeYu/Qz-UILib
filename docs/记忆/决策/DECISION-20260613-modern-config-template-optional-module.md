# 决策：Modern Config 模板页采用可选模块主用与 Forge 回退

## 背景

`club.heiqi.config` 模块已经提供 JSON/YAML、嵌套 map/list、路径访问和可变配置能力，未来计划独立为新的 Mod。项目配置模板页希望利用该模块支持更复杂的配置结构，同时保持现有 Forge Configuration 配置页可用。

## 候选方案

1. 以配置文件存在与否决定使用现代配置页或 Forge 配置页。
2. 在同一配置页中同时挂载 config 模块与 Forge Configuration，并做双向适配。
3. 以运行时模块能力检测为准：检测到 config 模块时使用现代配置页，检测不到时回退 Forge 配置页。

## 最终选择

采用方案 3：UILib 通过运行时类加载检测 config 模块是否存在。存在时使用 `ModernConfigTemplateScreen`，不存在时继续使用现有 `ForgeConfigTemplateScreen`。

## 选择原因

- config 模块未来会独立成 Mod，配置页入口必须与其运行时存在性解耦。
- 检测配置文件不能代表模块能力，容易把“是否安装模块”和“是否创建配置文件”混为一谈。
- 双向适配会把复杂结构回退兼容压力放到 UILib 内部，增加模板页复杂度和长期维护成本。
- 不做迁移工具可以保持配置页职责清爽，避免引入数据迁移、版本冲突和不可逆写回问题。

## 影响范围

- `ModConfigGui` 只负责检测模块能力和选择页面入口。
- `ModernConfigTemplateScreen` 可以依赖 config 模块类型，但必须通过桥接边界避免 config 模块缺失时入口类加载失败。
- 现有 Forge 配置页继续作为回退，不需要理解 config 模块复杂结构。
- 接入方如果需要同一配置同时兼容 Forge 回退，应自行设计回退模型。

## 后续注意事项

- 复杂配置结构回退到 Forge 时，建议接入方把 JSON 字符串存入 Forge cfg 的字符串属性，由业务侧自行解析。
- 不在 UILib 内置 Forge→Config 迁移按钮或自动迁移逻辑。
- 现代配置模板页分批施工已全部完结（原施工规划 spec 已随完成清理）。
- 后续如果构建系统支持真正 optional dependency/source set 分离，可重新收紧桥接位置，但不改变运行时检测策略。
