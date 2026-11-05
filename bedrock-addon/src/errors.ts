/** 与 shared-schemas/schema/error-codes.json 完全一致的错误码集合。 */
export type ErrorCode =
  | "LIBRARY_TRACK_NOT_FOUND" | "LIBRARY_AUDIO_MISSING" | "LIBRARY_CORRUPTED"
  | "AUDIO_DECODE_UNSUPPORTED" | "AUDIO_DEVICE_UNAVAILABLE" | "AUDIO_BUFFER_UNDERRUN"
  | "AUDIO_BACKEND_LIMITATION" | "LYRIC_PARSE_FAILED" | "PROVIDER_LOGIN_EXPIRED"
  | "PROVIDER_RATE_LIMITED" | "PROVIDER_TRACK_UNAVAILABLE" | "PROVIDER_NETWORK_OFFLINE"
  | "LICENSE_NOT_PERMITTED" | "PACK_VERSION_INCOMPATIBLE" | "SYNC_PERMISSION_DENIED"
  | "INTERNAL_UNEXPECTED";

export interface MuoniumError {
  readonly code: ErrorCode;
  readonly userMessage: string;
  readonly technicalDetail: string;
  readonly retryable: boolean;
  readonly suggestedAction: string;
  readonly sensitiveDataLeaked: boolean;
  readonly platform: string;
  readonly gameVersion: string;
  readonly packVersion: string;
  readonly protocolVersion: number;
}

const MESSAGES: Record<ErrorCode, string> = {
  LIBRARY_TRACK_NOT_FOUND: "曲库里找不到这首歌",
  LIBRARY_AUDIO_MISSING: "歌曲的音频文件缺失",
  LIBRARY_CORRUPTED: "曲库文件损坏",
  AUDIO_DECODE_UNSUPPORTED: "这个音频格式暂不支持",
  AUDIO_DEVICE_UNAVAILABLE: "音频设备不可用",
  AUDIO_BUFFER_UNDERRUN: "音频缓冲不足，播放可能卡顿",
  AUDIO_BACKEND_LIMITATION: "当前平台不支持这个操作",
  LYRIC_PARSE_FAILED: "歌词解析失败",
  PROVIDER_LOGIN_EXPIRED: "该来源登录已过期，请重新授权",
  PROVIDER_RATE_LIMITED: "该来源请求过于频繁，请稍后再试",
  PROVIDER_TRACK_UNAVAILABLE: "该歌曲在此来源暂不可用",
  PROVIDER_NETWORK_OFFLINE: "网络不可用，已切换为离线曲库",
  LICENSE_NOT_PERMITTED: "该内容没有可导入的授权标记",
  PACK_VERSION_INCOMPATIBLE: "附加包版本与游戏版本不兼容",
  SYNC_PERMISSION_DENIED: "没有同步权限（本项目不使用同步）",
  INTERNAL_UNEXPECTED: "发生未预期的内部错误",
};

const RETRYABLE: ReadonlySet<ErrorCode> = new Set<ErrorCode>([
  "LIBRARY_TRACK_NOT_FOUND", "LIBRARY_CORRUPTED", "AUDIO_DEVICE_UNAVAILABLE",
  "AUDIO_BUFFER_UNDERRUN", "PROVIDER_RATE_LIMITED", "PROVIDER_NETWORK_OFFLINE",
  "INTERNAL_UNEXPECTED",
]);

const SENSITIVE = /cookie|authorization|token|qrkey|unikey|phone|password/gi;

/** 日志脱敏：绝不把凭据正文写进内容日志。 */
export function redact(input: string): string {
  return input.replace(SENSITIVE, (m) => `${m}=<redacted>`);
}

export function makeError(code: ErrorCode, technicalDetail: string): MuoniumError {
  return {
    code,
    userMessage: MESSAGES[code],
    technicalDetail: redact(technicalDetail),
    retryable: RETRYABLE.has(code),
    suggestedAction: "请检查曲库文件，必要时重新导入附加包",
    sensitiveDataLeaked: false,
    platform: "bedrock",
    gameVersion: "1.21.130",
    packVersion: "1.0.0",
    protocolVersion: 1,
  };
}

export function formatForLog(err: MuoniumError): string {
  return `[muonium] ${err.code} ${err.userMessage} | ${err.technicalDetail}`;
}
