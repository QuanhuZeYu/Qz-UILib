# Elytra conventions 离线构建仍读取 GitHub manifest

## 现象与根因

LaTeX 规划增量执行 `gradlew.bat build --offline --console=plain` 时，在 GTNH convention 配置阶段报 `Failed to load the manifest from Github`，尚未编译。实际请求来自 Elytra conventions v1.1.2 的 ManifestUtils：直接 HttpClient 请求 GitHub，未使用 Gradle 的离线下载设施，网络 SSL 握手中止。

目标仓已有 `build/elytra_conventions/2.9.0-beta-2.json`，但该插件将 manifestNoCache 直接传给 allowFromCaching，默认 false 反而忽略缓存。缓存 SHA-256 为 `a4cae13c9b5d5a43a87ceab34cad1b71524008a09e51f0af17ed6d824d28ad89`，与当时 Qz-Miner 同名既有缓存字节相同；恢复过程没有下载或修改 manifest。

## 本次恢复

通过临时 Gradle init script，在 Elytra 插件应用后调用既有扩展 setter。实际 Gradle 装饰 API 使用 setter，不能对 getter 的 Boolean 再调用 `.set(true)`。

```groovy
gradle.beforeProject { project ->
    project.pluginManager.withPlugin("com.github.ElytraServers.elytra-conventions") {
        project.extensions.getByName("elytraModpackVersion").setManifestNoCache(true)
    }
}
```

完整 build 原参数追加 `--init-script D:/Code/MC/Qz工作站/temp/elytra_cached_manifest.init.gradle` 后成功，未改仓库构建版本或依赖。此恢复依赖本机存在对应的真实缓存；其他版本或无缓存环境不能照搬成“离线必定可用”。配置 help 和完整 build 分开验证，前者成功不能代替编译测试通过。

实际日志：工作站 `temp/manifest_help_diag.log`、`temp/manifest_cached_help_diag.log`，目标仓 `build/reports/latex-plan-completion/build.log`。构建统计由 Python 读取实际 JUnit XML，记录在同目录 build-verification.json。
