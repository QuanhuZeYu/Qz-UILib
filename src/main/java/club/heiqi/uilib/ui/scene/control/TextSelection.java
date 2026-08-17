package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

/**
 * TextSelection —— 文本选区模型（anchor/focus 双码点索引）。
 *
 * <p>受控文本控件的<b>本地 UI 态</b>：文本真值仍由外部 {@code value} 唯一持有，
 * 选区与 caret 同属控件内部状态，不进入 value。语义与 caret 的关系：
 * {@code focusCp} 即 caret 位置（选区折叠时 anchor==focus）。</p>
 *
 * <ul>
 *   <li>选区激活：{@code anchorCp != focusCp}（{@link #isActive()}）。</li>
 *   <li>归一化区间：{@link #startCp()} = min(anchor, focus)，{@link #endCp()} = max，
 *       半开区间 {@code [startCp, endCp)} 即选中文本码点范围。</li>
 *   <li>拖选/Shift 扩展：anchor 固定、focus 随指针/按键移动（{@link #withFocus(int)}）。</li>
 * </ul>
 *
 * <p>不可变值对象：所有操作返回新实例，索引均按 Unicode 码点语义。</p>
 */
@Desugar
public record TextSelection(int anchorCp, int focusCp) {

    /**
     * 构造选区（紧凑构造器做非负校验）。
     *
     * @param anchorCp 选区锚点码点索引
     * @param focusCp 选区焦点码点索引（即 caret）
     * @throws IllegalArgumentException 任一索引为负
     */
    public TextSelection {
        if (anchorCp < 0 || focusCp < 0) {
            throw new IllegalArgumentException(
                    "selection index must be non-negative: anchor=" + anchorCp + ", focus=" + focusCp);
        }
    }

    /**
     * 创建折叠选区（无选区，caret 位于指定位置）。
     *
     * @param caret caret 码点索引
     * @return 折叠选区
     */
    public static TextSelection collapsed(int caret) {
        return new TextSelection(caret, caret);
    }

    /**
     * 创建指定锚点与焦点的选区。
     *
     * @param anchor 锚点码点索引
     * @param focus 焦点码点索引
     * @return 选区实例
     */
    public static TextSelection of(int anchor, int focus) {
        return new TextSelection(anchor, focus);
    }

    /**
     * @return 选区是否激活（anchor 与 focus 不重合）
     */
    public boolean isActive() {
        return anchorCp != focusCp;
    }

    /**
     * @return 归一化选区起点（min(anchor, focus)）
     */
    public int startCp() {
        return Math.min(anchorCp, focusCp);
    }

    /**
     * @return 归一化选区终点（max(anchor, focus)，半开区间上界）
     */
    public int endCp() {
        return Math.max(anchorCp, focusCp);
    }

    /**
     * 以当前 anchor 为基准移动焦点（拖选/Shift 扩展语义）。
     *
     * @param newFocus 新焦点码点索引
     * @return 新选区（anchor 不变）
     */
    public TextSelection withFocus(int newFocus) {
        return new TextSelection(anchorCp, newFocus);
    }
}
