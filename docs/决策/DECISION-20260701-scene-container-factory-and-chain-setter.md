# 决策：scene 容器工厂糖 + setter 链式化（P2 批次，待用户拍板）

## 日期
2026-07-01

## 背景
scene 布局引擎范式统一收口普查（Oracle 1 容器工厂糖评估 + Oracle 2 范式统一性普查交叉结论）发现：
当前范式已被 `package-info.java` R1-R11 十一条契约红线 + `NORTH_STAR.md` I1-I12 不变量强约束焊死，
真正需要收口的点少而集中。P0/P1（删死代码 `bindDerived`、统一 create 参数名、package-info 补 R12、
WidthSizing 补高轴无对偶注释）属零风险清理与文档收口，**已完成**。剩余 P2 批次涉及架构取舍，
需用户拍板，故独立成决策文档沉淀。

## 关键事实（决策依据）

- **SceneNode setter 全部返回 void（不链式）**：`SceneNode.java:1293/1050/1074/1093/1116/1138/1221/1334`
  全 void，ast_grep 搜 `public SceneNode set$NAME` 零匹配。容器工厂糖只能压方向设置一行，
  后续配置仍是散的 void setter。
- **容器创建样板量**：211 处 `new SceneNode()` + 143 处 `setFlexDirection`，但典型容器 4-6 行，高度重复。
- **两派分层是隐性的**：组件层（16 个，`Supplier<SceneNode>` + record Props）vs Primitive 层
  （9 个，`Result` + 无样式）。package-info 未成文（P1-1 已补 R12 成文）。
- **bindDerived 是死代码**：`SceneRuntime.java:221/247/249` 三重载全库 0 调用（P0-1 已删）。
- **bind 的 Invalidation impact 参数运行时丢弃**：刻意保留作 I4 审查双轨锚点，
  每个 bind 调用点写一个不生效参数（认知税）。
- **宽高轴已基本对称**：fillParent/preferred/max/percent 全对称。唯一剩余 `WidthSizing{FILL,SHRINK}`
  无 `HeightSizing` 对偶——NORTH_STAR §4:111-113 明示的正当例外（高轴天然 shrink）。

## 候选方案

### P2-1 完整方案：setter 链式化 + 4 个静态工厂（Oracle 1 推荐）

**核心洞察**：SceneNode 37 个 setter 全 void（不链式）。纯工厂 `column()` 只能压"方向"一行，
后续配置仍散。**真正的杠杆是 setter 链式化**（`void setXxx` → `SceneNode setXxx` 返回 this），
让工厂+链式 = 真正流畅 DSL。

**推荐组合：链式化 37 setter + 4 个静态工厂**

链式化后效果（5 行塌成 1 行）：
```java
// 改前
SceneNode row = new SceneNode();
row.setFlexDirection(FlexDirection.ROW);
row.setGap(8);
row.setPadding(12);
row.setFillParentHeight(true);

// 改后
SceneNode row = SceneNode.row().setGap(8).setPadding(12).setFillParentHeight(true);
```

**工厂糖 API（只做零参+单参，拒绝双参重载）**：
```java
public static SceneNode column() { ... }
public static SceneNode row() { ... }
public static SceneNode column(int gap) { ... }
public static SceneNode row(int gap) { ... }
```

**兼容性**：void→引用类型对丢弃返回值的调用点透明（`node.setX();` 照常编译）。
**唯一风险**：`::setX` 方法引用从 `Consumer` 变 `Function`，必须先 grep `::set` 扫全仓核实。

**迁移**：链式化天然零迁移（存量调用点不动）；工厂糖不强迁存量 211 处（YAGNI，自然演进）。

**排除的方案**：
- ❌ Builder（C）：与 void setter 体系冲突，与项目 Props-Builder 语义错位
- ❌ HeightSizing 补全：高轴天然 shrink（默认态），无需显式 SHRINK 开关，补了制造语义模糊

**实施步骤**：
1. 先 grep `::set\w+` 扫全仓确认无方法引用阻断
2. AST 批量改 37 setter 链式化（`void`→`SceneNode` + `return this`）
3. 加 4 个静态工厂
4. 全量测试回归

### P2-2：bind 的 Invalidation impact 参数去留

保留 = 接受认知税换 I4 审查双轨锚点；砍掉 = 简化 API 但失 I4 双轨核对。**需用户拍板**。

## 最终选择
**待用户拍板**。P0/P1 已完成（删 bindDerived、统一 create 参数名、package-info 补 R12、
WidthSizing 补注释），P2 批次（setter 链式化 + 容器工厂糖 / bind impact 去留）等待用户决策后实施。

## 选择原因
- P2-1 真正的杠杆是 setter 链式化而非纯工厂糖，组合后才能形成流畅 DSL
- 链式化天然零迁移，存量调用点不动；工厂糖不强迁存量（YAGNI）
- 唯一风险（方法引用 `Consumer`→`Function`）可通过 grep 前置核实规避
- P2-2 bind impact 保留/砍掉是认知税与审查锚点的取舍，属价值偏好，不替用户决定

## 影响范围
- P2-1：`SceneNode` 37 个 setter 签名 void→SceneNode + 新增 4 个静态工厂；
  存量调用点零迁移；需全量布局测试回归
- P2-2：若砍掉，`SceneRuntime.bind` 签名简化，100+ 调用点需同步删除 impact 参数

## 后续注意事项
- 实施前必须 grep `::set\w+` 扫全仓确认无方法引用阻断
- 链式化后 setter 返回类型变化需回归全量布局测试（I1-I12 守恒核对）
- 工厂糖只做零参+单参，拒绝双参重载（避免重载爆炸）
- 不强迁存量 211 处 `new SceneNode()` 调用点（自然演进）

## 不变量守恒分析
- P0/P1 全部零行为变化（删死代码 + 注释 + 命名），I1-I12 无触碰
- P2-1 容器工厂糖若走纯工厂路径，也是纯增量，零破坏
- P2-1 若走链式 setter 路径，需回归全量布局测试（setter 返回类型变化）
- P2-2 砍掉 impact 参数不影响运行时行为（参数本就被运行时丢弃），仅影响 I4 审查双轨锚点

## 出处
- 来源：`docs/进展/布局引擎收口任务清单.md`（已抽取本决策后删除）
- 研究编排：2 Oracle（容器工厂糖评估 + 范式统一性普查）交叉结论
- HEAD：`b90eb53e` ｜ 分支：`feat/modern-config-p0-core`
