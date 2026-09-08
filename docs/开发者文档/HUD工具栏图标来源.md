# HUD 工具栏图标来源

聊天工具栏图标使用 Google 的 [Material Design Icons](https://github.com/google/material-design-icons)，样式为 `materialiconsoutlined`，固定版本为 `0cbb08816df07faaae3dca060d4ebb10b66c214f`。资源采用 [Apache License 2.0](https://github.com/google/material-design-icons/blob/0cbb08816df07faaae3dca060d4ebb10b66c214f/LICENSE)。

六个 PNG 均来自以下 HTTPS 前缀：

```text
https://raw.githubusercontent.com/google/material-design-icons/0cbb08816df07faaae3dca060d4ebb10b66c214f/png/
```

| 工具栏语义 | 相对此前缀的资源路径 | 加载中/失败回退 |
| --- | --- | --- |
| `edit` | `image/edit/materialiconsoutlined/24dp/2x/outline_edit_black_24dp.png` | `E` |
| `finish` | `navigation/check/materialiconsoutlined/24dp/2x/outline_check_black_24dp.png` | `V` |
| `cancel` | `navigation/close/materialiconsoutlined/24dp/2x/outline_close_black_24dp.png` | `X` |
| `reset-current` | `content/undo/materialiconsoutlined/24dp/2x/outline_undo_black_24dp.png` | `<` |
| `reset-all` | `action/restore/materialiconsoutlined/24dp/2x/outline_restore_black_24dp.png` | `R` |
| `action` | `navigation/more_horiz/materialiconsoutlined/24dp/2x/outline_more_horiz_black_24dp.png` | `*` |

资源接入时已逐个验证 HTTP 200、`Content-Type: image/png`、PNG 文件签名和 48×48 图像尺寸。调用方按 16×16 logical px 布局。

**本项目的修改**：原图为黑色透明背景轮廓。`ChatToolbarIcons` 在解码完成后复制位图，保持每个像素的 alpha，将 RGB 改为白色；不修改共享网络缓存中的原图。每次挂载至多转换一次，使用 `chat-toolbar:white:<完整URL>` 作为稳定宿主纹理键。

图片通过 UILib `DocumentRemoteImageCache` 下载。每个挂载实例由 scene 帧信号读取完成状态，成功或失败后退订，随 Owner 卸载释放本地引用。下载/解码缓存与 GPU 纹理由原有宿主生命周期管理。网络被阻断或资源暂时不可达时保留上表字符回退；失败条目遵循共享缓存原有策略，不主动重试。
