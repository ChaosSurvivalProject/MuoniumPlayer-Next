package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LyricCursorTest {

    private static Lyric.Document doc() {
        return new Lyric.Document(1, "s", 40_000L, List.of(
                new Lyric.Line(0, 10_000L, 13_500L, Lyric.Speaker.LEAD,
                        List.of(new Lyric.Word("第一句", 10_000L, 12_800L),
                                new Lyric.Word("歌词", 12_800L, 13_500L)), "第一句歌词", null),
                new Lyric.Line(1, 20_000L, 24_000L, Lyric.Speaker.LEAD, List.of(), "第二句", null)),
                "qrc", false);
    }

    @Test
    void binarySearchFindsActiveLine() {
        var c = new LyricCursor(doc());
        assertEquals(-1, c.lineIndexAt(9_999L));
        assertEquals(0, c.lineIndexAt(10_000L));
        assertEquals(0, c.lineIndexAt(13_500L));
        assertNull(c.lineAt(15_000L), "行间空白不应返回上一行");
        assertEquals(1, c.lineIndexAt(20_000L));
    }

    @Test
    void wordCursorTracksKaraokeTiming() {
        var c = new LyricCursor(doc());
        var line = c.lineAt(10_500L);
        assertEquals(0, c.wordIndexAt(line, 10_500L));
        assertEquals(1, c.wordIndexAt(line, 13_000L));
        assertEquals(-1, c.wordIndexAt(line, 13_600L));
    }

    @Test
    void noWordTimingReturnsMinusOne() {
        var c = new LyricCursor(doc());
        assertEquals(-1, c.wordIndexAt(c.lineAt(21_000L), 21_000L));
    }

    @Test
    void lookupCostIsLogarithmicOnLargeDocument() {
        List<Lyric.Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            lines.add(new Lyric.Line(i, i * 1000L, i * 1000L + 900L,
                    Lyric.Speaker.UNKNOWN, List.of(), "L" + i, null));
        }
        var c = new LyricCursor(new Lyric.Document(1, "s", 20_000_000L, lines, "lrc", false));
        assertEquals(19_999, c.lineIndexAt(19_999_500L));
        assertEquals(10_000, c.lineIndexAt(10_000_500L));
    }
}
