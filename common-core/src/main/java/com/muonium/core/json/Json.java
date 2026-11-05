package com.muonium.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖的安全 JSON 编解码器。
 *
 * <p>安全约束对应 docs/security.md：
 * 限制输入长度、嵌套深度、元素数量、字符串长度；拒绝原型污染键；
 * JSON 之外不执行任何脚本；不吞解析异常。
 */
public final class Json {

    public static final int MAX_INPUT_CHARS = 1_000_000;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_ELEMENTS = 100_000;

    private static final List<String> POLLUTION_KEYS =
            List.of("__proto__", "constructor", "prototype");

    private Json() {}

    public static Map<String, Object> parseObject(String text) {
        Object v = new Parser(text, false).parseDocument();
        if (!(v instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("JSON root must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) m;
        return result;
    }

    /** 允许任意 JSON 根值（测试向量用）。 */
    public static Object parseValue(String text) {
        return new Parser(text, true).parseDocument();
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON nesting too deep");
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { writeString(sb, s); return; }
        if (v instanceof Boolean b) { sb.append(b); return; }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) { sb.append("null"); return; }
            if (n instanceof Double || n instanceof Float) sb.append(d);
            else sb.append(n.longValue());
            return;
        }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o, depth + 1);
            }
            sb.append(']');
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value type: " + v.getClass());
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private final boolean allowAnyRoot;
        private int p;
        private int depth;
        private int elements;

        Parser(String text, boolean allowAnyRoot) {
            if (text == null) throw new IllegalArgumentException("JSON text is null");
            if (text.length() > MAX_INPUT_CHARS) {
                throw new IllegalArgumentException("JSON input too large: " + text.length());
            }
            this.s = text;
            this.allowAnyRoot = allowAnyRoot;
        }

        Object parseDocument() {
            skipWs();
            Object v = parseValue();
            skipWs();
            if (p != s.length()) throw error("Trailing content at " + p);
            if (!allowAnyRoot && !(v instanceof Map)) throw error("Root must be object");
            return v;
        }

        private Object parseValue() {
            if (++elements > MAX_ELEMENTS) throw error("Too many JSON elements");
            skipWs();
            if (p >= s.length()) throw error("Unexpected end of input");
            char c = s.charAt(p);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield parseNumber();
                    throw error("Unexpected character '" + c + "' at " + p);
                }
            };
        }

        private Map<String, Object> parseObject() {
            enter();
            p++; // {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (p < s.length() && s.charAt(p) == '}') { p++; leave(); return m; }
            while (true) {
                skipWs();
                if (p >= s.length() || s.charAt(p) != '"') throw error("Expected object key at " + p);
                String key = parseString();
                for (String bad : POLLUTION_KEYS) {
                    if (bad.equals(key)) throw error("Prototype-pollution key rejected: " + key);
                }
                skipWs();
                if (p >= s.length() || s.charAt(p) != ':') throw error("Expected ':' at " + p);
                p++;
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                if (p >= s.length()) throw error("Unterminated object");
                char c = s.charAt(p++);
                if (c == '}') { leave(); return m; }
                if (c != ',') throw error("Expected ',' or '}' at " + (p - 1));
            }
        }

        private List<Object> parseArray() {
            enter();
            p++; // [
            ArrayList<Object> a = new ArrayList<>();
            skipWs();
            if (p < s.length() && s.charAt(p) == ']') { p++; leave(); return a; }
            while (true) {
                a.add(parseValue());
                skipWs();
                if (p >= s.length()) throw error("Unterminated array");
                char c = s.charAt(p++);
                if (c == ']') { leave(); return a; }
                if (c != ',') throw error("Expected ',' or ']' at " + (p - 1));
            }
        }

        private String parseString() {
            if (s.charAt(p) != '"') throw error("Expected string at " + p);
            p++;
            StringBuilder sb = new StringBuilder();
            while (p < s.length()) {
                char c = s.charAt(p++);
                if (c == '"') {
                    if (sb.length() > MAX_INPUT_CHARS) throw error("String too long");
                    return sb.toString();
                }
                if (c == '\\') {
                    if (p >= s.length()) throw error("Bad escape");
                    char e = s.charAt(p++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (p + 4 > s.length()) throw error("Bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                            p += 4;
                        }
                        default -> throw error("Invalid escape \\" + e);
                    }
                } else if (c < 0x20) {
                    throw error("Unescaped control character at " + (p - 1));
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Object parseNumber() {
            int start = p;
            if (s.charAt(p) == '-') p++;
            while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
            boolean fp = false;
            if (p < s.length() && s.charAt(p) == '.') { fp = true; p++; while (p < s.length() && Character.isDigit(s.charAt(p))) p++; }
            if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
                fp = true; p++;
                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
            }
            String num = s.substring(start, p);
            try {
                return fp ? (Object) Double.valueOf(num) : (Object) Long.valueOf(num);
            } catch (NumberFormatException ex) {
                throw error("Invalid number '" + num + "'");
            }
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, p)) throw error("Expected " + word + " at " + p);
            p += word.length();
            return value;
        }

        private void enter() {
            if (++depth > MAX_DEPTH) throw error("JSON nesting too deep (> " + MAX_DEPTH + ")");
        }
        private void leave() { depth--; }

        private void skipWs() {
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') p++;
                else break;
            }
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON parse error: " + msg);
        }
    }
}
