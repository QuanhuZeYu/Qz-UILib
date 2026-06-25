# DECISION-20260625 performLayout 步骤顺序重构（技术债登记）

## 状态

技术债登记，待排期。非阻断，不影响当前合并。

## 背景

ROW 交叉轴居中 bug 修复（commit `3fe1a1b7`）暴露了 `SceneLayoutEngine.performLayout`
五步骤顺序的结构性缺陷。

### 当前步骤顺序（`SceneLayoutEngine.java:462-614`）

1. 步骤1（:472）：算 outerWidth / innerWidth（宽度维度，top-down 约束来）
2. 步骤2（:483-501）：遍历子 cache，算 mainContentSize / crossMax
3. 步骤3（:503-519）：算 mainAvail / mainStart
4. 步骤4（:521-596）：逐子定位（crossAvail 在此，:538）
5. 步骤5（:598-614）：算 root 自身高度并写 LayoutBox

### 缺陷

宽度在步骤1 就有单一权威 `computeWidth`，全程复用。
高度**没有对称的单一权威**——`rootFinalHeight` 被迫在步骤4 前临时算（:528），
夹在子节点定位循环之前。任何未来在步骤2-3 之间需要容器高度的逻辑，
都会发现高度"还没算"。这是 bug 的结构性温床。

根因：bottom-up 模型下高度逻辑上要等子节点布局完才能算，**但步骤顺序把
"容器自身尺寸"拆散在首尾**（宽度在首、高度在尾），导致定位子节点时
依赖了本应在其后才算的容器高度。

## 更优的步骤划分（架构建议，非本次改）

```
步骤 A：解析容器自身盒尺寸（width=computeWidth；height=computeHeight 的纯读部分）
步骤 B：算主轴/交叉轴可用空间（mainAvail / crossAvail，全部基于 A 的容器内尺寸）
步骤 C：定位子节点（消费 A、B，不再回算容器尺寸）
步骤 D：写容器 LayoutBox（直接用 A 的结果）
```

### 收益
- height 与 width 在步骤顺序上对称，crossAvail 在定位子节点前已是一等公民
- `computeHeight` 不再被夹在子节点循环里，消除"提前调"的颠倒
- 天然斩断 STRETCH 自反馈（容器高在步骤A 锁定，步骤C 才 STRETCH 改子高，不回灌）

### 代价
- 动 performLayout 核心顺序（约 30-50 行重排）
- 重跑全套 layout 测试（300+）
- 不改 bottom-up 模型本身（computeHeight 仍读子 cache，子仍后序递归先布局完）

## 为什么不在本次做

- 违反单一变更原则（本次是 bug 修复，不应夹带框架重构）
- reviewer 已基于现框架验过，重构后需重新审核
- 当前修复已正确且真机验证通过，技术债不阻断合并

## 关联

- bug 修复：commit `3fe1a1b7`
- oracle 架构复核：裁定"这次改对了用什么基准，下次该改对什么时候算基准"
- computeHeight 幂等护栏测试也建议后续补（防未来给纯读函数加副作用后"提前调"变隐患）
