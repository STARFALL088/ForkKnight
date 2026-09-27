package forkknight.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal reader for the JSON the wider realm answers with - enough of
 * RFC 8259 for objects, arrays, strings (escapes included), numbers,
 * booleans and null, and nothing else.
 *
 * <p>A document becomes plain Java: {@code Map<String, Object>},
 * {@code List<Object>}, {@code String}, {@code Long} or {@code Double},
 * {@code Boolean} and {@code null}. The typed helpers below keep callers
 * from hand-casting every node; anything that is not the shape asked for
 * raises a {@link FormatException} naming what was found instead.
 */
public final class Json {

    /** How deep the JSON may nest before the reader refuses (stack guard). */
    private static final int MAX_DEPTH = 64;

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /** Reads one whole JSON document; anything trailing it is refused. */
    public static Object parse(String text) {
        if (text == null || text.isBlank()) {
            throw new FormatException("there is no text to read", 0);
        }
        Json reader = new Json(text);
        reader.skipSpace();
        Object value = reader.readDocument(0);
        reader.skipSpace();
        if (reader.at != text.length()) {
            throw new FormatException("the document carries on after the value",
                reader.at);
        }
        return value;
    }

    // -------------------- typed helpers --------------------

    /** The value as an object, or a refusal naming what it really is. */
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        throw new FormatException("expected a JSON object, found " + kind(value), 0);
    }

    /** The value as an array, or a refusal naming what it really is. */
    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> typed = (List<Object>) list;
            return typed;
        }
        throw new FormatException("expected a JSON array, found " + kind(value), 0);
    }

    /** The value as a string; JSON null and absent both answer null. */
    public static String string(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new FormatException("expected a JSON string, found " + kind(value), 0);
    }

    /** The value as a whole number (a real drops its fraction). */
    public static long integer(Object value) {
        if (value instanceof Long whole) {
            return whole;
        }
        if (value instanceof Double real) {
            return real.longValue();
        }
        throw new FormatException("expected a JSON number, found " + kind(value), 0);
    }

    // -------------------- the reader --------------------

    private Object readDocument(int depth) {
        if (at >= text.length()) {
            throw new FormatException("the document ends too soon", at);
        }
        char c = text.charAt(at);
        switch (c) {
            case '{': return readObject(depth);
            case '[': return readArray(depth);
            case '"': return readQuoted();
            case 't': return readLiteral("true", Boolean.TRUE);
            case 'f': return readLiteral("false", Boolean.FALSE);
            case 'n': return readLiteral("null", null);
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject(int depth) {
        guard(depth);
        at++; // {
        Map<String, Object> map = new LinkedHashMap<>();
        skipSpace();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw new FormatException("an object key must be a string", at);
            }
            String key = readQuoted();
            skipSpace();
            if (peek() != ':') {
                throw new FormatException("expected ':' after the key '" + key + "'", at);
            }
            at++;
            skipSpace();
            map.put(key, readDocument(depth + 1));
            skipSpace();
            char c = peek();
            if (c == ',') {
                at++;
                continue;
            }
            if (c == '}') {
                at++;
                return map;
            }
            throw new FormatException("expected ',' or '}' inside an object", at);
        }
    }

    private List<Object> readArray(int depth) {
        guard(depth);
        at++; // [
        List<Object> list = new java.util.ArrayList<>();
        skipSpace();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(readDocument(depth + 1));
            skipSpace();
            char c = peek();
            if (c == ',') {
                at++;
                continue;
            }
            if (c == ']') {
                at++;
                return list;
            }
            throw new FormatException("expected ',' or ']' inside an array", at);
        }
    }

    private String readQuoted() {
        at++; // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw new FormatException("a string is never closed", at);
            }
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                out.append(readEscape());
                continue;
            }
            if (c < 0x20) {
                throw new FormatException("raw control character inside a string",
                    at - 1);
            }
            out.append(c);
        }
    }

    private char readEscape() {
        if (at >= text.length()) {
            throw new FormatException("a string is never closed", at);
        }
        char escaped = text.charAt(at++);
        switch (escaped) {
            case '"': return '"';
            case '\\': return '\\';
            case '/': return '/';
            case 'b': return '\b';
            case 'f': return '\f';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'u': return readUnicodeEscape();
            default:
                throw new FormatException("unknown escape '\\" + escaped + "'",
                    at - 1);
        }
    }

    private char readUnicodeEscape() {
        if (at + 4 > text.length()) {
            throw new FormatException("\\u needs four digits", at);
        }
        String hex = text.substring(at, at + 4);
        try {
            int code = Integer.parseInt(hex, 16);
            at += 4;
            return (char) code;
        } catch (NumberFormatException bad) {
            throw new FormatException("\\u" + hex + " is not four hex digits", at);
        }
    }

    private Object readLiteral(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw new FormatException("expected '" + word + "'", at);
        }
        at += word.length();
        return value;
    }

    private Object readNumber() {
        int start = at;
        if (peek() == '-') {
            at++;
        }
        while (at < text.length()
                && "+-.eE0123456789".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String token = text.substring(start, at);
        try {
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0
                    || token.indexOf('E') >= 0) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException bad) {
            throw new FormatException("'" + token + "' is no number", start);
        }
    }

    private void guard(int depth) {
        if (depth >= MAX_DEPTH) {
            throw new FormatException("the JSON nests deeper than " + MAX_DEPTH
                + " levels", at);
        }
    }

    private void skipSpace() {
        while (at < text.length()
                && " \t\n\r".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw new FormatException("the document ends too soon", at);
        }
        return text.charAt(at);
    }

    private static String kind(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof Map) {
            return "an object";
        }
        if (value instanceof List) {
            return "an array";
        }
        if (value instanceof String) {
            return "a string";
        }
        if (value instanceof Long || value instanceof Double) {
            return "a number";
        }
        if (value instanceof Boolean) {
            return "a truth value";
        }
        return value.getClass().getSimpleName();
    }

    /** Raised when the text is not the JSON it claims to be. */
    public static final class FormatException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public FormatException(String message, int at) {
            super("at " + at + ": " + message);
        }
    }
}
