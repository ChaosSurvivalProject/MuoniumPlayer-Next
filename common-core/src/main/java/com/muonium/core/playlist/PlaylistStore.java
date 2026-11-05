package com.muonium.core.playlist;

import com.muonium.core.model.Playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 歌单导入导出与 revision 冲突检测。队列是运行时状态，不写回源歌单。 */
public final class PlaylistStore {

    private PlaylistStore() {}

    public record MergeResult(Playlist.Document document, boolean conflict, String reason) {}

    public static Map<String, Object> exportDocument(Playlist.Document doc) { return doc.toMap(); }

    public static Playlist.Document importDocument(Map<String, Object> map) {
        return Playlist.Document.fromMap(map);
    }

    public static Playlist.Document addItem(Playlist.Document doc, String songId, long addedAtMs) {
        List<Playlist.Item> items = new ArrayList<>(doc.items());
        items.add(new Playlist.Item(songId, addedAtMs));
        return new Playlist.Document(doc.schemaVersion(), doc.id(), doc.name(), items,
                doc.revision() + 1, doc.updatedAt());
    }

    public static Playlist.Document removeItem(Playlist.Document doc, String songId) {
        List<Playlist.Item> items = new ArrayList<>();
        for (Playlist.Item it : doc.items()) if (!it.songId().equals(songId)) items.add(it);
        return new Playlist.Document(doc.schemaVersion(), doc.id(), doc.name(), items,
                doc.revision() + 1, doc.updatedAt());
    }

    /** revision 不一致即冲突，不静默覆盖，交由用户选择。 */
    public static MergeResult merge(Playlist.Document local, Playlist.Document remote, int baseRevision) {
        if (local.revision() != baseRevision && remote.revision() != baseRevision) {
            return new MergeResult(local, true, "本地与远端都基于 revision=" + baseRevision + " 发生了变更");
        }
        return new MergeResult(local.revision() >= remote.revision() ? local : remote, false, null);
    }
}
