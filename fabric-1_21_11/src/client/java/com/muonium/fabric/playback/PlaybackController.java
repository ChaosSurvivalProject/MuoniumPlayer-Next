package com.muonium.fabric.playback;

import com.muonium.core.event.EventBus;
import com.muonium.core.model.Lyric;
import com.muonium.core.model.PlaybackState;
import com.muonium.core.model.Playlist;
import com.muonium.core.model.Song;
import com.muonium.core.lyric.LyricCursor;
import com.muonium.core.playback.PlaybackStateMachine;
import com.muonium.core.queue.PlayQueue;
import com.muonium.core.spi.Ports;
import com.muonium.fabric.storage.LocalLibrary;

import java.util.List;

/**
 * 播放控制器：把状态机、队列、歌词游标和音频后端串起来。
 * 纯逻辑，不持有 Minecraft 对象，可单元测试。
 *
 * <p>后端不支持 pause/seek 时严格降级：显示为「停止」，绝不假装暂停；
 * 没有真实播放位置时不提供可拖动进度条。
 */
public final class PlaybackController implements AutoCloseable {

    private final PlaybackStateMachine machine;
    private final LocalLibrary library;
    private final Ports.AudioBackend audio;
    private final Ports.Clock clock;
    private final EventBus bus;
    private final Ports.MuoniumLogger logger;

    private PlayQueue queue;
    private LyricDocumentHolder lyric = new LyricDocumentHolder(null);
    private long playStartClockMs;
    private long pausedPositionMs;
    private float volume = 0.8f;

    public PlaybackController(PlaybackStateMachine machine, LocalLibrary library,
                              Ports.AudioBackend audio, Ports.Clock clock,
                              EventBus bus, Ports.MuoniumLogger logger) {
        this.machine = machine;
        this.library = library;
        this.audio = audio;
        this.clock = clock;
        this.bus = bus;
        this.logger = logger;
    }

    private record LyricDocumentHolder(Lyric.Document document) {
        LyricCursor cursor() { return document == null ? null : new LyricCursor(document); }
    }

    public void loadLibrary() {
        library.load();
        this.queue = new PlayQueue(library.ids(), Playlist.PlayMode.SEQUENCE,
                Playlist.RepeatMode.NONE, null, new Ports.RandomSource() {
                    private final java.util.Random r = new java.util.Random();
                    public long nextLong() { return r.nextLong(); }
                    public int nextInt(int b) { return r.nextInt(b); }
                }, null);
        bus.emit("library-loaded", library.size());
    }

    public boolean play(String songId) {
        Song s = library.find(songId);
        if (s == null) {
            logger.warn("play", "LIBRARY_TRACK_NOT_FOUND: " + songId);
            return false;
        }
        machine.transition(PlaybackStateMachine.Event.LOAD);
        if (!audio.prepare(s)) {
            machine.transition(PlaybackStateMachine.Event.FAIL);
            return false;
        }
        machine.transition(PlaybackStateMachine.Event.READY);
        if (!audio.play(0)) {
            machine.transition(PlaybackStateMachine.Event.FAIL);
            return false;
        }
        machine.transition(PlaybackStateMachine.Event.PLAY);
        playStartClockMs = clock.nowMs();
        pausedPositionMs = 0;
        bus.emit("now-playing", songId);
        return true;
    }

    /**
     * 暂停：后端不支持时降级为停止，并明确返回 false。
     * UI 必须据此把按钮显示为「停止」，不能假装暂停。
     */
    public boolean pause() {
        if (machine.state() != PlaybackState.PLAYING) return false;
        if (!audio.capabilities().pauseResume() || !audio.pause()) {
            stop();
            return false;
        }
        pausedPositionMs = audio.positionMs();
        machine.transition(PlaybackStateMachine.Event.PAUSE);
        bus.emit("state", PlaybackState.PAUSED);
        return true;
    }

    public boolean resume() {
        if (machine.state() != PlaybackState.PAUSED) return false;
        if (!audio.resume()) return false;
        playStartClockMs = clock.nowMs() - pausedPositionMs;
        machine.transition(PlaybackStateMachine.Event.RESUME);
        bus.emit("state", PlaybackState.PLAYING);
        return true;
    }

    /** @return false 表示后端不支持 seek，UI 不应显示可拖动进度条。 */
    public boolean seek(long positionMs) {
        if (!audio.capabilities().seek() || !audio.seek(positionMs)) return false;
        playStartClockMs = clock.nowMs() - positionMs;
        pausedPositionMs = positionMs;
        machine.reportIgnored(PlaybackStateMachine.Event.SEEK, "位置已更新");
        return true;
    }

    public void stop() {
        audio.stop();
        if (!machine.state().isTerminal()) {
            machine.transition(PlaybackStateMachine.Event.STOP);
        }
        bus.emit("state", PlaybackState.STOPPED);
    }

    /** 下一首：当前曲目结束、进入历史；队列耗尽则停住。 */
    public String next() {
        String id = queue == null ? null : queue.next();
        if (id == null) { stop(); return null; }
        stopSilently();
        return play(id) ? id : null;
    }

    public String previous() {
        String id = queue == null ? null : queue.previous();
        if (id == null) return null;
        stopSilently();
        return play(id) ? id : null;
    }

    private void stopSilently() {
        audio.stop();
        if (machine.state().isTerminal()) machine.transition(PlaybackStateMachine.Event.RESET);
    }

    public void setMode(Playlist.PlayMode mode) { if (queue != null) queue.setMode(mode); }
    public void setRepeat(Playlist.RepeatMode repeat) { if (queue != null) queue.setRepeat(repeat); }
    public void enqueue(String id) { if (queue != null) queue.enqueue(id); }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        audio.setVolume(volume);
    }
    public float volume() { return volume; }

    /** 估算位置：只有后端能给出真实位置时才可信，否则不得用于可拖动进度条。 */
    public long estimatedPositionMs() {
        return audio.capabilities().playbackPosition()
                ? audio.positionMs()
                : (machine.state() == PlaybackState.PAUSED ? pausedPositionMs : -1L);
    }

    public Lyric.Line currentLyricLine() {
        var c = lyric.cursor();
        if (c == null) return null;
        long pos = estimatedPositionMs();
        return pos < 0 ? null : c.lineAt(pos);
    }

    public void setLyric(Lyric.Document doc) { this.lyric = new LyricDocumentHolder(doc); }

    public PlaybackState state() { return machine.state(); }
    public Playlist.QueueSnapshot queueSnapshot() { return queue == null ? null : queue.snapshot(); }
    public List<PlaybackStateMachine.Transition> history() { return machine.history(); }

    @Override public void close() { stop(); }
}
