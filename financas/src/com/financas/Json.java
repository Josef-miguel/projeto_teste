package com.financas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parser e gerador de JSON minimalista (sem dependências externas). */
public final class Json {
    private Json() {}

    // ===================== escrita =====================

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(64);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) sb.append("null");
            else sb.append(d);
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else if (v instanceof Object[] arr) {
            writeValue(sb, Arrays.asList(arr));
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ===================== leitura =====================

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.eof()) throw p.err("conteúdo sobrando");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JSON inválido: objeto esperado");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean eof() {
            return i >= s.length();
        }

        char peek() {
            if (eof()) throw err("fim inesperado");
            return s.charAt(i);
        }

        IllegalArgumentException err(String m) {
            return new IllegalArgumentException("JSON inválido: " + m + " (posição " + i + ")");
        }

        void skipWs() {
            while (!eof() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            return switch (peek()) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> {
                    expect("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    expect("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    expect("null");
                    yield null;
                }
                default -> parseNumber();
            };
        }

        void expect(String word) {
            if (!s.startsWith(word, i)) throw err("literal inválido");
            i += word.length();
        }

        Map<String, Object> parseObject() {
            i++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                if (peek() != '"') throw err("chave esperada");
                String key = parseString();
                skipWs();
                if (peek() != ':') throw err("':' esperado");
                i++;
                map.put(key, parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    return map;
                }
                throw err("',' ou '}' esperado");
            }
        }

        List<Object> parseArray() {
            i++; // [
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    return list;
                }
                throw err("',' ou ']' esperado");
            }
        }

        String parseString() {
            i++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) throw err("string não fechada");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (eof()) throw err("escape incompleto");
                    char e = s.charAt(i++);
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
                            if (i + 4 > s.length()) throw err("unicode inválido");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw err("escape inválido");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object parseNumber() {
            int start = i;
            while (!eof() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.isEmpty()) throw err("número inválido");
            try {
                if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                    return Long.parseLong(num);
                }
                return Double.parseDouble(num);
            } catch (NumberFormatException e) {
                throw err("número inválido");
            }
        }
    }
}
