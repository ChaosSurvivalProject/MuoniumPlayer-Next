package com.muonium.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 外部进程与打包：固定参数、不拼 shell、防 Zip Slip。 */
public final class Packer {

    private Packer() {}

    private static String ffmpegBinary() {
        String e = System.getenv("MUONIUM_FFMPEG");
        return e == null || e.isBlank() ? "ffmpeg" : e;
    }

    private static String ffprobeBinary() {
        String e = System.getenv("MUONIUM_FFPROBE");
        return e == null || e.isBlank() ? "ffprobe" : e;
    }

    /** 用 ffprobe 探测时长，失败返回 -1（不抛异常导致游戏崩溃）。 */
    public static long probeDurationMs(Path audio) {
        if (audio == null || !Files.isRegularFile(audio)) return -1L;
        List<String> cmd = List.of(ffprobeBinary(), "-v", "error", "-show_entries",
                "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                audio.toAbsolutePath().toString());
        try {
            ProcessResult r = run(cmd);
            if (r.exit != 0 || r.stdout.isBlank()) return -1L;
            return (long) (Double.parseDouble(r.stdout.trim()) * 1000);
        } catch (Exception e) {
            return -1L;
        }
    }

    /** 转 OGG Vorbis + 采样率/声道规范化；stdout/stderr 异步排空避免管道死锁。 */
    public static void transcodeToOgg(Path input, Path output) {
        if (input == null || !Files.isRegularFile(input)) {
            throw new IllegalArgumentException("输入文件不存在: " + input);
        }
        List<String> cmd = List.of(ffmpegBinary(), "-hide_banner", "-v", "error", "-nostdin",
                "-i", input.toAbsolutePath().toString(),
                "-vn", "-map", "a:0?", "-c:a", "libvorbis", "-q:a", "4",
                "-ar", "44100", "-ac", "2", "-y", output.toAbsolutePath().toString());
        ProcessResult r = run(cmd);
        if (r.exit != 0 || !Files.isRegularFile(output)) {
            throw new IllegalStateException("ffmpeg 转码失败 (exit=" + r.exit + "): " + r.stderr.trim());
        }
    }

    private record ProcessResult(int exit, String stdout, String stderr) {}

    private static ProcessResult run(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(cmd));
            pb.redirectErrorStream(false);
            Process p = pb.start();
            // 异步排空，避免管道写满导致子进程挂死（上游 FfmpegAnalysisAudio.java:186 记录过这个坑）。
            var out = new StringBuilder();
            var err = new StringBuilder();
            Thread t1 = drain(p.getInputStream(), out);
            Thread t2 = drain(p.getErrorStream(), err);
            int exit = p.waitFor();
            t1.join(); t2.join();
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
                while ((n = in.read(buf)) != -1) sink.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // 解码失败由空输出体现，不吞掉主流程异常
            }
        }, "muonium-ffmpeg-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 打包 .mcaddon：BP/RP 分目录写入，逐条校验 entry 名，防 Zip Slip 与绝对路径。 */
    public static void pack(Path bp, Path rp, Path output) throws IOException {
        if (!Files.isDirectory(bp) || !Files.isDirectory(rp)) {
            throw new IllegalArgumentException("行为包/资源包目录不存在");
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(output))) {
            addTree(zos, bp, "behavior_pack/");
            addTree(zos, rp, "resource_pack/");
        }
    }

    private static void addTree(ZipOutputStream zos, Path root, String prefix) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (!LibraryValidator.isSafePath(rel)) {
                    throw new IOException("拒绝不安全的打包路径: " + rel);
                }
                ZipEntry e = new ZipEntry(prefix + rel);
                zos.putNextEntry(e);
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }
}
