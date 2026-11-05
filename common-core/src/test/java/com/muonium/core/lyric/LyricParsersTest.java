package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.muonium.core.lyric.LyricParsers.Format;

class LyricParsersTest {

    @Test
    void lrcSupportsMultipleTimestampsPerLine() {
        var d = LyricParsers.parse("[ti:标题]\n[00:10.00][00:20.00]第一句\n[00:30.50]第二句",
                "song_1", 60_000L);
        assertEquals(Format.LRC.wire(), d.format());
        assertFalse(d.unsupported());
        assertEquals(3, d.lines().size());
        assertEquals(10_000L, d.lines().get(0).startMs());
        assertEquals(20_000L, d.lines().get(1).startMs());
        assertEquals(30_500L, d.lines().get(2).startMs());
        assertEquals("第一句", d.lines().get(0).text());
    }

    @Test
    void millisecondsCentisecondsAndFractionDigitsAreNormalized() {
        var d = LyricParsers.parse("[00:01.5]a\n[00:02.50]b\n[00:03.500]c", "s", 10_000L);
        assertEquals(1_500L, d.lines().get(0).startMs());
        assertEquals(2_500L, d.lines().get(1).startMs());
        assertEquals(3_500L, d.lines().get(2).startMs());
    }

    @Test
    void qrcProvidesRealWordTiming() {
        var d = LyricParsers.parse("[10000,3500]第一句(10000,2800)歌词(12800,700)", "s", 214_000L);
        assertEquals(Format.QRC.wire(), d.format());
        assertEquals(1, d.lines().size());
        assertEquals(2, d.lines().get(0).words().size());
        assertEquals(10_000L, d.lines().get(0).words().get(0).startMs());
        assertEquals(12_800L, d.lines().get(0).words().get(1).startMs());
        assertEquals("第一句歌词", d.lines().get(0).text());
    }

    @Test
    void enhancedLrcAndYrcAreWordTimed() {
        var e = LyricParsers.parse("[00:10.00]<00:10.00>第一<00:12.80>歌词", "s", 20_000L);
        assertEquals(2, e.lines().get(0).words().size());
        var y = LyricParsers.parse("[10000,3500](10000,2800)第一(12800,700)歌词", "s", 20_000L);
        assertEquals(Format.YRC.wire(), y.format());
        assertEquals(2, y.lines().get(0).words().size());
    }

    @Test
    void ttmlKeepsSpeakerAndTimings() {
        String ttml = "<tt><body><div>"
                + "<p begin=\"00:10.000\" end=\"00:13.500\" ttm:agent=\"v1\">"
                + "<span begin=\"00:10.000\" end=\"00:12.800\">First</span>"
                + "<span begin=\"00:12.800\" end=\"00:13.500\">line</span></p>"
                + "</div></body></tt>";
        var d = LyricParsers.parse(ttml, "s", 20_000L);
        assertEquals(Format.TTML.wire(), d.format());
        assertEquals(1, d.lines().size());
        assertEquals(Lyric.Speaker.LEAD, d.lines().get(0).speaker());
        assertEquals(2, d.lines().get(0).words().size());
        assertEquals(10_000L, d.lines().get(0).startMs());
        assertEquals(13_500L, d.lines().get(0).endMs());
    }

    @Test
    void unknownAndKrcFormatsAreRejectedNotGuessed() {
        assertTrue(LyricParsers.parse("这不是歌词格式", "s", null).unsupported());
        assertTrue(LyricParsers.parse("krc1[dummy]", "s", null).unsupported());
        assertEquals(0, LyricParsers.parse("krc1[dummy]", "s", null).lines().size());
    }

    @Test
    void oversizeInputIsRejected() {
        String huge = "x".repeat(LyricParsers.MAX_INPUT_CHARS + 1);
        var d = LyricParsers.parse(huge, "s", null);
        assertTrue(d.unsupported());
        assertTrue(d.lines().isEmpty());
    }

    @Test
    void htmlEntitiesAndFullWidthSpacesAreCleaned() {
        var d = LyricParsers.parse("[00:01.00]A&amp;B　C", "s", 5_000L);
        assertEquals("A&B C", d.lines().get(0).text());
    }
}
