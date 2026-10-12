package club.heiqi.uilib.testing.gallery;

/** 广场任务资料；只提供本地样本与纯内容派生，不承担解析、布局或资源加载。 */
final class GallerySamples {
    private GallerySamples() {}

    static String article(int selected, boolean english) {
        switch (selected) {
            case 0:
                return workshopGuide(english);
            case 1:
                return productionChecklist(english);
            case 2:
                return shiftHandover(english);
            default:
                throw new IllegalArgumentException("未知阅读资料：" + selected);
        }
    }

    private static String workshopGuide(boolean english) {
        return (english
                        ? "---\nCategory: Workshop\nStatus: Ready\n---\n\n# Workshop guide\n\n"
                                + "A production batch combines **materials**, calculations and"
                                + " operator instructions. "
                        : "---\n分类: 工坊\n状态: 已确认\n---\n\n# 工坊使用说明\n\n一份生产任务同时包含**材料**、计算和操作指引。")
                + "![工坊示意图](illustration)\n\n"
                + (english ? "For two inputs per batch: " : "每批需要两份材料，批次数与总需求的关系为：")
                + "[$N=2b$](checklist)\n\n"
                + (english
                        ? "> Keep the instructions and material count together.\n\n"
                                + "3. Check the inputs\n"
                                + "4. Confirm the batch\n"
                                + "   - Review the checklist\n\n"
                        : "> 操作指引与材料数量保持在同一文档中。\n\n3. 核对材料\n4. 确认批次\n   - 复查任务清单\n\n")
                + "---\n\n$$\nN=2b\n$$\n\n"
                + "```text\ncount = batches * 2; // $literal$ **code** <plain>\n```\n\n"
                + (english
                        ? "| Material | Quantity | Status |\n"
                                + "| :--- | ---: | :---: |\n"
                                + "| [Input](checklist) | $2b$ | **Ready** |\n"
                                + "| Water | 12 | Confirmed |\n\n"
                        : "| 材料 | 数量 | 状态 |\n"
                                + "| :--- | ---: | :---: |\n"
                                + "| [输入材料](checklist) | $2b$ | **就绪** |\n"
                                + "| 水 | 12 | 已确认 |\n\n")
                + (english
                        ? "<p>HTML notes: <strong>keep the count</strong> and <a"
                                + " href=\"checklist\">read the instructions</a>.<br>Code stays"
                                + " literal: <code>$x$ **text**</code>.</p>\n\n"
                        : "<p>HTML 备注：<strong>保留数量</strong>，并<a"
                                + " href=\"checklist\">阅读操作说明</a>。<br>代码保留字面：<code>$x$"
                                + " **文字**</code>。</p>\n\n")
                + (english
                        ? "Read the [checklist](checklist) before starting. Contact"
                                + " help@example.test."
                        : "开始前阅读[任务清单](checklist)。联系 help@example.test。");
    }

    private static String productionChecklist(boolean english) {
        return "---\ntags: [workshop, production, handover]\nowner:\n  name: Lin\n  team: precision\nrevision: 3\n---\n\n"
                + (english
                        ? "# Production checklist\n\nProduction check: verify materials before confirming equipment and handover.\n\n"
                        : "# 排产核对单\n\n排产核对：先核实物料，再确认设备与交接。\n\n")
                + (english
                        ? "> Before starting\n> 1. Review incoming materials\n>    - Check component labels\n>    - Keep the latest inspection record\n> 2. Confirm the equipment\n>    > Do not release a batch with an unresolved alarm.\n\n"
                        : "> 开工前核对\n> 1. 复核来料\n>    - 检查组件标签\n>    - 保留最近一次检验记录\n> 2. 确认设备\n>    > 未解除告警的批次不得放行。\n\n")
                + (english
                        ? "## Production allocation\n\n| Stage | Planned quantity | Responsible team |\n"
                        : "## 工序分配\n\n| 工序 | 计划数量 | 责任班组 |\n")
                + "| :--- | ---: | :---: |\n"
                + (english
                        ? "| [Material review](checklist) | $2b$ | Incoming |\n| Assembly | $4b$ | Precision |\n| Inspection | $\\frac{4b}{2}$ | Quality |\n\n"
                        : "| [物料复核](checklist) | $2b$ | 来料组 |\n| 装配 | $4b$ | 精密组 |\n| 检验 | $\\frac{4b}{2}$ | 质量组 |\n\n")
                + (english
                        ? "## Scheduling command\n\nKeep the complete command on one line; scroll inside the code block to inspect the handover fields.\n\n"
                        : "## 排产指令\n\n指令保留一整行；在代码块内横向滚动，查看后方的交接字段。\n\n")
                + "```text\nproduction_order --material precision_component --batch 3 --operator Lin --inspection required --handover next_shift --reserve warehouse_east --equipment assembly_station --release_after_quality_review true\n  literal = '$x$ **code** <plain>';\n```\n\n"
                + (english
                        ? "## Capacity reconciliation\n\n| Rule | Check |\n| :--- | ---: |\n| Unbroken allocation | $"
                        : "## 产能核对\n\n| 规则 | 核对关系 |\n| :--- | ---: |\n| 不可拆分的工序分配 | $")
                + String.join("+", java.util.Collections.nCopies(48, "q_{batch}"))
                + "$ |\n\n"
                + (english
                        ? "<p>Safety: temperature &lt; 80°C &amp; keep ventilation.<br><a href=\"checklist\">Read instructions</a> <img src=\"illustration\" width=\"64\" height=\"64\" alt=\"Allocation illustration\"></p>\n\n"
                        : "<p>安全提示：温度 &lt; 80°C &amp; 保持通风。<br><a href=\"checklist\">阅读操作说明</a> <img src=\"illustration\" width=\"64\" height=\"64\" alt=\"排产插图\"></p>\n\n")
                + (english
                        ? "After review, record the responsible team and return to the workshop guide for the operating sequence."
                        : "完成核对后记录责任班组，再返回工坊指南查看实际操作顺序。");
    }

    private static String shiftHandover(boolean english) {
        return (english
                        ? "# Shift handover\n\nThe next operator needs the accepted quantity, pending inspection and the current equipment state.\n\n"
                        : "# 交接备忘\n\n下一位操作员需要知道已验收数量、待检事项和当前设备状态。\n\n")
                + (english
                        ? "1. Record accepted components\n   - Keep the inspection label with the batch\n2. Describe pending tasks\n   - Check alarms before restarting\n3. Sign the handover record\n\n"
                        : "1. 记录已验收组件\n   - 检验标签随批次保留\n2. 说明待办事项\n   - 重启前先核对告警\n3. 签署交接记录\n\n")
                + (english
                        ? "| Entry | Note |\n| :--- | :--- |\n| Operator | café / العربية / हिन्दी / 👩‍💻 |\n| Equipment | Confirm the current state on site |\n\n"
                        : "| 交接项 | 备注 |\n| :--- | :--- |\n| 操作员 | 小明 / café / العربية / हिन्दी / 👩‍💻 |\n| 设备 | 在现场确认当前状态 |\n\n")
                + (english
                        ? "> A signed record keeps the decision traceable.\n\n[Read instructions](checklist) ![Handover illustration](illustration)"
                        : "> 签署记录，让本次决定能够追溯。\n\n[阅读操作说明](checklist) ![交接插图](illustration)");
    }

    static String recipe(boolean english, int batches) {
        return (english ? "# Recipe: precision component\n\n" : "# 配方：精密组件\n\n")
                + "<img src=\"material\" width=\"48\" height=\"48\" alt=\"材料图像\"/>\n\n"
                + (english ? "Material consumption: " : "材料消耗：")
                + "$2 \\times "
                + batches
                + "="
                + batches * 2
                + "$\n\n"
                + (english ? "Output: " : "组件产出：")
                + "$4 \\times "
                + batches
                + "="
                + batches * 4
                + "$\n\n"
                + (english
                        ? "## Bill of materials\n\n| Material | Per batch | Current plan |\n"
                        : "## 配方清单\n\n| 材料 | 每批用量 | 当前计划 |\n")
                + "| :--- | ---: | ---: |\n"
                + (english ? "| Precision blank | 2 | " : "| 精密坯料 | 2 | ")
                + batches * 2
                + " |\n"
                + (english ? "| Coolant (mL) | 12 | " : "| 冷却液（mL） | 12 | ")
                + batches * 12
                + " |\n"
                + (english ? "| Calibration strip | 1 | " : "| 校准片 | 1 | ")
                + batches
                + " |\n"
                + (english ? "| Accepted components | 4 | " : "| 合格组件 | 4 | ")
                + batches * 4
                + " |\n\n"
                + (english
                        ? "## Planning checks\n\nMaterial per component: "
                        : "## 计划核对\n\n单件材料用量：")
                + "$\\frac{"
                + batches * 2
                + "}{"
                + batches * 4
                + "}=\\frac{1}{2}$\n\n"
                + (english ? "Square packing side: " : "方形托盘边长：")
                + "$\\sqrt{"
                + batches * 4
                + "}$\n\n"
                + (english ? "Two-component inspection pairs: " : "双件抽检组合数：")
                + "$\\binom{"
                + batches * 4
                + "}{2}="
                + (batches * 4 * (batches * 4 - 1) / 2)
                + "$\n\n"
                + (english ? "Material vector × selected batches:\n\n" : "物料向量乘以所选批次：\n\n")
                + "$$\n\\begin{bmatrix}2\\\\12\\\\1\\end{bmatrix}\\times "
                + batches
                + "=\\begin{bmatrix}"
                + batches * 2
                + "\\\\"
                + batches * 12
                + "\\\\"
                + batches
                + "\\end{bmatrix}\n$$\n\n"
                + (english
                        ? "1. Verify incoming labels\n2. Assemble and cool\n3. Inspect before releasing\n\n[Recipe instructions](recipe)"
                        : "1. 核对来料标签\n2. 完成装配与冷却\n3. 检验后放行\n\n[查看配方说明](recipe)");
    }

    static String materialFormula(int batches) {
        return "\\left(\\frac{\\embed{material}}{2}\\middle|\\sqrt{"
                + batches * 4
                + "}\\right)\\quad N="
                + batches * 2;
    }

    /** 三道工序是同一周期的数据来源，汇总和表格都消费该快照。 */
    static int[][] reportRows(int days) {
        return new int[][] {{4 * days, 3 * days}, {5 * days, 4 * days}, {3 * days, 2 * days}};
    }

    static String reportSummary(int[][] rows) {
        int input = 0, completed = 0;
        for (int[] row : rows) {
            input += row[0];
            completed += row[1];
        }
        return "生产汇总：投入 " + input + "；完成 " + completed + "；待处理 " + (input - completed);
    }

    static String report(boolean english, int days, int[][] rows) {
        int input = 0, completed = 0;
        for (int[] row : rows) {
            input += row[0];
            completed += row[1];
        }
        String[] stages =
                english
                        ? new String[] {"Cutting", "Assembly", "Inspection"}
                        : new String[] {"备料", "装配", "检验"};
        StringBuilder source =
                new StringBuilder(english ? "# Production report\n\n" : "# 生产报告\n\n");
        source.append(
                days == 1
                        ? (english ? "Period: today" : "统计周期：今日")
                        : (english ? "Period: this week" : "统计周期：本周"));
        source.append("\n\n$")
                .append(input)
                .append("=")
                .append(completed)
                .append("+")
                .append(input - completed)
                .append("$\n\n");
        source.append(english ? "Completion rate: " : "完成率：")
                .append("$\\frac{")
                .append(completed)
                .append("}{")
                .append(input)
                .append("}=")
                .append(completed * 100 / input)
                .append("\\%$\n\n");
        source.append(
                english
                        ? "## Stage reconciliation\n\n| Stage | Input | Completed | Pending |\n"
                        : "## 工序对账\n\n| 工序 | 投入 | 完成 | 待处理 |\n");
        source.append("| :--- | ---: | ---: | ---: |\n");
        for (int i = 0; i < rows.length; i++)
            source.append("| ")
                    .append(stages[i])
                    .append(" | ")
                    .append(rows[i][0])
                    .append(" | ")
                    .append(rows[i][1])
                    .append(" | ")
                    .append(rows[i][0] - rows[i][1])
                    .append(" |\n");
        source.append(english ? "| **Total** | " : "| **合计** | ")
                .append(input)
                .append(" | ")
                .append(completed)
                .append(" | ")
                .append(input - completed)
                .append(" |\n\n");
        source.append(
                english
                        ? "## Completion reconciliation\n\nThe stage sum equals the accumulated output at a steady nine completions per day. The bar marks the daily mean.\n\n"
                        : "## 完成量核对\n\n各工序完成量求和，等于每日稳定完成九项时的累计产出；上横线表示日均完成量。\n\n");
        source.append("$$\n\\begin{aligned}C&=\\sum_{i=1}^{3}c_i=")
                .append(completed)
                .append("\\\\C&=\\int_{0}^{")
                .append(days)
                .append("}9\\,dt=")
                .append(completed)
                .append("\\\\\\bar{c}&=\\frac{C}{")
                .append(days)
                .append("}=9\\end{aligned}\n$$\n\n");
        source.append(english ? "## Handover queue\n\n" : "## 交接待办\n\n");
        for (int i = 0; i < rows.length; i++)
            source.append(i + 1)
                    .append(". ")
                    .append(stages[i])
                    .append(english ? ": review " : "：复核 ")
                    .append(rows[i][0] - rows[i][1])
                    .append(english ? " pending tasks\n" : " 项待处理任务\n");
        source.append(
                english
                        ? "\nPending tasks remain in the selected period. Verify the stage totals before signing the handover.\n\n"
                        : "\n待处理任务计入所选周期；交接签署前逐工序复核。数字、表格和公式随周期一起更新。\n\n");
        source.append("<img src=\"illustration\" width=\"64\" height=\"64\" alt=\"报告标识\"/> ");
        source.append(english ? "[Report details](report)" : "[查看报告详情](report)");
        return source.toString();
    }

    static String message(boolean english, int id) {
        return (english
                        ? "Operator: café / العربية / हिन्दी. "
                        : "操作员：小明 / café / العربية / हिन्दी。")
                + (english ? "Completed " : "完成 ")
                + "<latex>2+2=4</latex>"
                + (english ? " batches.\n\nHandover #" : " 批次。\n\n交接记录 #")
                + id
                + (english
                        ? ": components have passed the first inspection; the next shift must check the coolant and calibration label before release.\n\n"
                        : "：组件已完成首轮检验；下一班放行前需复核冷却液与校准标签。\n\n")
                + (english
                        ? "Incoming review → assembly → inspection. Keep this record with the batch; report an unresolved alarm to the responsible operator.\n\n"
                        : "来料复核 → 装配 → 检验。记录随批次保留；未解除的告警交责任操作员处理。\n\n")
                + "<img src=\"illustration\" width=\"32\" height=\"32\" alt=\"交接标识\"/> <a href=\"activity\">"
                + (english ? "Activity details" : "查看活动记录")
                + "</a>";
    }
}
