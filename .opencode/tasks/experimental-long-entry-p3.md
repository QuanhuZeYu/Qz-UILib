# Experimental long Entry P3

## 目标

- 完成 Minecraft descriptor codec、服务端 controller 与 Issue #66 `GuiContainer` scene host。
- pointer/key activation 只选择一个 owner，scene 与 vanilla 不得 double-dispatch。
- 不复制或反射 `GuiContainer` 私有 draw loop，保持原版 Slot、carried 与第三方 phase。

## 当前事实

- 原版 `GuiContainer.drawScreen` 顺序是 background、Slot/foreground、carried、vanilla Slot tooltip。
- 当前 GTNH transformed 路径在 foreground 后增加 NEI objects，在 carried 后增加 NEI tooltips；原版 Slot tooltip已由该路径替换。
- early mixin 列表由 `EarlyMixins.buildMixinsForSide(...)` 动态登记，JSON 不含静态列表。
- 本机禁止 Gradle、编译、JUnit、构建和运行态验证；缺少 CI/用户证据时状态保持 `INCOMPLETE`。
- 分支 HEAD `18447142` 的 GitHub Actions run [`30285280527`](https://github.com/QuanhuZeYu/Qz-UILib/actions/runs/30285280527) 已通过编译、post-build checks、2474 项测试与 dedicated server smoke。
- clean consumer 与真实客户端 GUI Scale/Slot/overlay/shortcut/第三方 phase 矩阵仍无证据，P3 保持 `INCOMPLETE`。

## 实施合同

- 绘制顺序：`vanilla background -> scene main -> vanilla Slot/foreground -> third-party objects -> scene overlay -> carried -> vanilla/third-party tooltip`。
- pointer DOWN 同步 claim；MOVE/UP/CANCEL 只交给 DOWN owner。key 每次独立同步 claim。
- host 边界负责 native input 到 `SceneKey`/`SceneMouseButton` 和 logical px 的单次换算；scene 核心不接触 MC/LWJGL。
- codec/controller 只在 `ui.container.experimental.minecraft` 使用 MC 类型；storage 继续保持平台无关。
- 不新增 wire protocol、request ledger、retry、反射或 vanilla 私有状态复制。

## 临时诊断

- 集中开关：`-Dqzuilib.experimental.container.debug=true`，默认关闭。
- 日志前缀：`[QZUILIB-P3-TEMP]`。
- 覆盖 attach/detach、phase hook、pointer/key claim、owner 保持、坐标、pending、controller 请求/结果、close/dispose 和 phase 漂移。
- 详细日志只用于 P3 真机闭环。完成 CI、clean consumer 和游戏矩阵后，必须先提醒用户，再删除逐事件/逐帧临时日志；只保留必要的低频异常日志。未完成清理不得把 P3 标记为完成。

## 验收

- 已新增 codec/controller/claim/phase contract 测试并通过 CI；CI dedicated server smoke 无报错。
- 待取得 clean consumer、integrated server 与真实 GUI Scale/Slot/overlay/shortcut/第三方 phase 客户端矩阵。
- 最终检查并清理 `[QZUILIB-P3-TEMP]` 详细插桩，更新 `docs/反馈层/交接.md`。
