package com.muonium.core.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSafetyTest {

    @Test
    void prototypePollutionKeysAreRejected() {
        for (String key : List.of("__proto__", "constructor", "prototype")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.parseObject("{\"" + key + "\":{\"polluted\":true}}"), key);
        }
    }

    @Test
    void deeplyNestedInputIsRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Json.MAX_DEPTH + 5; i++) sb.append("[");
        sb.append("0");
        for (int i = 0; i < Json.MAX_DEPTH + 5; i++) sb.append("]");
        assertThrows(IllegalArgumentException.class, () -> Json.parseValue(sb.toString()));
    }

    @Test
    void oversizeAndMalformedInputAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Json.parseObject("x".repeat(Json.MAX_INPUT_CHARS + 1)));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":1} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
    }

    @Test
    void stringsNullsNumbersAndEscapesRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("title", "中文 \"引号\" \\ 反斜杠\n换行");
        original.put("emoji", "🎵");
        original.put("durationMs", 214000L);
        original.put("loudness", -14.2);
        original.put("enabled", true);
        original.put("missing", null);
        String json = Json.stringify(original);
        Map<String, Object> back = Json.parseObject(json);
        assertEquals("中文 \"引号\" \\ 反斜杠\n换行", back.get("title"));
        assertEquals("🎵", back.get("emoji"));
        assertEquals(214000L, back.get("durationMs"));
        assertEquals(-14.2, back.get("loudness"));
        assertEquals(Boolean.TRUE, back.get("enabled"));
        assertTrue(back.containsKey("missing"));
        assertNull(back.get("missing"));
    }

    @Test
    void nonFiniteNumbersBecomeNull() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nan", Double.NaN);
        m.put("inf", Double.POSITIVE_INFINITY);
        assertEquals("{\"nan\":null,\"inf\":null}", Json.stringify(m));
    }

    @Test
    void parseValueAcceptsArrayRoot() {
        Object v = Json.parseValue("[1, 2, {\"a\":3}]");
        assertInstanceOf(List.class, v);
        assertEquals(3, ((List<?>) v).size());
    }
}
