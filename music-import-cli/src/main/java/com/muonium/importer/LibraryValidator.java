package com.muonium.importer;

import com.muonium.core.model.Song;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入器输出前校验。对应提示词 5.3 的输出检查单。
 * 纯逻辑，可单元测试，不触碰磁盘。
 */
public final class LibraryValidator {

    private LibraryValidator() {}

    public record Result(boolean ok, List<String> errors, int tracks) {}

    private static final Set<String> ALLOWED_LICENSES = Set.of(
            "user_provided", "cc0", "cc-by", "cc-by-sa", "public_domain", "own_recording");

    public static Result validate(Map<String, Object> doc) {
        List<String> errors = new ArrayList<>();
        if (doc == null) return new Result(false, List.of("清单为空"), 0);

        Object v = doc.get("schemaVersion");
        if (!(v instanceof Number n) || n.intValue() != 1) {
            errors.add("schemaVersion 必须为 1");
        }

        Object rawTracks = doc.get("tracks");
        if (!(rawTracks instanceof List<?> tracks)) {
            return new Result(false, List.of("缺少 tracks 数组"), 0);
        }
        if (tracks.size() > 100_000) errors.add("tracks 数量超过上限 100000");

        Set<String> ids = new HashSet<>();
        Set<String> soundEvents = new HashSet<>();
        int count = 0;
        for (Object o : tracks) {
            if (!(o instanceof Map<?, ?> mm)) { errors.add("tracks[" + count + "] 不是对象"); continue; }
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) mm;
            count++;
            validateTrack(t, count - 1, ids, soundEvents, errors);
        }
        return new Result(errors.isEmpty(), List.copyOf(errors), count);
    }

    private static void validateTrack(Map<String, Object> t, int i, Set<String> ids,
                                      Set<String> soundEvents, List<String> errors) {
        String prefix = "tracks[" + i + "]";
        Object idObj = t.get("id");
        String id = idObj == null ? null : idObj.toString();
        if (id == null || !Song.isValidResourceName(id)) {
            errors.add(prefix + ".id 非法（要求小写 ASCII/下划线稳定编号）: " + id);
        } else if (!ids.add(id)) {
            errors.add(prefix + ".id 重复: " + id);
        }

        Object src = t.get("source");
        if (src instanceof Map<?, ?> sm) {
            Object lic = sm.get("license");
            if (lic == null || !ALLOWED_LICENSES.contains(lic.toString())) {
                errors.add(prefix + ".source.license 缺少可导入的授权标记（LICENSE_NOT_PERMITTED）");
            }
        } else {
            errors.add(prefix + ".source 缺失");
        }

        if (t.containsKey("tempUrl") || t.containsKey("streamUrl")) {
            errors.add(prefix + " 不得持久化临时音源 URL");
        }
        for (String sensitive : List.of("cookie", "token", "password", "phone")) {
            if (t.containsKey(sensitive)) errors.add(prefix + " 含敏感字段 " + sensitive);
        }

        Object assets = t.get("assets");
        if (assets instanceof Map<?, ?> am) {
            Object audio = am.get("audio");
            if (audio instanceof Map<?, ?> aa) {
                Object ref = aa.get("ref");
                String refStr = ref == null ? "" : ref.toString();
                if (!isSafePath(refStr)) errors.add(prefix + ".assets.audio.ref 路径不安全: " + refStr);
                if (refStr.startsWith("muonium:") && !soundEvents.add(refStr)) {
                    errors.add(prefix + " 声音事件重复: " + refStr);
                }
            } else {
                errors.add(prefix + ".assets.audio 缺失");
            }
            checkRef(am.get("cover"), prefix + ".assets.cover", errors);
            checkRef(am.get("lyric"), prefix + ".assets.lyric", errors);
        } else {
            errors.add(prefix + ".assets 缺失");
        }

        Object dur = t.get("durationMs");
        if (dur instanceof Number n && n.longValue() < 0) errors.add(prefix + ".durationMs 不能为负数");
        Object ds = t.get("durationSource");
        if (ds == null) errors.add(prefix + ".durationSource 必须记录来源");
    }

    private static void checkRef(Object o, String label, List<String> errors) {
        if (o instanceof Map<?, ?> m) {
            Object ref = m.get("ref");
            if (ref != null && !isSafePath(ref.toString())) errors.add(label + ".ref 路径不安全");
        }
    }

    /** 防路径穿越、绝对路径、URL、Windows 盘符、Zip Slip。 */
    public static boolean isSafePath(String ref) {
        if (ref == null || ref.isBlank() || ref.length() > 512) return false;
        if (ref.contains("..") || ref.startsWith("/") || ref.startsWith("\\")) return false;
        if (ref.matches("^[a-zA-Z]:.*")) return false;
        if (ref.contains("://")) return false;
        if (ref.contains("\u0000")) return false;
        return true;
    }
}
