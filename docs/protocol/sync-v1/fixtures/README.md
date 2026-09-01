# Money Link Protocol — sync v1 fixtures

权威协议样例（authoritative source）。`money-client-skill` 仓在
`references/fixtures/sync-v1/` 保存一份逐字节复制，两边的测试都校验本目录
`manifest.json` 中的 SHA-256；任何协议变更必须在同一次跨仓库变更中更新两边。

## 约定

- 传输信封：`MoneyLanRequest{version, requestId, action, token?, arguments}` /
  `MoneyLanResponse{version, requestId, ok, data?, error?{code, message}}`。
  fixture 文件保存完整信封，`*_request.json` 为请求、`*_response.json` 为响应。
- 时间戳一律 epoch millis（Long 数字）。`expectedUpdatedAt` 等版本字段按精确相等比较。
- 金额一律最小货币单位十进制字符串。存储层 payload 的 `amount`/`delta` 为绝对值
  （方向看 `direction` 或字段名）；历史投影（`records.list` / `records.list.detailed`）
  的 `amount` 带符号（流入为正、流出为负）。
- 可空字段为 JSON null 时**省略该键**（`explicitNulls=false`）：活跃记录没有
  `deletedAt` 键，未关闭账户没有 `closedAt` 键。客户端按「缺省即 null」解析。
- 枚举为小写蛇形字符串：`cash_flow`、`transfer`、`balance_update`、
  `balance_adjustment`、`inflow`、`outflow`、`funding`、`investment`。
- `payload_*.json` / `tombstone_*.json` 是镜像行信封
  `{entityKind, sourceRevision, payload}`，出现在 snapshot 页、pull change
  和 push 冲突响应的 `serverPayload` 中。tombstone 不带 `payload` 键。
- `sync.snapshot` 首次请求不带 `snapshotRevision`/`cursor`，由服务端定基并在响应中
  返回；之后携带相同 `snapshotRevision` 与上一页 `nextCursor` 续传。游标为不透明
  字符串，客户端不得解析或拼接。
- `sync.push` 的批次幂等键就是信封级 `requestId`（沿用协议既有幂等机制），
  请求体内不再重复携带。批内每个补丁用客户端生成的 `patchId` 定位结果。
- 认证字段（`token`）、`datasetId` 等均为示例值，不是秘密。

## 文件

| 文件 | 内容 |
|---|---|
| `server_info_response.json` | 能力宣告（含 sync v1 capabilities） |
| `sync_state_response.json` | 数据集与 revision 状态 |
| `payload_account.json` … `payload_balance_adjustment.json` | 五类镜像行 DTO |
| `tombstone_cash_flow.json` | 删除墓碑行 |
| `sync_snapshot_request_first.json` / `sync_snapshot_request_next.json` | 定基与续传请求 |
| `sync_snapshot_response_page.json` / `sync_snapshot_response_done.json` | 快照分页 |
| `sync_pull_request.json` / `sync_pull_response.json` | 增量拉取（upsert + delete） |
| `sync_push_request.json` / `sync_push_response.json` | 批量备注补丁（applied/conflict/invalid） |
| `records_list_detailed_response.json` | 扩展字段列表（note/updatedAt/operationId） |
| `error_*.json` | 稳定错误码响应样例 |
| `manifest.json` | 上述文件的 SHA-256（重新生成方式见下） |

重新生成 manifest（在 fixtures 目录内，Git Bash）：

```bash
sha256sum *.json | grep -v manifest.json | jq -R -s 'split("\n") | map(select(length>0) | split("  ") | {(.[1]): .[0]}) | add | {algorithm:"sha256", files:.}' > manifest.json
```
