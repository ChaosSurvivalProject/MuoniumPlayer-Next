package com.muonium.core.lyric;

import com.muonium.core.model.Lyric;

import java.util.List;

/** 歌词游标：每帧查找必须 O(log n)，用二分而非线性扫描。 */
public final class LyricCursor {

    private final Lyric.Document document;
    private final long[] starts;

    public LyricCursor(Lyric.Document document) {
        this.document = document;
        this.starts = new long[document.lines().size()];
        for (int i = 0; i < starts.length; i++) starts[i] = document.lines().get(i).startMs();
    }

    /** 返回 startMs <= positionMs 的最后一行；没有则返回 -1（空行/前奏）。 */
    public int lineIndexAt(long positionMs) {
        int lo = 0, hi = starts.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] <= positionMs) { ans = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return ans;
    }

    public Lyric.Line lineAt(long positionMs) {
        int i = lineIndexAt(positionMs);
        if (i < 0) return null;
        Lyric.Line l = document.lines().get(i);
        Long end = l.endMs();
        if (end != null && positionMs > end) return null; // 行间空白
        return l;
    }

    /** 逐字高亮：返回当前字索引，无逐字数据时返回 -1。 */
    public int wordIndexAt(Lyric.Line line, long positionMs) {
        if (line == null || line.words().isEmpty()) return -1;
        List<Lyric.Word> ws = line.words();
        int lo = 0, hi = ws.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ws.get(mid).startMs() <= positionMs) { ans = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        if (ans >= 0) {
            Long end = ws.get(ans).endMs();
            if (end != null && positionMs > end) return -1;
        }
        return ans;
    }

    public Lyric.Document document() { return document; }
}
