package com.muonium.core;

import com.muonium.core.error.MuoniumError;
import com.muonium.core.model.Playlist;
import com.muonium.core.queue.PlayQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 深度审查中确认并修复的真实 bug 回归测试。
 * 每条对应 docs/test-report.md §6 的一个条目。
 */
class RegressionTest {

    // ---- Bug 1: 分号后的凭据泄露 / JSON 凭据不脱敏 ----

    @Test
    void redactCoversSemicolonSeparatedCookieFields() {
        String raw = "login Cookie: uin=12345; qm_key=SECRET123; skey=ABC p=1";
        String out = MuoniumError.redact(raw);
        assertFalse(out.contains("12345"), "uin 必须脱敏: " + out);
        assertFalse(out.contains("SECRET123"), "qm_key 必须脱敏: " + out);
        assertFalse(out.contains("ABC"), "skey 必须脱敏: " + out);
        assertTrue(out.contains("<redacted>"));
    }

    @Test
    void redactCoversJsonCredentials() {
        String out = MuoniumError.redact("{\"cookie\":\"SECRET\",\"token\":\"TOK\"}");
        assertFalse(out.contains("SECRET"), out);
        assertFalse(out.contains("TOK"), out);
        assertTrue(out.contains("<redacted>"));
    }

    @Test
    void redactCoversEqualsFormWithSemicolons() {
        String out = MuoniumError.redact("qm_key=SECRET123; skey=ABC uin=1");
        assertFalse(out.contains("SECRET123"), out);
        assertFalse(out.contains("ABC"), out);
    }

    @Test
    void redactDoesNotOverMatchCommonWords() {
        String normal = "tokenizer loaded tokens=42 cookieCutters ready";
        assertEquals(normal, MuoniumError.redact(normal),
                "脱敏只应匹配凭据赋值，不应命中 token/cookie 出现在普通单词中");
    }

    // ---- Bug 2: volumePercent=1（1%）被错当成 1.0（100%）----

    @Test
    void volumePercentOneMigratesToPointZeroOne() {
        var raw = new java.util.LinkedHashMap<String, Object>();
        raw.put("configVersion", 1);
        raw.put("volumePercent", 1);
        var m = com.muonium.core.config.ConfigMigrator.migrate(raw);
        assertEquals(0.01, ((Number) m.get("volume")).doubleValue(), 1e-9);
    }

    @Test
    void volumePercentZeroAndHundredAreClamped() {
        var raw = new java.util.LinkedHashMap<String, Object>();
        raw.put("configVersion", 1);
        raw.put("volumePercent", 0);
        assertEquals(0.0, ((Number) com.muonium.core.config.ConfigMigrator.migrate(raw).get("volume")).doubleValue(), 1e-9);
        raw.put("volumePercent", 100);
        assertEquals(1.0, ((Number) com.muonium.core.config.ConfigMigrator.migrate(raw).get("volume")).doubleValue(), 1e-9);
    }

    // ---- Bug 3: 快照恢复后 LIST 循环失效 ----

    @Test
    void restoredQueueKeepsListRepeat() {
        PlayQueue fresh = new PlayQueue(List.of("a", "b"), Playlist.PlayMode.SEQUENCE,
                Playlist.RepeatMode.LIST, 1L, new TestPorts.SeededRandom(1), null);
        assertEquals("a", fresh.next());
        assertEquals("b", fresh.next());
        assertEquals("a", fresh.next(), "fresh 队列应 LIST 循环回 a");
        assertEquals("b", fresh.next());

        PlayQueue restored = PlayQueue.restore(fresh.snapshot(), new TestPorts.SeededRandom(1));
        assertEquals("a", restored.next(), "restore 后仍应 LIST 循环回 a");
        assertEquals("b", restored.next());
    }

    // ---- Bug 4 & 5: 歌词损坏输入不得崩溃 ----

    @Test
    void badTtmlClockDoesNotThrow() {
        var doc = assertDoesNotThrow(() -> com.muonium.core.lyric.LyricParsers.parse(
                "<p begin=\"abc\" end=\"1s\">x</p>", "s", 1000L));
        assertNotNull(doc);
        assertEquals(1, doc.lines().size());
    }

    @Test
    void illegalUnicodeEntityDoesNotThrow() {
        var doc = assertDoesNotThrow(() -> com.muonium.core.lyric.LyricParsers.parse(
                "[00:01.00]&#x110000; bad", "s", 1000L));
        assertNotNull(doc);
        assertEquals(1, doc.lines().size());
        assertTrue(doc.lines().get(0).text().contains("&#x110000;"), "非法实体应原样保留");
    }

    @Test
    void surrogateEntityDoesNotThrow() {
        var doc = assertDoesNotThrow(() -> com.muonium.core.lyric.LyricParsers.parse(
                "[00:01.00]&#xD800; surrogate", "s", 1000L));
        assertNotNull(doc);
    }
}
