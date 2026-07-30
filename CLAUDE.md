# 协作规范摘要

完整规则以 `AGENTS.md` 为准。

- scene core 保持平台无关；数据/paint 层不调用 GL，backend 恢复其触碰的 GL 状态。
- layout、paint、replay、裁剪和输入统一使用 UILib logical px；平台缩放只在 host 边界成对转换。
- UI 由 state/signal 驱动，输入 handler 不直接修改节点属性或树结构。
- 默认使用 OpenCode 内置 `build`，可按需委派一般智能体；身份和目标写入任务笔记即可，不要求状态机或阶段写回。
- 子 agent 默认只读；并行写盘只允许互不重叠的文件范围，同一文件同时只有一个写入者。
- 本机禁止 Gradle、编译、构建、测试、运行态和 verify；必要实证交 CI 或用户。
- 终端命令经 `python scripts/run-agent-command.py` 参数列表执行；文件操作优先专用工具。
- 修改协作规则、核心架构、公共 API/兼容承诺或发布策略前取得用户确认；未经明确要求不执行 commit、merge、push、tag 或 release。
