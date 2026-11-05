package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 歌词规范化：排序、时间倒退、重复时间戳、缺失结束时间、空白/全半角、
 * HTML 实体、翻译错位（±80ms 容差，参考上游 LyricParser.java:178 的行为规格）、超长行。
 */
public final class LyricNormalizer {

    private LyricNormalizer() {}

    public static final long TRANSLATION_TOLERANCE_MS = 80L;
    public static final int MAX_LINE_CHARS = 500;

    public static Lyric.Document normalize(Lyric.Document doc) {
        if (doc == null) throw new IllegalArgumentException("doc");
        List<Lyric.Line> src = new ArrayList<>(doc.lines());
        src.sort(Comparator.comparingLong(Lyric.Line::startMs));

        List<Lyric.Line> out = new ArrayList<>(src.size());
        long prevEnd = 0L;
        for (int i = 0; i < src.size(); i++) {
            Lyric.Line l = src.get(i);
            long start = Math.max(0L, Math.max(l.startMs(), prevEnd));
            Long end = l.endMs();
            if (end != null && end < start) end = start;

            List<Lyric.Word> words = new ArrayList<>();
            long wPrevEnd = start;
            for (Lyric.Word w : l.words()) {
                long ws = Math.max(start, Math.max(w.startMs(), wPrevEnd));
                Long we = w.endMs();
                if (we != null && we < ws) we = ws;
                String txt = clamp(LyricParsers.clean(w.text()));
                words.add(new Lyric.Word(txt, ws, we));
                wPrevEnd = we != null ? we : ws;
            }

            String text = clamp(LyricParsers.clean(l.text()));
            if (text.isEmpty() && !words.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Lyric.Word w : words) sb.append(w.text());
                text = sb.toString();
            }

            Long resolvedEnd = end;
            if (resolvedEnd == null) {
                if (i + 1 < src.size()) resolvedEnd = Math.max(start, src.get(i + 1).startMs());
                else if (doc.durationMs() != null) resolvedEnd = Math.max(start, doc.durationMs());
            }
            out.add(new Lyric.Line(out.size(), start, resolvedEnd, l.speaker(), words, text,
                    l.translation() == null ? null : clamp(LyricParsers.clean(l.translation()))));
            prevEnd = resolvedEnd != null ? resolvedEnd : start;
        }
        return new Lyric.Document(doc.schemaVersion(), doc.songId(), doc.durationMs(), out,
                doc.format(), doc.unsupported());
    }

    /** 翻译按时间戳对齐；找不到精确匹配时用容差内最近的一行。 */
    public static Lyric.Document applyTranslation(Lyric.Document doc, List<Lyric.Line> translationLines) {
        List<Lyric.Line> result = new ArrayList<>();
        for (Lyric.Line l : doc.lines()) {
            String best = null;
            long bestDistance = Long.MAX_VALUE;
            for (Lyric.Line t : translationLines) {
                long d = Math.abs(t.startMs() - l.startMs());
                if (d <= TRANSLATION_TOLERANCE_MS && d < bestDistance) {
                    bestDistance = d;
                    best = LyricParsers.clean(t.text());
                }
            }
            result.add(new Lyric.Line(l.index(), l.startMs(), l.endMs(), l.speaker(),
                    l.words(), l.text(), best == null ? l.translation() : best));
        }
        return new Lyric.Document(doc.schemaVersion(), doc.songId(), doc.durationMs(), result,
                doc.format(), doc.unsupported());
    }

    private static String clamp(String s) {
        return s.length() > MAX_LINE_CHARS ? s.substring(0, MAX_LINE_CHARS) : s;
    }
}
