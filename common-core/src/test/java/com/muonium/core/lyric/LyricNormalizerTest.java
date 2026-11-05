package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LyricNormalizerTest {

    private static Lyric.Document doc(List<Lyric.Line> lines, Long duration) {
        return new Lyric.Document(1, "s", duration, lines, "lrc", false);
    }

    @Test
    void outOfOrderLinesAreSorted() {
        var d = doc(List.of(
                new Lyric.Line(0, 20_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "第二句", null),
                new Lyric.Line(1, 10_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "第一句", null)), 40_000L);
        var n = LyricNormalizer.normalize(d);
        assertEquals("第一句", n.lines().get(0).text());
        assertEquals(0, n.lines().get(0).index());
        assertEquals(1, n.lines().get(1).index());
    }

    @Test
    void negativeAndBackwardsTimeAreClamped() {
        var d = doc(List.of(
                new Lyric.Line(0, -500L, null, Lyric.Speaker.UNKNOWN, List.of(), "负", null),
                new Lyric.Line(1, 1_000L, 9_000L, Lyric.Speaker.UNKNOWN, List.of(), "长", null),
                new Lyric.Line(2, 2_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "倒退", null)), 30_000L);
        var n = LyricNormalizer.normalize(d);
        assertEquals(0L, n.lines().get(0).startMs());
        assertEquals(9_000L, n.lines().get(2).startMs(), "时间倒退应被钳制到上一行结束");
    }

    @Test
    void missingEndTimeFallsBackToNextLineStart() {
        var d = doc(List.of(
                new Lyric.Line(0, 10_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "A", null),
                new Lyric.Line(1, 15_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "B", null)), 30_000L);
        var n = LyricNormalizer.normalize(d);
        assertEquals(15_000L, n.lines().get(0).endMs());
        assertEquals(30_000L, n.lines().get(1).endMs(), "最后一行退回到歌曲时长");
    }

    @Test
    void duplicateTimestampsArePreservedButIndexed() {
        var d = doc(List.of(
                new Lyric.Line(0, 10_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "A", null),
                new Lyric.Line(1, 10_000L, null, Lyric.Speaker.UNKNOWN, List.of(), "B", null)), 30_000L);
        var n = LyricNormalizer.normalize(d);
        assertEquals(2, n.lines().size());
        assertEquals(10_000L, n.lines().get(0).startMs());
        assertEquals(10_000L, n.lines().get(1).startMs());
    }

    @Test
    void translationIsAlignedWithinTolerance() {
        var base = LyricNormalizer.normalize(doc(List.of(
                new Lyric.Line(0, 10_000L, 13_000L, Lyric.Speaker.UNKNOWN, List.of(), "原文", null)), 30_000L));
        var tr = List.of(new Lyric.Line(0, 10_060L, null, Lyric.Speaker.UNKNOWN, List.of(), "First line", null));
        var out = LyricNormalizer.applyTranslation(base, tr);
        assertEquals("First line", out.lines().get(0).translation());
    }

    @Test
    void overlongLineIsClamped() {
        String longText = "字".repeat(LyricNormalizer.MAX_LINE_CHARS + 100);
        var d = doc(List.of(new Lyric.Line(0, 0L, null, Lyric.Speaker.UNKNOWN, List.of(), longText, null)), 10_000L);
        assertEquals(LyricNormalizer.MAX_LINE_CHARS, LyricNormalizer.normalize(d).lines().get(0).text().length());
    }
}
