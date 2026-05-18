package club.heiqi.uilib.ui.dom;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 类似浏览器 DOMTokenList 的 class 列表管理器。
 *
 * <p>提供 add/remove/toggle/contains/replace 等标准操作，
 * 内部维护有序去重的 token 集合，变更时通知宿主元素触发样式重算。</p>
 */
public final class DomTokenList implements Iterable<String> {

    private final LinkedHashSet<String> tokens = new LinkedHashSet<String>();
    private final Runnable changeCallback;

    /**
     * 创建 DomTokenList。
     *
     * @param changeCallback token 变更时的回调；不为 null
     */
    DomTokenList(Runnable changeCallback) {
        this.changeCallback = Objects.requireNonNull(changeCallback, "changeCallback");
    }

    /**
     * 返回当前 token 数量。
     *
     * @return token 数量
     */
    public int length() {
        return tokens.size();
    }

    /**
     * 判断是否包含指定 token。
     *
     * @param token 待检查的 token
     * @return 是否包含
     */
    public boolean contains(String token) {
        return tokens.contains(normalizeToken(token));
    }

    /**
     * 添加一个或多个 token。
     *
     * @param tokenValues 待添加的 token
     */
    public void add(String... tokenValues) {
        boolean changed = false;
        for (String token : tokenValues) {
            if (tokens.add(normalizeToken(token))) {
                changed = true;
            }
        }
        if (changed) {
            changeCallback.run();
        }
    }

    /**
     * 移除一个或多个 token。
     *
     * @param tokenValues 待移除的 token
     */
    public void remove(String... tokenValues) {
        boolean changed = false;
        for (String token : tokenValues) {
            if (tokens.remove(normalizeToken(token))) {
                changed = true;
            }
        }
        if (changed) {
            changeCallback.run();
        }
    }

    /**
     * 切换指定 token 的存在状态。
     *
     * <p>如果 token 存在则移除并返回 false，不存在则添加并返回 true。</p>
     *
     * @param token 待切换的 token
     * @return 切换后 token 是否存在
     */
    public boolean toggle(String token) {
        String normalized = normalizeToken(token);
        boolean result;
        if (tokens.contains(normalized)) {
            tokens.remove(normalized);
            result = false;
        } else {
            tokens.add(normalized);
            result = true;
        }
        changeCallback.run();
        return result;
    }

    /**
     * 强制设置 token 的存在状态。
     *
     * @param token 目标 token
     * @param force true 则添加，false 则移除
     * @return force 参数值
     */
    public boolean toggle(String token, boolean force) {
        if (force) {
            add(token);
        } else {
            remove(token);
        }
        return force;
    }

    /**
     * 替换一个 token 为另一个。
     *
     * @param oldToken 旧 token
     * @param newToken 新 token
     * @return 是否成功替换（旧 token 存在时返回 true）
     */
    public boolean replace(String oldToken, String newToken) {
        String normalizedOld = normalizeToken(oldToken);
        String normalizedNew = normalizeToken(newToken);
        if (!tokens.contains(normalizedOld)) {
            return false;
        }
        // 保持插入顺序：重建集合
        LinkedHashSet<String> newSet = new LinkedHashSet<String>(tokens.size());
        for (String existing : tokens) {
            if (existing.equals(normalizedOld)) {
                newSet.add(normalizedNew);
            } else {
                newSet.add(existing);
            }
        }
        tokens.clear();
        tokens.addAll(newSet);
        changeCallback.run();
        return true;
    }

    /**
     * 返回空格分隔的 token 字符串表示。
     *
     * @return className 字符串
     */
    public String value() {
        if (tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String token : tokens) {
            if (!first) {
                sb.append(' ');
            }
            sb.append(token);
            first = false;
        }
        return sb.toString();
    }

    /**
     * 从空格分隔的字符串批量设置 token。
     *
     * <p>会清除现有 token 并重新解析。</p>
     *
     * @param className 空格分隔的 class 字符串；为 null 或空时清空列表
     */
    void setValue(String className) {
        tokens.clear();
        if (className != null && !className.isEmpty()) {
            for (String part : className.split("\\s+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }
        }
        changeCallback.run();
    }

    /**
     * 返回只读的 token 集合视图。
     *
     * @return 只读 token 集合
     */
    public Set<String> toSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(tokens));
    }

    @Override
    public Iterator<String> iterator() {
        return Collections.unmodifiableSet(tokens).iterator();
    }

    @Override
    public String toString() {
        return value();
    }

    /**
     * 规范化 token：不允许空白和空字符串。
     */
    private static String normalizeToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        if (token.contains(" ") || token.contains("\t") || token.contains("\n")) {
            throw new IllegalArgumentException("Token cannot contain whitespace: '" + token + "'");
        }
        return token;
    }
}
