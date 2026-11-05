package com.muonium.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置迁移。默认 client_only=true；升级时移除任何服务端/同步相关键，
 * 与纯客户端边界一致。未来版本号高于 CURRENT_VERSION 时抛异常，拒绝猜测。
 */
public final class ConfigMigrator {

    private ConfigMigrator() {}

    public static final int CURRENT_VERSION = 3;

    private static final String[] FORBIDDEN_PREFIXES = {"server", "sync", "room", "remote", "broadcast"};

    public static Map<String, Object> migrate(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>(raw == null ? Map.of() : raw);
        int v = readVersion(m);
        if (v > CURRENT_VERSION) {
            throw new IllegalArgumentException("配置版本 " + v + " 高于当前支持的 " + CURRENT_VERSION);
        }
        if (v < 1) v = 1;

        if (v < 2) {
            // v1 -> v2: volume 由 0..100 百分比改为 0..1 浮点；旧键重命名
            Object pct = m.containsKey("volumePercent") ? m.get("volumePercent") : m.get("volume");
            if (pct instanceof Number n) {
                double vol = n.doubleValue();
                if (vol > 1.0) vol = vol / 100.0;
                m.put("volume", Math.max(0.0, Math.min(1.0, vol)));
            } else {
                m.put("volume", 0.8);
            }
            m.remove("volumePercent");
            v = 2;
        }

        if (v < 3) {
            // v2 -> v3: 纯客户端化；删除所有服务端/联机同步设置；关闭遥测与外部进程
            m.keySet().removeIf(k -> {
                String lower = k.toLowerCase(java.util.Locale.ROOT);
                for (String p : FORBIDDEN_PREFIXES) if (lower.startsWith(p)) return true;
                return false;
            });
            m.put("clientOnly", true);
            m.put("telemetryEnabled", false);
            m.put("externalProcessesEnabled", false);
            m.put("crashReportingEnabled", false);
            v = 3;
        }

        m.put("configVersion", CURRENT_VERSION);
        return m;
    }

    private static int readVersion(Map<String, Object> m) {
        Object v = m.get("configVersion");
        if (v == null) v = m.get("version");
        return v instanceof Number n ? n.intValue() : 1;
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configVersion", 1);
        m.put("volume", 80);
        m.put("serverEnabled", true);   // 故意放入旧版脏数据，供迁移测试覆盖
        return migrate(m);
    }
}
