# headless 出图指南（不开游戏渲染 UI）

**用途**：agent 与开发者不开游戏、不启窗口，一条命令把 UI 页面渲染成 PNG，并同时拿到「画了什么」与「画出来没有」的证据。
形态对标 Qt `-platform offscreen`：**同一份 UI 代码**、同一套生产渲染链路（`UiRenderContext` / `SceneFramePipeline` /
`SceneHostAssembly`），只是换了个平台宿主。

## 快速开始

```bat
gradlew exportHeadlessClasspath
build\headless\qz-shot.bat --page=playground --size=1280x720 --out=out\shot.png
```

`exportHeadlessClasspath` 生成两个启动器（内含 classpath 参数文件与 natives 路径）：

| 启动器 | 类路径 | 用途 |
|---|---|---|
| `qz-shot.bat` / `qz-shot.sh` | 最小集（107 项） | **agent 默认**：快、不含 Minecraft 静态初始化风险 |
| `qz-shot-full.bat` / `qz-shot-full.sh` | 完整开发类路径（137 项，含重编译 Minecraft 类） | 页面渲染一旦触及 MC 类型（如 `IChatComponent`）时使用 |

**冷启动约 1.6~2.0 s**；`gradlew` 跑单张图要 12~22 s（配置与编译开销），批量出图请一律用启动器。

## 参数

| 参数 | 说明 |
|---|---|
| `--page=playground\|text-probe` | 页面；`playground` = 测试场地，`text-probe` = 单行文本探针 |
| `--page-index=N` / `--page-indexes=0,1,…` | playground 子页下标（0 总览 / 1 单行文本 / 2 多行文本 / 3 浮层 / 4 响应式 / 5 富文本 / 6 控制字符 / 7 LaTeX / 8 Markdown） |
| `--size=WxH` / `--sizes=WxH,…` | 单档 / 分辨率矩阵（360P~2K 任意尺寸，渲染到自建 FBO，与窗口无关） |
| `--out=path` | 输出 PNG；批量时自动追加 `-p<下标>-<W>x<H>` 后缀 |
| `--actions="…"` / `--script=file` | 输入脚本（见下） |
| `--text=…` | `text-probe` 页面渲染的文本 |
| `--bg=RRGGBB\|transparent` | 宿主背景，默认不透明深色；透明底请显式指定 |
| `--frames=N` / `--settle=N` / `--max-frames=N` | 帧计划：最少帧数 / 稳定判据（连续 N 帧像素一致）/ 硬上限 |
| `--probe` | 只打印能力（GL 版本、stencil、字体数量）不出图 |

页面与尺寸可同时给，按「页面 × 尺寸」笛卡尔积出图。

## 输入脚本

每条语句换行或 `;` 分隔，`#` 起注释：

```
move 315 88        # 移动到逻辑坐标
moveby 10 -5       # 相对移动
down / up [BUTTON] # 按下 / 抬起（默认 LEFT）
click              # 点击（按下与抬起自动跨帧，更贴近真实输入）
dblclick           # 双击
scroll 3           # 滚轮（纵） / scroll 2 3（横纵）
key ENTER          # 按键（跨帧） / keydown / keyup
type abc           # 逐字符输入（char 路径）
compose 中文输入    # 整串提交（外部文本接管 / IME 语义）
cancel             # 指针取消（失焦）
frame / wait 4     # 帧边界 / 空推进 4 帧
```

示例：点开「单行文本」页再出图

```bat
build\headless\qz-shot.bat --page=playground --actions="move 315 88; frame; click; wait 4" --out=out\nav.png
```

## 怎么读输出

```
[headless] request: page=playground#1 size=1280x720 frames=2 background=FF0E1014 settle=2 maxFrames=60
[headless] capabilities: gl=4.6.0 … stencil=8 maxTexture=32768 fonts=245 awtHeadless=false
[headless] input: dispatched=3 pending=0 pointer=315,88 clock=133ms
[headless] commands: commands=62 [fill=0 surface=16 border=0 text=46/1050ch segments=0 image=0] … bounds=1,1..1279,717 outsideViewport=0
[headless] output: …png (200031 bytes)
[headless] self-check: ok 1280x720 ink=100.00% inkPx=921600 opaquePx=921600 meanAlpha=255.0 colors=1290 glError=0
[headless] frames: 4/60
[headless] elapsed: 615 ms
```

- `commands`（命令面）与 `self-check`（像素面）是**两条独立证据**：命令说有绘制、像素说画出来了。只有一边成立即为设施故障，
  不是「UI 画得不好」；
- `outsideViewport>0`：有矩形命令完全落在视口外（小视口溢出提示，不判失败）；
- `frames: N/上限`：等于上限说明未收敛（动画/时间源持续变化），不是错误但内容可能还在变；
- 批量模式末尾有 `batch: N/M ok — page#0@1280x720=ok …` 汇总行。

## 退出码

| 码 | 含义 |
|---|---|
| 0 | 成功 |
| 2 | 参数错误 |
| 3 | 能力 / 上下文 / 渲染失败（诊断带阶段标签：能力探测 / GL 上下文 / scene 装配 / 帧推进 / 像素读回 / PNG 编码） |
| 4 | 像素自检未通过，或批量中存在失败档位 |

## 排查

| 现象 | 原因与处理 |
|---|---|
| `[GL 上下文] 创建 GL 上下文失败` | 缺 natives（启动器已带 `-Djava.library.path`）；Linux 无桌面环境需 Xvfb |
| `fonts=0`、出图豆腐块或字体初始化失败 | 环境无系统字体（AWT 字体子系统全有或全无）：装 fontconfig + 字体包后重启进程 |
| 整帧全透明且自检 FAILED | 帧前置语义 / 帧缓冲绑定问题，属设施缺陷，请带自检输出报障 |
| 文字残缺（只出部分字形） | 字形异步生成尚未就绪：提高 `--settle` / `--max-frames`（默认已自动收敛） |
| 需要一次出多张 | `--sizes=` / `--page-indexes=`：同进程内多档，字体与 GL 上下文只初始化一次 |

## 边界（不要据此下结论）

- 出图**不代表**真机 MC 宿主 / Angelica / lwjgl3ify 上下文；
- GL 输出依赖驱动实现，**不作为逐像素金样**；
- 不覆盖原版包装类禁令（Tessellator 等）一类问题；
- 不经过 `LwjglInputSource` 的 poll 差分语义——headless 注入的是帧，桥内部的差分/边沿类缺陷不在覆盖范围内；
- **chat3 / HUD 不在可渲染面内**：chat3 视图的内容树由宿主装配链驱动（`ChatContainer` / `ChatHudWindow` / 输入屏幕），
  `ChatSceneController.buildContent` 直接挂树实测为**空画面**（`commands=0`）；HUD 宿主 `SceneHudHost` / `HudRegistry` 在 `client/`（MC 域）。
  两者要纳入，正确姿势是在测试域用生产装配接线，而不是在 headless 里复刻宿主。
