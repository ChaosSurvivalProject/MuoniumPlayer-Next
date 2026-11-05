import { ActionFormData, type ActionFormResponse } from "@minecraft/server-ui";
import type { Player } from "@minecraft/server";
import type { Library, Track } from "./library";
import { findTrack } from "./library";
import type { PlayQueue } from "./queue";
import type { RepeatMode, PlayMode } from "./queue";
import type { Settings } from "./state";
import { saveSettings } from "./state";
import { playTrack, stopTrack, progressView } from "./player";
import { PAUSE_RESUME_IMPLEMENTED, STOP_ONLY_LABEL } from "./capabilities";
import { formatForLog } from "./errors";

export interface SessionState {
  queue: PlayQueue;
  current: Track | null;
  settings: Settings;
}

export interface UiContext {
  readonly library: Library;
  readonly getSession: (player: Player) => SessionState;
}

function report(player: Player, message: string): void {
  // 只发消息，不逐帧输出位置，不写内容日志刷屏
  player.sendMessage(message);
}

function reportError(player: Player, err: { userMessage: string } | undefined): void {
  report(player, err ? `§c${err.userMessage}` : "§c操作失败");
}

function pushSettings(session: SessionState): void {
  saveSettings(session.settings);
}

function doPlay(player: Player, ctx: UiContext, track: Track): void {
  const session = ctx.getSession(player);
  const res = playTrack(player, track, session.settings.volume);
  if (!res.ok) {
    if (res.error) console.warn(formatForLog(res.error));
    reportError(player, res.error);
    return;
  }
  const previous = session.current;
  session.current = track;
  const view = progressView(track);
  report(player, `§a正在播放：${track.title}（${view.label}）`);
  if (previous !== null && previous.audioEvent !== track.audioEvent) {
    // 切歌前尝试中断上一音轨；失败时明确提示，不假装无缝
    const stopped = stopTrack(player, previous.audioEvent);
    if (stopped.limitation && stopped.error) console.warn(formatForLog(stopped.error));
  }
}

function doStop(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const res = stopTrack(player, session.current?.audioEvent ?? null);
  session.current = null;
  if (res.limitation) {
    report(player, "§e已标记为停止；当前版本无法中断已发出的声音，请等待音轨结束或重进世界");
  } else {
    report(player, "§a已停止播放");
  }
}

function doNext(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const id = session.queue.next();
  if (id === null) {
    doStop(player, ctx);
    report(player, "§e队列已播放完，已停住");
    return;
  }
  const track = findTrack(ctx.library, id);
  if (track === undefined) {
    report(player, "§c曲库里找不到这首歌，已跳过");
    return;
  }
  stopTrack(player, session.current?.audioEvent ?? null);
  doPlay(player, ctx, track);
}

function doPrevious(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const id = session.queue.previous();
  if (id === null) return;
  const track = findTrack(ctx.library, id);
  if (track === undefined) return;
  stopTrack(player, session.current?.audioEvent ?? null);
  doPlay(player, ctx, track);
}

enum MainAction { PLAY_FIRST = 0, STOP = 1, NEXT = 2, PREVIOUS = 3, LIBRARY = 4, MODE = 5, REPEAT = 6, VOLUME = 7, INFO = 8 }

export function showMainForm(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const current = session.current;
  const form = new ActionFormData();
  form.title("音乐播放器");
  form.body(
    current === null
      ? "尚未播放。曲库已预导入，断网也能播放。\n本播放器只在你本机出声，服务端不需要任何配套件。"
      : `正在播放：${current.title}\n${progressView(current).label}\n暂停/进度/跳转在当前版本未验证，仅提供「${STOP_ONLY_LABEL}」。`
  );
  form.button("播放第一首");
  form.button(PAUSE_RESUME_IMPLEMENTED ? "暂停" : STOP_ONLY_LABEL);
  form.button("下一首");
  form.button("上一首");
  form.button("曲库（选择歌曲）");
  form.button(`播放模式：${session.settings.mode === "shuffle" ? "随机" : "顺序"}`);
  form.button(`循环：${repeatLabel(session.settings.repeat)}`);
  form.button(`音量：${Math.round(session.settings.volume * 100)}%`);
  form.button("关于 / 能力说明");

  form.show(player).then((response: ActionFormResponse) => {
    if (response.canceled) return;
    switch (response.selection) {
      case MainAction.PLAY_FIRST: {
        const first = ctx.library.tracks[0];
        if (first === undefined) report(player, "§c曲库为空");
        else doPlay(player, ctx, first);
        break;
      }
      case MainAction.STOP: doStop(player, ctx); break;
      case MainAction.NEXT: doNext(player, ctx); break;
      case MainAction.PREVIOUS: doPrevious(player, ctx); break;
      case MainAction.LIBRARY: showLibraryForm(player, ctx); break;
      case MainAction.MODE: {
        const next: PlayMode = session.settings.mode === "shuffle" ? "sequence" : "shuffle";
        session.settings = { ...session.settings, mode: next };
        session.queue.setMode(next);
        pushSettings(session);
        report(player, `§a播放模式：${next === "shuffle" ? "随机" : "顺序"}`);
        break;
      }
      case MainAction.REPEAT: showRepeatForm(player, ctx); break;
      case MainAction.VOLUME: showVolumeForm(player, ctx); break;
      case MainAction.INFO: showInfoForm(player); break;
      default: break;
    }
  }).catch((e: unknown) => {
    console.warn(`[muonium] 表单异常: ${String(e)}`);
  });
}

function repeatLabel(r: RepeatMode): string {
  return r === "one" ? "单曲" : r === "list" ? "列表" : "不循环";
}

function showLibraryForm(player: Player, ctx: UiContext): void {
  const form = new ActionFormData();
  form.title("曲库");
  form.body(`共 ${ctx.library.tracks.length} 首。点击播放；缺少资源时不会崩溃。`);
  for (const track of ctx.library.tracks) {
    const view = progressView(track);
    form.button(`${track.title}\n${view.label}`);
  }
  form.show(player).then((response: ActionFormResponse) => {
    if (response.canceled) return;
    const idx = response.selection;
    if (idx === undefined) return;
    const track = ctx.library.tracks[idx];
    if (track === undefined) {
      report(player, "§c曲库里找不到这首歌");
      return;
    }
    stopTrack(player, ctx.getSession(player).current?.audioEvent ?? null);
    doPlay(player, ctx, track);
  }).catch((e: unknown) => console.warn(`[muonium] 曲库表单异常: ${String(e)}`));
}

function showRepeatForm(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const form = new ActionFormData();
  form.title("循环模式");
  form.body("单曲循环优先级高于列表循环。");
  form.button("不循环");
  form.button("单曲循环");
  form.button("列表循环");
  form.show(player).then((response: ActionFormResponse) => {
    if (response.canceled || response.selection === undefined) return;
    const options: readonly RepeatMode[] = ["none", "one", "list"];
    const chosen = options[response.selection];
    if (chosen === undefined) return;
    session.settings = { ...session.settings, repeat: chosen };
    session.queue.setRepeat(chosen);
    pushSettings(session);
    report(player, `§a循环：${repeatLabel(chosen)}`);
  }).catch((e: unknown) => console.warn(`[muonium] 循环表单异常: ${String(e)}`));
}

function showVolumeForm(player: Player, ctx: UiContext): void {
  const session = ctx.getSession(player);
  const form = new ActionFormData();
  form.title("音量");
  form.body(`当前 ${Math.round(session.settings.volume * 100)}%。音量只作用于本玩家。`);
  for (const v of [0, 25, 50, 75, 100] as const) form.button(`${v}%`);
  form.show(player).then((response: ActionFormResponse) => {
    if (response.canceled || response.selection === undefined) return;
    const levels: readonly number[] = [0, 0.25, 0.5, 0.75, 1];
    const chosen = levels[response.selection];
    if (chosen === undefined) return;
    session.settings = { ...session.settings, volume: chosen };
    pushSettings(session);
    report(player, `§a音量：${Math.round(chosen * 100)}%`);
  }).catch((e: unknown) => console.warn(`[muonium] 音量表单异常: ${String(e)}`));
}

function showInfoForm(player: Player): void {
  const form = new ActionFormData();
  form.title("关于");
  form.body(
    "MuoniumPlayer 基岩版（纯客户端）\n" +
    "· 曲库随包预导入，断网可播\n" +
    "· 不写服务端代码、不做跨玩家同步、不读取 Floodgate/Geyser\n" +
    "· 暂停 / 进度 / seek / 完成事件均为 UNKNOWN，暂不实现假功能\n" +
    "· 远程任意音频无法直接喂给基岩声音系统\n" +
    "· 在聊天输入 !muonium 或 !music 打开本菜单"
  );
  form.button("关闭");
  form.show(player).catch((e: unknown) => console.warn(`[muonium] 关于表单异常: ${String(e)}`));
}
