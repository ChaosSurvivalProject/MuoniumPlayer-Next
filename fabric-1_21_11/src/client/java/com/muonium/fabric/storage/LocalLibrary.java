package com.muonium.fabric.storage;

import com.muonium.core.json.Json;
import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地曲库：只读取用户显式指定的曲库目录与清单文件，绝不自动扫描整个磁盘。
 * 损坏 / 缺失 / 非法 JSON 返回空曲库并记录日志，不让游戏崩溃。
 */
public final class LocalLibrary {

    private final Ports.JsonCodec codec;
    private final Path libraryFile;
    private final Ports.MuoniumLogger logger;
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private volatile boolean loaded;

    public LocalLibrary(Ports.JsonCodec codec, Path libraryDir, Ports.MuoniumLogger logger) {
        this.codec = codec;
        this.libraryFile = libraryDir.resolve("library.json");
        this.logger = logger;
    }

    /** @return 成功载入的曲目数；失败返回 0。 */
    public synchronized int load() {
        songs.clear();
        loaded = true;
        if (!Files.isRegularFile(libraryFile)) {
            logger.warn("library", "曲库清单不存在，使用空曲库: " + libraryFile);
            return 0;
        }
        try {
            String text = Files.readString(libraryFile, StandardCharsets.UTF_8);
            if (text.length() > Json.MAX_INPUT_CHARS) {
                logger.error("library", "曲库清单过大，拒绝加载", null);
                return 0;
            }
            Map<String, Object> root = codec.parse(text);
            Object raw = root.get("tracks");
            if (!(raw instanceof List<?> tracks)) {
                logger.error("library", "曲库清单缺少 tracks 数组", null);
                return 0;
            }
            for (Object o : tracks) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                try {
                    Song s = Song.fromMap(m);
                    songs.put(s.id(), s);
                } catch (RuntimeException bad) {
                    logger.warn("library", "跳过损坏曲目: " + bad.getMessage());
                }
            }
            return songs.size();
        } catch (IOException | RuntimeException e) {
            // 不吞异常：记录后降级为空曲库
            logger.error("library", "曲库加载失败: " + e.getMessage(), e);
            return 0;
        }
    }

    public Song find(String id) { return id == null ? null : songs.get(id); }

    public List<Song> all() { return List.copyOf(songs.values()); }

    public List<String> ids() { return new ArrayList<>(songs.keySet()); }

    public int size() { return songs.size(); }

    public boolean isLoaded() { return loaded; }

    public Path libraryFile() { return libraryFile; }
}
