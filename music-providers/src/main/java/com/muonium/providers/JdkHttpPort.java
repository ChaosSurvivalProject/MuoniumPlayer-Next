package com.muonium.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

/**
 * 基于 JDK 11+ {@link HttpClient} 的 HTTP 端口实现。
 * 仅用于本机桌面 / 导入器；不进入游戏运行时网络路径（游戏内播放器不发起平台请求）。
 *
 * <p><b>降级纪律</b>：把 HTTP 状态码映射为可区分的错误码与中文提示——
 * 401/403 → 凭据失效（不可重试）；429 → 限流（可重试）；5xx → 上游故障（可重试）；
 * 超时 → TIMEOUT（可重试）；中断 → 恢复中断标志。所有日志只保留 host，不回显完整 URL / Cookie。
 */
public final class JdkHttpPort implements HttpPort {

    private final HttpClient client;

    public JdkHttpPort() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getText(String url, Map<String, String> headers) throws ProviderException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            applyHeaders(b, headers);
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                throw httpStatusError(r.statusCode(), url);
            }
            return r.body();
        } catch (ProviderException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new ProviderException("请求超时: " + safeHost(url)
                    + "（网络不可达或响应过慢，稍后重试）", e, ProviderException.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 不吞中断，恢复标志供上层收尾
            throw new ProviderException("请求被中断: " + safeHost(url), e, ProviderException.NETWORK);
        } catch (IOException e) {
            throw new ProviderException("网络错误: " + safeHost(url), e, ProviderException.NETWORK);
        } catch (Exception e) {
            throw new ProviderException("HTTP 请求失败: " + safeHost(url), e);
        }
    }

    @Override
    public void download(String url, Path target, Map<String, String> headers) throws ProviderException {
        try {
            Files.createDirectories(target.getParent());
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET();
            applyHeaders(b, headers);
            HttpResponse<Path> r = client.send(b.build(), HttpResponse.BodyHandlers.ofFile(target));
            if (r.statusCode() < 200 || r.statusCode() >= 300 || !Files.isRegularFile(target)) {
                // 下载失败不留半截文件（P0：退出无残留临时文件）
                safeDelete(target);
                throw httpStatusError(r.statusCode(), url);
            }
            // 某些实现返回临时文件名，确保落到 target
            if (!target.equals(r.body())) {
                Files.move(r.body(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (ProviderException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            safeDelete(target);
            throw new ProviderException("下载超时: " + safeHost(url), e, ProviderException.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            safeDelete(target);
            throw new ProviderException("下载被中断: " + safeHost(url), e, ProviderException.NETWORK);
        } catch (IOException e) {
            safeDelete(target);
            throw new ProviderException("下载网络错误: " + safeHost(url), e, ProviderException.NETWORK);
        } catch (Exception e) {
            safeDelete(target);
            throw new ProviderException("下载失败: " + safeHost(url), e);
        }
    }

    /** 把 HTTP 状态码映射为可区分的错误码 + 可读提示（不含 URL 查询串 / Cookie）。包可见以便单测。 */
    static ProviderException httpStatusError(int status, String url) {
        String host = safeHost(url);
        if (status == 401 || status == 403) {
            return new ProviderException("HTTP " + status + " for " + host
                    + "：Cookie 已失效或无权限访问，请更新你自己的 Cookie（见 docs/qq-music.md）",
                    null, ProviderException.AUTH_EXPIRED);
        }
        if (status == 429) {
            return new ProviderException("HTTP 429 for " + host
                    + "：平台限流，请稍后重试", null, ProviderException.RATE_LIMITED);
        }
        if (status >= 500) {
            return new ProviderException("HTTP " + status + " for " + host
                    + "：平台服务暂不可用，稍后重试", null, ProviderException.UPSTREAM);
        }
        return new ProviderException("HTTP " + status + " for " + host
                + "：请求被拒绝", null, ProviderException.NETWORK);
    }

    private static void safeDelete(Path p) {
        try { if (p != null) Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    private static void applyHeaders(HttpRequest.Builder b, Map<String, String> headers) {
        if (headers != null) headers.forEach(b::header);
    }

    private static String safeHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "url";
        }
    }
}
