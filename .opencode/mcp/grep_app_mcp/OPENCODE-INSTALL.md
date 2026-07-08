# OPENCODE 安装说明 — grep_app_mcp

本目录是本机 opencode 运行所依赖的 `grep_app` MCP 服务的安装位置。

## 来源

- 上游仓库：https://github.com/ai-tools-all/grep_app_mcp.git
- 原始安装方式：从上游仓库复制源码到本目录，再执行依赖安装与构建。

## 当前用途

- opencode 配置（`.opencode/opencode.json` 中的 `grep_app` 项）以本目录下的
  `dist/server-stdio.js` 作为 stdio 启动入口：
  - 启动命令：`node <本目录>/dist/server-stdio.js`
- `dist/` 由 TypeScript 编译产物构成，`node_modules/` 为运行期依赖，二者均为
  本机安装产物，正常情况下不进主仓库。

## 重建步骤

若本目录只有本说明文件，先在临时目录拉取上游源码，再复制到本目录，复制时不要带入
上游 `.git` 目录：

```pwsh
git clone https://github.com/ai-tools-all/grep_app_mcp.git $env:TEMP\grep_app_mcp
Copy-Item -Recurse -Force $env:TEMP\grep_app_mcp\* .
Remove-Item -Recurse -Force .\.git -ErrorAction SilentlyContinue
```

若 `dist/` 或 `node_modules/` 缺失，在本目录下执行：

```pwsh
npm install
npm run build
```

构建后确认 `dist/server-stdio.js` 存在即可被 opencode 加载。

## 维护约束

- **禁止重新引入嵌套 `.git`**：更新上游代码可以先 clone 到临时目录，再复制
  文件到本目录；复制后必须删除 `.git`，避免外层 git 把本目录误判为
  submodule / gitlink。
- 本目录整体由本地 `.opencode/.gitignore` 忽略，仅保留本说明文件可进入版本
  管理；`node_modules/`、`dist/`、`package.json` 等均按本机安装产物处理。
