# 错误记录：CodeGraph MCP 启动失败

## 错误现象

- opencode 中 `codegraph` MCP 看起来没有成功启动
- 直接用非 shell 方式启动 `npx` 或 `codegraph-mcp` 会出现 `ENOENT` / `EINVAL`
- 通过 `cmd.exe` 包装后如果继续传 `--mcp`，CodeGraph 报错：`the argument '--mcp' cannot be used multiple times`

## 触发场景

- Windows 环境下，在 opencode 项目配置或全局配置中直接写：`npx -y @astudioplus/codegraph-mcp --mcp`
- 或直接写：`codegraph-mcp --mcp`
- opencode 以非 shell 子进程方式启动 MCP 命令时，不能像 PowerShell 一样自动解析 npm 的 `.cmd` shim

## 根本原因

- Windows 上 `npx` 和 `codegraph-mcp` 通常是 `.cmd` shim，非 shell 启动无法直接执行，必须通过 `cmd.exe /d /s /c` 包装
- `@astudioplus/codegraph-mcp` 的 npm 启动器会自行进入 MCP 模式，再显式追加 `--mcp` 会让底层 CodeGraph 二进制收到重复 `--mcp` 参数
- 初始配置只用 PowerShell 手动执行验证，没有模拟 opencode 的非 shell MCP 启动方式，因此漏掉了 `.cmd` shim 问题

## 修复方案

- 项目级 `.opencode/opencode.json` 使用：`cmd.exe /d /s /c "npx -y @astudioplus/codegraph-mcp --graph-only ..."`
- 个人全局 opencode 配置使用：`cmd.exe /d /s /c "codegraph-mcp --graph-only ..."`
- 移除显式 `--mcp`
- 默认增加 `--graph-only`，优先保证结构图谱、调用链、依赖图和影响分析工具稳定启动

## 预防措施

- Windows 下新增 opencode MCP 配置时，不只在 PowerShell 中手动跑命令，还要用 Node `spawn` 或等效方式模拟非 shell 启动
- 对 npm 包型 MCP，先确认包启动器是否已经封装 MCP 模式，再决定是否传 `--mcp`
- CodeGraph MCP 至少验证 `initialize` 与 `tools/list`，确认工具列表可返回后再认为接入成功
- 配置变更后必须重启 opencode，运行中会话不会热加载新 MCP 配置
