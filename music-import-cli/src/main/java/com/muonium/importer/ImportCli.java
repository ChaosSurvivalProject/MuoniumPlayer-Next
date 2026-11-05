package com.muonium.importer;

import com.muonium.core.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 本机导入器 CLI。
 *
 * <p>硬约束：
 * <ul>
 *   <li>只处理用户显式选择的合法文件；不自动扫描整个磁盘。</li>
 *   <li>不内置任何平台破解/解灰/绕过会员逻辑，不存储 Cookie/Token。</li>
 *   <li>FFmpeg/ffprobe 通过固定参数列表调用，不拼 shell；默认需用户显式启用。</li>
 *   <li>打包防 Zip Slip；输出前校验清单、ID、路径、许可证标记与完整性。</li>
 * </ul>
 */
public final class ImportCli {

    private ImportCli() {}

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    static int run(String[] args) {
        if (args.length == 0 || List.of("--help", "-h", "help").contains(args[0])) {
            printUsage();
            return args.length == 0 ? 2 : 0;
        }
        String cmd = args[0];
        try {
            return switch (cmd) {
                case "validate" -> cmdValidate(args);
                case "probe" -> cmdProbe(args);
                case "transcode" -> cmdTranscode(args);
                case "pack" -> cmdPack(args);
                default -> { System.err.println("未知命令: " + cmd); printUsage(); yield 2; }
            };
        } catch (Exception e) {
            System.err.println("命令失败 [" + cmd + "]: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("""
                MuoniumPlayer 本机导入器（纯客户端，不连接任何游戏服务端）

                  validate <library.json>                 校验曲库清单
                  probe <audio-file>                      用 ffprobe 探测时长(ms)
                  transcode <input> <output.ogg>          用 ffmpeg 转 OGG Vorbis
                  pack <BP目录> <RP目录> <out.mcaddon>    打包附加包（防路径穿越）

                环境变量 MUONIUM_FFMPEG / MUONIUM_FFPROBE 可指定可执行文件；
                缺省从 PATH 查找。外部进程默认关闭，调用这些子命令即视为显式启用。
                """);
    }

    private static int cmdValidate(String[] args) throws IOException {
        if (args.length < 2) { System.err.println("用法: validate <library.json>"); return 2; }
        Path p = Path.of(args[1]);
        String text = Files.readString(p, StandardCharsets.UTF_8);
        Map<String, Object> doc = Json.parseObject(text);
        LibraryValidator.Result r = LibraryValidator.validate(doc);
        r.errors().forEach(e -> System.out.println("ERROR " + e));
        System.out.println(r.ok()
                ? "VALIDATE PASS: " + r.tracks() + " 首曲目"
                : "VALIDATE FAIL: " + r.errors().size() + " 个问题");
        return r.ok() ? 0 : 1;
    }

    private static int cmdProbe(String[] args) {
        if (args.length < 2) { System.err.println("用法: probe <audio-file>"); return 2; }
        long ms = Packer.probeDurationMs(Path.of(args[1]));
        if (ms < 0) { System.err.println("PROBE FAIL: ffprobe 不可用或文件无法解析"); return 1; }
        System.out.println("PROBE OK durationMs=" + ms);
        return 0;
    }

    private static int cmdTranscode(String[] args) {
        if (args.length < 3) { System.err.println("用法: transcode <input> <output.ogg>"); return 2; }
        Packer.transcodeToOgg(Path.of(args[1]), Path.of(args[2]));
        System.out.println("TRANSCODE OK -> " + args[2]);
        return 0;
    }

    private static int cmdPack(String[] args) throws IOException {
        if (args.length < 4) { System.err.println("用法: pack <BP目录> <RP目录> <out.mcaddon>"); return 2; }
        Packer.pack(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        System.out.println("PACK OK -> " + args[3]);
        return 0;
    }
}
