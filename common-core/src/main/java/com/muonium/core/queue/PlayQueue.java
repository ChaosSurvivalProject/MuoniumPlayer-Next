package com.muonium.core.queue;

import com.muonium.core.model.Playlist;
import com.muonium.core.spi.Ports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 播放队列：顺序 / 随机 / 循环 / 历史。
 *
 * <p>行为契约（来自 docs/source-audit.md 的规格化）：
 * <ul>
 *   <li>点击下一首：当前曲目结束并进入 played 历史；{@code repeat=ONE} 时优先单曲循环，不推进。</li>
 *   <li>队列耗尽：{@code repeat=LIST} 时回到源歌单开头，否则返回 {@code null}（停住，
 *       由上层依据 autoplaySourcePlaylistId 决定是否续播，core 不擅自回到源歌单）。</li>
 *   <li>随机播放：使用可恢复的 {@code shuffleSeed} 与已播放集合，同一 seed 产生同一顺序；
 *       UI 刷新通过 snapshot/restore 恢复，绝不重新洗牌。</li>
 *   <li>单曲循环优先级高于列表循环。</li>
 * </ul>
 *
 * <p>所有公开方法 synchronized，保证并发加入/移除的可见性。
 */
public final class PlayQueue {

    private final List<String> baseOrder;
    private final List<String> upcoming = new ArrayList<>();
    private final List<String> played = new ArrayList<>();
    private final Ports.RandomSource random;
    private final String autoplaySourcePlaylistId;

    private String currentId;
    private Playlist.PlayMode mode;
    private Playlist.RepeatMode repeat;
    private Long shuffleSeed;

    public PlayQueue(List<String> songIds, Playlist.PlayMode mode, Playlist.RepeatMode repeat,
                     Long shuffleSeed, Ports.RandomSource random, String autoplaySourcePlaylistId) {
        Objects.requireNonNull(songIds, "songIds");
        Objects.requireNonNull(random, "random");
        LinkedHashSet<String> dedup = new LinkedHashSet<>(songIds);
        this.baseOrder = List.copyOf(dedup);
        this.mode = mode == null ? Playlist.PlayMode.SEQUENCE : mode;
        this.repeat = repeat == null ? Playlist.RepeatMode.NONE : repeat;
        this.random = random;
        this.autoplaySourcePlaylistId = autoplaySourcePlaylistId;
        this.shuffleSeed = shuffleSeed != null ? shuffleSeed : random.nextLong();
        this.upcoming.addAll(orderFor(this.mode, this.baseOrder, this.shuffleSeed));
    }

    private PlayQueue(Playlist.QueueSnapshot s, Ports.RandomSource random) {
        this.baseOrder = List.of();
        this.upcoming.addAll(s.upcoming());
        this.played.addAll(s.played());
        this.currentId = s.playingSongId();
        this.mode = s.mode();
        this.repeat = s.repeat();
        this.shuffleSeed = s.shuffleSeed();
        this.random = random;
        this.autoplaySourcePlaylistId = s.autoplaySourcePlaylistId();
    }

    /** 从快照恢复：直接使用保存的 upcoming，不重排队列。 */
    public static PlayQueue restore(Playlist.QueueSnapshot snapshot, Ports.RandomSource random) {
        return new PlayQueue(Objects.requireNonNull(snapshot), random);
    }

    /** 确定性洗牌：同一 seed 必定得到同一顺序。 */
    private static List<String> orderFor(Playlist.PlayMode mode, List<String> ids, long seed) {
        List<String> out = new ArrayList<>(ids);
        if (mode == Playlist.PlayMode.SHUFFLE) {
            Random r = new Random(seed);
            for (int i = out.size() - 1; i > 0; i--) {
                Collections.swap(out, i, r.nextInt(i + 1));
            }
        }
        return out;
    }

    public synchronized String current() { return currentId; }

    /** @return 下一首 id；队列耗尽且非列表循环时返回 null（停住）。 */
    public synchronized String next() {
        if (currentId != null && repeat == Playlist.RepeatMode.ONE) {
            return currentId;
        }
        if (currentId != null) played.add(currentId);
        if (upcoming.isEmpty()) {
            if (repeat == Playlist.RepeatMode.LIST && !baseOrder.isEmpty()) {
                upcoming.addAll(orderFor(mode, baseOrder, shuffleSeed == null ? 0L : shuffleSeed));
            } else {
                currentId = null;
                return null;
            }
        }
        currentId = upcoming.remove(0);
        return currentId;
    }

    /** 上一首：回到历史栈顶；无历史时保持当前。 */
    public synchronized String previous() {
        if (played.isEmpty()) return currentId;
        String prev = played.remove(played.size() - 1);
        if (currentId != null) upcoming.add(0, currentId);
        currentId = prev;
        return currentId;
    }

    public synchronized void setMode(Playlist.PlayMode newMode) {
        if (newMode == null || newMode == mode) return;
        this.mode = newMode;
        if (newMode == Playlist.PlayMode.SHUFFLE) {
            long seed = shuffleSeed == null ? 0L : shuffleSeed;
            List<String> shuffled = orderFor(Playlist.PlayMode.SHUFFLE, upcoming, seed ^ 0x5DEECE66DL);
            upcoming.clear();
            upcoming.addAll(shuffled);
        } else {
            List<String> byBase = new ArrayList<>();
            for (String id : baseOrder) if (upcoming.contains(id)) byBase.add(id);
            for (String id : upcoming) if (!byBase.contains(id)) byBase.add(id);
            upcoming.clear();
            upcoming.addAll(byBase);
        }
    }

    public synchronized void setRepeat(Playlist.RepeatMode newRepeat) {
        this.repeat = newRepeat == null ? Playlist.RepeatMode.NONE : newRepeat;
    }

    public synchronized void enqueue(String songId) {
        if (songId != null && !songId.isBlank()) upcoming.add(songId);
    }

    public synchronized void enqueueNext(String songId) {
        if (songId != null && !songId.isBlank()) upcoming.add(0, songId);
    }

    public synchronized boolean remove(String songId) {
        return upcoming.remove(songId);
    }

    public synchronized void clearUpcoming() { upcoming.clear(); }

    public synchronized List<String> upcoming() { return List.copyOf(upcoming); }
    public synchronized List<String> played() { return List.copyOf(played); }
    public synchronized Playlist.PlayMode mode() { return mode; }
    public synchronized Playlist.RepeatMode repeat() { return repeat; }
    public synchronized Long shuffleSeed() { return shuffleSeed; }

    public synchronized Playlist.QueueSnapshot snapshot() {
        return new Playlist.QueueSnapshot(currentId, played, upcoming, mode, shuffleSeed,
                repeat, autoplaySourcePlaylistId);
    }
}
