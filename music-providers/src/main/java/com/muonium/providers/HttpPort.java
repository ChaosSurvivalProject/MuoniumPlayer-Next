package com.muonium.providers;

import java.nio.file.Path;
import java.util.Map;

/**
 * HTTP 端口：把"网络"从音乐源逻辑中抽离，使 QQ 音乐适配器能在纯 JVM 下用假实现完整测试。
 * common-core 的契约是零网络；本模块同样把所有 I/O 注入到这里，不直接 new HttpClient。
 */
public interface HttpPort {

    /** GET 文本（JSON / LRC 等）。失败抛 {@link ProviderException}。 */
    String getText(String url, Map<String, String> headers) throws ProviderException;

    /** 下载二进制到 target（同名覆盖）。失败抛 {@link ProviderException}。 */
    void download(String url, Path target, Map<String, String> headers) throws ProviderException;
}
