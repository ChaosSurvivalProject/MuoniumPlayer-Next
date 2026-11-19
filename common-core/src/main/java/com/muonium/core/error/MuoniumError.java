package com.muonium.core.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 跨端统一错误模型。用户消息必须中文、可读；技术详情不得泄露 Cookie/Token。 */
public final class MuoniumError {

    private MuoniumError() {}

    public enum Code {
        LIBRARY_TRACK_NOT_FOUND("曲库里找不到这首歌", true),
        LIBRARY_AUDIO_MISSING("歌曲的音频文件缺失", false),
        LIBRARY_CORRUPTED("曲库文件损坏", true),
        AUDIO_DECODE_UNSUPPORTED("这个音频格式暂不支持", false),
        AUDIO_DEVICE_UNAVAILABLE("音频设备不可用", true),
        AUDIO_BUFFER_UNDERRUN("音频缓冲不足，播放可能卡顿", true),
        AUDIO_BACKEND_LIMITATION("当前平台不支持这个操作", false),
        LYRIC_PARSE_FAILED("歌词解析失败", false),
        PROVIDER_LOGIN_EXPIRED("该来源登录已过期，请重新授权", false),
        PROVIDER_RATE_LIMITED("该来源请求过于频繁，请稍后再试", true),
        PROVIDER_TRACK_UNAVAILABLE("该歌曲在此来源暂不可用", false),
        PROVIDER_NETWORK_OFFLINE("网络不可用，已切换为离线曲库", true),
        LICENSE_NOT_PERMITTED("该内容没有可导入的授权标记", false),
        PACK_VERSION_INCOMPATIBLE("附加包版本与游戏版本不兼容", false),
        SYNC_PERMISSION_DENIED("没有同步权限（本项目不使用同步）", false),
        INTERNAL_UNEXPECTED("发生未预期的内部错误", true);

        private final String userMessage;
        private final boolean retryable;
        Code(String userMessage, boolean retryable) {
            this.userMessage = userMessage; this.retryable = retryable;
        }
        public String userMessage() { return userMessage; }
        public boolean retryable() { return retryable; }
    }

    /** 敏感信息字段名：日志脱敏必须过滤这些键（含 QQ 音乐会话密钥）。 */
    public static final String[] SENSITIVE_KEYS = {
            "set-cookie", "cookie", "authorization", "token", "qrkey", "unikey",
            "qm_key", "skey", "pskey", "phone", "password"
    };

    /** 由 SENSITIVE_KEYS 生成正则alternation，避免手写漂移。 */
    private static final String KEY_PATTERN = buildKeyPattern();

    private static String buildKeyPattern() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SENSITIVE_KEYS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(java.util.regex.Pattern.quote(SENSITIVE_KEYS[i]));
        }
        return sb.toString();
    }

    public record Detail(Code code, String userMessage, String technicalDetail,
                         boolean retryable, String suggestedAction,
                         boolean sensitiveDataLeaked, String platform,
                         String gameVersion, String packVersion, String protocolVersion) {
        public Detail {
            Objects.requireNonNull(code, "code");
            if (userMessage == null || userMessage.isBlank()) userMessage = code.userMessage();
            if (technicalDetail == null) technicalDetail = "";
            if (suggestedAction == null) suggestedAction = "请检查曲库文件后重试";
        }

        public static Detail of(Code code, String technicalDetail) {
            return new Detail(code, code.userMessage(), redact(technicalDetail), code.retryable(),
                    "请检查曲库文件与日志，必要时重新导入", false,
                    "unknown", "unknown", "unknown", "1");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code.name());
            m.put("userMessage", userMessage);
            m.put("technicalDetail", technicalDetail);
            m.put("retryable", retryable);
            m.put("suggestedAction", suggestedAction);
            m.put("sensitiveDataLeaked", sensitiveDataLeaked);
            m.put("platform", platform);
            m.put("gameVersion", gameVersion);
            m.put("packVersion", packVersion);
            m.put("protocolVersion", protocolVersion);
            return m;
        }
    }

    /**
     * 日志脱敏：把凭据字段的值整体替换为 {@code <redacted>}。
     *
     * <p>修复要点（原实现的真实漏洞）：原正则用 {@code [^\s,;]+} 在分号处停止，
     * 导致 {@code Cookie: uin=1; qm_key=SECRET; skey=XXX} 中分号后的核心密钥全部明文泄露；
     * 且 JSON 形式 {@code {"cookie":"SECRET"}} 完全不匹配。本实现覆盖三种形态：
     * <ol>
     *   <li>JSON 键值：{@code "cookie":"value"}；</li>
     *   <li>等号/冒号且值可含分号：{@code key=value; other=...} —— 一直吃到空白/引号/JSON 边界；</li>
     *   <li>HTTP 头整行：{@code Cookie: a=1; b=2} —— 吃掉到行尾。</li>
     * </ol>
     * 只做替换，不回显原值；对 null 返回空串。
     */
    public static String redact(String input) {
        if (input == null) return "";
        String s = input;
        // 1) JSON 键值："key" : "value"
        s = s.replaceAll("(?i)(\"(?:" + KEY_PATTERN + ")\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
                "$1\"<redacted>\"");
        // 2) HTTP 头整行：Cookie / Authorization / Set-Cookie 后到行尾
        s = s.replaceAll("(?i)\\b(cookie|set-cookie|authorization|token|qrkey|unikey|qm_key|skey|pskey|phone|password)"
                        + "\\s*:\\s*[^\\r\\n]*",
                "$1: <redacted>");
        // 3) 等号形式：key=value; other=value（值可含分号，吃到空白或 JSON 边界）
        s = s.replaceAll("(?i)\\b(" + KEY_PATTERN + ")\\s*=\\s*[^\\s,}\\]\\\"]+",
                "$1=<redacted>");
        return s;
    }
}
