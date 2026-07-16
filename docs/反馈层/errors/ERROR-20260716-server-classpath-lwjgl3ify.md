# 标准服务端类路径混入 LWJGL3ify

## 错误现象

GitHub Actions run [`29509328749`](https://github.com/QuanhuZeYu/Qz-UILib/actions/runs/29509328749) 的编译和 2396 项测试均通过，但 dedicated server gate 的 `runServer` 在启动前被 `Lwjgl3ifyRelauncherTweaker` 主动拒绝服务端环境，导致 4.6.0 发布继续阻断。

## 触发场景

项目以 `devOnlyNonPublishable` 引入 `com.github.GTNewHorizons:lwjgl3ify`，供客户端开发运行与可选增强输入链路使用。GTNHGradle / RetroFuturaGradle 的标准 `RunMinecraftTask` 从通用 `runtimeClasspath` 组装最终运行类路径，因此标准 `runServer` 同样得到 LWJGL3ify 自身 jar，并在 CI dedicated smoke 中触发仅客户端 relauncher。

## 根本原因

「不进入发布 POM」与「不进入开发期 server classpath」是两个独立边界。`devOnlyNonPublishable` 已守住发布依赖，但仍通过 `runtimeClasspath` 进入标准 server 运行任务；此前没有对最终 task classpath 建立按组件身份的 side 隔离，也没有同时守卫 client 必须保留该 artifact 的双向门禁。

## 修复方案

- 在插件链末尾的 `addon.late.gradle` 使用 `runtimeClasspath.incoming.artifactView`，按 `ModuleComponentIdentifier` 的 group/module 精确选择 LWJGL3ify 自身 artifact。
- 仅对 `runServer`、`runVanillaServer`、`runObfServer` 三个公开 `RunMinecraftTask` 惰性调用 `setClasspath(classpath.minus(...))`；不改变依赖声明、发布 POM及传递组件，也不触碰显式依赖 LWJGL3ify/RFB bouncer 的 modern server 任务。
- `verifyRunClasspathIsolation` 执行时要求坐标恰好选中一个 artifact，断言三项标准 server 既不含选中文件也无同名 jar 泄漏，并反向断言 `runClient`、`runClient21` 仍包含选中文件。门禁接入 `check` 与标准 `runServer`。

## 预防措施

- side 隔离应发生在最终运行任务 classpath，不以 `client-only` 跳过 dedicated server CI，也不通过全局删依赖掩盖边界问题。
- 删除依据必须是公开组件坐标视图；文件名只能作为 fail-closed 泄漏诊断，禁止参与删除。
- 每次调整运行依赖后同时验证标准 server 排除、client 保留、modern server 未被纳入过滤，并核对本地发布 POM 无新增依赖。
- 本地 classpath 门禁通过不等于 server 已成功启动；只有包含该修复的最终 `4.0` tip 对应新 CI 全绿后，才可解除 `4.6.0` tag 阻断。
