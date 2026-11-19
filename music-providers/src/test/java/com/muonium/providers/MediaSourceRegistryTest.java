package com.muonium.providers;

import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourceRegistryTest {

    @Test
    void resolvesByNameLazily() {
        MediaSourceRegistry reg = new MediaSourceRegistry();
        reg.register("local", () -> null);
        reg.register("qq", () -> null);
        assertTrue(reg.contains("qq"));
        assertEquals(2, reg.names().size());
        assertEquals("local", reg.defaultName());
    }

    @Test
    void unknownSourceThrows() {
        MediaSourceRegistry reg = new MediaSourceRegistry();
        reg.register("local", () -> null);
        ProviderException ex = assertThrows(ProviderException.class, () -> reg.get("qq"));
        assertTrue(ex.getMessage().contains("未知音乐源"));
    }

    @Test
    void setDefaultRequiresExisting() {
        MediaSourceRegistry reg = new MediaSourceRegistry();
        reg.register("local", () -> null);
        assertThrows(ProviderException.class, () -> reg.setDefault("qq"));
        reg.register("qq", () -> null);
        reg.setDefault("qq");
        assertEquals("qq", reg.defaultName());
    }
}
