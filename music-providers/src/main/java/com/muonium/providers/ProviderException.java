package com.muonium.providers;

/**
 * 音乐源适配层统一异常。所有对外方法以非受检形式抛出，
 * 上层（CLI / Fabric 集成）捕获后转成用户可见的中文提示，且脱敏（不回显 Cookie/Token）。
 *
 * <p><b>降级可读性</b>：每个错误带一个稳定的 {@link #code()} 与 {@link #retryable()} 标志，
 * 让上层能区分「凭据失效 / 限流 / 上游故障 / 网络超时 / 解析失败」并给出对应提示，
 * 而不是把一切笼统报成「网络错误」。这是提示词 P0「四种失败场景均需明确降级」的落地。
 */
public final class ProviderException extends RuntimeException {

    // 稳定错误码：CLI / UI / 文档排错表据此匹配，不依赖易变的中文文案。
    public static final String CREDENTIAL = "QQ_MUSIC_REQUIRES_COOKIE";
    public static final String AUTH_EXPIRED = "AUTH_EXPIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String UPSTREAM = "UPSTREAM";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NETWORK = "NETWORK";
    public static final String SIGN = "SIGN";
    public static final String LYRIC = "LYRIC";
    public static final String TRANSCODE = "TRANSCODE";
    public static final String PARSE = "PARSE";
    public static final String PROVIDER = "PROVIDER";

    private final String explicitCode;

    public ProviderException(String message) {
        this(message, null, null);
    }

    public ProviderException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ProviderException(String message, Throwable cause, String explicitCode) {
        super(message, cause);
        this.explicitCode = explicitCode;
    }

    /** 给 CLI / UI 用的简短错误码，便于用户对照排错文档。 */
    public String code() {
        if (explicitCode != null && !explicitCode.isBlank()) return explicitCode;
        String m = getMessage() == null ? "" : getMessage();
        if (m.contains("COOKIE")) return CREDENTIAL;
        if (m.contains("HTTP")) return NETWORK;
        if (m.contains("NETWORK")) return NETWORK;
        if (m.contains("sign") || m.contains("SIGN")) return SIGN;
        if (m.contains("lyric") || m.contains("LYRIC")) return LYRIC;
        if (m.contains("transcode") || m.contains("ffmpeg")) return TRANSCODE;
        if (m.contains("parse") || m.contains("PARSE")) return PARSE;
        return PROVIDER;
    }

    /**
     * 是否建议调用方稍后重试：限流 / 上游故障 / 网络超时 / 一般网络错误为 true；
     * 凭据失效、签名失效、解析失败等重试无意义，为 false。上层据此做指数退避或提示更新 Cookie。
     */
    public boolean retryable() {
        switch (code()) {
            case RATE_LIMITED:
            case UPSTREAM:
            case TIMEOUT:
            case NETWORK:
                return true;
            default:
                return false;
        }
    }
}
