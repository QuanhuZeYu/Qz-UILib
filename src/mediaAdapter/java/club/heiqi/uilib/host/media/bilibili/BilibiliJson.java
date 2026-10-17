package club.heiqi.uilib.host.media.bilibili;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 有限JSON语法及纯值读取；每个语法产生式各自消费游标，不构造平台播放策略。 */
final class BilibiliJson {
    private final String text;
    private int at, nodes;
    private BilibiliJson(String text) { this.text = text; }

    static Object parse(String text) throws IOException {
        BilibiliJson parser = new BilibiliJson(text);
        Object result = parser.value(0);
        parser.space();
        if (parser.at != text.length()) throw parser.error();
        return result;
    }

    private Object value(int depth) throws IOException {
        space();
        if (depth > 64 || ++nodes > 200000 || at >= text.length()) throw error();
        switch (text.charAt(at)) {
            case '"': return string();
            case '{': return object(depth);
            case '[': return array(depth);
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default: return number();
        }
    }

    private Map<String, Object> object(int depth) throws IOException {
        need('{');
        Map<String, Object> result = new LinkedHashMap<>();
        space();
        if (take('}')) return result;
        do {
            space();
            if (at >= text.length() || text.charAt(at) != '"') throw error();
            String key = string();
            space(); need(':');
            if (result.containsKey(key)) throw error();
            result.put(key, value(depth + 1));
            space();
            if (take('}')) return result;
            need(',');
        } while (true);
    }

    private List<Object> array(int depth) throws IOException {
        need('[');
        List<Object> result = new ArrayList<>();
        space();
        if (take(']')) return result;
        do {
            result.add(value(depth + 1));
            space();
            if (take(']')) return result;
            need(',');
        } while (true);
    }

    private Object literal(String word, Object result) throws IOException {
        if (!text.startsWith(word, at)) throw error();
        at += word.length();
        return result;
    }

    private Number number() throws IOException {
        int start = at;
        if (take('-') && at == text.length()) throw error();
        integer();
        boolean decimal = fraction();
        decimal = exponent() || decimal;
        try {
            String number = text.substring(start, at);
            if (!decimal) return Long.parseLong(number);
            double result = Double.parseDouble(number);
            if (!Double.isFinite(result)) throw error();
            return result;
        } catch (NumberFormatException failure) { throw error(); }
    }

    private void integer() throws IOException {
        if (take('0')) {
            if (at < text.length() && Character.isDigit(text.charAt(at))) throw error();
            return;
        }
        if (at >= text.length() || text.charAt(at) < '1' || text.charAt(at) > '9') throw error();
        digits();
    }

    private boolean fraction() throws IOException {
        if (!take('.')) return false;
        if (!digit()) throw error();
        digits(); return true;
    }

    private boolean exponent() throws IOException {
        if (!take('e') && !take('E')) return false;
        if (!take('+')) take('-');
        if (!digit()) throw error();
        digits(); return true;
    }

    private void digits() { while (digit()) at++; }
    private boolean digit() { return at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9'; }

    private String string() throws IOException {
        need('"');
        StringBuilder result = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') return result.toString();
            if (c < 32) throw error();
            result.append(c == '\\' ? escape() : c);
        }
        throw error();
    }

    private char escape() throws IOException {
        if (at == text.length()) throw error();
        char c = text.charAt(at++);
        switch (c) {
            case '"': case '\\': case '/': return c;
            case 'b': return '\b';
            case 'f': return '\f';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'u': return unicode();
            default: throw error();
        }
    }

    private char unicode() throws IOException {
        if (at + 4 > text.length()) throw error();
        try {
            char result = (char) Integer.parseInt(text.substring(at, at + 4), 16);
            at += 4; return result;
        } catch (NumberFormatException failure) { throw error(); }
    }

    private void space() {
        while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\n'
                || text.charAt(at) == '\r' || text.charAt(at) == '\t')) at++;
    }
    private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private void need(char c) throws IOException { if (!take(c)) throw error(); }
    private IOException error() { return new IOException("Bilibili JSON响应无效或超过结构预算，位置 " + at); }

    @SuppressWarnings("unchecked") static Map<String, Object> map(Object value) { return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap(); }
    @SuppressWarnings("unchecked") static List<Object> list(Object value) { return value instanceof List ? (List<Object>) value : Collections.emptyList(); }
    static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    static String id(Object value) { return value == null ? "" : value.toString(); }
    static String string(Map<String, Object> map, String key, String fallback) { Object value = map.get(key); return value == null ? fallback : value.toString(); }
}
