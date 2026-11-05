# 统一歌词模型

Schema：[lyric.schema.json](../shared-schemas/schema/lyric.schema.json)
实现：Java `common-core/lyric/*`；示例：[shared-schemas/examples/lyric.json](../shared-schemas/examples/lyric.json)
测试向量：[shared-schemas/vectors/lyric-timeline.json](../shared-schemas/vectors/lyric-timeline.json)

## 模型

```json
{
  "schemaVersion": 1,
  "songId": "song_01JZ...",
  "durationMs": 214000,
  "format": "qrc",
  "unsupported": false,
  "lines": [
    {
      "index": 0,
      "startMs": 10000,
      "endMs": 13500,
      "speaker": "lead",
      "text": "第一句歌词",
      "translation": "First lyric line",
      "words": [
        { "text": "第一句", "startMs": 10000, "endMs": 12800 },
        { "text": "歌词", "startMs": 12800, "endMs": 13500 }
      ]
    }
  ]
}
```

`endMs: null` 表示**未知结束时间**，不得用 0 或猜测值填充。
支持空行、重复时间戳、对唱（`speaker`）、翻译、逐字。

## 导入映射

| 来源 | 映射 | 本项目 |
|---|---|---|
| LRC | 行级 | ✅ `LyricParsers.parseLrc`，支持多时间戳 `[00:10.00][00:20.00]`、毫秒/百分秒 |
| QQ QRC | 逐字 | ✅ `parseQrc`：`[start,dur]文本(start,dur)` |
| 网易 YRC | 逐字 | ✅ `parseYrc`：`(start,dur)文本` |
| 增强 LRC | 逐字 | ✅ `parseEnhancedLrc`：`<mm:ss.xx>文本` |
| AMLL TTML | 说话人 + 行/字时间 + 翻译 | ✅ `parseTtml`（`<p begin/end ttm:agent>` + `<span>`） |
| 酷狗 KRC | 逐字/音素按可获得信息 | ⛔ `unsupported`：加密私有格式，拒绝猜测 |
| 未知格式 | — | ⛔ `unsupported=true`、`lines=[]`，保留原始文件交调用方保管 |

**逐字歌词是一等公民**：只有真实存在逐字时间轴时才产生 `words`。
普通 LRC **绝不按字数平均切分**伪造字符时间轴
（依据上游 `LyricParser.java:121` 的明确注释与本项目行为规格 C7）。

## 规范化规则（`LyricNormalizer`）

| 情况 | 处理 |
|---|---|
| 毫秒 / 百分秒 / 1~3 位小数 | `mm:ss.x` → x 长度 1=×100、2=×10、3=×1（毫秒） |
| 时间倒退 | 钳制到上一行 `endMs` |
| 负数时间 | 归零 |
| 重复时间戳 | 保留多行但重排 `index` |
| 缺失结束时间 | 用下一行 `startMs`；最后一行退回到 `durationMs` |
| 全半角 / 空白 / 换行 | 全角空格与 NBSP → 普通空格，连续空白折叠，trim |
| HTML/XML 转义 | `&amp; &lt; &gt; &quot; &#39; &#xNN; &#NN;` 解码 |
| 翻译错位 | 按时间戳对齐，容差 **±80 ms**（参考上游 `LyricParser.java:178`） |
| 超长行 | 截断到 500 字符 |
| 恶意巨型文件 | 输入 > 1,000,000 字符直接拒绝并标 `unsupported` |
| 排序 | 按 `startMs` 升序 |

## 游标（性能）

`LyricCursor` 用**二分查找** `lineAt(positionMs)` 与 `wordIndexAt(line, positionMs)`，
每帧 O(log n)。测试 `lookupCostIsLogarithmicOnLargeDocument` 用 20,000 行验证。
禁止每帧线性扫描，禁止用 `Thread.sleep` 当歌词时钟（由注入的 `Ports.Clock` 驱动）。

## 进度显示约束

基岩端没有真实播放位置，因此只显示**导入时声明的估算时长**，
不提供可拖动进度条，也不逐帧输出位置日志。见
[bedrock-capability-matrix.md](bedrock-capability-matrix.md)。
