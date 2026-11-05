// src/main.ts
import { world as world2, system, Player } from "@minecraft/server";

// pack/behavior_pack/library.json
var library_default = {
  schemaVersion: 1,
  protocolVersion: 1,
  gameVersion: "1.21.130",
  tracks: [
    {
      schemaVersion: 1,
      id: "track_0001",
      title: "\u793A\u4F8B\u97F3\u8F68\uFF08\u65E0\u7248\u6743\uFF09",
      sortTitle: "\u793A\u4F8B\u97F3\u8F68",
      artists: [{ id: "artist_0001", name: "\u7A0B\u5E8F\u5316\u751F\u6210" }],
      album: { id: "album_0001", name: "\u6D4B\u8BD5\u7D20\u6750" },
      durationMs: 5e3,
      durationSource: "import_probe",
      source: {
        provider: "user_library",
        providerTrackId: "track_0001",
        license: "cc0",
        region: "unknown"
      },
      assets: {
        audio: { kind: "resource_sound_event", ref: "muonium:track_0001" },
        cover: { kind: "pack_texture", ref: "textures/muonium/covers/track_0001" },
        lyric: { kind: "pack_file", ref: "lyrics/track_0001.muoni.json" }
      },
      flags: {
        hasLyric: false,
        hasTranslation: false,
        explicit: false,
        playableOffline: true
      },
      updatedAt: "2026-11-04T00:00:00+08:00"
    }
  ]
};

// src/library.ts
var MAX_TRACKS = 1e5;
var SAFE_ID = /^[a-z0-9_]+$/;
var SAFE_REF = /^[a-z0-9_:./-]+$/;
function readString(value) {
  return typeof value === "string" ? value : "";
}
function readNumber(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
function asRecord(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value : {};
}
function loadLibrary() {
  const root = asRecord(library_default);
  const rawTracks = Array.isArray(root["tracks"]) ? root["tracks"] : [];
  if (rawTracks.length > MAX_TRACKS) return emptyLibrary();
  const tracks = [];
  const seen = /* @__PURE__ */ new Set();
  for (const item of rawTracks) {
    const t = asRecord(item);
    const id = readString(t["id"]);
    if (!SAFE_ID.test(id) || seen.has(id)) continue;
    const assets = asRecord(t["assets"]);
    const audio = asRecord(assets["audio"]);
    const audioEvent = readString(audio["ref"]);
    const source = asRecord(t["source"]);
    const license = readString(source["license"]);
    if (!SAFE_REF.test(audioEvent) || license.length === 0) continue;
    const flags = asRecord(t["flags"]);
    seen.add(id);
    tracks.push({
      id,
      title: readString(t["title"]).slice(0, 500),
      durationMs: readNumber(t["durationMs"]),
      durationSource: readString(t["durationSource"]),
      audioEvent,
      license,
      playableOffline: flags["playableOffline"] === true
    });
  }
  return {
    schemaVersion: readNumber(root["schemaVersion"]) ?? 1,
    protocolVersion: readNumber(root["protocolVersion"]) ?? 1,
    gameVersion: readString(root["gameVersion"]),
    tracks
  };
}
function emptyLibrary() {
  return { schemaVersion: 1, protocolVersion: 1, gameVersion: "1.21.130", tracks: [] };
}
function findTrack(library2, id) {
  return library2.tracks.find((t) => t.id === id);
}
function declaredDuration(track) {
  return track.durationMs !== null && track.durationMs >= 0 ? track.durationMs : null;
}

// src/queue.ts
function shuffle(ids, seed) {
  const out = [...ids];
  let s = seed | 0 || 1;
  const next = () => {
    s ^= s << 13;
    s ^= s >>> 17;
    s ^= s << 5;
    return s >>> 0;
  };
  for (let i = out.length - 1; i > 0; i--) {
    const j = next() % (i + 1);
    const tmp = out[i];
    out[i] = out[j];
    out[j] = tmp;
  }
  return out;
}
var PlayQueue = class _PlayQueue {
  base;
  upcoming;
  played = [];
  currentId = null;
  mode;
  repeat;
  shuffleSeed;
  constructor(songIds, mode, repeat, seed) {
    this.base = [...new Set(songIds)];
    this.mode = mode;
    this.repeat = repeat;
    this.shuffleSeed = seed;
    this.upcoming = mode === "shuffle" ? shuffle(this.base, seed ?? 1) : [...this.base];
  }
  static restore(s) {
    const q = new _PlayQueue([], s.mode, s.repeat, s.shuffleSeed);
    q.upcoming = [...s.upcoming];
    q.played = [...s.played];
    q.currentId = s.playingId;
    return q;
  }
  current() {
    return this.currentId;
  }
  /** 队列耗尽且非列表循环时返回 null（停住），不擅自回到源歌单。 */
  next() {
    if (this.currentId !== null && this.repeat === "one") return this.currentId;
    if (this.currentId !== null) this.played.push(this.currentId);
    if (this.upcoming.length === 0) {
      if (this.repeat === "list" && this.base.length > 0) {
        this.upcoming = this.mode === "shuffle" ? shuffle(this.base, this.shuffleSeed ?? 1) : [...this.base];
      } else {
        this.currentId = null;
        return null;
      }
    }
    const id = this.upcoming.shift();
    this.currentId = id ?? null;
    return this.currentId;
  }
  previous() {
    const prev = this.played.pop();
    if (prev === void 0) return this.currentId;
    if (this.currentId !== null) this.upcoming.unshift(this.currentId);
    this.currentId = prev;
    return this.currentId;
  }
  setMode(mode) {
    if (mode === this.mode) return;
    this.mode = mode;
    if (mode === "shuffle") this.upcoming = shuffle(this.upcoming, (this.shuffleSeed ?? 1) ^ 1575931494);
    else {
      const ordered = this.base.filter((id) => this.upcoming.includes(id));
      for (const id of this.upcoming) if (!ordered.includes(id)) ordered.push(id);
      this.upcoming = ordered;
    }
  }
  setRepeat(repeat) {
    this.repeat = repeat;
  }
  enqueue(id) {
    if (id.length > 0) this.upcoming.push(id);
  }
  remove(id) {
    const i = this.upcoming.indexOf(id);
    if (i < 0) return false;
    this.upcoming.splice(i, 1);
    return true;
  }
  upcomingView() {
    return [...this.upcoming];
  }
  playedView() {
    return [...this.played];
  }
  snapshot() {
    return {
      playingId: this.currentId,
      upcoming: [...this.upcoming],
      played: [...this.played],
      mode: this.mode,
      repeat: this.repeat,
      shuffleSeed: this.shuffleSeed
    };
  }
};

// src/state.ts
import { world } from "@minecraft/server";
var SETTINGS_KEY = "muonium:settings.v1";
var MAX_SAVED_CHARS = 4096;
var DEFAULT_SETTINGS = {
  volume: 0.8,
  mode: "sequence",
  repeat: "none"
};
function isMode(v) {
  return v === "sequence" || v === "shuffle";
}
function isRepeat(v) {
  return v === "none" || v === "one" || v === "list";
}
function clampVolume(v) {
  return typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.8;
}
function loadSettings() {
  try {
    const raw = world.getDynamicProperty(SETTINGS_KEY);
    if (typeof raw !== "string" || raw.length > MAX_SAVED_CHARS) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null) return DEFAULT_SETTINGS;
    const o = parsed;
    return {
      volume: clampVolume(o["volume"]),
      mode: isMode(o["mode"]) ? o["mode"] : "sequence",
      repeat: isRepeat(o["repeat"]) ? o["repeat"] : "none"
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}
function saveSettings(s) {
  try {
    const text = JSON.stringify(s);
    if (text.length > MAX_SAVED_CHARS) return false;
    world.setDynamicProperty(SETTINGS_KEY, text);
    return true;
  } catch {
    return false;
  }
}

// src/ui.ts
import { ActionFormData } from "@minecraft/server-ui";

// src/errors.ts
var MESSAGES = {
  LIBRARY_TRACK_NOT_FOUND: "\u66F2\u5E93\u91CC\u627E\u4E0D\u5230\u8FD9\u9996\u6B4C",
  LIBRARY_AUDIO_MISSING: "\u6B4C\u66F2\u7684\u97F3\u9891\u6587\u4EF6\u7F3A\u5931",
  LIBRARY_CORRUPTED: "\u66F2\u5E93\u6587\u4EF6\u635F\u574F",
  AUDIO_DECODE_UNSUPPORTED: "\u8FD9\u4E2A\u97F3\u9891\u683C\u5F0F\u6682\u4E0D\u652F\u6301",
  AUDIO_DEVICE_UNAVAILABLE: "\u97F3\u9891\u8BBE\u5907\u4E0D\u53EF\u7528",
  AUDIO_BUFFER_UNDERRUN: "\u97F3\u9891\u7F13\u51B2\u4E0D\u8DB3\uFF0C\u64AD\u653E\u53EF\u80FD\u5361\u987F",
  AUDIO_BACKEND_LIMITATION: "\u5F53\u524D\u5E73\u53F0\u4E0D\u652F\u6301\u8FD9\u4E2A\u64CD\u4F5C",
  LYRIC_PARSE_FAILED: "\u6B4C\u8BCD\u89E3\u6790\u5931\u8D25",
  PROVIDER_LOGIN_EXPIRED: "\u8BE5\u6765\u6E90\u767B\u5F55\u5DF2\u8FC7\u671F\uFF0C\u8BF7\u91CD\u65B0\u6388\u6743",
  PROVIDER_RATE_LIMITED: "\u8BE5\u6765\u6E90\u8BF7\u6C42\u8FC7\u4E8E\u9891\u7E41\uFF0C\u8BF7\u7A0D\u540E\u518D\u8BD5",
  PROVIDER_TRACK_UNAVAILABLE: "\u8BE5\u6B4C\u66F2\u5728\u6B64\u6765\u6E90\u6682\u4E0D\u53EF\u7528",
  PROVIDER_NETWORK_OFFLINE: "\u7F51\u7EDC\u4E0D\u53EF\u7528\uFF0C\u5DF2\u5207\u6362\u4E3A\u79BB\u7EBF\u66F2\u5E93",
  LICENSE_NOT_PERMITTED: "\u8BE5\u5185\u5BB9\u6CA1\u6709\u53EF\u5BFC\u5165\u7684\u6388\u6743\u6807\u8BB0",
  PACK_VERSION_INCOMPATIBLE: "\u9644\u52A0\u5305\u7248\u672C\u4E0E\u6E38\u620F\u7248\u672C\u4E0D\u517C\u5BB9",
  SYNC_PERMISSION_DENIED: "\u6CA1\u6709\u540C\u6B65\u6743\u9650\uFF08\u672C\u9879\u76EE\u4E0D\u4F7F\u7528\u540C\u6B65\uFF09",
  INTERNAL_UNEXPECTED: "\u53D1\u751F\u672A\u9884\u671F\u7684\u5185\u90E8\u9519\u8BEF"
};
var RETRYABLE = /* @__PURE__ */ new Set([
  "LIBRARY_TRACK_NOT_FOUND",
  "LIBRARY_CORRUPTED",
  "AUDIO_DEVICE_UNAVAILABLE",
  "AUDIO_BUFFER_UNDERRUN",
  "PROVIDER_RATE_LIMITED",
  "PROVIDER_NETWORK_OFFLINE",
  "INTERNAL_UNEXPECTED"
]);
var SENSITIVE = /cookie|authorization|token|qrkey|unikey|phone|password/gi;
function redact(input) {
  return input.replace(SENSITIVE, (m) => `${m}=<redacted>`);
}
function makeError(code, technicalDetail) {
  return {
    code,
    userMessage: MESSAGES[code],
    technicalDetail: redact(technicalDetail),
    retryable: RETRYABLE.has(code),
    suggestedAction: "\u8BF7\u68C0\u67E5\u66F2\u5E93\u6587\u4EF6\uFF0C\u5FC5\u8981\u65F6\u91CD\u65B0\u5BFC\u5165\u9644\u52A0\u5305",
    sensitiveDataLeaked: false,
    platform: "bedrock",
    gameVersion: "1.21.130",
    packVersion: "1.0.0",
    protocolVersion: 1
  };
}
function formatForLog(err) {
  return `[muonium] ${err.code} ${err.userMessage} | ${err.technicalDetail}`;
}

// src/capabilities.ts
var PAUSE_RESUME_IMPLEMENTED = false;
var SEEK_IMPLEMENTED = false;
var POSITION_BAR_IMPLEMENTED = false;
var STOP_ONLY_LABEL = "\u505C\u6B62";

// src/player.ts
function clamp(v) {
  return Number.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.8;
}
function playTrack(player, track, volume) {
  if (!track.playableOffline) {
    return { ok: false, error: makeError("LIBRARY_AUDIO_MISSING", `track=${track.id}`) };
  }
  try {
    player.playSound(track.audioEvent, { volume: clamp(volume) });
    return { ok: true };
  } catch (e) {
    return { ok: false, error: makeError("AUDIO_BACKEND_LIMITATION", String(e)) };
  }
}
function stopTrack(player, audioEvent) {
  if (audioEvent === null) return { ok: true };
  try {
    player.runCommand(`stopsound @s ${audioEvent}`);
    return { ok: true };
  } catch (e) {
    return {
      ok: false,
      limitation: true,
      error: makeError("AUDIO_BACKEND_LIMITATION", `stopsound \u4E0D\u53EF\u7528: ${String(e)}`)
    };
  }
}
function progressView(track) {
  const declared = declaredDuration(track);
  return {
    label: declared === null ? "\u65F6\u957F\u672A\u77E5" : `\u58F0\u660E\u65F6\u957F ${Math.round(declared / 1e3)} \u79D2\uFF08\u4F30\u7B97\uFF0C\u975E\u5B9E\u65F6\u4F4D\u7F6E\uFF09`,
    declaredMs: declared,
    showBar: POSITION_BAR_IMPLEMENTED,
    seekable: SEEK_IMPLEMENTED
  };
}

// src/ui.ts
function report(player, message) {
  player.sendMessage(message);
}
function reportError(player, err) {
  report(player, err ? `\xA7c${err.userMessage}` : "\xA7c\u64CD\u4F5C\u5931\u8D25");
}
function pushSettings(session) {
  saveSettings(session.settings);
}
function doPlay(player, ctx2, track) {
  const session = ctx2.getSession(player);
  const res = playTrack(player, track, session.settings.volume);
  if (!res.ok) {
    if (res.error) console.warn(formatForLog(res.error));
    reportError(player, res.error);
    return;
  }
  const previous = session.current;
  session.current = track;
  const view = progressView(track);
  report(player, `\xA7a\u6B63\u5728\u64AD\u653E\uFF1A${track.title}\uFF08${view.label}\uFF09`);
  if (previous !== null && previous.audioEvent !== track.audioEvent) {
    const stopped = stopTrack(player, previous.audioEvent);
    if (stopped.limitation && stopped.error) console.warn(formatForLog(stopped.error));
  }
}
function doStop(player, ctx2) {
  const session = ctx2.getSession(player);
  const res = stopTrack(player, session.current?.audioEvent ?? null);
  session.current = null;
  if (res.limitation) {
    report(player, "\xA7e\u5DF2\u6807\u8BB0\u4E3A\u505C\u6B62\uFF1B\u5F53\u524D\u7248\u672C\u65E0\u6CD5\u4E2D\u65AD\u5DF2\u53D1\u51FA\u7684\u58F0\u97F3\uFF0C\u8BF7\u7B49\u5F85\u97F3\u8F68\u7ED3\u675F\u6216\u91CD\u8FDB\u4E16\u754C");
  } else {
    report(player, "\xA7a\u5DF2\u505C\u6B62\u64AD\u653E");
  }
}
function doNext(player, ctx2) {
  const session = ctx2.getSession(player);
  const id = session.queue.next();
  if (id === null) {
    doStop(player, ctx2);
    report(player, "\xA7e\u961F\u5217\u5DF2\u64AD\u653E\u5B8C\uFF0C\u5DF2\u505C\u4F4F");
    return;
  }
  const track = findTrack(ctx2.library, id);
  if (track === void 0) {
    report(player, "\xA7c\u66F2\u5E93\u91CC\u627E\u4E0D\u5230\u8FD9\u9996\u6B4C\uFF0C\u5DF2\u8DF3\u8FC7");
    return;
  }
  stopTrack(player, session.current?.audioEvent ?? null);
  doPlay(player, ctx2, track);
}
function doPrevious(player, ctx2) {
  const session = ctx2.getSession(player);
  const id = session.queue.previous();
  if (id === null) return;
  const track = findTrack(ctx2.library, id);
  if (track === void 0) return;
  stopTrack(player, session.current?.audioEvent ?? null);
  doPlay(player, ctx2, track);
}
function showMainForm(player, ctx2) {
  const session = ctx2.getSession(player);
  const current = session.current;
  const form = new ActionFormData();
  form.title("\u97F3\u4E50\u64AD\u653E\u5668");
  form.body(
    current === null ? "\u5C1A\u672A\u64AD\u653E\u3002\u66F2\u5E93\u5DF2\u9884\u5BFC\u5165\uFF0C\u65AD\u7F51\u4E5F\u80FD\u64AD\u653E\u3002\n\u672C\u64AD\u653E\u5668\u53EA\u5728\u4F60\u672C\u673A\u51FA\u58F0\uFF0C\u670D\u52A1\u7AEF\u4E0D\u9700\u8981\u4EFB\u4F55\u914D\u5957\u4EF6\u3002" : `\u6B63\u5728\u64AD\u653E\uFF1A${current.title}
${progressView(current).label}
\u6682\u505C/\u8FDB\u5EA6/\u8DF3\u8F6C\u5728\u5F53\u524D\u7248\u672C\u672A\u9A8C\u8BC1\uFF0C\u4EC5\u63D0\u4F9B\u300C${STOP_ONLY_LABEL}\u300D\u3002`
  );
  form.button("\u64AD\u653E\u7B2C\u4E00\u9996");
  form.button(PAUSE_RESUME_IMPLEMENTED ? "\u6682\u505C" : STOP_ONLY_LABEL);
  form.button("\u4E0B\u4E00\u9996");
  form.button("\u4E0A\u4E00\u9996");
  form.button("\u66F2\u5E93\uFF08\u9009\u62E9\u6B4C\u66F2\uFF09");
  form.button(`\u64AD\u653E\u6A21\u5F0F\uFF1A${session.settings.mode === "shuffle" ? "\u968F\u673A" : "\u987A\u5E8F"}`);
  form.button(`\u5FAA\u73AF\uFF1A${repeatLabel(session.settings.repeat)}`);
  form.button(`\u97F3\u91CF\uFF1A${Math.round(session.settings.volume * 100)}%`);
  form.button("\u5173\u4E8E / \u80FD\u529B\u8BF4\u660E");
  form.show(player).then((response) => {
    if (response.canceled) return;
    switch (response.selection) {
      case 0 /* PLAY_FIRST */: {
        const first = ctx2.library.tracks[0];
        if (first === void 0) report(player, "\xA7c\u66F2\u5E93\u4E3A\u7A7A");
        else doPlay(player, ctx2, first);
        break;
      }
      case 1 /* STOP */:
        doStop(player, ctx2);
        break;
      case 2 /* NEXT */:
        doNext(player, ctx2);
        break;
      case 3 /* PREVIOUS */:
        doPrevious(player, ctx2);
        break;
      case 4 /* LIBRARY */:
        showLibraryForm(player, ctx2);
        break;
      case 5 /* MODE */: {
        const next = session.settings.mode === "shuffle" ? "sequence" : "shuffle";
        session.settings = { ...session.settings, mode: next };
        session.queue.setMode(next);
        pushSettings(session);
        report(player, `\xA7a\u64AD\u653E\u6A21\u5F0F\uFF1A${next === "shuffle" ? "\u968F\u673A" : "\u987A\u5E8F"}`);
        break;
      }
      case 6 /* REPEAT */:
        showRepeatForm(player, ctx2);
        break;
      case 7 /* VOLUME */:
        showVolumeForm(player, ctx2);
        break;
      case 8 /* INFO */:
        showInfoForm(player);
        break;
      default:
        break;
    }
  }).catch((e) => {
    console.warn(`[muonium] \u8868\u5355\u5F02\u5E38: ${String(e)}`);
  });
}
function repeatLabel(r) {
  return r === "one" ? "\u5355\u66F2" : r === "list" ? "\u5217\u8868" : "\u4E0D\u5FAA\u73AF";
}
function showLibraryForm(player, ctx2) {
  const form = new ActionFormData();
  form.title("\u66F2\u5E93");
  form.body(`\u5171 ${ctx2.library.tracks.length} \u9996\u3002\u70B9\u51FB\u64AD\u653E\uFF1B\u7F3A\u5C11\u8D44\u6E90\u65F6\u4E0D\u4F1A\u5D29\u6E83\u3002`);
  for (const track of ctx2.library.tracks) {
    const view = progressView(track);
    form.button(`${track.title}
${view.label}`);
  }
  form.show(player).then((response) => {
    if (response.canceled) return;
    const idx = response.selection;
    if (idx === void 0) return;
    const track = ctx2.library.tracks[idx];
    if (track === void 0) {
      report(player, "\xA7c\u66F2\u5E93\u91CC\u627E\u4E0D\u5230\u8FD9\u9996\u6B4C");
      return;
    }
    stopTrack(player, ctx2.getSession(player).current?.audioEvent ?? null);
    doPlay(player, ctx2, track);
  }).catch((e) => console.warn(`[muonium] \u66F2\u5E93\u8868\u5355\u5F02\u5E38: ${String(e)}`));
}
function showRepeatForm(player, ctx2) {
  const session = ctx2.getSession(player);
  const form = new ActionFormData();
  form.title("\u5FAA\u73AF\u6A21\u5F0F");
  form.body("\u5355\u66F2\u5FAA\u73AF\u4F18\u5148\u7EA7\u9AD8\u4E8E\u5217\u8868\u5FAA\u73AF\u3002");
  form.button("\u4E0D\u5FAA\u73AF");
  form.button("\u5355\u66F2\u5FAA\u73AF");
  form.button("\u5217\u8868\u5FAA\u73AF");
  form.show(player).then((response) => {
    if (response.canceled || response.selection === void 0) return;
    const options = ["none", "one", "list"];
    const chosen = options[response.selection];
    if (chosen === void 0) return;
    session.settings = { ...session.settings, repeat: chosen };
    session.queue.setRepeat(chosen);
    pushSettings(session);
    report(player, `\xA7a\u5FAA\u73AF\uFF1A${repeatLabel(chosen)}`);
  }).catch((e) => console.warn(`[muonium] \u5FAA\u73AF\u8868\u5355\u5F02\u5E38: ${String(e)}`));
}
function showVolumeForm(player, ctx2) {
  const session = ctx2.getSession(player);
  const form = new ActionFormData();
  form.title("\u97F3\u91CF");
  form.body(`\u5F53\u524D ${Math.round(session.settings.volume * 100)}%\u3002\u97F3\u91CF\u53EA\u4F5C\u7528\u4E8E\u672C\u73A9\u5BB6\u3002`);
  for (const v of [0, 25, 50, 75, 100]) form.button(`${v}%`);
  form.show(player).then((response) => {
    if (response.canceled || response.selection === void 0) return;
    const levels = [0, 0.25, 0.5, 0.75, 1];
    const chosen = levels[response.selection];
    if (chosen === void 0) return;
    session.settings = { ...session.settings, volume: chosen };
    pushSettings(session);
    report(player, `\xA7a\u97F3\u91CF\uFF1A${Math.round(chosen * 100)}%`);
  }).catch((e) => console.warn(`[muonium] \u97F3\u91CF\u8868\u5355\u5F02\u5E38: ${String(e)}`));
}
function showInfoForm(player) {
  const form = new ActionFormData();
  form.title("\u5173\u4E8E");
  form.body(
    "MuoniumPlayer \u57FA\u5CA9\u7248\uFF08\u7EAF\u5BA2\u6237\u7AEF\uFF09\n\xB7 \u66F2\u5E93\u968F\u5305\u9884\u5BFC\u5165\uFF0C\u65AD\u7F51\u53EF\u64AD\n\xB7 \u4E0D\u5199\u670D\u52A1\u7AEF\u4EE3\u7801\u3001\u4E0D\u505A\u8DE8\u73A9\u5BB6\u540C\u6B65\u3001\u4E0D\u8BFB\u53D6 Floodgate/Geyser\n\xB7 \u6682\u505C / \u8FDB\u5EA6 / seek / \u5B8C\u6210\u4E8B\u4EF6\u5747\u4E3A UNKNOWN\uFF0C\u6682\u4E0D\u5B9E\u73B0\u5047\u529F\u80FD\n\xB7 \u8FDC\u7A0B\u4EFB\u610F\u97F3\u9891\u65E0\u6CD5\u76F4\u63A5\u5582\u7ED9\u57FA\u5CA9\u58F0\u97F3\u7CFB\u7EDF\n\xB7 \u5728\u804A\u5929\u8F93\u5165 !muonium \u6216 !music \u6253\u5F00\u672C\u83DC\u5355"
  );
  form.button("\u5173\u95ED");
  form.show(player).catch((e) => console.warn(`[muonium] \u5173\u4E8E\u8868\u5355\u5F02\u5E38: ${String(e)}`));
}

// src/main.ts
var library = loadLibrary();
var settings = loadSettings();
var sessions = /* @__PURE__ */ new Map();
function getSession(player) {
  let s = sessions.get(player.id);
  if (s === void 0) {
    s = {
      queue: new PlayQueue(library.tracks.map((t) => t.id), settings.mode, settings.repeat, null),
      current: null,
      settings
    };
    sessions.set(player.id, s);
  }
  return s;
}
var ctx = { library, getSession };
var OPEN_EVENT_ID = "muonium:open";
system.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id !== OPEN_EVENT_ID) return;
  const entity = event.sourceEntity ?? event.initiator;
  if (entity instanceof Player) {
    showMainForm(entity, ctx);
  } else {
    console.warn("[muonium] \u8BE5\u811A\u672C\u4E8B\u4EF6\u6CA1\u6709\u53EF\u5173\u8054\u7684\u73A9\u5BB6\uFF0C\u8868\u5355\u672A\u6253\u5F00");
  }
});
world2.afterEvents.playerJoin.subscribe((event) => {
  const player = world2.getPlayers().find((p) => p.id === event.playerId);
  if (player !== void 0) {
    player.sendMessage("\xA7a[MuoniumPlayer] \u8F93\u5165 /scriptevent muonium:open \u6253\u5F00\u97F3\u4E50\u64AD\u653E\u5668\uFF08\u7EAF\u5BA2\u6237\u7AEF\uFF0C\u65AD\u7F51\u53EF\u64AD\uFF09");
  }
});
world2.afterEvents.playerLeave.subscribe((event) => {
  sessions.delete(event.playerId);
});
world2.afterEvents.worldLoad.subscribe(() => {
  console.warn(
    `[muonium] \u66F2\u5E93 ${library.tracks.length} \u9996\u5DF2\u52A0\u8F7D\uFF1B\u7EAF\u5BA2\u6237\u7AEF\uFF0C\u670D\u52A1\u7AEF\u65E0\u9700\u4EFB\u4F55\u914D\u5957\u4EF6\u3002\u534F\u8BAE\u7248\u672C ${library.protocolVersion}\uFF1B\u76EE\u6807\u6E38\u620F ${library.gameVersion}`
  );
});
