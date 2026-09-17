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

## 不弹窗（离屏语义）

出图期间屏幕上**不会出现任何窗口**，也不抢焦点——可以一边出图一边继续用电脑。

GL 上下文需要窗口句柄，而 LWJGL2 的 `Display.create()` 会创建**真实可见窗口**（旧行为，标题 `Qz-UILib headless`，实测会挡住屏幕）。现在把 Display 挂到一个**从不显示**的 AWT `Canvas` 上：`pack()` 只为拿到 native peer，顶层容器始终不可见；渲染目标本来就是自建 FBO，那个窗口对出图没有任何贡献。

实测（出图期间每 50 ms 枚举本进程顶层窗口）：**可见窗口峰值 0 个**；进程内确有 AWT 隐藏容器（`SunAwtFrame`，visible=false）与驱动自建的离屏窗口（`NVOGLDC invisible` / `__wglDummyWindowFodder`，均 visible=false）。

两条约束：

- **不要设 `-Djava.awt.headless=true`**：离屏窗口句柄由 AWT 提供，headless 模式下会直接报错并说明原因；
- 出图结束会显式释放 GL 上下文与隐藏容器（`GlOffscreenSurface.shutdownContext`）。少了这一步，`System.exit` 会停在 AWT 的退出钩子里——表现为「图已经写出来了，进程却不退出」。

## 参数

| 参数 | 说明 |
|---|---|
| `--page=A\|B\|C\|D` | 单页面；`playground` = 测试场地，`text-probe` = 单行文本探针，`chat` = 聊天 3.0 内容树，`hud` = 同一内容树走 HUD 宿主装配（两者均见下节） |
| `--pages=A,B,…` | **多页面矩阵**（与尺寸 / 外观 / 字号轴同构）；与 `--page` 同时给出时本参数胜 |
| `--page-index=N` / `--page-indexes=0,1,…` | `playground` 子页下标（0 总览 / 1 单行文本 / 2 多行文本 / 3 浮层 / 4 响应式 / 5 富文本 / 6 控制字符 / 7 LaTeX / 8 Markdown）；`hud` 页当作**锚点**（0 左上 / 1 右上 / 2 左下 / 3 右下），`chat` 与 `text-probe` 忽略 |
| `--size=WxH` / `--sizes=WxH,…` | 单档 / 分辨率矩阵（360P~2K 任意尺寸，渲染到自建 FBO，与窗口无关） |
| `--out=path` | 输出 PNG；矩阵出图时按轴追加后缀：`-pg<页面名>`（多页面时）/ `-p<下标>` / `-th<外观档>` / `-fs<百分比>` / `-WxH`，各段只在对应轴存在多档时出现 |
| `--actions="…"` / `--script=file` | 输入脚本（见下） |
| `--text=…` | `text-probe` 的文本；`chat` 的消息串（语法见「聊天页」） |
| `--bg=RRGGBB\|transparent` | 宿主背景，默认不透明深色；透明底请显式指定 |
| `--frames=N` / `--settle=N` / `--max-frames=N` | 帧计划：最少帧数 / 稳定判据（连续 N 帧像素一致）/ 硬上限 |
| `--clock=epochMillis` | 虚拟墙钟基准（默认 `2024-01-01T00:00:00Z`）：**最后一条**消息的到达时刻 + 帧时钟起点，见「出图确定性」 |
| `--theme=NAME` / `--themes=NAME,…` | 外观档（`liquid-glass-dark` / `liquid-glass-light` / `solid-dark`）；不给 = 各页面用自己的默认外观，见「环境矩阵」 |
| `--font-scale=P` / `--font-scales=P,…` | 用户级字号缩放百分比（100 = 不缩放，域 100~200）：单档 / 字号矩阵，见「环境矩阵」 |
| `--debug` | 打开诊断采样：摘要多一行 `perf:`（帧内阶段耗时与计数）。**不改变绘制**，像素与关闭态逐位相同 |
| `--share-context` | 批量时同进程复用 GL/字体上下文（快，但产物带 atlas 历史依赖，见「出图确定性」）。默认逐档独立进程 |
| `--probe` | 只打印能力（GL 版本、stencil、字体数量）不出图 |
| `--nodes[=all]` | **目标寻址**：打印节点事实表（仅可命中节点；`=all` 打印完整树），先推进一帧拿布局，**不产出 PNG** |
| `--find=TEXT` | 按可见文本找可命中节点（大小写不敏感子串），给出地址与中心点；无命中 exit 4 |
| `--center=PATH` | 解节点地址取中心点（如 `--center=r2/1/0/8`） |

页面、外观、字号、尺寸四个维度可同时给，按笛卡尔积出图（产物命名规则见「环境矩阵」）。

多页面一次出图：

```bat
build\headless\qz-shot.bat --pages=playground,chat,hud --size=1280x720 --out=out\all.png
:: 产出 out\all-pgplayground-1280x720.png / -pgchat-… / -pghud-…
```

## 目标寻址（不数像素）

agent 出图后常要「点某个按钮再出图」。此前只能硬编码坐标（`move 315 88`），页面稍改即失效。
寻址把「画布上有什么、在哪、能不能点」变成可读事实：

```bat
:: 1) 看有哪些可点的东西（不含 PNG 产物）
build\headless\qz-shot-full.bat --page=playground --size=1280x720 --nodes
:: 2) 按文案找目标，拿到地址与中心点
build\headless\qz-shot-full.bat --page=playground --size=1280x720 --find=Markdown
::   [headless]   r2/1/0/8 SceneNode "Markdown 渲染" @879,64 148x40 fs=16 center=953,84
:: 3) 用中心点点击（或用 --center=r2/1/0/8 复核地址）
build\headless\qz-shot-full.bat --page=playground --size=1280x720 ^
  --actions="move 953 84; frame; click; wait 6" --out=out\md.png
```

**地址语义**：`r<根序号>[/<子下标>]…`。根序号是 runtime 的树根登记顺序（主树 + 各浮层，同一装配下确定），
子下标是父节点的子节点序号（同级按 z-order）。地址**跨进程稳定**——与 `System.identityHashCode` 不同，
可以写进脚本长期使用。

**名称来自子树**（无障碍树口径）：可交互节点通常自己不持文本，文案挂在子 label 上，故名称取「自身文本为空时
向下第一个非空文本」。没有这一步，导航按钮只会显示成一排 `r0/0 @0,0 57x40`。

**两条使用纪律**：

- **必须先推进过一帧**：坐标来自布局结果（`cachedLayout`），未布局时投影如实报 0。`--nodes` / `--find` / `--center`
  内部会自动推进，进程内自建的寻址需自己先 render；
- **坐标就是命中口径**：绝对坐标由局部 `LayoutBox` 沿路径累加，与 `SceneHitTester` 同法 —— 投影里报出的中心点，
  就是点击会命中的位置。

实测（`--page=playground --size=1280x720`）：寻址得 `r2/1/0/8` 中心 `953,84` → 按该坐标点击后出图，
命令面与 `--page-index=8` 直接切页**逐项相同**（`commands=101 segments=62 bounds=1,0..1279,751 frames=22`），
像素差 15942/921600（1.7%，集中在文本反锯齿区，来自 atlas 历史而非寻址路径）。

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

## 环境矩阵（外观 × 字号 × 分辨率 × 诊断）

出图的观感不只由页面决定，也由**环境事实**决定。请求可声明四类环境量，与尺寸一样按档位扫：

```bat
:: 外观矩阵
build\headless\qz-shot.bat --page=chat --size=1280x720 --themes=liquid-glass-dark,liquid-glass-light,solid-dark --out=out\theme.png
:: 字号矩阵（作用点 = 解析出口的倍率层，参与布局而不只是把字画大）
build\headless\qz-shot.bat --page=chat --size=1280x720 --font-scales=100,150,200 --out=out\fs.png
:: 360P~2K 分辨率矩阵
build\headless\qz-shot.bat --page=hud --sizes=640x360,854x480,960x540,1280x720,1600x900,1920x1080,2560x1440 --out=out\hud.png
:: 一帧花在哪（采样摘要随本次出图给出）
build\headless\qz-shot.bat --page=hud --size=1920x1080 --debug
```

**产物命名 = 「偏离缺省的维度」+ 尺寸**：`out\hud-640x360.png`、`out\theme-thsolid-dark-1280x720.png`、
`out\fs-fs150-1280x720.png`（外观/字号非缺省才带 `-th<档名>` / `-fs<P>`，页下标在指定时带 `-p<N>`，
**多页面**时每档带 `-pg<页面名>`）。缺省命令的路径因此逐字不变。

三类环境量的**接入时机不同**，这不是实现细节而是语义差别：

| 环境量 | 时机 | 为什么 |
|---|---|---|
| 外观档 `--theme` | **装配期**（建树前） | 控件的配方派生在构建期捕获主题信号对象；换一个信号对象只影响此后构建的控件，已建树的部分不会重算 |
| 字号倍率 `--font-scale` | 装配后、首帧前 | 走 `SceneRuntime.setFontScale`，自带字号代际失效通道，写一次即通知全部消费者 |
| 诊断 `--debug` | 每帧表态 | 帧是采样的管辖单位，值读是帧内直读 |

**`--theme` 只对读主题系统的树有效**，别拿它当「所有页面都能换配色」：

| 页面 | 换档效果 | 原因 |
|---|---|---|
| `playground` | **变色**（默认 vs 浅色档 685824/921600 像素不同） | 外壳与 9 个演示页都经 `SceneThemes` 取配方 |
| `chat` / `hud` | **逐像素相同**（实测 0/921600） | chat3 的 HUD 形态配色来自它自己的进程级色板 `ChatMarkdownSettings`（气泡底/正文/组头…），不读 runtime 默认主题；只有容器形态（输入屏打开时）走 `SceneThemes` |
| `text-probe` | 无效 | 前景色写死，连安装都不做 |

出处：`HeadlessThemes` 的类注释记着这条边界与实测值。要用 `--theme` 看效果，请用 `playground` 或走主题系统的业务页面。

四条实测性质，可直接当回归判据：

| 性质 | 实测 |
|---|---|
| `--debug` 不改变像素 | 同一命令加/不加 `--debug`：**0 / 921600** 像素不同 |
| 不给 `--theme` 不改变像素 | 与引入本轴之前的产物逐像素相同（`--theme=` 的缺省是「不干预」而非「选默认档」） |
| `--font-scale` 改变像素 | 100 与 150：**176911 / 921600** 像素不同（跨过标题/换行等布局差异） |
| 同命令逐像素可复现 | 连跑两次：**0 / 921600**（见「出图确定性」） |

环境事实**全部来自请求**：headless 生产包内不得回落生产环境单例（`HeadlessEnvironmentInjectionGuardTest`
守着这条）。违反的后果是静默的——同一命令在不同 `Config` 下出图不同，且 `--debug` 变成一句空话
（采样器永远打不开，命令本身不报错）。

### 小视口下只显示最新几条是预期

HUD 形态的堆叠高度上限是**视口高 × 0.5**（`hudMaxHeightRatio`），超出时按到达时刻剔除更旧的组
（`ChatSceneController#trimHudGroupsByHeight`，设计意图「刷屏不侵占半屏以上」）。故 360P 这类小视口下
只会保留最新的一两组——这是设计行为，不是出图故障。需要看完整内容就用足够大的尺寸，或减少 `--text` 的消息数。

探针的消息按 **1 秒一条**的节奏到达（`ARRIVAL_SPACING_MILLIS`），最后一条恰为 `--clock=` 基准。
真实聊天不可能多条同刻到达，而「同刻到达」会让上述剔除逻辑一次越过全部组 ⇒ 整树为空、出图只剩背景
（实测 `--page=chat --size=1100x720` 命令面 0 条，而同一内容在 `1200x720` 有 24 条）。

## 聊天页（chat）

用生产内容构建入口（`ChatSceneController.buildContent`，与真机 HUD 注册工厂同一方法）渲染 chat3 内容树，
出图含气泡、组头、markdown、行内公式、链接与自动换行。

```bat
build\headless\qz-shot.bat --page=chat --size=1280x720 --out=out\chat.png
```

`--text` 是消息串，消息之间用 `;;` 分隔（不取 `|`：那是 Windows 命令行管道符），每条消息三选一：

| 形式 | 含义 |
|---|---|
| `发送者:内容` | 玩家消息（气泡 + 组头）；内容走 markdown 管道 |
| `md:内容` | markdown 系统行（左对齐、无气泡、无组头） |
| 其余 | 普通系统文本（居中一行） |

消息里的字面 `\n` 会转成换行。示例：

```bat
build\headless\qz-shot.bat --page=chat --text="Steve:**粗体** 与 `code`;;md:## 标题\n- 列表项" --out=out\chat.png
```

两点注意：

- 消息组首次合成有 **180 ms 入场动画**（组节点 opacity 0→1），动画期间整树不可见、像素逐帧不变，
  「连续 N 帧像素一致」的稳定判据会把这段误判成「已收敛」。故 `chat` 页在未显式给 `--frames` 时
  默认最少 20 帧（≈320 ms 虚拟时间）；自己指定帧数时要覆盖动画时长。
- 本页只渲染内容根，**不套 HUD 外壳、不做四角锚定与倍率缩放**，内容贴在左上角，与真机 HUD 的放置位置不同；
  要出「HUD 放置后的画面」用 `--page=hud`（下节）。

## HUD 页（hud）

同一份聊天内容树走**生产 HUD 宿主装配**（`ui.scene.host.SceneHostWindow`：外壳 + 内容 + 装饰层 + 帧管线），
并按四角锚定放置——即「同一份内容代码，换宿主」的对照，观感与真机 HUD 一致。

```bat
build\headless\qz-shot-full.bat --page=hud --size=1280x720 --out=out\hud.png
build\headless\qz-shot-full.bat --page=hud --page-indexes=0,1,2,3 --out=out\hud.png
```

- `--page-index` 选锚点：**0 = 左上 / 1 = 右上 / 2 = 左下（默认，与原版聊天同位）/ 3 = 右下**；
  `--page-indexes=0,1,2,3` 一次出四角矩阵；
- 消息语法、默认消息集、入场动画与最小帧数都与 `chat` 页相同；
- 外壳与真机**同源**：`SceneHostWindow.Shell.HUD_DEFAULT`（内边距 7/6 + 半透明底）是客户端
  `SceneHudHost` 与 headless 共用的同一份默认外壳，不是两处各写一份；
- **本页无输入**：HUD 窗口的契约就是不持有输入源、不参与命中仲裁，故 `--actions` 对本页不生效；
- **`--text=`（空消息集）出纯背景是预期**：内容空尺寸 ⇒ 整窗（含外壳）隐藏，输出
  `commands=0 / bounds=(empty) / colors=1`。这与 `chat` 页的同款现象含义不同——那里 `commands=0` 是缺陷信号（见排查表）。

## 出图确定性

**同一命令在同一台机器上逐像素可复现**（实测：相隔 73 秒的两次 `--page=hud` 出图差异 0 像素，
且其中一次落在跨分钟边界上）。这不是自动成立的，靠三条约束：

- **时间事实只来自请求**：消息到达时刻与帧时钟起点都取 `--clock=` 给的虚拟墙钟基准（两者同源），
  headless 生产包内**不得出现 `System.currentTimeMillis()`**（`HeadlessWallClockGuardTest` 守着）。
  踩过的坑：组头时间戳取进程当前时刻 ⇒ 同一命令在 13:05 与 13:07 出图差 9590 像素，
  差异集中在时间戳及其相邻区 —— 「改一行 → 出图对拍」这条主用途随之失效；
- **帧计划确定**：动画按虚拟时间轴推进（每帧 +16 ms），与真实耗时无关；`--frames` / `--settle` /
  `--max-frames` 给定后帧数确定；
- **每个产物从冷进程起算**：字体 atlas 是**进程级按需资源** —— 字形落在 atlas 的哪个位置取决于「此前
  生成过哪些字形」。同一 JVM 内渲染第 N 档时 atlas 里已有前面各档的字形，本档字形的 UV 因此不同，
  边缘采样出现 **±1~±5 微差**。实测：批量 `[640x360,2560x1440]` 的 2560 档与单跑差 31534/3686400 像素，
  把前置档从 640 换成 1280 又得到第三个结果 —— 产物取决于它在进程内的渲染次序，而不只取决于请求。
  故**批量默认逐档独立进程**（每档 +1.9 s 左右启动开销）；`--share-context` 换回同进程复用（快，
  但带上述历史依赖，只建议扫观感用）；
- **输出抖动（已定位并修复）**：同一命令曾偶发产出两种结果之一。定位（2026-09-17）：历史命令
  `--page=playground --page-index=8 --size=1280x720 --font-scale=100`，本机 50 次得 **48 次**
  `a8fb1156c3978823…`（`commands=101 segments=62`）、**2 次** `2669a01c4c59cc42…`
  （`commands=100 segments=61`）；独立复核在修复前基线上另跑 32 次得 29:3、16 次得 14:2 ——
  即 **4% ~ 12.5%，合并样本 7/98 ≈ 7%，该比率随机器负载浮动，不要当固定值**。两个 hash 与当初审核
  记录的一对完全相同；像素差异是滚动条滑块底端圆角的 8×6 px（30 像素，行剖面 2/2/4/8/8/6）。
  **根因**：`TextLayoutService.tryAcquireWidthMissBudget()` 把宽度缓存 miss 预算绑在 **16ms 真实
  时间窗**（`System.nanoTime()`）上，超预算（默认 64/窗口）的码点按**空格宽近似**排版。冷启动一帧
  要测量的码点远超预算，**哪些码点被近似取决于这一次运行的真实耗时** ⇒ 布局宽度随机器负载漂移。
  **定论依据不是「跑 50 次没复现」**（4% 基线下 0/50 的偶然概率约 0.13，单独不足以定论），而是
  只改预算的剂量-反应对照（各 16 次）：默认 64 → 14:2；**10^8（永不耗尽）→ 16/16 全同**；
  **1（几乎必耗尽）→ 产物换成第三种分布**（全部 `commands=100`，从不出现锚点值）。预算不可耗尽
  时其它真实时间源（字形老化队列、图页上传排空）仍在跑却稳定 ⇒ 排除「真凶另有其人」。
  **修复**：headless 会话解除该预算（`HeadlessSession.open` 里置
  `FontConfig.widthCacheMissBudgetPerWindow = 0`），全部码点走精确测量。耗时无退化（均值 +0.9%，
  Welch t=1.43；含 JVM 启动的 wall 中位均约 3.5 s）。
  **加大 `--settle` 消除不了**：settle 由 2 提到 20（帧数 10~12 → 27~30）后少数派仍 3/18 ⇒ 不是
  「没来得及收敛」，而是被近似的码点拿不到真值、`widthConvergeEpoch` 不抬（`GlyphRuntimeTables`
  只在债务计数**恰好归零**时自增），近似布局被**固化进最终帧**。
  > 生产侧口径未变：游戏内该预算仍是「按时间窗降级」（卡顿时文本宽度会被近似），且同样会被固化。
  > 这是**框架正确性**问题（同一输入的产出取决于机器负载），是否把降级口径改成与时间无关的量
  > （按帧 / 按渲染代际）或按码点粒度驱动失效，需单独决策。
  **「逐字节相同」仍非跨机器保证**：锚点对拍请在同机同命令下连跑取多数值（≥5 次），或比对内容量
  （`bounds` / `commands` 数 / 文本字符数）。

默认基准按本机时区显示为 `08:00`（东八区）。要观察其它时刻的观感用 `--clock=`：

```bat
build\headless\qz-shot-full.bat --page=hud --clock=1735689600000 --out=out\hud-2025.png
```

**不保证**跨机器、跨 GL 驱动、跨字体环境的逐像素一致 —— 那三个变量不在设施控制内；
逐像素对拍只在「同一机器 + 同一命令」下成立。

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
| `--page=chat` 出全背景、`colors=1` | 帧数不足：入场动画（180 ms）期间整树不可见且像素不变，会被判成「已收敛」。提高 `--frames`（≥20） |
| `--page=chat` 出全背景且 `commands=0` | 入场动画**整段未起播**：组 opacity 恒 0 → 零透明子树被 paint 跳过 → 命令面为空。历史上的根因是虚拟时钟起点早于消息出生时刻（构造期控制器初始化耗时数百 ms 的时序竞态，规划 F23）；2026-09-18 起两者同源于 `--clock=` 注入的基准，该竞态已根除。若仍复现，按「出图确定性」一节查时间源，不要再往帧数上加 |
| `--page=hud --text=`（空消息集）出纯背景 | **预期行为**而非故障：内容空尺寸 ⇒ 整窗（含外壳）隐藏，`commands=0 / colors=1` 正确。给非空 `--text` 即出外壳与内容 |
| 需要一次出多张 | `--sizes=` / `--page-indexes=`：同进程内多档，字体与 GL 上下文只初始化一次 |
| 出图完成但进程不退出 | 历史缺陷（GL 上下文挂在隐藏 AWT 容器上，未显式释放时 `System.exit` 停在 AWT 退出钩子）；现已由 `HeadlessShotMain` 出图后调 `GlOffscreenSurface.shutdownContext()` 处理。若复现请报障 |
| 屏幕上出现窗口 / 抢焦点 | 不应发生（可见窗口峰值 0）。若复现，检查是否有人改回 `Display.create()` 直连路径，见「不弹窗」 |
| 批量各档互相不一致 / 与单跑不一致 | 检查是否用了 `--share-context`：该模式下产物带字体 atlas 历史依赖（±1~5 微差）。默认的逐档独立进程模式已无此问题 |
| 两次出图像素不同 | 先确认命令逐字相同：`--clock=` 不同本就该不同（时间戳/动画轴变了），`--frames` 不同也会不同（动画进度不同）。同机同命令仍不同则是设施缺陷，带两份命令与产物报障 |

## 边界（不要据此下结论）

- 出图**不代表**真机 MC 宿主 / Angelica / lwjgl3ify 上下文；
- **同机同命令逐像素可复现**（时间已解耦，见「出图确定性」）；但**不作为跨机器 / 跨 GL 驱动 / 跨字体环境的金样** —— 那三个变量不在设施控制内；
- 不覆盖原版包装类禁令（Tessellator 等）一类问题；
- 不经过 `LwjglInputSource` 的 poll 差分语义——headless 注入的是帧，桥内部的差分/边沿类缺陷不在覆盖范围内；
- **chat3 已纳入**（`--page=chat`）：走的是生产同一入口 `ChatSceneController.buildContent`。
  仍未覆盖的是 MC 宿主侧接线（输入屏、网络消息来源、原版聊天接管），它们不属于渲染面。
- **HUD 已纳入**（`--page=hud`）：窗口宿主已上提为宿主无关的 `ui.scene.host.SceneHostWindow`
  （外壳 + 内容 + 装饰层 + 帧管线 + 空内容语义），与客户端 `client.hud.SceneHudHost.RetainedWindow` 共用同一份装配。
  仍未覆盖的是 MC 宿主侧接线（注册表生命周期、工具栏注册表、倍率设置持久化），它们不属于渲染面。
