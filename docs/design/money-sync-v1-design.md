# Money 电脑端本地镜像与增量同步方案 — v1

状态：v1 两仓代码已实现，单测/契约测试全绿；模拟器端到端验证已通过（2026-09-01，见下）；待性能验收（阶段 4 真实大数据集）  
项目：Money Android + `money-client-skill` Python 桥接  
最后更新：2026-09-01

> 2026-09-01 端到端验证（Pixel_9a 模拟器，真实 App + 真实 Python CLI）：
> - 配对 → `sync.state`（5 个 v1 capabilities）→ 首次快照 1 页 100 行（127ms，约 5 个请求，≤10 预算内）。
> - 桥接单条写入 → Journal（single）→ 增量 pull（1 变更，32ms）；UI 手动记账 → 增量 pull 可见。
> - `notes preview/apply` 批量推送 → 批量 Journal（batch）→ 严格 LIFO 撤销 → 撤销结果再次增量到达（含建单撤销的 tombstone）。
> - 幂等重放实测：同一确定性 requestId 重发返回已存结果（applied/revision 不变）；换 requestId + 过期 expectedUpdatedAt 得到新鲜 CONFLICT（带服务端 payload）。
> - 停止服务后探测返回 unreachable，Token 随会话失效。
> - 另修复一处新契约测试缺陷（备份导入快照须带默认提醒配置行才能通过导入后复读校验），BackupDatasetSwitchContractTest 已在设备上通过。
> - E2E 暴露的后续修订：全冲突批次原会留一条零成功 Journal 记录、占用撤销栈位；已按“零成功批次不入 Journal”修改（4.6 第 9 条 / 5.5），重试改走稳定重分类。

> 2026-09-01 评审修订要点：
> - 错误码收敛到现有协议码表（删除 `AUTH_REQUIRED` / `SERVICE_EXPIRED`，复用 `UNAUTHORIZED` / `SESSION_EXPIRED`）；capabilities 明确挂载在未认证的 `server.info` 响应。
> - 协议数据格式统一为现有风格：时间戳 epoch millis（Long）、金额为最小货币单位字符串；新增镜像行 payload DTO 定义（4.3）。
> - change-log 覆盖补齐：Journal 撤销恢复写入、提醒自动记账、账户创建事务化；revision 基线与保留策略写明（5.2）。
> - `sync.push` 写入预算按请求计 1 次写动作（不按补丁条数），另设每分钟 ≤10 批的独立窗口，幂等重放免费；全量备注整理约 1 分钟内完成（4.6）。
> - 删除备注的 `|` 禁令；备注校验统一为 trim + 200 字上限（4.6、6.3）。
> - 明确镜像不提供运行余额视图；fixture 权威源与跨仓复制校验（第 7 节阶段 0）。

本文档是两个仓库的共同实施基线：

1. Android 仓库：`C:\Users\exqin\Desktop\file\myproject\money`
2. Python skill 仓库：`C:\Users\exqin\Desktop\file\myproject\money-client-skill`

本方案只新增电脑端的持久化镜像、增量同步和受控批量备注修改能力，不引入云端服务。手机仍然是账本的唯一权威来源，电脑端数据库是可重建的本地副本。

## 1. 背景与问题

当前 LAN 协议是“一次请求建立一次 TCP 连接”。`records.list` 返回摘要后，Python 客户端为了读取准确备注，会再对每条收支和转账调用一次 `records.get`。这形成 N+1 请求：账单数量增大时，连接建立、序列化、Android 并发槽位和 15 秒超时会叠加，最终表现为超时。

当前实现的关键约束如下：

- Android LAN 服务只在用户主动启动后存在，服务会过期，也可能因进程被系统回收而停止。
- 当前 Money Link Protocol 是 v1，服务端每个连接处理一个请求，最多同时处理 4 个连接，单帧上限为 256 KiB。
- 所有账本写入必须经过现有 Use Case，不能由 LAN 路由器直接访问 DAO。
- AI 写入已有请求幂等、更新版本校验和 Journal 撤销机制。
- 用户希望备注使用自然中文，不额外维护类别字段；类别由分析时根据备注和账单字段推断。

因此，解决方案不应继续扩大单次查询或并发请求，而应把“首次全量读取”和“后续变化同步”分开。

## 2. 核心决策

### 2.1 权威关系

```text
Android Room 账本
    │  唯一权威，可写
    ▼
Android 持久化变更日志（revision）
    │  pull / snapshot
    ▼
电脑端 SQLite 本地镜像
    │
    ├── 本地查询与分析
    ├── 待发送备注补丁 outbox
    └── 同步任务状态与冲突记录
```

- Android 数据库是真实账本。
- Python SQLite 只保存镜像、同步游标、待发送补丁和冲突信息。
- Python 不复制 Room 文件、不读 Android 备份文件、不直接改手机数据库。
- 手机端发生变化后，电脑端通过 `revision` 拉取变化。
- 电脑端修改备注后，先写入本地 outbox，再向手机提交带 `expectedUpdatedAt` 的补丁。
- 网络中断时任务可以暂停、重试和续传，不能依赖内存中的 asyncio 任务状态。

### 2.2 同步形态

v1 采用“异步任务 + 请求式拉取”，而不是让手机主动连接电脑：

- Python 的 `sync` 命令和 MCP 工具立即创建或执行一个持久化同步任务。
- 每次同步先调用 `sync.state`，再按需要执行 snapshot、pull、push。
- Android 服务停止后配对令牌立即失效（配对与会话状态全部在内存），恢复同步必须先在手机上重新启动服务、用新配对码重新配对。电脑端的镜像、游标、outbox 和任务状态全部持久化，重新配对后从中断处继续。不存在无需用户操作的静默恢复。
- v1 不要求手机反向连接电脑，也不要求常驻后台服务。
- 真正的长连接、多路复用和手机主动推送保留给 v2；v1 通过分页和批量已经可以把数百次请求降到个位数。

### 2.3 v1 的写入范围

电脑端 v1 只允许修改：

- `cash_flow` 的 `note`
- `transfer` 的 `note`

补丁不得修改金额、日期、账户、收支方向、转账两端或删除状态。账户、四类账本记录和软删除状态会同步到电脑端，但其他字段以只读镜像形式存在。

这样可以支持“把有记录以来的备注统一整理”，同时不会把备注清理误变成财务数据重写。

## 3. 目标与非目标

### 3.1 目标

1. 首次连接可以稳定导入全部历史账户和账单。
2. 首次导入后，只传输上次同步之后发生的变化。
3. 电脑端可以离线查询、筛选、生成备注修改预览。
4. 备注修改支持批量提交、断线重试、幂等和冲突报告。
5. 账单数量从几百增长到几万时，不再依赖逐条 `records.get`。
6. Android 和 Python 两个仓库都有可测试、可迁移、可恢复的状态。

### 3.2 非目标

- 不增加云端后端或账号系统。
- 不让电脑端成为第二个账本权威。
- 不在 v1 做手机到电脑的主动推送。
- 不新增账单类别字段或把 AI 推断类别写回账单。
- 不通过复制数据库文件实现同步。
- 镜像不提供 `balanceBefore` / `balanceAfter` 运行余额视图；需要余额或汇总的分析以手机端 `records.list` / `ledger.summary` 为准。
- 不绕过 Android 现有写权限开关、版本冲突校验、Journal 和软删除规则。
- 不承诺 Android 服务停止期间仍能实时获取手机的新数据。

## 4. 协议设计

### 4.1 兼容策略

继续使用当前 Money Link Protocol v1 的长度前缀 JSON 帧，不在 v1 引入新的传输层。现有协议没有握手协商环节，未认证的 `server.info` 是事实上的 hello；新增能力通过在该响应中增加 `capabilities` 字段宣告：

```json
{
  "protocolVersion": 1,
  "app": "Money",
  "pairingAllowed": true,
  "allowWrite": true,
  "expiresAt": 1788278400000,
  "capabilities": [
    "sync.state.v1",
    "sync.snapshot.v1",
    "sync.pull.v1",
    "sync.push.note.v1",
    "records.list.detailed.v1"
  ]
}
```

兼容规则：

- 新 Python 客户端连接旧 Android 时，`server.info` 没有 `capabilities` 字段即视为不支持同步能力，应明确提示升级，不自动执行无界限的 N+1 读取。
- 新 Android 仍保留已有 `records.list`、`records.get` 和单条写入接口，保证旧客户端继续可用。
- 新 Python 客户端可以在小数据量场景使用旧接口作为有限回退，但必须设置记录数上限和总超时。
- 新 Android 不改变现有 protocol version；若将来改变帧格式、连接模型或认证方式，再升到 Protocol v2。

### 4.2 数据集与 revision

Android 维护一个持久化 `datasetId` 和单调递增的全局 `revision`：

- `datasetId` 在普通重启、重新配对和端口变化后保持不变。
- 替换备份或重置账本时生成新的 `datasetId`，避免两个不同账本被错误合并。
- 每一个会影响镜像的账本变化至少产生一个 change-log 项。
- revision 只由 Android 分配，Python 不自行递增服务器游标。
- change-log 保留策略到期后，Android 返回 `minAvailableRevision`。
- Python 请求的游标早于可用范围时，收到 `RESYNC_REQUIRED`，必须重新做 snapshot。

`sync.state` 响应（capabilities 已在 `server.info` 宣告，此处不重复）：

```json
{
  "datasetId": "uuid",
  "revision": 1842,
  "minAvailableRevision": 1,
  "serverTime": 1788264000000
}
```

协议数据格式与现有 LAN DTO 完全一致，新接口不得引入第二种风格：

- 时间戳一律为 epoch millis（Long 数字）。`expectedUpdatedAt` 等版本字段必须按精确相等比较——记录级 `updatedAt` 是毫秒精度且同毫秒冲突时单调 +1，任何秒级或字符串截断都会产生假冲突或假匹配。
- 金额一律为最小货币单位的十进制字符串。
- 枚举值为现有 LAN DTO 使用的小写蛇形字符串（如 `cash_flow`、`transfer`）。

### 4.3 镜像行 payload DTO

snapshot 页面、pull 的 change 项和 push 冲突响应中的 `serverPayload` 使用同一套镜像行 DTO。字段名与格式对齐现有 `records.get` 的 `StoredRecordResult` 和 `accounts.list` 响应，精确字段集由协议 fixture 钉死（见第 7 节阶段 0）。

每行以信封形式出现：`{ "entityKind": "...", "sourceRevision": 1830, "payload": { ... } }`。

- `account`：`accountId`、`name`、`initialBalance`、`kind`、`isHidden`、`closedAt`、`displayOrder`、`colorName`、`iconName`、`createdAt`、`lastUsedAt`、`lastBalanceUpdateAt`。账户没有 `updatedAt` 版本令牌，也不存在删除/墓碑（账户生命周期 = `isHidden` + `closedAt`）。
- `cash_flow`：`recordId`、`accountId`、`direction`、`amount`、`note`、`occurredAt`、`createdAt`、`updatedAt`、`deletedAt`、`operationId`。
- `transfer`：`recordId`、`fromAccountId`、`toAccountId`、`amount`、`note`、`occurredAt`、`createdAt`、`updatedAt`、`deletedAt`、`operationId`。
- `balance_update`：`recordId`、`accountId`、`actualBalance`、`systemBalanceBeforeUpdate`、`delta`、`occurredAt`、`createdAt`、`updatedAt`、`deletedAt`、`operationId`。
- `balance_adjustment`：`recordId`、`accountId`、`delta`、`occurredAt`、`createdAt`、`updatedAt`、`deletedAt`、`operationId`。
- 删除 tombstone：`entityKind`、`recordId`、`deletedAt`、`updatedAt`，不带完整 payload（snapshot 页面与 pull change 中都可能出现）。

注意：镜像不包含 `balanceBefore` / `balanceAfter` 运行余额（它们只存在于 `records.list` 的投影中，且会随历史编辑失效）。镜像只是权威记录的副本，不提供余额视图。

### 4.4 `sync.snapshot`

snapshot 用于首次同步或 `RESYNC_REQUIRED` 后重建镜像。

第一次请求确定 `snapshotRevision`，之后按稳定的 `entityKind + recordId` 游标分页读取当前投影。每一页返回账户、记录或软删除 tombstone（信封 + payload，见 4.3）。

```json
{
  "datasetId": "uuid",
  "snapshotRevision": 1842,
  "rows": [
    { "entityKind": "account", "sourceRevision": 12, "payload": { } },
    { "entityKind": "cash_flow", "sourceRevision": 1830, "payload": { } }
  ],
  "nextCursor": "opaque-cursor",
  "done": false
}
```

实现要求：

- 游标必须是不透明值，客户端不能自行拼接；语义上是一个 keyset 位置（`snapshotRevision` + `entityKind` + 最后一条 `recordId`），服务端无状态地从游标恢复分页。
- 游标可以跨连接、跨服务重启继续使用：只要携带相同的 `snapshotRevision`，断点续传就是安全的；正确性由完成后的 catch-up pull 兜底。若续传时 change-log 保留窗口已越过 `snapshotRevision`，服务端返回 `RESYNC_REQUIRED`，客户端放弃本次快照并重新定基。
- 服务端按字节大小限制响应，目标不超过 128 KiB，绝不能超过当前 256 KiB 帧上限。
- 服务端可以把一页缩小到单条记录，以容纳很长备注。
- 快照不需要在 Android 内存中保存完整副本。
- 快照期间发生的新增、更新和删除，通过完成 snapshot 后从 `snapshotRevision` 继续 `sync.pull` 补齐。
- Python 只有在 snapshot 页面和 catch-up pull 都完成后，才把数据集标记为 `ready`。
- `datasetId` 变化时不得静默合并到旧镜像。

### 4.5 `sync.pull`

请求使用“从某 revision 之后开始”的语义：

```json
{
  "datasetId": "uuid",
  "afterRevision": 1842,
  "limit": 100
}
```

响应中的 change 按 revision 升序排列：

```json
{
  "datasetId": "uuid",
  "fromRevision": 1842,
  "toRevision": 1850,
  "changes": [
    {
      "revision": 1843,
      "entityKind": "cash_flow",
      "recordId": 27,
      "operation": "upsert",
      "updatedAt": 1788263880000,
      "payload": {}
    }
  ],
  "hasMore": false
}
```

删除变化使用 `operation: "delete"`，携带 `recordId`、`deletedAt` 和必要的版本信息。Python 应用 change 时遵守 `sourceRevision` 最大值，重复收到旧 change 不得覆盖新数据。

### 4.6 `sync.push`

`sync.push` 是批量传输接口。单批最多 50 条，并同时受帧大小约束。

```json
{
  "datasetId": "uuid",
  "requestId": "stable-request-id",
  "patches": [
    {
      "patchId": "local-patch-id",
      "entityKind": "cash_flow",
      "recordId": 27,
      "expectedUpdatedAt": 1786747800000,
      "changes": {
        "note": "聚餐 翅客 王慨然和他小弟"
      }
    }
  ]
}
```

服务端必须：

1. 检查当前会话仍然允许写入。
2. 检查 `datasetId` 一致。
3. 检查记录类型、记录存在性和软删除状态。
4. 校验备注：`changes` 只允许 `note` 一个字段；新备注 trim 后不得超过 200 字（与 UI 的 `normalizeLedgerNote` 策略一致，本次将该限制下沉到同步写入路径；v1 不改变既有单条 LAN 写入的行为）；允许空备注。不满足的补丁返回 `INVALID_PATCH`，不影响同批其他补丁。
5. 比较 `expectedUpdatedAt`；不一致时返回冲突，不覆盖手机上较新的修改。
6. 通过批量 Journal Use Case 和现有更新 Use Case 完成写入。
7. 为成功的变化追加 change-log revision。
8. 对相同 `requestId` 重试返回原结果，不重复修改。
9. **零成功批次不入 Journal**：只有至少成功应用一条补丁的批次才写入批量 Journal。全冲突/全无效批次没有产生任何修改，入栈只会让“撤销最近一步”先弹出一个无操作条目。此类批次的重试按新请求重新分类——冲突与无效判定是稳定的（`updatedAt` 严格单调、永旧不复新；无效校验无状态），重新分类必然得到相同结果，因此不存结果也能保证重试无副作用。

写入预算计费：现有 60 次/分钟的全局限流在路由前按动作扣费，管的是单条写动作，继续有效。`sync.push` 按**请求**计 1 次写动作，而不是按补丁条数——批次本身已有 ≤50 条的硬上限和 Journal 整批撤销兜底，按条计费会把 348 条的全量备注整理拖到 7–8 分钟，交互上不可接受。同时给 `sync.push` 设独立节奏窗口：每分钟 ≤10 批（即 ≤500 条/分钟），防止失控客户端用批量接口绕开单条预算，超出返回 `RATE_LIMITED`。同一 `requestId` 的幂等重放只返回已存结果、不计费（限已入栈批次；零成功批次未入 Journal，重试重新分类、正常计费）。

代价如实记录：sync 路径的写入速率上限从 60 条/分钟放宽到 500 条/分钟，安全兜底依赖批次上限、单会话单客户端、写开关和 Journal 撤销，而不是速率。

性能预期：服务端处理一批约 1–2 秒（每补丁一次读、校验、CAS 更新、Journal item 与 change-log 写入）。348 条 ≈ 7 批，全量备注整理 1 分钟以内完成；典型的几十条近期备注整理为 1 批、数秒内返回。

响应按补丁返回结果：

```json
{
  "requestId": "stable-request-id",
  "results": [
    {
      "patchId": "local-patch-id",
      "status": "applied",
      "revision": 1843
    },
    {
      "patchId": "another-patch-id",
      "status": "conflict",
      "serverUpdatedAt": 1786615200000,
      "serverPayload": {}
    }
  ]
}
```

成功后 Python 仍应从自己的 `appliedRevision` 继续 pull，以统一处理服务器真实状态和其他同时发生的变化；不能只依赖 push 响应做乐观覆盖。

### 4.7 错误和重试

新接口收敛到现有协议的错误码表（现有实现中错误码是散落的字符串字面量，本次在代码中集中定义为稳定注册表），只新增同步语义必需的码：

- `UNAUTHORIZED`：未配对或会话令牌失效（现有码）。
- `SESSION_EXPIRED`：4 小时会话到期（现有码）。注意：手机服务停止后没有任何进程能返回错误帧，"服务不可达"是客户端的 TCP 连接失败，不是协议错误码。
- `WRITE_DISABLED`：手机当前未打开允许修改（现有码）。
- `UNSUPPORTED_CAPABILITY`：旧 Android 不支持同步能力（新码；客户端通常应在 `server.info` 阶段就发现不支持，此为兜底）。
- `DATASET_MISMATCH`：本地镜像和手机不是同一个数据集（新码）。
- `RESYNC_REQUIRED`：服务器已清理请求游标之前的 change-log（新码）。
- `CONFLICT`：记录的 `updatedAt` 已变化（现有码）。
- `RATE_LIMITED`：达到请求或写入预算（现有码）。
- `FRAME_TOO_LARGE` / `RESPONSE_TOO_LARGE`：请求或响应超过帧限制（现有码）。
- `INVALID_PATCH`：补丁字段、记录类型或备注不合法（新码）。
- 其余现有码（`PAIRING_*`、`NOT_FOUND`、`VALIDATION_FAILED`、`INTERNAL_ERROR` 等）语义不变。

重试策略：

- 查询和 pull 超时可以有限次数重试。
- push 在“写入结果未知”的情况下必须使用完全相同的 `requestId` 重试，不能生成新 requestId。
- 连续失败后任务进入 `paused`，保留错误和游标，等待下次显式或计划同步。
- 不记录配对码、session token、完整账单 payload 或备注到普通日志。

## 5. Android 仓库修改计划

仓库：`C:\Users\exqin\Desktop\file\myproject\money`

### 5.1 Domain 层

新增同步领域模型和接口，建议目录：

- `app/src/main/java/com/shihuaidexianyu/money/domain/model/sync/SyncModels.kt`
- `app/src/main/java/com/shihuaidexianyu/money/domain/repository/SyncRepository.kt`
- `app/src/main/java/com/shihuaidexianyu/money/domain/usecase/sync/GetSyncStateUseCase.kt`
- `app/src/main/java/com/shihuaidexianyu/money/domain/usecase/sync/ExportSyncSnapshotPageUseCase.kt`
- `app/src/main/java/com/shihuaidexianyu/money/domain/usecase/sync/PullSyncChangesUseCase.kt`
- `app/src/main/java/com/shihuaidexianyu/money/domain/usecase/sync/PushSyncPatchesUseCase.kt`

领域模型至少包含：

- `SyncState`
- `SyncCapability`
- `SyncEntityKind`
- `SyncChange`
- `SyncSnapshotPage`
- `NotePatch`
- `PatchResult`
- `SyncError`

这些模型不得依赖 Android、Room 或 LAN JSON DTO。LAN DTO 在 `lan` 层转换为领域模型。

### 5.2 持久化与 Room

当前数据库版本为 19。同步 change-log 和批量 Journal 一起落地时，数据库版本提升到 20：

- `app/src/main/java/com/shihuaidexianyu/money/data/db/MoneyDatabase.kt`
- 新增 `MIGRATION_19_20`
- 更新 `app/schemas/`
- 扩展 `MoneyDatabaseMigrationTest`

建议新增表：

```text
sync_dataset
  singletonId, datasetId, nextRevision, createdAt

sync_change_log
  revision PRIMARY KEY,
  entityKind,
  recordId,
  operation,
  payloadJson 或 tombstone 字段,
  updatedAt,
  requestId,
  createdAt

ai_mutation_journal_items
  journalId,
  itemIndex,
  entityKind,
  recordId,
  beforeSnapshotJson,
  afterSnapshotJson,
  expectedUpdatedAt
```

`sync_change_log.payloadJson` 存的是 4.3 定义的镜像行 DTO JSON。

revision 基线：19 → 20 迁移时创建 `sync_dataset` 行（新 `datasetId`，`nextRevision = 1`），change-log 为空。迁移前已存在的账本数据没有任何 change-log 项，完全由首次 snapshot 覆盖；客户端未同步过时游标为 0（表示"从 revision 1 之前开始"）。`minAvailableRevision` 初始为 1。

change-log 保留策略：至少保留最近 10 000 个 revision，且不清理 30 天以内的项；清理推进时同步更新 `minAvailableRevision`。客户端游标早于 `minAvailableRevision` 时返回 `RESYNC_REQUIRED`。

现有 `ai_mutation_journal` 增加 `entryType`（`single` / `batch`，旧数据视为 `single`）和批次元数据。单条 Journal 保持兼容；批量 Journal 由一个顶层 entry 加多个 item 组成。不要把多个账单的 before/after JSON 塞进现有单条字段，也不要用虚假的 `recordId` 表示批次。

change-log 不属于用户可导出的账本数据，不进入 backup JSON。备份替换时必须：

1. 在同一替换事务中清理旧 change-log。
2. 生成新的 `datasetId`。
3. 将 revision 从新数据集重新开始。
4. 让旧 Python 镜像收到 `DATASET_MISMATCH` 后重新初始化，而不是尝试合并。

### 5.3 变更记录覆盖范围

所有会出现在镜像中的变化都必须在同一 Room 事务中写入 change-log：

- 账户创建、编辑、隐藏、关闭、重新打开、排序。注意：账户不可删除（无墓碑），也没有 `updatedAt` 版本令牌；账户类 change-log 项的 `updatedAt` 只是变更登记时间，不能用作并发令牌。
- Cash flow、Transfer、Balance update、Balance adjustment 的创建、编辑、软删除、恢复。
- **Journal 撤销路径**：单条 undo 和批量 undo 会把 before-snapshot 写回账本（可能改动备注、金额、删除状态），这些恢复写入同样必须追加 change-log；discard 不改账本，不记录。漏掉这条路径会让电脑端整理过的备注被手机端一次撤销后永久失真。
- **提醒自动记账**：`ProcessDueReminderUseCase` 在一个事务内插入 cash flow，用户不操作 UI 也会发生，必须覆盖。
- 备份导入、回滚和重置所导致的数据集切换。

不能只在 LAN 路由里记日志，否则 Android UI 的修改、提醒自动记账、撤销和导入操作会让电脑端镜像逐渐失真。

实现顺序建议：

1. 先抽出一个领域级 `AppendSyncChangesUseCase` 或等价接口。
2. 在现有各类 mutation use case 的事务边界内调用它。绝大多数 mutation 已经跑在 `DatabaseTransactionRunner` / `transactionRepository.runInTransaction` 内，注入点现成；例外是 `CreateAccountUseCase`（当前账户插入和提醒配置写入是两次非原子调用），需要先为它补一个显式事务边界。
3. AI Journaled mutation（含 undo）复用同一事务和同一 revision 分配器。
4. 导入替换走专用的数据集切换路径（清 change-log、换 `datasetId`、revision 从 1 重新开始），不为每一行生成无意义的普通变更。
5. 启动遗留迁移在已完成 v14 迁移的安装上不再写账本；若个别路径仍产生账本写入，同样必须记录。调试种子数据仅限 debug 构建，允许不记录。

不要引入 DAO trigger 来隐式生成 change-log——那样产生的 JSON 无法被领域层解释，也会绕过测试。

### 5.4 LAN 协议与路由

修改位置：

- `app/src/main/java/com/shihuaidexianyu/money/lan/MoneyLanProtocol.kt`
- `app/src/main/java/com/shihuaidexianyu/money/lan/MoneyLanRequestRouter.kt`
- 必要时新增 `lan/MoneyLanSyncDtos.kt`
- `app/src/main/java/com/shihuaidexianyu/money/lan/MoneyLanServer.kt`

任务：

- 在 `server.info` 响应中增加 `capabilities` 字段（4.1）。
- 增加 `sync.state`、`sync.snapshot`、`sync.pull`、`sync.push` 路由。
- 保留当前连接并发限制、帧大小限制、会话校验和写开关。
- 对 snapshot、pull、push 做明确的页大小、帧大小和写入预算校验；`sync.push` 按 4.6 的规则计费（按请求计 1 次写动作 + 每分钟 ≤10 批的独立窗口，幂等重放免费）。
- 把现有散落的错误码字符串集中定义为稳定注册表，将协议异常映射为稳定错误码，不把堆栈或敏感数据返回给 Python。
- 路由层只调用同步 Use Case；不直接读取 DAO，也不直接调用 ledger repository 写入。

同时做一个低风险的即时优化：新增 `records.list.detailed`（能力名 `records.list.detailed.v1`），在现有 `records.list` 基础上增加 `note`、`updatedAt`、`deletedAt`、`operationId`。它必须和 snapshot 一样有帧预算保护：默认页 50 条、上限 100 条，服务端按帧大小缩小页，不能让 100 条长备注顶爆 256 KiB 帧。这样旧查询路径不再为了每条记录调用 `records.get`，但它不能替代 durable revision 同步。

### 5.5 批量 Journal 与撤销

`sync.push` 的批次是一个可重试的请求，也是一个 Journal 逻辑单元：

- 顶层 Journal 记录批次 requestId、客户端、条数、摘要和状态（`entryType = batch`）。仅当批次至少成功应用一条补丁时才落栈；全冲突/全无效批次不产生 Journal 记录（撤销栈只放成功修改，重试靠稳定重分类保证无副作用，见 4.6 第 9 条）。
- Journal item 保存每条记录的 before/after 快照和 `expectedUpdatedAt`。
- 批次中的每个 item 仍然只能修改备注。
- 批次 requestId 唯一（复用现有 `ai_mutation_journal.requestId` 唯一索引），重复请求只返回第一次结果。
- `journal.list` 的 entry 增加 `entryType`、`itemCount`、`appliedCount`、`conflictCount`；批次展示摘要和成功/冲突数量。
- 撤销语义：批次与单条在同一 LIFO 栈中按 journal id 混排，`journal.undo_latest` 永远只弹栈顶。栈顶是批次时整批作为一个单元：先在一个事务内对所有 item 做当前语义状态预检（与现有单条 undo 的"忽略 `updatedAt` 的语义相等"检查一致），任一 item 漂移即整批拒绝、不改任何数据，并返回逐条冲突明细；预检全部通过才在同一事务内逐条恢复 before-snapshot。整个批次 undo 用一个 `undoRequestId` 幂等（复用现有 `undoRequestId` 唯一索引）。
- `journal.undo_latest` 的响应需要扩展批次形状：`entryType = batch` 时返回 `items` 数组（逐条 `recordId` + 恢复结果/冲突原因），单条响应形状保持不变。
- 单条旧 Journal 的撤销行为保持不变。

批量 Use Case 必须复用现有的账户生命周期检查、记录存在性检查、版本检查、刷新账户活动状态和 Journal 规则。不能为了吞吐直接对 DAO 执行 SQL update。撤销产生的恢复写入同样要走 5.3 的 change-log 路径。

### 5.6 DI、测试和构建

更新：

- `di/DataGraph.kt`
- `di/UseCaseGraph.kt`
- `MoneyAppContainer.kt`
- 所有受影响的 LAN router/ViewModel 测试工厂

Android 测试至少包括：

- 19 → 20 Room migration 和旧 Journal 数据兼容（旧数据 `entryType` 视为 `single`）。
- 每一种 mutation 都恰好写入正确的 change-log 项，包括：UI 用例、AI 单条写入、**AI 撤销（undo）恢复写入**、**提醒自动记账**、账户生命周期（含补上事务后的 `CreateAccountUseCase`）。
- 同一 requestId 重试不会重复写账；`sync.push` 幂等重放不消耗写入预算。
- snapshot 期间发生新增、更新、删除，catch-up 后最终一致。
- 游标过旧时正确返回 `RESYNC_REQUIRED`；保留窗口推进后 `minAvailableRevision` 正确。
- push 的 expectedUpdatedAt 冲突不覆盖手机数据；备注 trim / 200 字上限 / 非 note 字段返回 `INVALID_PATCH`。
- 批量 Journal 的成功、部分冲突、整批 undo（含预检失败整批拒绝）和后续修改冲突；批次与单条混排的 LIFO 顺序。
- 备份导入 / 回滚在同一事务内切换 `datasetId` 并清空 change-log（需要一个真实 Room 的 `replaceAll` 测试——现有 coordinator 测试全部 fake 掉 repository）。
- 旧 protocol 客户端仍能调用原有接口。
- 帧大小、页大小、并发和服务过期错误；`records.list.detailed` 的帧预算缩页。

注意：当前 LAN 层（server / router / framing）没有任何测试，上述协议测试需要从零建设测试设施，工作量不要按"扩展已有测试"估算。

提交前执行：

```powershell
.\gradlew.bat test
.\gradlew.bat kspDebugKotlin
```

有设备时再执行 Room migration、LAN 集成和 `connectedAndroidTest`。

## 6. Python skill 仓库修改计划

仓库：`C:\Users\exqin\Desktop\file\myproject\money-client-skill`

### 6.1 本地 SQLite 镜像

默认数据目录沿用当前配置目录：

```text
%APPDATA%\money-mcp\
├── connection.json       # 已有配对配置，单独保护
└── sync\
    └── datasets\
        └── <datasetId>.db
```

支持环境变量或 CLI 参数覆盖数据目录，便于测试。镜像数据库不保存 session token；日志也不能打印 token、配对码或完整账单。

建议新增：

- `scripts/money_sync_store.py`
- `scripts/money_sync_engine.py`
- `scripts/money_sync_models.py`

推荐表：

```text
mirror_meta
  dataset_id, applied_revision, min_available_revision,
  sync_state, last_sync_at, last_error

mirror_accounts
  account_id, source_revision, payload_json, last_change_at

mirror_records
  entity_kind, record_id, source_revision, deleted,
  occurred_at, account_id, amount, note, updated_at, payload_json

pending_patches
  patch_id, entity_kind, record_id, expected_updated_at,
  changes_json, state, attempts, last_error, created_at, updated_at

sync_conflicts
  conflict_id, patch_id, server_payload_json,
  expected_updated_at, server_updated_at, state, created_at

sync_jobs
  job_id, job_kind, state, cursor_json,
  attempts, last_error, created_at, updated_at
```

`mirror_records` 使用常用字段加原始 JSON：常用字段支持本地筛选和备注查询，原始 JSON 保留未来字段。软删除记录仍保留，避免误把服务器删除当成”从未同步过”。`mirror_accounts` 没有 `deleted` 列（账户不可删除、无墓碑），`last_change_at` 来自 change-log 的登记时间，仅作展示，不是版本令牌——账户没有 `updatedAt`，同步一致性只依赖 `source_revision`。

镜像数据库用 `PRAGMA user_version` 记录 schema 版本。版本不匹配时直接删除该 dataset 的镜像库并重新同步——镜像只是缓存，不为缓存写迁移脚本。

所有页面应用都在 SQLite 事务内完成：

- 先校验 `datasetId` 和 revision 顺序。
- 只接受更大的 `sourceRevision`。
- 更新 `mirror_meta.applied_revision`。
- 失败时整页回滚，任务保留当前游标。

首次 snapshot 应写入临时状态，完成 catch-up 后再切换为 `ready`；中途进程退出不能留下一个看似完整但实际缺页的镜像。

### 6.2 同步引擎

`money_sync_engine.py` 负责以下状态机：

```text
new
  └─ state probe
       ├─ dataset unknown → snapshot pages → catch-up pull → ready
       ├─ same dataset → pull from applied_revision → optional push → ready
       ├─ RESYNC_REQUIRED → staged snapshot → catch-up → ready
       └─ service/auth/network error → paused
```

同步顺序建议：

1. 读取本地 meta。
2. `sync.state` 检查服务、数据集和服务器 revision。手机服务重启后必须先完成重新配对（用户在手机上读取新配对码），引擎要把"等待重新配对"作为显式状态展示，不能报成普通网络错误。
3. 数据集不一致时停止并要求显式选择新镜像，不自动删除旧库。
4. 有旧游标时先 pull，使本地记录和 `expectedUpdatedAt` 尽量新。
5. 如有待发 outbox，按 50 条以内分批 push，批间连续发送，遇到 `RATE_LIMITED`（每分钟 10 批窗口）再退避（见 4.6 计费规则）。
6. push 后再次 pull，确认手机权威状态。
7. 写入统计信息和下一次可恢复的任务状态。

进行中的 snapshot 中断后按 4.4 的游标语义续传：携带相同 `snapshotRevision` 的 keyset 游标可以跨连接、跨（重新配对后的）服务重启继续使用；若服务端返回 `RESYNC_REQUIRED`，放弃暂存区并重新定基。

读操作可以使用 asyncio；同一个数据集的 pull 必须按 revision 顺序提交，不能让并行页面乱序覆盖。不同数据集才允许并行，v1 默认不启用多数据集并行。

### 6.3 Outbox、预览和备注修改

新增 `money_patch.py`，只生成结构化备注补丁：

- 输入是记录 ID、当前镜像中的 `updatedAt` 和新备注。
- 只允许 `changes.note`；新备注 trim 后 ≤200 字、允许为空，与服务端校验（4.6）保持一致。
- 不增加类别字段，不把类别写进备注，除非用户明确要求。
- preview 展示的必须是将要写入的一字不差的最终文本；客户端不得做任何静默规范化（不替换字符、不改写措辞）。AI 生成建议时避免使用结构化分隔符只是风格提示，不构成协议校验。
- 每个补丁有稳定 `patchId`，本地重复运行不会生成重复 outbox 项。

推荐采用两步提交：

1. `preview`：列出将修改的记录、旧备注、新备注、当前版本和预计冲突数，生成 `planId` 与计划摘要哈希。
2. `apply`：用户明确确认同一个 `planId` 后，才把补丁送到 `sync.push`。

这样可以把“AI 先整理建议”和“真正修改账本”分开。发生冲突时保留手机版本和本地计划，交给用户决定重做、跳过或生成新备注。

### 6.4 传输层优化

修改：

- `scripts/money_bridge_core.py`
- `scripts/money_client_cli.py`
- 必要时新增 `scripts/money_transport.py`

第一阶段不要求长连接，先做这些稳定性修复：

- 拆分连接超时、写超时、读超时和总请求超时（现状只有一个 15 秒总超时）。
- 连接失败、会话过期、帧过大、协议错误使用可判断的异常类型；”服务不可达”（TCP 失败）和 `SESSION_EXPIRED`（服务在运行但会话到期）必须区分开。
- retry 只对同一个 requestId 生效；写请求未知结果不能换 requestId（现状已满足，保持）。
- `status` 增加轻量 liveness probe，区分”配置存在”和”手机服务当前可达”。
- 同步请求使用页面和批次，不再在 CLI 中对每条记录顺序调用 `records.get`。
- push 批间连续发送，收到 `RATE_LIMITED` 时按窗口退避并保留任务进度；单批服务端处理约 1–2 秒，push 的读超时应放宽（如 30 秒）——超时重试有同一 requestId 幂等兜底。
- CLI 输出进度、已应用 revision、剩余 outbox 和冲突数量，但不输出完整敏感账单。

后续如果实测仍受连接建立影响，再单独设计 Protocol v2 的持久连接和 request multiplexing；不能在 v1 中混用两种 framing。

### 6.5 CLI 和 MCP 工具

在 `scripts/money_client_cli.py` 增加：

```text
sync state
sync run
sync status
sync resume <job-id>
sync conflicts
sync reset --dataset <dataset-id>       # 显式操作，默认不删除
notes preview ...
notes apply --plan <plan-id>
```

在 `scripts/money_mcp.py` 增加高层工具：

- `money_sync_state`
- `money_sync`
- `money_sync_status`
- `money_query_local_records`
- `money_preview_note_changes`
- `money_apply_note_changes`
- `money_list_sync_conflicts`

已有的账户、记录、summary、Journal 和单条写入工具继续保留。高层工具内部调用同步引擎和本地数据库，不能让模型自行循环几百次低层 `records.get`。备注修改计划在单批以内时 `money_apply_note_changes` 同步返回结果；多批计划作为 job 执行、用 `money_sync_status` 轮询进度，避免 AI 宿主侧的工具调用超时。

### 6.6 skill 文档与资源

更新或新增：

- `SKILL.md`：保持短小，只描述触发条件、手机权威、同步优先级、安全边界和入口。
- `references/sync.md`：同步状态机、协议字段、恢复和冲突处理。
- `references/architecture.md`：补充电脑端 SQLite 镜像、outbox 和 revision。
- `references/protocol.md`：补充 capability、snapshot、pull、push 和错误码。
- `references/operations.md`：补充配对、liveness、sync run、暂停和 reset。
- `README.md`：补充本地镜像目录、CLI 示例和升级兼容说明。
- `agents/openai.yaml`：将批量备注整理路由到 preview/apply 流程，将历史账单查询路由到本地镜像优先。
- `scripts/test_*.py`：增加同步状态机、SQLite、故障恢复和 MCP schema 测试。

`SKILL.md` 不复制所有协议细节；细节通过 `references/` 渐进加载。重复的协议定义必须在一次跨仓库变更中同步更新，避免 Android 和 skill 各自演化出不同字段。

实现 skill 后运行：

```powershell
uv run python -m unittest discover
uv run python C:\Users\exqin\.codex\skills\.system\skill-creator\scripts\quick_validate.py C:\Users\exqin\Desktop\file\myproject\money-client-skill
```

`dist/` 只在功能、测试和文档都通过后重新生成，不把构建产物当作开发源文件。

## 7. 两个仓库的协作顺序

### 阶段 0：先固定契约

Android 与 Python 同时完成：

- 本文档评审。
- protocol fixture JSON，覆盖 4.3 的全部 payload DTO、sync 四个端点的请求/响应和错误样例。
- capability 和错误码名称。
- `datasetId`、revision、snapshot cursor、patch requestId 的语义。
- 备注补丁只允许 note 字段，校验规则（trim、≤200 字）与 4.6 一致。

fixture 权威源放在 Android 仓库 `docs/protocol/sync-v1/fixtures/`；Python 仓库在 `references/fixtures/` 保存一份复制，并用一个校验两边 SHA-256 一致的测试防止漂移。任何协议变更必须在同一次跨仓库变更中更新两边 fixture 和文档。

产物：一套可被 Android 和 Python 测试共同读取的协议样例；此阶段不改真实账本行为。

### 阶段 1：Android 先建立可靠的 revision

Android：

- Room 19 → 20。
- `sync_dataset` 和 `sync_change_log`。
- 所有 mutation 路径记录变更（含 undo 恢复写入、提醒自动记账；为 `CreateAccountUseCase` 补事务）。
- snapshot/pull 只读接口。

Python 可并行：

- SQLite schema、store、fake phone server。
- 对 fixture 实现 snapshot/pull。
- 本地查询优先读取镜像。

### 阶段 2：端到端只读同步

先不开放批量写：

- 真实手机首次 snapshot。
- 断线后从 cursor 续传。
- Android UI 修改后 Python pull 可见。
- 备份导入后 dataset mismatch 能安全处理。
- 旧客户端继续工作。

只有只读同步连续通过，才进入写入阶段。

### 阶段 3：批量备注写入

Android：

- 批量 Journal schema 和 Use Case。
- `sync.push` note-only。
- 冲突、幂等（重放免费）、批次 undo（预检失败整批拒绝）。

Python：

- outbox、preview/apply、批量 push。
- 失败重试和冲突持久化。
- MCP 高层工具和自然语言备注规则。

### 阶段 4：性能和体验

- 删除旧的 exact-note N+1 路径。
- 增加 `records.list.detailed` 兼容接口（带帧预算缩页）。
- 进度、恢复、冲突处理和 liveness 提示。
- 用真实历史数据做大数据集测试。
- 评估是否需要 Protocol v2（长连接、多路复用、帧压缩）；没有测量证据不提前引入。

## 8. 测试与验收标准

### 8.1 正确性

- 首次同步后，镜像中的账户、四类账本记录、备注、版本和软删除状态与手机一致。
- Android UI、AI 单条写入及其撤销、提醒自动记账、批量 push、备份导入都会产生正确的 revision 行为。
- 同一请求重试不会重复创建、修改或删除记录。
- 电脑端的修改必须带旧版本；手机上已有更新时只报告冲突。
- 本地修改失败不会丢失 outbox；服务停止不会丢失 job、cursor 或冲突。
- 数据集变化不会静默合并两个账本。

### 8.2 性能

以当前约 348 条历史记录作为基准（假设手机亮屏、正常家庭 WiFi）：

- 首次读取不得为每条记录发起 `records.get`。
- 首次同步 = 1 次 `sync.state` + 约 2–4 页 snapshot + 1–2 页 pull，总请求数 ≤10，预期数秒内完成；作为对照，现状 `records --exact-notes` 默认参数约需 102 次顺序连接。
- 之后的增量同步 = `sync.state` + pull，通常 ≤3 个请求、亚秒级。
- AI 分析期间的记录查询走本地镜像，不触碰手机（0 网络往返）；不经过镜像的交互式小查询用 `records.list.detailed`，1 个请求拿全字段。
- 100 条备注修改 = 2 批 push，预期数秒到十几秒；348 条全量整理 ≤7 批，1 分钟以内。
- 大备注、慢手机、断网和服务重启不能导致整个本地数据库损坏。
- 同步超时时给出阶段、游标、重试次数和下一步操作。

### 8.3 安全和隐私

- 只接受用户主动启动的临时 LAN 服务。
- 保留现有配对和 session token 校验；文档与客户端提示必须如实说明：服务停止后必须重新配对才能继续同步，没有无交互恢复。
- 全量 snapshot 让配对客户端可以用少量请求导出全量账本，这是配对门控下的刻意取舍（现有 `records.list` 翻页本来也能全量读），需在安全说明中写明。
- 保留手机端允许修改开关和写入预算。
- 本地数据库文件权限收紧；Windows 日志不写 token、配对码和完整账单。
- 提供显式 `sync reset` 清理镜像和 outbox，并在清理前显示数据集和待处理数量。
- 任何批量修改都先 preview，再由用户明确确认 apply。

## 9. 风险与处理

### 服务临时存在

Python 不能把”任务创建成功”误报为”已同步”。任务必须区分 `queued`、`running`、`paused` 和 `ready`，并显示手机服务不可达。

### 手机灭屏与省电

Android 省电策略会让灭屏手机的 WiFi 往返延迟从几毫秒飙升到数百毫秒，是所有 LAN 交互延迟的最大环境变量。首次全量同步和批量 push 建议保持手机亮屏；客户端超时要按灭屏场景留余量，不能把环境延迟误判为服务故障。

### change-log 膨胀

Android 按 5.2 的策略清理 change-log（至少保留最近 10 000 个 revision，且不清理 30 天以内的项），并通过 `minAvailableRevision` 告知客户端。客户端落后太久时做完整 snapshot，而不是返回不完整增量。

### 电脑端镜像过期

本地分析结果显示 `appliedRevision`、服务器最后探测时间和 `stale` 状态。涉及财务总额的结论优先要求 fresh sync，或明确标注使用的是本地快照。

### 并发修改

`expectedUpdatedAt` 只解决同一条记录的覆盖冲突；批量任务还必须逐条返回结果。批次 undo 先全量检查，再决定是否执行，不能部分覆盖后才发现冲突。

### 协议演化

所有新字段尽量可选并通过 capability 开启。协议 fixture、Android DTO、Python DTO 和 references 文档必须在同一个变更序列中更新。

## 10. Definition of Done

只有以下条件全部满足，才认为 v1 完成：

1. Android Room migration、change-log、snapshot、pull、push 和批量 Journal 测试通过。
2. Python SQLite 镜像、outbox、任务恢复、冲突和 MCP 工具测试通过。
3. 真实手机完成一次全量 snapshot，随后只用 revision 增量同步。
4. 电脑端可以离线查询历史记录和生成自然中文备注预览。
5. 用户确认后可以批量提交备注修改，金额和账户等财务字段保持不变。
6. 断网、手机服务停止、进程重启和 push 超时都能恢复或明确暂停。
7. 旧客户端和未升级 Android 的兼容行为已验证。
8. skill 文档、协议 references、README 与实现一致，并通过 skill 校验脚本。
9. 真实数据验收后，再考虑清理旧的逐条 exact-note 查询路径或实现 Protocol v2 长连接。

