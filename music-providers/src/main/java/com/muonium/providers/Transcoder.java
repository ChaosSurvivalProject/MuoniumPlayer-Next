package com.muonium.providers;

import java.nio.file.Path;

/**
 * 转码端口：把下载到的原始格式（QQ 音乐多为 M4A/MP3）转成 Minecraft 可用的 OGG Vorbis。
 * 注入式设计，测试用假实现（直接复制或命名）验证编排逻辑，不依赖 ffmpeg。
 */
public interface Transcoder {

    /**
     * 将 in 转码为 OGG，写入 {@code outDir/{baseName}.ogg}，返回最终文件路径。
     * 不得修改 in；outDir 不存在时自行创建。
     */
    Path transcodeToOgg(Path in, Path outDir, String baseName) throws ProviderException;
}
