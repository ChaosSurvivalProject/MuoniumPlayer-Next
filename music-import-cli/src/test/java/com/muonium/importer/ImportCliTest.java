package com.muonium.importer;

import com.muonium.core.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportCliTest {

    private static String track(String id, String ref, String license) {
        return "{\"id\":\"" + id + "\",\"durationMs\":214000,\"durationSource\":\"import_probe\","
                + "\"source\":{\"provider\":\"user_library\",\"license\":\"" + license + "\"},"
                + "\"assets\":{\"audio\":{\"kind\":\"resource_sound_event\",\"ref\":\"" + ref + "\"}}}";
    }

    private static String library(String tracks) {
        return "{\"schemaVersion\":1,\"tracks\":[" + tracks + "]}";
    }

    @Test
    void validLibraryPasses() {
        var r = LibraryValidator.validate(Json.parseObject(library(track("track_0001", "muonium:track_0001", "user_provided"))));
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(1, r.tracks());
    }

    @Test
    void duplicateIdsAreRejected() {
        String t = track("track_0001", "muonium:a", "user_provided") + "," + track("track_0001", "muonium:b", "user_provided");
        var r = LibraryValidator.validate(Json.parseObject(library(t)));
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("重复")));
    }

    @Test
    void duplicateSoundEventsAreRejected() {
        String t = track("track_0001", "muonium:same", "user_provided") + "," + track("track_0002", "muonium:same", "user_provided");
        var r = LibraryValidator.validate(Json.parseObject(library(t)));
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("声音事件重复")));
    }

    @Test
    void pathTraversalAndUrlsAreRejected() {
        for (String bad : new String[]{"../../etc/passwd", "/absolute/path", "C:/Windows/x", "https://x/y.mp3"}) {
            var r = LibraryValidator.validate(Json.parseObject(library(track("track_0001", bad, "cc0"))));
            assertFalse(r.ok(), bad);
            assertFalse(LibraryValidator.isSafePath(bad), bad);
        }
    }

    @Test
    void credentialsAndTempUrlsAreRejected() {
        String t = "{\"id\":\"track_0001\",\"durationSource\":\"import_probe\",\"cookie\":\"abc\","
                + "\"tempUrl\":\"https://x\",\"source\":{\"license\":\"cc0\"},"
                + "\"assets\":{\"audio\":{\"ref\":\"muonium:a\"}}}";
        var r = LibraryValidator.validate(Json.parseObject(library(t)));
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("临时音源 URL")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("敏感字段 cookie")));
    }

    @Test
    void missingLicenseIsRejected() {
        var r = LibraryValidator.validate(Json.parseObject(library(track("track_0001", "muonium:a", ""))));
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("LICENSE_NOT_PERMITTED")));
    }

    @Test
    void prototypePollutionInLibraryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Json.parseObject("{\"__proto__\":{\"polluted\":true},\"tracks\":[]}"));
    }

    @Test
    void cliValidateCommandWorksOnRealFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("library.json");
        Files.writeString(f, library(track("track_0001", "muonium:track_0001", "cc0")));
        assertEquals(0, ImportCli.run(new String[]{"validate", f.toString()}));
        Files.writeString(f, library(track("../bad", "muonium:a", "cc0")));
        assertEquals(1, ImportCli.run(new String[]{"validate", f.toString()}));
    }

    @Test
    void safePathAcceptsNormalRelativePaths() {
        assertTrue(LibraryValidator.isSafePath("sounds/muonium/track_0001.ogg"));
        assertTrue(LibraryValidator.isSafePath("muonium:track_0001"));
        assertFalse(LibraryValidator.isSafePath(""));
        assertFalse(LibraryValidator.isSafePath(null));
    }

    @Test
    void packRejectsMissingDirectories() {
        assertThrows(IllegalArgumentException.class,
                () -> Packer.pack(Path.of("/nope"), Path.of("/nope2"), Path.of("/tmp/x.mcaddon")));
    }

    @Test
    void emptyLibraryHasZeroTracks() {
        var r = LibraryValidator.validate(Map.of("schemaVersion", 1, "tracks", java.util.List.of()));
        assertTrue(r.ok());
        assertEquals(0, r.tracks());
    }
}
