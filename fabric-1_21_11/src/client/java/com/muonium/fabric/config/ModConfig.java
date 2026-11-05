package com.muonium.fabric.config;

import com.muonium.core.config.ConfigMigrator;
import com.muonium.core.json.Json;
import com.muonium.core.spi.Ports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本机配置。默认 client_only=true；写入采用「临时文件 + 原子移动」，
 * 中断写入后可恢复，不留半截 JSON。凭据绝不写入这里。
 */
public final class ModConfig {

    private final float volume;
    private final Path libraryPath;
    private final boolean clientOnly;

    private ModConfig(Map<String, Object> m, Path configDir) {
        this.volume = m.get("volume") instanceof Number n ? n.floatValue() : 0.8f;
        this.clientOnly = !Boolean.FALSE.equals(m.get("clientOnly"));
        Object lp = m.get("libraryPath");
        this.libraryPath = lp == null ? configDir.resolve("library") : Path.of(lp.toString());
    }

    public float volume() { return volume; }
    public Path libraryPath() { return libraryPath; }
    public boolean clientOnly() { return clientOnly; }

    public static ModConfig load(Ports.JsonCodec codec, Path configDir) {
        Map<String, Object> raw = new LinkedHashMap<>();
        Path file = configDir.resolve("config.json");
        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                raw = Json.parseObject(text);
            } catch (IOException | RuntimeException e) {
                System.err.println("[muonium] 配置损坏，使用默认值: " + e.getMessage());
            }
        }
        Map<String, Object> migrated = ConfigMigrator.migrate(raw);
        return new ModConfig(migrated, configDir);
    }

    /** 原子写入：先写 .tmp，再 ATOMIC_MOVE。 */
    public static void saveAtomic(Ports.JsonCodec codec, Path configDir, Map<String, Object> data)
            throws IOException {
        Files.createDirectories(configDir);
        Path target = configDir.resolve("config.json");
        Path tmp = configDir.resolve("config.json.tmp");
        Files.writeString(tmp, codec.stringify(data), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 一键清除所有本机配置与缓存目录（不含用户曲库音频）。 */
    public static void clearAll(Path configDir) throws IOException {
        if (!Files.isDirectory(configDir)) return;
        try (var paths = Files.walk(configDir)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
