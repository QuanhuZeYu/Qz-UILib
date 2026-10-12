package club.heiqi.uilib.ui.scene.text.math;

/** 数学样式层级；脚本字号始终相对本次布局的根 em。 */
public enum MathStyle {
    DISPLAY,
    TEXT,
    SCRIPT,
    SCRIPT_SCRIPT;

    MathStyle script() {
        return this == DISPLAY || this == TEXT ? SCRIPT : SCRIPT_SCRIPT;
    }

    MathStyle fraction() {
        return this == DISPLAY ? TEXT : script();
    }
}
