import { world, system, Player } from "@minecraft/server";
import { loadLibrary } from "./library";
import { PlayQueue } from "./queue";
import { loadSettings } from "./state";
import { showMainForm, type UiContext, type SessionState } from "./ui";

/**
 * 基岩行为包入口（@minecraft/server 2.4.0 / @minecraft/server-ui 2.0.0）。
 *
 * <p>触发方式：在聊天执行 `/scriptevent muonium:open` 打开官方表单 UI。
 * 经核实，@minecraft/server 2.4.0 的 WorldBeforeEvents / WorldAfterEvents
 * **没有 chatSend**（官方文档注释里有示例但类型定义中不存在），
 * 因此不凭记忆写聊天事件，改用官方稳定的 scriptEventReceive。
 *
 * <p>不注册自定义命令、不读取服务端状态、不做跨玩家同步。
 * 只向触发事件的玩家显示表单并播放声音。
 */
const library = loadLibrary();
const settings = loadSettings();
const sessions = new Map<string, SessionState>();

function getSession(player: { readonly id: string }): SessionState {
    let s = sessions.get(player.id);
    if (s === undefined) {
        s = {
            queue: new PlayQueue(library.tracks.map((t) => t.id), settings.mode, settings.repeat, null),
            current: null,
            settings,
        };
        sessions.set(player.id, s);
    }
    return s;
}

const ctx: UiContext = { library, getSession };

const OPEN_EVENT_ID = "muonium:open";

system.afterEvents.scriptEventReceive.subscribe((event) => {
    if (event.id !== OPEN_EVENT_ID) return;
    const entity = event.sourceEntity ?? event.initiator;
    if (entity instanceof Player) {
        showMainForm(entity, ctx);
    } else {
        console.warn("[muonium] 该脚本事件没有可关联的玩家，表单未打开");
    }
});

world.afterEvents.playerJoin.subscribe((event) => {
    const player = world.getPlayers().find((p) => p.id === event.playerId);
    if (player !== undefined) {
        player.sendMessage("§a[MuoniumPlayer] 输入 /scriptevent muonium:open 打开音乐播放器（纯客户端，断网可播）");
    }
});

/** 重进世界时清理会话，避免持有失效的 Player 引用。 */
world.afterEvents.playerLeave.subscribe((event) => {
    sessions.delete(event.playerId);
});

world.afterEvents.worldLoad.subscribe(() => {
    console.warn(
        `[muonium] 曲库 ${library.tracks.length} 首已加载；纯客户端，服务端无需任何配套件。` +
        `协议版本 ${library.protocolVersion}；目标游戏 ${library.gameVersion}`
    );
});
