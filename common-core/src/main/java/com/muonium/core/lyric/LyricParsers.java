package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词导入解析。映射规则见 docs/lyric-schema.md：
 * LRC→行级；QRC/YRC/增强 LRC→逐字；AMLL TTML→说话人+行/字时间+翻译；
 * KRC 及未知格式→拒绝猜测，标记 unsupported 并保留原始文件由调用方保管。
 *
 * <p>正则参考上游真实实现（LyricParser.java:28-36）但为独立重写，未复制代码。
 */
public final class LyricParsers {

    private LyricParsers() {}

    public enum Format { LRC, QRC, YRC, ENHANCED_LRC, TTML, KRC, UNSUPPORTED;
        public String wire() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static final Pattern LRC_LINE = Pattern.compile("((?:\\[\\d{1,3}:\\d{1,2}[.:]\\d{1,3}])+)(.*)");
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})[.:](\\d{1,3})]");
    private static final Pattern ENHANCED_WORD = Pattern.compile("<(\\d{1,3}):(\\d{1,2})[.:](\\d{1,3})>([^<]*)");
    private static final Pattern TIMED_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern QRC_WORD = Pattern.compile("([^()]*?)\\((\\d+),(\\d+)\\)");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+)(?:,(\\d+))?\\)([^()]*)");
    private static final Pattern TTML_P = Pattern.compile(
            "<p\\b([^>]*)>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TTML_SPAN = Pattern.compile(
            "<span\\b([^>]*)>(.*?)</span>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "(begin|end|ttm:agent|agent)\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    /** 输入上限：恶意巨型文件在进入解析前被拒绝。 */
    public static final int MAX_INPUT_CHARS = 1_000_000;

    public static Format detect(String text) {
        if (text == null || text.isBlank()) return Format.UNSUPPORTED;
        String t = text.trim();
        if (t.startsWith("<tt") || t.contains("<p ") || t.contains("<p>")) return Format.TTML;
        if (t.startsWith("krc") || t.startsWith("[id:$") || t.contains("<?krc")) return Format.KRC;
        Matcher timed = TIMED_LINE.matcher(firstContentLine(t));
        if (timed.find()) {
            String body = timed.group(3).trim();
            // YRC 是「(绝对起始,时长)文本」，QRC 是「文本(起始,时长)」；用括号位置消歧。
            if (body.startsWith("(") && YRC_WORD.matcher(body).find()) return Format.YRC;
            if (QRC_WORD.matcher(body).find()) return Format.QRC;
            if (YRC_WORD.matcher(body).find()) return Format.YRC;
            return Format.LRC;
        }
        if (ENHANCED_WORD.matcher(t).find()) return Format.ENHANCED_LRC;
        if (LRC_LINE.matcher(firstContentLine(t)).find()) return Format.LRC;
        return Format.UNSUPPORTED;
    }

    private static String firstContentLine(String t) {
        for (String line : t.split("\\r?\\n")) {
            String s = line.trim();
            if (s.isEmpty() || s.matches("\\[(ti|ar|al|by|offset|re|ve):.*]")) continue;
            return s;
        }
        return "";
    }

    public static Lyric.Document parse(String text, String songId, Long durationMs) {
        if (text == null || text.length() > MAX_INPUT_CHARS) {
            return unsupported(songId, durationMs, text == null ? "null input" : "input too large");
        }
        Format f = detect(text);
        List<Lyric.Line> lines = switch (f) {
            case LRC -> parseLrc(text);
            case QRC -> parseQrc(text);
            case YRC -> parseYrc(text);
            case ENHANCED_LRC -> parseEnhancedLrc(text);
            case TTML -> parseTtml(text);
            case KRC, UNSUPPORTED -> List.of();
        };
        boolean unsupported = f == Format.KRC || f == Format.UNSUPPORTED;
        return LyricNormalizer.normalize(new Lyric.Document(
                Lyric.Document.CURRENT_SCHEMA, songId, durationMs, lines, f.wire(), unsupported));
    }

    public static Lyric.Document unsupported(String songId, Long durationMs, String reason) {
        return new Lyric.Document(Lyric.Document.CURRENT_SCHEMA, songId, durationMs, List.of(),
                Format.UNSUPPORTED.wire(), true);
    }

    private static List<Lyric.Line> parseLrc(String text) {
        List<Lyric.Line> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            Matcher m = LRC_LINE.matcher(raw.trim());
            if (!m.find()) continue;
            String body = clean(m.group(2));
            Matcher tm = LRC_TIME.matcher(m.group(1));
            while (tm.find()) {
                long ms = clockToMs(tm.group(1), tm.group(2), tm.group(3));
                out.add(new Lyric.Line(out.size(), ms, null, Lyric.Speaker.UNKNOWN, List.of(), body, null));
            }
        }
        return out;
    }

    private static List<Lyric.Line> parseEnhancedLrc(String text) {
        List<Lyric.Line> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            Matcher head = LRC_LINE.matcher(raw.trim());
            if (!head.find()) continue;
            long lineStart = 0;
            Matcher first = LRC_TIME.matcher(head.group(1));
            if (first.find()) lineStart = clockToMs(first.group(1), first.group(2), first.group(3));
            String body = head.group(2);
            List<Lyric.Word> words = new ArrayList<>();
            Matcher w = ENHANCED_WORD.matcher(body);
            while (w.find()) {
                long s = clockToMs(w.group(1), w.group(2), w.group(3));
                words.add(new Lyric.Word(clean(w.group(4)), s, null));
            }
            String plain = clean(body.replaceAll("<\\d{1,3}:\\d{1,2}[.:]\\d{1,3}>", ""));
            out.add(new Lyric.Line(out.size(), lineStart, null, Lyric.Speaker.UNKNOWN, words, plain, null));
        }
        return out;
    }

    private static List<Lyric.Line> parseQrc(String text) {
        List<Lyric.Line> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            Matcher head = TIMED_LINE.matcher(raw.trim());
            if (!head.find()) continue;
            long start = Long.parseLong(head.group(1));
            long dur = Long.parseLong(head.group(2));
            String body = head.group(3);
            List<Lyric.Word> words = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            Matcher w = QRC_WORD.matcher(body);
            while (w.find()) {
                long ws = Long.parseLong(w.group(2));
                long wd = Long.parseLong(w.group(3));
                String txt = clean(w.group(1));
                if (!txt.isEmpty()) { words.add(new Lyric.Word(txt, ws, ws + wd)); plain.append(txt); }
            }
            if (words.isEmpty()) plain.append(clean(body));
            out.add(new Lyric.Line(out.size(), start, start + dur, Lyric.Speaker.UNKNOWN,
                    words, plain.toString(), null));
        }
        return out;
    }

    private static List<Lyric.Line> parseYrc(String text) {
        List<Lyric.Line> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            Matcher head = TIMED_LINE.matcher(raw.trim());
            if (!head.find()) continue;
            long start = Long.parseLong(head.group(1));
            long dur = Long.parseLong(head.group(2));
            String body = head.group(3);
            List<Lyric.Word> words = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            Matcher w = YRC_WORD.matcher(body);
            while (w.find()) {
                long ws = Long.parseLong(w.group(1));
                long wd = Long.parseLong(w.group(2));
                String txt = clean(w.group(4));
                words.add(new Lyric.Word(txt, ws, ws + wd));
                plain.append(txt);
            }
            out.add(new Lyric.Line(out.size(), start, start + dur, Lyric.Speaker.UNKNOWN,
                    words, plain.toString(), null));
        }
        return out;
    }

    private static List<Lyric.Line> parseTtml(String text) {
        List<Lyric.Line> out = new ArrayList<>();
        Matcher p = TTML_P.matcher(text);
        while (p.find()) {
            Map<String, String> pa = attrs(p.group(1));
            long start = parseClock(pa.get("begin"));
            Long end = pa.containsKey("end") ? parseClock(pa.get("end")) : null;
            Lyric.Speaker speaker = speakerOf(pa.get("ttm:agent"), pa.get("agent"));
            List<Lyric.Word> words = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            Matcher sp = TTML_SPAN.matcher(p.group(2));
            boolean matched = false;
            while (sp.find()) {
                matched = true;
                Map<String, String> sa = attrs(sp.group(1));
                long ws = parseClock(sa.get("begin"));
                Long we = sa.containsKey("end") ? parseClock(sa.get("end")) : null;
                String txt = clean(stripTags(sp.group(2)));
                words.add(new Lyric.Word(txt, ws, we));
                plain.append(txt);
            }
            String body = clean(stripTags(p.group(2)));
            out.add(new Lyric.Line(out.size(), start, end, speaker, words,
                    matched ? plain.toString() : body, null));
        }
        return out;
    }

    private static java.util.Map<String, String> attrs(String s) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        Matcher a = ATTR.matcher(s == null ? "" : s);
        while (a.find()) m.put(a.group(1).toLowerCase(java.util.Locale.ROOT), a.group(2));
        return m;
    }

    private static Lyric.Speaker speakerOf(String agent, String fallback) {
        String v = agent != null ? agent : fallback;
        if (v == null) return Lyric.Speaker.UNKNOWN;
        return switch (v.toLowerCase(java.util.Locale.ROOT)) {
            case "v1", "lead", "a" -> Lyric.Speaker.LEAD;
            case "v2", "backing", "b" -> Lyric.Speaker.BACKING;
            case "duet_a" -> Lyric.Speaker.DUET_A;
            case "duet_b" -> Lyric.Speaker.DUET_B;
            default -> Lyric.Speaker.UNKNOWN;
        };
    }

    /** 支持 mm:ss.mmm / hh:mm:ss.mmm / Ns / Nms。 */
    static long parseClock(String v) {
        if (v == null || v.isBlank()) return 0L;
        String s = v.trim();
        if (s.endsWith("ms")) return (long) Double.parseDouble(s.substring(0, s.length() - 2));
        if (s.endsWith("s")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1000);
        String[] parts = s.split(":");
        double sec = 0;
        for (String part : parts) sec = sec * 60 + Double.parseDouble(part);
        return (long) (sec * 1000);
    }

    private static long clockToMs(String mm, String ss, String frac) {
        long minutes = Long.parseLong(mm);
        long seconds = Long.parseLong(ss);
        long f = Long.parseLong(frac);
        long millis = frac.length() == 2 ? f * 10L : (frac.length() == 1 ? f * 100L : f);
        return minutes * 60_000L + seconds * 1000L + millis;
    }

    private static String stripTags(String s) { return s.replaceAll("<[^>]*>", ""); }

    static String clean(String s) {
        if (s == null) return "";
        String v = decodeEntities(s)
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .trim();
        return v.length() > 500 ? v.substring(0, 500) : v;
    }

    private static String decodeEntities(String s) {
        String v = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'");
        Matcher m = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);").matcher(v);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = m.group(1);
            int cp = g.startsWith("x") ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
