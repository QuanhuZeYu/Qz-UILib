# Qz-UILib Minecraft

Minecraft 的 UI 页面、文字渲染与输入宿主，当前版本 **5.0.0-beta3**。

## 安装

从 [5.0.0-beta3 Release](https://github.com/QuanhuZeYu/Qz-UILib/releases/tag/5.0.0-beta3) 下载对应安装 ZIP，将其中全部 JAR 放入实例的 `mods`，移除旧版 UILib。

| Minecraft / 加载器 | 安装包 | 随包运行组件 |
| --- | --- | --- |
| 1.7.10 / Forge | [1.7.10.zip](https://github.com/QuanhuZeYu/Qz-UILib/releases/download/5.0.0-beta3/qz-uilib-minecraft-5.0.0-beta3-1.7.10.zip) | 主 Mod、Core、字体适配；GL 已合并 |
| 1.12.2 / Forge | [forge112.zip](https://github.com/QuanhuZeYu/Qz-UILib/releases/download/5.0.0-beta3/qz-uilib-minecraft-5.0.0-beta3-forge112.zip) | 主 Mod、Core、字体与 GL 适配 |
| 1.16.5 / Forge | [forge116.zip](https://github.com/QuanhuZeYu/Qz-UILib/releases/download/5.0.0-beta3/qz-uilib-minecraft-5.0.0-beta3-forge116.zip) | 主 Mod、Core、字体与 GL 适配 |
| 26.3 / NeoForge | [neoforge263.zip](https://github.com/QuanhuZeYu/Qz-UILib/releases/download/5.0.0-beta3/qz-uilib-minecraft-5.0.0-beta3-neoforge263.zip) | 主 Mod，内置 Core、字体与 GL |

- Forge 1.7.10 另需 [UniMixins 0.3.1](https://github.com/LegacyModdingMC/UniMixins/releases/tag/0.3.1) 的 `+unimixins-all-1.7.10-0.3.1.jar`。
- `manual` 为可选测试广场附加包，安装后通过 `/qzcore` 打开；媒体功能另装同宿主的 `media` 包。
- 各组件统一使用 `5.0.0-beta3`；源码包与 Maven ZIP 供开发使用。
- Forge 1.7.10 的 GTNH、Angelica/Iris、lwjgl3ify/RFB 与 AE2 接入由 [Compat 1.0.0-beta1](https://github.com/QuanhuZeYu/Qz-UILib-Compat/releases/tag/1.0.0-beta1) 提供。

## 开发接入

页面与宿主扩展约定见[宿主接入规格](specs/host/2026-10-18-uilib-minecraft-hosts-spec.md)。
同版本 Release 的 Maven ZIP 提供中性宿主开发接口：

`asia.quanhuzeyu.uilib:minecraft-host:5.0.0-beta3:development-java8`

## 许可

自有代码采用 [MIT](LICENSE)；第三方通知见 [Core](https://github.com/QuanhuZeYu/Qz-UILib-Core/blob/5.0.0-beta3/THIRD-PARTY-NOTICES.md) 与 [Platform](https://github.com/QuanhuZeYu/Qz-UILib-Platform/blob/5.0.0-beta3/THIRD-PARTY-NOTICES.md)。
