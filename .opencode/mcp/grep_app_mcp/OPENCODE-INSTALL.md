# OPENCODE 安装说明 — grep_app_mcp

本目录是本机 opencode 运行所依赖的 `grep_app` MCP 服务的安装产物。

## 来源

- 上游仓库：https://github.com/ai-tools-all/grep_app_mcp.git
- 原始安装方式：`git clone` 上游仓库到本目录。

## 当前用途

- opencode 配置（`.opencode/opencode.json` 中的 `grep_app` 项）以本目录下的
  `dist/server-stdio.js` 作为 stdio 启动入口：
  - 启动命令：`node <本目录>/dist/server-stdio.js`
- `dist/` 由 TypeScript 编译产物构成，`node_modules/` 为运行期依赖，二者均为
  本机安装产物，**不要删除**。

## 重建步骤

若 `dist/` 或 `node_modules/` 缺失，在本目录下执行：

```pwsh
npm install
npm run build
```

构建后确认 `dist/server-stdio.js` 存在即可被 opencode 加载。

## 维护约束

- **禁止重新引入嵌套 `.git`**：本目录已从外层仓库角度去嵌套化，不得再次
  执行 `git clone` 等会生成 `.git` 的操作；更新上游代码请以复制文件或
  `npm` 重建方式处理，避免重新出现嵌套 Git 仓库导致外层 git 把本目录误判
  为 submodule / gitlink。
- 本目录整体由外层 `.opencode/.gitignore` 忽略，仅保留本说明文件纳入版本
  管理；`node_modules/`、`dist/`、`package.json` 等均为本机产物，不进仓库。
