# 核心闭环人工测试部署

- 源码提交 dcf4d0e7（核心9b603d3e）；构建任务 build manualTestJar 32s通过。产物版本为构建时9b603d3e-dirty，与提交后源码对应。
- 目标实例：C:/Users/16249/Desktop/MC/PrismLauncher-Windows-MinGW-w64-Portable-11.0.3/instances/GT_New_Horizons_2.9.0-beta-3_uilib测试。
- 部署前未发现UILib/Architect jar，无需覆盖或移除原文件。
- 发布核心与人工附加包分别部署，附加包已检查入口/面板存在、无重复核心类；javap确认MC GUI方法已重混淆。

- C:/Users/16249/Desktop/MC/PrismLauncher-Windows-MinGW-w64-Portable-11.0.3/instances/GT_New_Horizons_2.9.0-beta-3_uilib测试/.minecraft/mods/qz_uilib-4.11.1-refactor-uilib-core.101+9b603d3e7c-dirty.jar
  SHA256 e671b5ecf6780a3d83ef53404a7c105bf50ba7bb710069fe6112b393c67cc556

- C:/Users/16249/Desktop/MC/PrismLauncher-Windows-MinGW-w64-Portable-11.0.3/instances/GT_New_Horizons_2.9.0-beta-3_uilib测试/.minecraft/mods/qz_uilib_manualtest-core-loop-1.jar
  SHA256 a6e5d0a582fc4b8170c3c58322d6d702b712575aa681043f56f5a96a1225fe6f

人工第1轮（用户启动游戏）：进单人世界 /qzcore，点添加两次观察计数/列表，再正文滚轮观察标题/按钮不动，最后模态后台添加无效、关闭恢复。仅此三项，其余缩放/字号/资源重载后续分批。尚未执行实机，部署成功不等同渲染验收。
