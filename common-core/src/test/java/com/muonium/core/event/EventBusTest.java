package com.muonium.core.event;

import com.muonium.core.error.MuoniumError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    void subscribeEmitAndUnsubscribe() {
        var bus = new EventBus();
        List<String> got = new ArrayList<>();
        var sub = bus.on("state", String.class, got::add);
        bus.emit("state", "PLAYING");
        assertEquals(1, bus.subscriberCount("state"));
        sub.close();
        bus.emit("state", "STOPPED");
        assertEquals(List.of("PLAYING"), got);
        bus.close();
    }

    @Test
    void wrongPayloadTypeIsIgnored() {
        var bus = new EventBus();
        List<String> got = new ArrayList<>();
        bus.on("state", String.class, got::add);
        bus.emit("state", 42);
        assertTrue(got.isEmpty());
        bus.close();
    }

    @Test
    void closeReleasesAllSubscriptions() {
        var bus = new EventBus();
        bus.on("a", String.class, s -> {});
        bus.close();
        assertEquals(0, bus.subscriberCount("a"));
        assertThrows(IllegalStateException.class, () -> bus.on("a", String.class, s -> {}));
    }

    @Test
    void errorDetailsNeverLeakCredentials() {
        String detail = MuoniumError.redact("request failed Cookie=abc123; token=xyz; Authorization: Bearer q");
        assertFalse(detail.contains("abc123"));
        assertFalse(detail.contains("xyz"));
        assertTrue(detail.contains("<redacted>"));
        assertEquals("曲库里找不到这首歌",
                MuoniumError.Detail.of(MuoniumError.Code.LIBRARY_TRACK_NOT_FOUND, "").userMessage());
    }
}
