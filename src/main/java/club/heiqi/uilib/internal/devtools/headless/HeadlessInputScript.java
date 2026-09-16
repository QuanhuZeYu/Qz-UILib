package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;

/**
 * 输入脚本：把一段可读文本编译为 {@link HeadlessInputDevice} 上的动作序列。
 *
 * <h3>语法（每条语句用换行或 {@code ;} 分隔，{@code #} 起注释）</h3>
 * <pre>
 * move X Y            移动到逻辑坐标
 * moveby DX DY        相对移动
 * down|up [BUTTON]    按下 / 抬起（默认 LEFT）
 * click [BUTTON]      点击（按下与抬起自动跨帧）
 * dblclick [BUTTON]   双击
 * scroll DY           纵向滚轮
 * scroll DX DY        横纵滚轮
 * keydown|keyup|key KEY  按键（key 为按下 + 跨帧抬起）
 * type TEXT           逐字符输入（char 路径）
 * compose TEXT        整串提交（外部文本接管 / IME 语义）
 * cancel              指针取消（失焦）
 * frame               插入帧边界
 * wait N              空推进 N 帧
 * </pre>
 *
 * <p>为什么需要脚本层：agent 的典型诉求是「点到某个按钮 → 再出一张图」，
 * 这要求一次调用里既描述交互又描述出图时机。脚本是这套动作语义的文本载体，
 * 与设备模型同源，不引入第二条输入路径。</p>
 */
public final class HeadlessInputScript {

    private HeadlessInputScript() {
    }

    /**
     * 把脚本文本编译进设备。
     *
     * @param device 目标设备
     * @param script 脚本文本；null / 空串表示无脚本
     * @return 编译出的语句数
     */
    public static int apply(HeadlessInputDevice device, String script) {
        if (device == null) {
            throw new IllegalArgumentException("device 不可为空");
        }
        if (script == null || script.trim().isEmpty()) {
            return 0;
        }
        List<String> statements = splitStatements(script);
        for (String statement : statements) {
            applyStatement(device, statement);
        }
        return statements.size();
    }

    /**
     * 切分语句：按换行与分号断开，去掉注释与空白，丢弃空语句。
     *
     * @param script 脚本文本
     * @return 语句列表
     */
    public static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<String>();
        for (String rawLine : script.split("[\\r\\n;]")) {
            String line = rawLine;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (!line.isEmpty()) {
                statements.add(line);
            }
        }
        return statements;
    }

    private static void applyStatement(HeadlessInputDevice device, String statement) {
        String[] parts = statement.split("\\s+", 2);
        String keyword = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        if ("move".equals(keyword)) {
            int[] xy = twoInts(rest, statement);
            device.moveTo(xy[0], xy[1]);
        } else if ("moveby".equals(keyword)) {
            int[] xy = twoInts(rest, statement);
            device.moveBy(xy[0], xy[1]);
        } else if ("down".equals(keyword)) {
            device.press(parseButton(rest));
        } else if ("up".equals(keyword)) {
            device.release(parseButton(rest));
        } else if ("click".equals(keyword)) {
            device.click(parseButton(rest));
        } else if ("dblclick".equals(keyword)) {
            device.doubleClick(parseButton(rest));
        } else if ("scroll".equals(keyword)) {
            int[] deltas = ints(rest, statement);
            if (deltas.length == 1) {
                device.scroll(deltas[0]);
            } else if (deltas.length == 2) {
                device.scroll(deltas[0], deltas[1]);
            } else {
                throw badStatement(statement, "scroll 需要 1~2 个整数");
            }
        } else if ("keydown".equals(keyword)) {
            device.keyDown(parseKey(rest, statement));
        } else if ("keyup".equals(keyword)) {
            device.keyUp(parseKey(rest, statement));
        } else if ("key".equals(keyword)) {
            device.pressKey(parseKey(rest, statement));
        } else if ("type".equals(keyword)) {
            device.type(rest);
        } else if ("compose".equals(keyword)) {
            device.compose(rest);
        } else if ("cancel".equals(keyword)) {
            device.cancelPointer();
        } else if ("frame".equals(keyword)) {
            device.frame();
        } else if ("wait".equals(keyword)) {
            int[] frames = ints(rest, statement);
            if (frames.length != 1) {
                throw badStatement(statement, "wait 需要 1 个整数");
            }
            device.waitFrames(frames[0]);
        } else {
            throw badStatement(statement, "未知语句关键字：" + keyword);
        }
    }

    private static HeadlessFailure badStatement(String statement, String reason) {
        return new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                "输入脚本无法解析（" + reason + "）：" + statement);
    }

    private static SceneMouseButton parseButton(String token) {
        if (token == null || token.isEmpty()) {
            return SceneMouseButton.LEFT;
        }
        try {
            return SceneMouseButton.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY, "未知鼠标按钮：" + token);
        }
    }

    private static SceneKey parseKey(String token, String statement) {
        if (token == null || token.isEmpty()) {
            throw badStatement(statement, "缺少按键名");
        }
        String name = token.trim().toUpperCase(Locale.ROOT);
        if (name.length() == 1) {
            char ch = name.charAt(0);
            if (ch >= 'A' && ch <= 'Z') {
                name = "KEY_" + name;
            } else if (Character.isDigit(ch)) {
                name = "DIGIT_" + name;
            }
        }
        try {
            return SceneKey.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw badStatement(statement, "未知按键名：" + token);
        }
    }

    private static int[] twoInts(String rest, String statement) {
        int[] values = ints(rest, statement);
        if (values.length != 2) {
            throw badStatement(statement, "需要 2 个整数");
        }
        return values;
    }

    private static int[] ints(String rest, String statement) {
        if (rest.isEmpty()) {
            return new int[0];
        }
        String[] tokens = rest.split("\\s+");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                values[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                throw badStatement(statement, "不是整数：" + tokens[i]);
            }
        }
        return values;
    }
}
