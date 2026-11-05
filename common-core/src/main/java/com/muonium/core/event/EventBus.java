package com.muonium.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 轻量同步事件总线。关闭播放器时必须 close() 释放订阅，避免事件订阅泄漏。
 * UI 不直接持有音频解码对象，只订阅这里的状态事件。
 */
public final class EventBus implements AutoCloseable {

    public record Subscription(String topic, Runnable cancel) implements AutoCloseable {
        @Override public void close() { cancel.run(); }
    }

    private final Map<String, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public <T> Subscription on(String topic, Class<T> type, Consumer<T> handler) {
        if (closed) throw new IllegalStateException("EventBus is closed");
        Consumer<Object> boxed = payload -> {
            if (payload == null || type.isInstance(payload)) handler.accept(type.cast(payload));
        };
        handlers.computeIfAbsent(topic, k -> new ArrayList<>()).add(boxed);
        return new Subscription(topic, () -> handlers.getOrDefault(topic, List.of()).remove(boxed));
    }

    public <T> void emit(String topic, T payload) {
        if (closed) return;
        for (Consumer<Object> h : new ArrayList<>(handlers.getOrDefault(topic, List.of()))) {
            h.accept(payload);
        }
    }

    public int subscriberCount(String topic) {
        return handlers.getOrDefault(topic, List.of()).size();
    }

    @Override
    public void close() {
        closed = true;
        handlers.clear();
    }
}
