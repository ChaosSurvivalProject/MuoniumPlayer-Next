package com.muonium.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 ffmpeg 把任意音视频流转码为 OGG Vorbis（44.1kHz / 双声道 / q4），固定参数、不拼 shell。
 * 二进制路径来自环境变量 MUONIUM_FFMPEG，缺省从 PATH 查找。
 */
public final class FfmpegTranscoder implements Transcoder {

    private static final Pattern SAFE = Pattern.compile("[a-z0-9_\\-]+");

    public static FfmpegTranscoder create() {
        return new FfmpegTranscoder();
    }

    private static String ffmpegBinary() {
        String e = System.getenv("MUONIUM_FFMPEG");
        return (e == null || e.isBlank()) ? "ffmpeg" : e;
    }

    @Override
    public Path transcodeToOgg(Path in, Path outDir, String baseName) throws ProviderException {
        if (in == null || !Files.isRegularFile(in)) {
            throw new ProviderException("TRANSCODE: 输入文件不存在: " + (in == null ? "null" : in));
        }
        if (baseName == null || !SAFE.matcher(baseName).matches()) {
            throw new ProviderException("TRANSCODE: 非法文件名: " + baseName);
        }
        Path out = outDir.resolve(baseName + ".ogg");
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new ProviderException("TRANSCODE: 无法创建输出目录: " + outDir, e);
        }
        List<String> cmd = List.of(
                ffmpegBinary(), "-hide_banner", "-v", "error", "-nostdin",
                "-i", in.toAbsolutePath().toString(),
                "-vn", "-map", "a:0?", "-c:a", "libvorbis", "-q:a", "4",
                "-ar", "44100", "-ac", "2", "-y", out.toAbsolutePath().toString());
        ProcessResult r = run(cmd);
        if (r.exit != 0 || !Files.isRegularFile(out)) {
            throw new ProviderException("TRANSCODE: ffmpeg 失败 (exit=" + r.exit + "): " + r.stderr.trim());
        }
        return out;
    }

    private record ProcessResult(int exit, String stdout, String stderr) {}

    private static ProcessResult run(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(cmd));
            pb.redirectErrorStream(false);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread t1 = drain(p.getInputStream(), out);
            Thread t2 = drain(p.getErrorStream(), err);
            int exit = p.waitFor();
            t1.join();
            t2.join();
            return new ProcessResult(exit, out.toString(), err.toString());
        } catch (IOException e) {
            return new ProcessResult(-1, "", "外部进程不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", "外部进程被中断");
        }
    }

    private static Thread drain(InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (in) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    sink.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // 解码失败由空输出体现
            }
        }, "muonium-transcode-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
