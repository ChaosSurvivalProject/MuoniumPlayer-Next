package com.muonium.providers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * QQ 音乐非官方接口签名器（结构演示）。
 *
 * <p><b>非官方、仅供个人使用。</b> 腾讯未提供面向第三方的播放 API，社区版实现
 * （如 jsososo/QQMusicApi）通过 {@code musicu.fcg} 的 {@code sign} 参数校验反爬。
 * 签名算法为 {@code md5(musicId + jsonData + musicKey)}，其中 musicId 固定为 "zza"，
 * musicKey 是一段随客户端版本变化的常量。</p>
 *
 * <p><b>重要</b>：本文件中的 {@link #MUSIC_KEY} 为占位值，<b>仅供演示调用结构</b>；
 * 真实可用值需从最新版 QQ 音乐 Web 端或社区版 API 同步。单元测试使用注入的假签名器，
 * 不依赖此处的真实密钥，因此本占位不影响测试与编译。集成者若发现 401/签名错误，
 * 应先更新本常量再上报。</p>
 */
public final class QQMusicSign {

    private QQMusicSign() {}

    /** 固定标识（社区版约定）。 */
    public static final String MUSIC_ID = "zza";

    /**
     * 占位密钥。真实值随 QQ 音乐客户端版本变化，需从最新版同步。
     * 此处不是可用密钥，仅用于让调用结构完整可编译；真实请求请替换为最新值。
     */
    public static final String MUSIC_KEY = "REPLACE_WITH_SYNCED_QQ_MUSIC_KEY";

    /** 签名：md5(musicId + dataJson + musicKey)。 */
    public static String sign(String dataJson) {
        return md5(MUSIC_ID + dataJson + MUSIC_KEY);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException("SIGN: 缺少 MD5 算法", e);
        }
    }
}
