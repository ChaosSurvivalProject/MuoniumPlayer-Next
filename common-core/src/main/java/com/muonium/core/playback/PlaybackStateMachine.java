package com.muonium.core.playback;

import com.muonium.core.model.PlaybackState;
import com.muonium.core.spi.Ports;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 播放状态机：Idle/Loading/Ready/Buffering/Playing/Paused/Stopped/Ended/Failed。
 *
 * <p>非法转换抛 {@link IllegalStateException}，不吞异常、不静默降级。
 * 「取消 / 失败 / 未知」分别用 STOPPED / FAILED / 保持原状态 + 日志区分。
 * 使用注入的 Clock 记录转换序号与时间戳，禁止用 Thread.sleep 当时钟。
 */
public final class PlaybackStateMachine {

    public enum Event {
        LOAD, BUFFER_UNDERRUN, BUFFER_RECOVERED, READY, PLAY, PAUSE, RESUME,
        SEEK, STOP, END, FAIL, RESET
    }

    public record Transition(long seq, long atMs, PlaybackState from, Event event, PlaybackState to) {}

    private static final Map<PlaybackState, Map<Event, PlaybackState>> TABLE = buildTable();

    private static Map<PlaybackState, Map<Event, PlaybackState>> buildTable() {
        Map<PlaybackState, Map<Event, PlaybackState>> t = new EnumMap<>(PlaybackState.class);
        for (PlaybackState s : PlaybackState.values()) t.put(s, new EnumMap<>(Event.class));

        t.get(PlaybackState.IDLE).put(Event.LOAD, PlaybackState.LOADING);
        t.get(PlaybackState.IDLE).put(Event.FAIL, PlaybackState.FAILED);
        t.get(PlaybackState.IDLE).put(Event.RESET, PlaybackState.IDLE);

        t.get(PlaybackState.LOADING).put(Event.READY, PlaybackState.READY);
        t.get(PlaybackState.LOADING).put(Event.BUFFER_UNDERRUN, PlaybackState.BUFFERING);
        t.get(PlaybackState.LOADING).put(Event.STOP, PlaybackState.STOPPED);
        t.get(PlaybackState.LOADING).put(Event.FAIL, PlaybackState.FAILED);

        t.get(PlaybackState.READY).put(Event.PLAY, PlaybackState.PLAYING);
        t.get(PlaybackState.READY).put(Event.LOAD, PlaybackState.LOADING);
        t.get(PlaybackState.READY).put(Event.STOP, PlaybackState.STOPPED);
        t.get(PlaybackState.READY).put(Event.FAIL, PlaybackState.FAILED);

        t.get(PlaybackState.BUFFERING).put(Event.BUFFER_RECOVERED, PlaybackState.PLAYING);
        t.get(PlaybackState.BUFFERING).put(Event.READY, PlaybackState.READY);
        t.get(PlaybackState.BUFFERING).put(Event.STOP, PlaybackState.STOPPED);
        t.get(PlaybackState.BUFFERING).put(Event.FAIL, PlaybackState.FAILED);

        t.get(PlaybackState.PLAYING).put(Event.PAUSE, PlaybackState.PAUSED);
        t.get(PlaybackState.PLAYING).put(Event.BUFFER_UNDERRUN, PlaybackState.BUFFERING);
        t.get(PlaybackState.PLAYING).put(Event.END, PlaybackState.ENDED);
        t.get(PlaybackState.PLAYING).put(Event.STOP, PlaybackState.STOPPED);
        t.get(PlaybackState.PLAYING).put(Event.LOAD, PlaybackState.LOADING);
        t.get(PlaybackState.PLAYING).put(Event.SEEK, PlaybackState.PLAYING);
        t.get(PlaybackState.PLAYING).put(Event.FAIL, PlaybackState.FAILED);

        t.get(PlaybackState.PAUSED).put(Event.RESUME, PlaybackState.PLAYING);
        t.get(PlaybackState.PAUSED).put(Event.PLAY, PlaybackState.PLAYING);
        t.get(PlaybackState.PAUSED).put(Event.LOAD, PlaybackState.LOADING);
        t.get(PlaybackState.PAUSED).put(Event.STOP, PlaybackState.STOPPED);
        t.get(PlaybackState.PAUSED).put(Event.SEEK, PlaybackState.PAUSED);
        t.get(PlaybackState.PAUSED).put(Event.FAIL, PlaybackState.FAILED);

        for (PlaybackState terminal : List.of(PlaybackState.STOPPED, PlaybackState.ENDED, PlaybackState.FAILED)) {
            t.get(terminal).put(Event.LOAD, PlaybackState.LOADING);
            t.get(terminal).put(Event.RESET, PlaybackState.IDLE);
        }
        return t;
    }

    private final Ports.Clock clock;
    private final Ports.MuoniumLogger logger;
    private final List<Transition> history = new ArrayList<>();
    private PlaybackState state = PlaybackState.IDLE;
    private long seq;

    public PlaybackStateMachine(Ports.Clock clock, Ports.MuoniumLogger logger) {
        this.clock = clock;
        this.logger = logger;
    }

    public synchronized PlaybackState state() { return state; }

    public synchronized PlaybackState transition(Event event) {
        PlaybackState target = TABLE.getOrDefault(state, Map.of()).get(event);
        if (target == null) {
            if (logger != null) {
                logger.warn("state-machine", "非法转换被拒绝: " + state + " --" + event + "--> ?");
            }
            throw new IllegalStateException("Illegal transition: " + state + " --" + event + "--> ?");
        }
        PlaybackState from = state;
        state = target;
        history.add(new Transition(++seq, clock.nowMs(), from, event, target));
        return state;
    }

    /** 不改变状态的事件（例如 SEEK 失败）只记日志，不抛异常。 */
    public synchronized void reportIgnored(Event event, String reason) {
        if (logger != null) logger.warn("state-machine", "事件被忽略: " + event + " (" + reason + ")");
    }

    public synchronized List<Transition> history() { return List.copyOf(history); }
}
