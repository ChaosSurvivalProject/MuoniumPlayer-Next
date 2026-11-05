/** 队列：与 Java common-core 共享数据格式与行为契约，但为独立实现（非移植代码）。 */
export type PlayMode = "sequence" | "shuffle";
export type RepeatMode = "none" | "one" | "list";

export interface QueueSnapshot {
  readonly playingId: string | null;
  readonly upcoming: readonly string[];
  readonly played: readonly string[];
  readonly mode: PlayMode;
  readonly repeat: RepeatMode;
  readonly shuffleSeed: number | null;
}

/** xorshift32：确定性、可恢复，不依赖 Math.random。 */
function shuffle(ids: readonly string[], seed: number): string[] {
  const out = [...ids];
  let s = seed | 0 || 1;
  const next = (): number => {
    s ^= s << 13; s ^= s >>> 17; s ^= s << 5;
    return s >>> 0;
  };
  for (let i = out.length - 1; i > 0; i--) {
    const j = next() % (i + 1);
    const tmp = out[i] as string;
    out[i] = out[j] as string;
    out[j] = tmp;
  }
  return out;
}

export class PlayQueue {
  private readonly base: readonly string[];
  private upcoming: string[];
  private played: string[] = [];
  private currentId: string | null = null;
  private mode: PlayMode;
  private repeat: RepeatMode;
  private shuffleSeed: number | null;

  constructor(songIds: readonly string[], mode: PlayMode, repeat: RepeatMode, seed: number | null) {
    this.base = [...new Set(songIds)];
    this.mode = mode;
    this.repeat = repeat;
    this.shuffleSeed = seed;
    this.upcoming = mode === "shuffle" ? shuffle(this.base, seed ?? 1) : [...this.base];
  }

  static restore(s: QueueSnapshot): PlayQueue {
    const q = new PlayQueue([], s.mode, s.repeat, s.shuffleSeed);
    q.upcoming = [...s.upcoming];
    q.played = [...s.played];
    q.currentId = s.playingId;
    return q;
  }

  current(): string | null { return this.currentId; }

  /** 队列耗尽且非列表循环时返回 null（停住），不擅自回到源歌单。 */
  next(): string | null {
    if (this.currentId !== null && this.repeat === "one") return this.currentId;
    if (this.currentId !== null) this.played.push(this.currentId);
    if (this.upcoming.length === 0) {
      if (this.repeat === "list" && this.base.length > 0) {
        this.upcoming = this.mode === "shuffle"
          ? shuffle(this.base, this.shuffleSeed ?? 1)
          : [...this.base];
      } else {
        this.currentId = null;
        return null;
      }
    }
    const id = this.upcoming.shift();
    this.currentId = id ?? null;
    return this.currentId;
  }

  previous(): string | null {
    const prev = this.played.pop();
    if (prev === undefined) return this.currentId;
    if (this.currentId !== null) this.upcoming.unshift(this.currentId);
    this.currentId = prev;
    return this.currentId;
  }

  setMode(mode: PlayMode): void {
    if (mode === this.mode) return;
    this.mode = mode;
    if (mode === "shuffle") this.upcoming = shuffle(this.upcoming, (this.shuffleSeed ?? 1) ^ 0x5deece66);
    else {
      const ordered = this.base.filter((id) => this.upcoming.includes(id));
      for (const id of this.upcoming) if (!ordered.includes(id)) ordered.push(id);
      this.upcoming = ordered;
    }
  }

  setRepeat(repeat: RepeatMode): void { this.repeat = repeat; }
  enqueue(id: string): void { if (id.length > 0) this.upcoming.push(id); }
  remove(id: string): boolean {
    const i = this.upcoming.indexOf(id);
    if (i < 0) return false;
    this.upcoming.splice(i, 1);
    return true;
  }
  upcomingView(): readonly string[] { return [...this.upcoming]; }
  playedView(): readonly string[] { return [...this.played]; }

  snapshot(): QueueSnapshot {
    return {
      playingId: this.currentId,
      upcoming: [...this.upcoming],
      played: [...this.played],
      mode: this.mode,
      repeat: this.repeat,
      shuffleSeed: this.shuffleSeed,
    };
  }
}
