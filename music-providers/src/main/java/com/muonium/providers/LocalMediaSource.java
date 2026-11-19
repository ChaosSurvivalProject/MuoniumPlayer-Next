package com.muonium.providers;

import com.muonium.core.json.Json;
import com.muonium.core.lyric.LyricParsers;
import com.muonium.core.model.Lyric;
import com.muonium.core.model.Song;
import com.muonium.core.spi.Ports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地曲库音乐源：把已导入的 library.json 暴露成统一的 {@link Ports.MediaSource}，
 * 这样搜索 / 播放入口对"本地"与"QQ 音乐"保持一致。
 *
 * <p>只读用户显式指定的曲库目录，绝不自动扫描磁盘；解析失败降级为空列表。
 */
public final class LocalMediaSource implements Ports.MediaSource {

    private final Ports.JsonCodec codec;
    private final Path libraryDir;
    private final List<Song> songs = new ArrayList<>();

    public LocalMediaSource(Ports.JsonCodec codec, Path libraryDir) {
        this.codec = codec;
        this.libraryDir = libraryDir;
        load();
    }

    private void load() {
        Path file = libraryDir.resolve("library.json");
        if (!Files.isRegularFile(file)) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.length() > Json.MAX_INPUT_CHARS) return;
            Map<String, Object> root = codec.parse(text);
            Object raw = root.get("tracks");
            if (!(raw instanceof List<?> tracks)) return;
            for (Object o : tracks) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                try {
                    songs.add(Song.fromMap(m));
                } catch (RuntimeException ignored) {
                    // 跳过损坏曲目，不阻断整体加载
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 加载失败：空曲库
        }
    }

    private MediaLike toResource(Song s) {
        String audio = s.assets() == null || s.assets().audio() == null ? null : s.assets().audio().ref();
        String cover = s.assets() == null || s.assets().cover() == null ? null : s.assets().cover().ref();
        return new MediaLike(s.id(), s.title(),
                s.artists().stream().map(Song.ArtistRef::name).toList(),
                s.album() == null ? null : s.album().name(),
                s.durationMs(), audio, cover, null,
                s.source() == null ? "local" : s.source().provider());
    }

    @Override
    public List<Ports.MediaResource> search(String query, int page) {
        String q = (query == null ? "" : query).trim().toLowerCase(Locale.ROOT);
        List<Ports.MediaResource> out = new ArrayList<>();
        for (Song s : songs) {
            if (q.isEmpty()) { out.add(toResource(s).toMediaResource()); continue; }
            boolean hit = s.title() != null && s.title().toLowerCase(Locale.ROOT).contains(q);
            if (!hit) {
                for (Song.ArtistRef a : s.artists()) {
                    if (a.name() != null && a.name().toLowerCase(Locale.ROOT).contains(q)) { hit = true; break; }
                }
            }
            if (!hit && s.album() != null && s.album().name() != null
                    && s.album().name().toLowerCase(Locale.ROOT).contains(q)) hit = true;
            if (hit) out.add(toResource(s).toMediaResource());
        }
        return out;
    }

    @Override
    public Ports.MediaResource resolve(String sourceTrackId) {
        for (Song s : songs) {
            if (s.id().equals(sourceTrackId)) return toResource(s).toMediaResource();
        }
        return null;
    }

    @Override
    public List<Ports.MediaResource> getPlaylist(String id, int page) {
        // 本地曲库以整库作为单一播放列表
        return songs.stream().map(s -> toResource(s).toMediaResource()).toList();
    }

    @Override
    public Lyric.Document getLyric(Ports.MediaResource track) {
        return null; // 本地歌词由 Fabric 后端直接读文件；统一接口此处不重复实现
    }

    @Override
    public String getCover(Ports.MediaResource track) {
        return track.coverRef();
    }

    @Override
    public Ports.MediaResource prepareForImport(Ports.MediaResource track) {
        // 本地资源已在本机，原样返回即可
        return track;
    }

    @Override
    public void close() {
        songs.clear();
    }

    /** 内部轻量载体，避免重复构造 MediaResource。 */
    private record MediaLike(String id, String title, List<String> artists, String album,
                             Long durationMs, String audioRef, String coverRef,
                             Lyric.Document lyric, String license) {
        Ports.MediaResource toMediaResource() {
            return new Ports.MediaResource(id, title, artists, album, durationMs, audioRef, coverRef, lyric, license);
        }
    }
}
