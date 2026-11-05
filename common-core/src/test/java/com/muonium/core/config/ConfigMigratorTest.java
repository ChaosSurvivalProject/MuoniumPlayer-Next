package com.muonium.core.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigratorTest {

    @Test
    void v1MigratesToCurrentAndStripsServerKeys() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("volume", 80);
        raw.put("serverEnabled", true);
        raw.put("syncRoomId", "abc");
        var m = ConfigMigrator.migrate(raw);
        assertEquals(ConfigMigrator.CURRENT_VERSION, m.get("configVersion"));
        assertEquals(0.8, ((Number) m.get("volume")).doubleValue(), 1e-9);
        assertEquals(Boolean.TRUE, m.get("clientOnly"));
        assertFalse(m.containsKey("serverEnabled"));
        assertFalse(m.containsKey("syncRoomId"));
        assertEquals(Boolean.FALSE, m.get("telemetryEnabled"));
        assertEquals(Boolean.FALSE, m.get("externalProcessesEnabled"));
    }

    @Test
    void defaultConfigIsClientOnly() {
        var m = ConfigMigrator.defaults();
        assertEquals(Boolean.TRUE, m.get("clientOnly"));
        assertEquals(Boolean.FALSE, m.get("crashReportingEnabled"));
    }

    @Test
    void futureVersionIsRejectedNotGuessed() {
        Map<String, Object> raw = Map.of("configVersion", ConfigMigrator.CURRENT_VERSION + 5);
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrator.migrate(raw));
    }

    @Test
    void alreadyCurrentConfigIsStable() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("configVersion", 3);
        raw.put("volume", 0.5);
        raw.put("clientOnly", true);
        var m = ConfigMigrator.migrate(raw);
        assertEquals(0.5, ((Number) m.get("volume")).doubleValue(), 1e-9);
        assertEquals(3, m.get("configVersion"));
    }

    @Test
    void nullConfigProducesSafeDefaults() {
        var m = ConfigMigrator.migrate(null);
        assertEquals(Boolean.TRUE, m.get("clientOnly"));
        assertTrue(((Number) m.get("volume")).doubleValue() <= 1.0);
    }
}
