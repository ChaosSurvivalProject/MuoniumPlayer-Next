package com.muonium.providers;

import com.muonium.core.spi.Ports;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QQMusicIntegrationTest {

    @Test
    void registryWithoutCookieHasOnlyLocal() {
        MediaSourceRegistry reg = QQMusicIntegration.registry(Path.of("."), null, "0");
        assertTrue(reg.contains("local"));
        assertFalse(reg.contains("qq"));
    }

    @Test
    void registryWithCookieRegistersQq() {
        MediaSourceRegistry reg = QQMusicIntegration.registry(Path.of("."), "uin=99; qm_key=x", "99");
        assertTrue(reg.contains("qq"));
        assertTrue(reg.names().contains("local"));
    }

    @Test
    void streamingBackendWrapsNative() {
        Ports.AudioBackend nativeBackend = new Fakes.FakeAudioBackend();
        QQMusicAudioBackend b = QQMusicIntegration.streamingBackend(
                nativeBackend, Path.of("."), "uin=1; qm_key=x", "1", null);
        assertNotNull(b);
        assertTrue(b instanceof QQMusicAudioBackend);
    }

    @Test
    void uinExtractedFromCookie() {
        assertEquals("12345", QQMusicIntegration.uinFromCookie("a=b; uin=12345; c=d"));
        assertEquals("0", QQMusicIntegration.uinFromCookie(null));
        assertEquals("0", QQMusicIntegration.uinFromCookie("no-uin-here"));
    }
}
