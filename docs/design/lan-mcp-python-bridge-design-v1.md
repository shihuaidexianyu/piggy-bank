# 局域网 AI 桥接方案设计 — v1

状态：首版代码已实现；真实手机与目标 AI 客户端的端到端验收待执行
项目：Money（com.shihuaidexianyu.money）
最后更新：2026-08-31

本文档定义 Money 的局域网 AI 接入方案。

方案由两个部分组成：

1. Android App 在用户主动开启后，通过局域网端口提供一套轻量、专用于 Money 的请求/响应协议。
2. 电脑上的 Python 脚本连接手机，并通过标准输入输出（stdio）向 AI 客户端提供标准 MCP 服务。

最重要的设计决定是：**Android App 不直接实现 MCP**。

MCP 版本兼容、工具 Schema、AI 友好的参数处理以及不同 AI 客户端之间的差异，全部放在容易替换和升级的 Python 桥接程序里。手机仍然是账本数据的唯一权威来源，并继续通过现有 Use Case 执行所有写入和业务校验。

## 1. 最终使用体验

用户的完整操作流程如下：

1. 打开 Money，进入“设置 > 局域网 AI 接口”。
2. 选择“只读”或“允许修改”，然后点击“启动”。
3. Money 显示手机局域网地址、端口、八位配对码和服务到期时间。
4. 在电脑上运行一次配对命令：

~~~powershell
uv run money_mcp.py pair --host 192.168.1.20 --port 43127 --code 12345678
~~~

5. 在 AI 客户端中配置本地 MCP 服务：

~~~json
{
  "mcpServers": {
    "money": {
      "command": "uv",
      "args": [
        "run",
        "--project",
        "C:/path/to/money-client-skill/scripts",
        "C:/path/to/money-client-skill/scripts/money_mcp.py",
        "serve"
      ]
    }
  }
}
~~~

6. AI 调用标准 MCP 工具。
7. Python 桥接程序把 MCP 调用转换成 Money 专用协议请求并发送给手机。
8. 手机通过现有 Repository 和 Use Case 查询或修改真实账本。

数据链路如下：

~~~text
AI 客户端
  │
  │ 标准 MCP（stdio）
  ▼
电脑上的 Python Money MCP 桥接程序
  │
  │ Money Link Protocol v1（局域网 TCP）
  ▼
Android 前台服务
  │
  │ 类型化命令路由
  ▼
现有 Use Case / 专用查询接口
  │
  ▼
Room 数据库
~~~

## 2. 目标和非目标

### 2.1 目标

1. 让本地 AI 查询账户、余额、历史流水和可靠的财务统计。
2. 在用户明确开启写权限后，让 AI 新增、修改、软删除和恢复普通收支及转账记录。
3. 让 Android 实现不依赖 MCP 协议版本和具体 AI 客户端。
4. 复用项目现有的校验、幂等、乐观并发、关闭账户保护、软删除和派生状态刷新逻辑。
5. 面向单个用户和可信局域网，保持安装及使用流程足够简单。
6. 不为了提供一个端口而给 Android 引入完整 HTTP/MCP 服务端框架。
7. 由手机负责计算权威账本数据，Python 只做转换和编排。

### 2.2 v1 明确不做的内容

- Android App 不实现 MCP 传输和 MCP SDK。
- 不使用云端中继。
- 不支持外网远程访问、NAT 穿透或公共地址。
- 不实现 OAuth、永久凭据和多用户身份系统。
- v1 不实现 TLS。
- v1 不实现 mDNS 自动发现。
- 不提供任意 SQL 或原始 DAO 访问。
- 不允许通过桥接程序下载完整备份。
- 不提供备份导入、数据库重置、账户关闭/重开和提醒管理。
- 不提供批量修改语言。
- 首版不允许 AI 创建或修改余额核对记录与手动余额调整记录。
- 不在手机或 Python 中生成不透明的“财务健康评分”。
- 不承诺支持所有 MCP 客户端；发布前只要求通过目标客户端的兼容性验证。

## 3. 关键简化决策

| 决策 | 为什么这样设计 | 收益 | 维护成本 | v1 结论 |
|---|---|---:|---:|---|
| Python 作为真正的 MCP Server | MCP 更适合放在 AI 所在电脑上 | 极高 | 中 | 加入 |
| Android 使用 Money 专用协议 | 消除 Android 端 MCP、OAuth、SSE 和版本兼容成本 | 极高 | 低到中 | 加入 |
| Python MCP 使用 stdio | AI 客户端直接启动本地子进程，不需要电脑端端口和鉴权 | 极高 | 低 | 加入 |
| 手机协议使用长度前缀 TCP JSON | 只依赖 Android/JDK Socket 和现有序列化库 | 高 | 低到中 | 加入 |
| 每个 TCP 连接只处理一个请求 | 不需要多路复用、心跳、长连接恢复和连接状态机 | 高 | 低 | 加入 |
| 临时配对码和临时 Token | 足够满足用户主动开启的可信局域网会话 | 高 | 低 | 加入 |
| v1 使用明文 TCP | 避免证书生成、分发和信任配置 | 中 | 低 | 加入，但必须警告 |
| 用户主动启动的会话默认允许写入 | 启动临时服务本身就是一次明确授权，避免强 AI Agent 被重复审批打断 | 高 | 低 | 加入 |
| 手机端会话级写入开关 | 需要只读时仍可在启动前切换，但不做逐笔确认 | 高 | 低 | 加入 |
| Python 可选只读环境变量 | 作为电脑端额外收紧手段，不成为正常写入的第二道必选开关 | 中 | 低 | 加入 |
| 所有写入调用现有 Use Case | 保留全部账本一致性规则 | 极高 | 低 | 加入 |
| 持久化 AI Journal + 严格 LIFO 撤销 | 用可追踪、可逐步回退的保护替代低效逐笔审批 | 极高 | 中 | 加入 |
| 永久配对设备 | 更方便，但需要凭据保存、轮换和撤销 | 中 | 高 | 延后 |
| TLS | 能保护不可信网络，但证书支持成本较高 | 中高 | 高 | 延后 |
| mDNS 自动发现 | 能省去输入地址，但普通 MCP 客户端不会直接使用 | 中 | 中 | 延后 |
| 记录所有读取访问 | 数据量和隐私成本高，对恢复账本没有帮助 | 低 | 中高 | 延后 |
| 打包 Windows/macOS/Linux 可执行文件 | 减少 Python 安装要求，但增加多平台发布维护 | 中 | 高 | 延后 |

v1 的安全边界必须明确：

> 本功能只用于个人家庭网络或其他可信局域网。临时 Token 用于控制访问，但明文 TCP 不提供传输加密。

如果未来需要在公共 Wi-Fi 或其他不可信网络中使用，应升级为标准 TLS。项目不能自行发明字段加密或自定义密码协议。

## 4. 两端职责划分

### 4.1 Android App 负责

- 启动和停止前台服务。
- 监听设备 IPv4 网络接口，并展示可连接的局域网地址。
- 生成一次性配对码和随机会话 Token。
- 执行服务到期和只读/可写权限判断。
- 读取和写入有长度上限的协议帧。
- 校验协议版本和命令参数。
- 把读取命令映射到专用查询接口。
- 把写入命令映射到现有 Use Case。
- 在同一个 Room 事务中提交账本写入和 AI Journal 入栈。
- 按全局栈顶执行语义快照校验和逐步撤销。
- 把异常转换为稳定错误码。
- 在用户停止、四小时会话到期、前台服务超时或进程退出时关闭端口。
- 在设置页面和前台通知中持续显示运行状态。

Android App 不负责：

- 定义 MCP 工具 Schema。
- 解析自然语言日期。
- 根据模糊名称猜测账户。
- 针对不同 AI 客户端做兼容。
- 把很多底层请求编排成 AI 工作流。
- 替电脑重试语义不确定的写入。

### 4.2 Python 桥接程序负责

- 通过 stdio 提供标准 MCP Server。
- 暴露稳定、适合 AI 使用的工具 Schema 和说明。
- 保存当前临时手机连接配置。
- 使用配对码向手机换取随机会话 Token。
- 使用 Decimal 精确转换金额。
- 把带 `Z` 或显式时区偏移的 ISO 8601 时间转换为手机协议时间边界。
- 在账户名称唯一匹配时解析为账户 ID。
- 为写操作生成并复用 requestId；新增记录的 operationId 由手机稳定派生。
- 原样传递分页游标。
- 把手机错误转换成简洁的 MCP 工具错误。
- 在明确上限内组合多个手机查询。
- 确保日志只写 stderr，绝不污染 MCP stdout。

Python 桥接程序不允许：

- 自行计算权威余额。
- 直接操作导出的数据库。
- 在同名账户之间自动猜测。
- 写入响应不确定时使用新的 requestId 重试。
- 绕过手机端的只读/可写权限。
- 把自由文本备注声称为正式消费分类。

## 5. Money Link Protocol v1

Money Link Protocol（以下简称 MLP）是手机和 Python 桥接程序之间的专用协议。

它不是公开通用协议，也不是 MCP 的替代品。它只负责在本项目的两个受控组件之间传递类型化账本命令。

### 5.1 传输模型

- 手机使用 ServerSocket 监听当前局域网地址。
- 绑定端口 0，由操作系统选择一个可用临时端口。
- 每个 TCP 连接恰好包含一个请求和一个响应。
- Python 读取响应后关闭连接。
- 手机写完响应后也关闭连接。
- 各请求相互独立。
- 没有持久连接、会话复用、多路复用、心跳、订阅和服务端推送。
- Socket 读取超时为 10 秒。
- 最多同时接受 4 个 Socket。
- 最多同时执行 2 个重型聚合查询。

AI 工具调用频率很低，局域网中新建 TCP 连接的成本可以忽略。相比长连接，一请求一连接能显著降低生命周期和错误恢复复杂度。

### 5.2 帧格式

每个请求和响应都采用以下格式：

~~~text
4 字节无符号大端序负载长度
N 字节 UTF-8 JSON
~~~

限制：

- 请求和响应 JSON 均最大 256 KiB。
- 非法长度或超限长度必须在分配 JSON 内存之前拒绝。
- 一个连接只有一个请求，不接受响应后或请求帧后的额外数据。

选择长度前缀而不是 NDJSON 的原因：

- 解析前即可知道准确大小。
- 更容易防止超大内存分配。
- JSON 字符串可以包含任意转义内容。
- Kotlin 和 Python 都能简单实现 readExactly。

### 5.3 请求结构

~~~json
{
  "version": 1,
  "requestId": "57bfc8b4-b37d-4d95-97bc-461543044f07",
  "token": "base64url-session-token",
  "action": "records.list",
  "arguments": {
    "startInclusive": 1785513600000,
    "endExclusive": 1788192000000,
    "limit": 50
  }
}
~~~

字段规则：

- version 必须为 1；`server.info` 可在配对前查询支持版本。
- requestId 是 Python 生成的 UUID，响应必须原样返回。
- 除 `server.info` 和 `session.pair` 外，token 必填。
- action 必须来自封闭命令集合。
- arguments 必须是 JSON Object，并映射到明确的 Kotlin DTO。
- 写入请求出现未知字段时直接拒绝。
- 读取请求只有在文档明确标记为前向兼容时，才能忽略可选未知字段。

### 5.4 成功响应

~~~json
{
  "version": 1,
  "requestId": "57bfc8b4-b37d-4d95-97bc-461543044f07",
  "ok": true,
  "data": {
    "records": [],
    "nextCursor": null
  }
}
~~~

### 5.5 错误响应

~~~json
{
  "version": 1,
  "requestId": "57bfc8b4-b37d-4d95-97bc-461543044f07",
  "ok": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "金额必须大于 0"
  }
}
~~~

稳定错误码：

| 错误码 | 含义 | Python 处理方式 |
|---|---|---|
| UNSUPPORTED_VERSION | 手机和脚本没有共同协议版本 | 提示更新 App 或桥接程序 |
| UNAUTHORIZED | Token 缺失或错误 | 提示重新配对 |
| SESSION_EXPIRED | 手机会话已经结束 | 提示重新启动服务 |
| WRITE_DISABLED | 手机会话为只读 | 不自动重试 |
| VALIDATION_FAILED | 协议参数或领域参数错误 | 把可操作提示返回 AI |
| NOT_FOUND | 账户或记录不存在 | 不自动重试 |
| CONFLICT | 读取后记录已被修改 | 重新读取后再提出修改 |
| RATE_LIMITED | 请求过多 | 读取可在指定时间后重试 |
| RESPONSE_TOO_LARGE | 结果会超过上限 | 缩小范围或分页 |
| LEDGER_NOT_READY | 启动迁移门禁阻止访问 | 稍后重试 |
| AMOUNT_OVERFLOW | 金额运算超过 Long 范围 | 缩小金额或检查数据 |
| INTERNAL_ERROR | 手机端意外错误 | 只显示 requestId 供排查 |

配对和帧层还会返回 `PAIRING_EXPIRED`、`PAIRING_LOCKED`、`ALREADY_PAIRED`、`PAIRING_FAILED`、`FRAME_TOO_LARGE`、`INVALID_REQUEST` 与 `UNKNOWN_ACTION`。关闭账户等领域校验在 v1 统一映射为 `VALIDATION_FAILED`。

手机响应中不得包含：

- SQL 语句。
- 文件路径。
- 堆栈信息。
- Kotlin 类名。
- 原始内部异常。
- Token 或配对码。

### 5.6 配对流程

启动服务时生成：

- 八位数字配对码。
- 256 位随机会话 Token。
- 到期时间。
- 是否允许写入。

session.pair 是唯一不需要 Token 的状态变更命令：

~~~json
{
  "version": 1,
  "requestId": "uuid",
  "action": "session.pair",
  "arguments": {
    "code": "12345678",
    "clientName": "Desktop Money MCP Bridge"
  }
}
~~~

配对码规则：

- 十分钟后失效。
- 首次成功配对后立即失效。
- 连续失败五次后锁定。
- 不能改变用户在手机上选择的权限。

成功响应返回：

- 随机会话 Token。
- 是否允许写入。
- 到期时间。
- 会话 ID。

手机时区、金额精度和 Journal 策略通过配对后的 `service.context` 查询，避免配对响应承担业务协议职责。

Android 只在前台服务内存中保存 Token。停止服务后 Token 自然消失，不需要持久凭据清理。

Python 把 Token 保存到当前用户目录下的临时连接配置中。配置只服务于当前手机会话；过期后必须重新配对。

### 5.7 基础服务命令

#### server.info

不需要 Token，只返回：

- 应用名称。
- 支持的 MLP 版本。
- 当前是否允许配对。
- 当前会话是否允许写入及到期时间。

为什么需要：Python 在输入配对码前需要先确认端口确实属于兼容的 Money App。

收益：高。维护成本：低。v1 加入。

## 6. 手机端命令

### 6.1 读取命令总表

| action | 用途 | 可复用基础 | 收益 | 成本 | v1 |
|---|---|---|---:|---:|---|
| service.context | 查询手机时区、金额精度、权限和 Journal 策略 | 系统时区与会话状态 | 高 | 低 | 加入 |
| accounts.list | 查询账户和精确余额 | AccountRepository、余额 Use Case | 极高 | 低 | 加入 |
| records.list | 过滤和分页查询统一历史 | 现有 History Repository/DAO | 极高 | 低 | 加入 |
| records.get | 获取权威记录和修订版本 | 各类型 Repository 查询 | 高 | 低 | 加入 |
| ledger.summary | 查询确定性期间汇总和当前账户余额 | 现有历史汇总与余额 Use Case | 极高 | 低 | 加入 |
| journal.list | 查询最近 AI 修改及撤销状态 | AI Journal Repository | 高 | 低 | 加入 |

### 6.2 accounts.list

参数：

- includeClosed：是否包含关闭账户；隐藏账户始终返回，因为 AI 分析需要完整资产口径。

返回：

- 账户 ID 和名称。
- 日常/投资类型。
- 隐藏和关闭状态。
- 开户时间。
- 当前余额。
- 最后活动时间。

手机协议始终使用账户 ID。Python MCP 工具可以额外接受账户名称，但名称解析只是桥接层的便捷功能。

### 6.3 records.list

参数与 HistoryRecordFilters 保持一致：

- 包含关键字。
- 记录类型。
- 账户 ID。
- 开始时间（包含）。
- 结束时间（不包含）。
- 最小和最大绝对金额。
- 增加/减少方向。
- 不透明游标。
- 1 到 100 的分页大小。

游标由 occurredAt、sourceOrder 和 recordId 三个字段组成，Python 只负责原样传回。

响应包含：

- 记录类型和记录 ID。
- 账户 ID 和名称。
- 关联账户信息。
- 精确金额语义。
- 发生时间。
- 标题/备注展示文本。
- 记录前后余额。
- 下一页游标。

不提供“返回全部流水”的无界参数。

### 6.4 records.get

记录身份始终由类型和 ID 共同组成：

~~~json
{
  "type": "cash_flow",
  "id": "42"
}
~~~

四张账本表的 ID 不是全局唯一，因此 type 不能省略。

响应必须包含 updatedAt。后续更新或删除必须携带该值，以便现有 Use Case 检测并发修改。

### 6.5 ledger.summary

返回指定范围内的：

- 现金收入。
- 现金支出。
- 筛选范围内的账本净变化和记录总数。
- 当前总资产。
- 当前各账户余额。

这些值由手机端计算。Python 可以改变展示格式，但不能根据下载的流水重新定义权威总额。

### 6.6 后续统计候选

`summary.trend` 和 `cashflows.rank` 暂不进入首版实现。它们需要新的手机端聚合查询；等真实使用证明趋势桶或大额排行经常被调用时再加入，收益才足以覆盖 SQL、时区边界和测试维护成本。

## 7. 写入命令

用户在手机上以“允许 AI 修改账目”模式启动临时服务后，本次已配对会话可以直接写入，不做逐笔手机确认。Python 默认服从手机会话权限；若用户希望电脑端额外收紧，可设置 `MONEY_MCP_READ_ONLY=1`。

这种授权方式的保护重点不是增加操作摩擦，而是确保每一步成功写入都满足：业务写入与 Journal 入栈原子提交、请求 ID 幂等、撤销前快照冲突检测、全局 LIFO 回退。

### 7.1 写入命令总表

| action | 对应 Use Case | 收益 | 成本 | v1 |
|---|---|---:|---:|---|
| cashflow.create | CreateCashFlowRecordUseCase | 极高 | 低 | 加入 |
| cashflow.update | UpdateCashFlowRecordUseCase | 高 | 低 | 加入 |
| cashflow.delete | DeleteCashFlowRecordUseCase | 高 | 低 | 加入 |
| transfer.create | CreateTransferRecordUseCase | 高 | 低 | 加入 |
| transfer.update | UpdateTransferRecordUseCase | 高 | 低 | 加入 |
| transfer.delete | DeleteTransferRecordUseCase | 高 | 低 | 加入 |
| journal.undo_latest | AiJournaledLedgerUseCase + 现有恢复/修改/删除 Use Case | 极高 | 中 | 加入 |
| 余额核对写入 | 余额核对 Use Case | 中 | 语义风险高 | 不加入 |
| 手动余额调整写入 | 余额调整 Use Case | 中 | 语义风险高 | 不加入 |
| 账户生命周期写入 | 账户 Use Case | 对当前目标低 | 高 | 不加入 |
| 提醒写入 | 提醒 Use Case | 对当前目标低 | 中 | 不加入 |
| 批量写入 | 当前没有安全批量编排 | 中 | 极高 | 不加入 |

### 7.2 所有写入的幂等性

Python 为每次 MCP 工具调用生成一次 UUID requestId；手机把新增记录的 operationId 派生为 `ai:<requestId>`，并在 Journal 表上对 requestId 建唯一索引。

同一次调用发生网络重试时，必须复用原 requestId。新增、修改、删除以及撤销都因此具备网络重试幂等性。

如果 Python 已经发送写入但没有收到响应：

- 可以使用相同 requestId 自动重试一次。
- 不能因为响应未知而生成新的 requestId。
- 手机应返回原先已写入的结果，而不是再次新增。

这里防止的是网络重试重复记账，不是“用户再次明确发起相同业务操作”。

### 7.3 更新和删除的并发控制

更新或删除之前，Python 先调用 records.get，并把得到的 expectedUpdatedAt 放入写入请求。

如果这段时间内用户已经在手机上修改了记录，现有 Use Case 返回冲突。

发生 CONFLICT 时：

- Python 不允许静默覆盖。
- Python 向 AI 返回“记录已变化”。
- AI 必须重新读取记录，再决定是否提出新的修改。

### 7.4 删除和恢复

所有删除仍是软删除。

手机删除成功后，把现有 LedgerUndoToken 保存在对应的持久化 Journal 项中，不把恢复责任交给 Python 临时内存。

撤销删除时由手机从栈顶 Journal 取出 Token，并继续调用 RestoreLedgerRecordUseCase。Python 和 AI 不需要持有或理解恢复 Token。

### 7.5 Journal 与栈式撤销

`ai_mutation_journal` 是 Room v19 新增的设备本地表，不进入明文备份。每个成功的 AI 新增、修改、删除保存：

- 唯一 requestId、会话与客户端名称。
- 操作类型、记录类型和记录 ID。
- 操作前与操作后的完整、类型化语义快照。
- 删除操作所需的 LedgerUndoToken。
- applied、undone 或 discarded 状态。

账本修改和 Journal INSERT 由同一个外层 Room 事务包裹；任何一方失败都整体回滚。撤销只处理最新的 applied 项：先把当前存储记录与“操作后快照”比较，比较时忽略 updatedAt，但保留业务字段、operationId、createdAt 与删除状态。忽略 updatedAt 是为了让“撤销较新修改后生成的新修订时间”仍能继续逐层撤销较老修改；业务字段不一致则说明记录后来被改过，撤销必须停止。

撤销动作本身不再次压入 Journal，而是把原项标记为 undone。若用户确认冲突后的当前数据应当保留，可以在手机端把栈顶标记为 discarded，再继续处理更早的项。导入或回滚备份会在同一替换事务内清空 Journal，因为旧快照不能作用于替换后的账本。

## 8. Python 对 AI 暴露的 MCP 工具

Python MCP 工具比手机 action 更适合 AI 使用。

| MCP 工具 | 使用的手机 action | 为什么需要 | 收益和成本 |
|---|---|---|---|
| money_get_context | service.context | 告诉 AI 金额、时间和账本语义 | 高收益、低成本 |
| money_list_accounts | accounts.list | 所有账户范围操作的基础 | 极高收益、低成本 |
| money_query_records | records.list | 核心历史查询，支持过滤和有界分页 | 极高收益、中成本 |
| money_get_record | records.get | 精确修改和删除前必须读取 | 高收益、低成本 |
| money_financial_summary | ledger.summary | 为财务分析提供可靠期间数据和当前账户余额 | 极高收益、桥接成本低 |
| money_get_journal | journal.list | 让 AI 和用户看到可撤销栈及历史状态 | 高收益、低成本 |
| money_create_cash_flow | 账户解析 + cashflow.create | 最常见的 AI 辅助记账 | 极高收益、中成本 |
| money_update_cash_flow | records.get + cashflow.update | 修正普通收支 | 高收益、中成本 |
| money_delete_cash_flow | records.get + cashflow.delete | 软删除错误收支 | 高收益、中成本 |
| money_create_transfer | 账户解析 + transfer.create | 覆盖账户间资金移动 | 高收益、中成本 |
| money_update_transfer | records.get + transfer.update | 修正转账细节 | 高收益、中成本 |
| money_delete_transfer | records.get + transfer.delete | 软删除错误转账 | 高收益、中成本 |
| money_undo_last_change | journal.undo_latest | 严格撤销最近一步，冲突时停止 | 极高收益、中成本 |

MCP 工具需要正确声明只读、破坏性、幂等和 closed-world annotations。

这些 annotations 只帮助 AI 客户端展示和判断，不能当作授权。手机会话权限始终是最终权限来源。

### 8.1 账户名称解析

AI 工具可以接受账户 ID 或账户名称。

规则：

1. 同时提供 ID 和名称时，以 ID 为准并验证名称是否匹配。
2. 名称精确规范化后只匹配一个开放账户时，可自动解析。
3. 没有匹配时，返回可用账户列表。
4. 出现多个匹配时返回歧义错误。
5. Python 绝不选择“最相似”的账户。

### 8.2 金额转换

AI 工具接受十进制字符串，例如：

~~~json
{
  "amount": "1234.56"
}
~~~

Python 使用 decimal.Decimal：

- 禁止 Float/Double。
- 拒绝超过手机 currency scale 的小数位。
- 检查 Long 可表示范围。
- 转换为最小货币单位字符串。

发送给手机：

~~~json
{
  "minorUnits": "123456"
}
~~~

### 8.3 时间转换

AI 工具接受 ISO 8601：

~~~text
2026-08-31T14:30:00+08:00
~~~

时间输入必须带 `Z` 或显式 offset。Python 会拒绝无 offset 的时间，AI 可以先通过 `money_get_context` 获取手机时区后再构造明确时间，从而避免电脑和手机时区不同时静默偏移。

所有写入结果返回手机最终保存的 epochMillis；需要展示为手机本地时间时，使用 `money_get_context` 返回的时区转换。

Python 不解析“上周二晚上”这类自然语言。AI 必须先把含糊时间转换成明确 ISO 时间，必要时询问用户。

新增收支或转账没有提供时间时，Python 不用电脑时钟生成时间戳，也不发送 `occurredAt`；手机端在执行 Journal 事务时使用手机当前时间。显式提供历史时间时才由 Python 转换并发送。这样手机始终是账本时间的权威来源，也避免两端仅相差几秒时被手机误判为未来记录。

### 8.4 有界分页

money_query_records 提供：

- page_size：最大 100。
- cursor：可选游标对象。

每次只请求一页；结果仍有更多数据时返回 nextCursor，AI 将该对象原样传回下一次调用。这样没有隐藏的无界循环，也不会意外导出完整账本。

### 8.5 业务含义筛选

App 历史页、`money_query_records` 与 `money_financial_summary` 共用同一套派生筛选，不新增记录分类字段：

- `all`：不限制业务含义。
- `daily_expense`：日常账户（`funding`）上的现金流支出。
- `investment_pnl`：投资账户（`investment`）上的全部非零对账差额。
- `investment_gain`：投资账户上金额为正的对账差额。
- `investment_loss`：投资账户上金额为负的对账差额。

业务含义由记录类型、金额方向和账户类型在查询时联合推导。把账户从日常改为投资会按现有领域规则追溯解释其历史，但不会改写任何账本记录。它与关键词、账户、时间、金额、记录类型和金额方向按“且”组合；例如同时选择“投资收益”和“金额减少”会得到空结果，这是条件真实交集，不在客户端静默改写用户选择。

这样设计的收益明显高于维护成本：AI 和用户可以直接询问日常消费与投资盈亏，而实现只增加一组共享查询条件和枚举，不引入分类表、逐笔标注、备份格式升级或数据库迁移。

## 9. Python 工程设计

### 9.1 目录结构

Python bridge 已迁移到独立的 [money-client-skill](https://github.com/shihuaidexianyu/money-client-skill) 仓库：

~~~text
money-client-skill/
├── SKILL.md
├── README.md
├── agents/
├── assets/
├── references/
└── scripts/
    ├── pyproject.toml
    ├── uv.lock
    ├── money_bridge_core.py
    ├── money_client_cli.py
    ├── money_mcp.py
    └── test_*.py
~~~

桥接实现随自包含 skill 独立发布，`scripts/` 将协议核心、确定性 CLI 与 MCP stdio 入口分开维护，避免把配置、协议和工具代码堆进单个文件。

### 9.2 依赖策略

- Python 3.10 或更高版本。
- 使用 uv 作为文档中的默认安装和运行工具。
- 使用官方 MCP Python SDK v2。
- 提交 uv.lock，锁定精确解析版本。
- MCP major version 不能无上限。
- 手机 TCP、配置和 Decimal 转换优先使用 Python 标准库。
- 没有明确需求时不增加 HTTP、重试或二维码依赖。
- 所有日志写 stderr。
- 日志不得包含 Token 和配对码。

示意代码：

~~~python
from mcp.server import MCPServer

mcp = MCPServer(
    "Money",
    instructions="通过局域网连接用户手机上的 Money 账本。",
)

@mcp.tool()
async def money_list_accounts(include_hidden: bool = True) -> dict:
    """查询手机账本中的账户及精确余额。"""
    return await phone_client.call(
        "accounts.list",
        {"includeHidden": include_hidden},
    )

if __name__ == "__main__":
    mcp.run()
~~~

这只是结构示例。正式实现必须按照 uv.lock 中固定 SDK 版本的实际 API 编写，不能盲目复制示意代码。

### 9.3 配置文件

配置建议保存到当前系统用户目录，例如 Windows：

~~~text
%APPDATA%/money-mcp/connection.json
~~~

内容：

~~~json
{
  "host": "192.168.1.20",
  "port": 43127,
  "token": "temporary-token",
  "session_id": "uuid",
  "allow_write": true,
  "expires_at": 1788192000000
}
~~~

要求：

- 只保存当前临时会话。
- pair 成功后原子写入。
- Token 过期后 serve 返回明确错误。
- 新配对覆盖旧临时配置。
- 日志和异常不得回显 Token。

### 9.4 命令行

v1 提供：

~~~text
python money_mcp.py pair --host <ip> --port <port> --code <8位配对码>
python money_mcp.py serve
~~~

含义：

- pair：完成手机配对并保存临时 Token。
- serve：按手机会话授予的权限启动 MCP stdio Server。

如果手机会话是只读，手机必须返回 WRITE_DISABLED。

如果设置 `MONEY_MCP_READ_ONLY=1`，Python 应在发送网络请求前拒绝写工具，即使手机会话可写。

## 10. Android 工程设计

### 10.1 包结构

~~~text
app/src/main/java/com/shihuaidexianyu/money/
├── lan/
│   ├── MoneyLanService.kt
│   ├── MoneyLanServer.kt
│   ├── MoneyLanProtocol.kt
│   ├── MoneyLanRequestRouter.kt
│   └── MoneyLanRuntime.kt
├── data/dao/entity/repository/
│   └── AiMutationJournal*
├── domain/repository/
│   └── AiMutationJournalRepository.kt
├── domain/usecase/
│   └── AiJournaledLedgerUseCase.kt
└── ui/lan/
    ├── LanMcpScreen.kt
    └── LanMcpViewModel.kt
~~~

Journal Repository 和 Use Case 仍由 DataGraph、UseCaseGraph 与 MoneyAppContainer 手动构建。

不引入 Hilt、Dagger 或 Koin。

前台 Service 通过 MoneyApplication.container 获取依赖并启动 MoneyLanServer；RequestRouter 在每次业务请求前检查 StartupMigrationCoordinator.isReady。

Server 持有：

- CoroutineScope。
- ServerSocket。
- 当前会话。
- 最多 4 个并发连接的信号量。
- 一次性配对码、临时 Token 与会话到期时间。

### 10.2 领域边界

MoneyLanRequestRouter 可以依赖：

- 专用 MoneyLanQueryRepository。
- 明确的查询 Use Case。
- 现有写入 Use Case。

它不能依赖：

- MoneyDatabase。
- Room DAO。
- 任意 SQL 执行器。
- Android UI ViewModel。

写入映射：

~~~text
cashflow.create/update/delete  ┐
transfer.create/update/delete  ├→ AiJournaledLedgerUseCase → 现有写入 Use Case + Journal
journal.undo_latest            ┘                        → 现有逆操作 Use Case
~~~

由此保留：

- 关闭账户写入保护。
- 记录时间不能早于开户时间。
- Long 金额运算。
- operationId 幂等。
- expectedUpdatedAt 乐观并发。
- 软删除和身份校验恢复。
- 写入后的账户活动状态刷新。
- 对账差额固定不重算。

### 10.3 Manifest 和权限

需要增加：

~~~xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
~~~

内部 Service：

~~~xml
<service
    android:name=".lan.MoneyLanService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
~~~

POST_NOTIFICATIONS 已经存在。

只有实现 mDNS 后才需要 CHANGE_WIFI_MULTICAST_STATE。

增加 INTERNET 权限后，项目安全说明必须从“没有 INTERNET 权限”修改为：

> Money 没有互联网后端，不主动访问外网；只有用户明确开启时才提供临时局域网账本接口。

当项目未来 target SDK 升级到 API 37 时，必须在同一次升级中适配 ACCESS_LOCAL_NETWORK 运行时权限。

### 10.4 服务状态

~~~text
Stopped
  → Starting
  → Running(
       host,
       port,
       pairingState,
       scope,
       expiresAt,
       clientName?
     )
  → Error(message)
  → Stopped
~~~

启动后可先展示端口；每个业务请求都会检查 `StartupMigrationCoordinator` 已 Ready。没有可显示的私有/链路本地 IPv4 地址时，页面明确提示地址不可用。Service 内部只保留一个 Server 实例。

停止条件：

- 用户在页面或通知中点击停止。
- 设定时长到期。
- Service 被销毁。
- App 进程被停止。

v1 不注册网络变化回调；网络切换后若旧地址失效，用户停止并重新开启本次会话即可，旧 Token 同时失效。

服务不允许：

- BOOT_COMPLETED 自动恢复。
- WorkManager 启动。
- START_STICKY 恢复。
- 进程重启后复用旧 Token。

### 10.5 前台通知

通知渠道：

~~~text
局域网 AI 接口
~~~

只读示例：

~~~text
局域网 AI 接口已开启
只读 · 192.168.1.20:43127 · 剩余 14 分钟
~~~

可写示例：

~~~text
局域网 AI 接口已开启
已连接 Desktop-PC · 可读写
~~~

通知操作：

- 停止。

通知不能显示：

- 余额。
- 记录备注。
- 配对码。
- Token。

### 10.6 设置页面

在设置的数据或隐私区域增加：

- 标题：局域网 AI 接口。
- 停止时副标题：通过电脑上的 MCP 脚本访问本机账本。
- 运行时副标题：已开启 · 只读，或已开启 · 允许修改。

详情页面包含：

1. 可信局域网警告。
2. 会话级“允许 AI 修改账目”开关，默认开启，可在启动前改成只读。
3. 固定最长 4 小时并支持随时手动停止；低于 Android dataSync 前台服务的 6 小时后台配额。
4. 启动/停止按钮。
5. 运行时显示 IP、端口、配对码和到期时间。
6. 配对成功后显示客户端名称。
7. 可复制的配对命令模板。
8. 持久化 Journal 最近记录、可撤销步数、撤销栈顶和放弃冲突项入口。

“允许修改”的说明必须明确：

> 在本次临时会话中，配对电脑可以直接执行普通收支和转账修改，不会逐笔弹出手机确认。

这必须是用户可见的产品行为，不能隐藏在技术文档中。

## 11. 数据格式

### 11.1 金额

手机协议金额字段使用最小货币单位字符串，例如：

~~~json
{"amount": "123456"}
~~~

领域层仍然使用 Long。

协议不接受 JSON 浮点金额。

手机必须拒绝：

- 超过 Long 范围。
- 与 scale 不匹配的小数位。
- 新增收支或转账中的零金额/负金额。
- 手动伪造的 NaN、Infinity 等非十进制值。

### 11.2 时间

MLP 使用 JSON 整数形式的 epoch millis。当前时间数量级远低于 JavaScript 安全整数上限；Python 端仍使用任意精度整数处理。

所有时间范围统一为：

~~~text
[startInclusive, endExclusive)
~~~

Python 负责把带 `Z` 或显式时区偏移的 ISO 8601 转换为 epoch millis；无偏移输入直接拒绝。AI 可以先调用 `money_get_context` 获取手机时区。

### 11.3 ID 和修订版本

- ID、updatedAt 和 deletedAt 使用 JSON 整数；金额 Long 单独使用十进制字符串。
- 账本记录身份必须同时包含类型和 ID。
- operationId 是不透明字符串；AI 新增记录使用由 requestId 稳定派生的 `ai:<requestId>`。
- updatedAt 和 deletedAt 使用 epoch millis。
- 历史游标是由 `occurredAt`、`sourceOrder`、`recordId` 构成的对象；Python 和 AI 只负责原样传回。
- 恢复 Token 只保存在手机 Journal 中，不暴露给 Python 或 AI。

## 12. 限流和资源上限

当前实现的硬限制：

- 最多 4 个同时连接。
- 每分钟最多 60 个写入或撤销请求，足够正常 Agent 使用并阻止失控循环。
- 历史分页最大 100。
- 请求和响应帧最大 256 KiB。
- 单 Socket 超时 15 秒。
- 同一临时会话只允许一台电脑完成配对。
- 配对码最多失败 5 次。

历史分页和连接上限用于控制上下文、耗电与并发。写入限流只维护一个短期时间戳队列，成本较低，并能阻止 Agent 失控循环。

## 13. 测试方案

### 13.1 首版已落地的自动测试

Android 侧已经覆盖 Journal 最关键的数据安全路径：连续修改逐层撤销、撤销新增、撤销删除、语义冲突停止、写入 requestId 幂等和撤销 requestId 幂等。Room 仪器测试已增加 `18 → 19` 的建表与索引校验。

Python 侧已经覆盖：Decimal 精确金额转换、带偏移时间转换、拒绝无时区时间、临时配置读写，以及模拟响应丢失后使用相同 requestId 重试。另通过官方 MCP SDK 的内存客户端校验全部工具名称与关键 annotations。

### 13.2 后续发布前应继续补强

首版代码已经可运行，但网络边界仍值得增加独立的 Android 测试：

- 非法或超限帧、非法 JSON 与未知 action。
- 配对到期、一次性行为、失败锁定、Token、只读权限和写入限流。
- Router 各查询 action 的参数与稳定错误映射。
- 前台服务、通知停止按钮及 loopback Socket 仪器测试。
- 设置页面的大字体和无障碍语义测试。

Python 后续补强项包括账户名称歧义、只读环境变量、游标透传和手机错误映射。若协议继续扩展，再引入两端共用的 JSON Fixture；当前 v1 命令面较小，先避免维护一套尚未消费的 Fixture 目录。

### 13.3 真实设备验收

在真实 Android 手机和 Windows 电脑上：

1. 启动只读会话。
2. 完成 Python 配对。
3. 连接目标 AI 客户端。
4. 查询账户并与 App 余额对比。
5. 查询有限历史范围并使用游标继续。
6. 对比期间汇总和 App/领域计算结果。
7. 确认只读模式拒绝写入。
8. 以可写模式重启手机服务和 Python MCP。
9. 新增、修改、删除一笔测试收支，并用 Journal 逐步撤销。
10. 新增、修改、删除一笔测试转账，并用 Journal 逐步撤销。
11. 检查相关账户余额和活动时间刷新。
12. 手动停止服务，确认旧 Token 被拒绝。
13. 验证 4 小时到期和 Android 前台服务超时回调都会安全停服。

提交前运行：

~~~powershell
.\gradlew.bat test
uv --directory C:\path\to\money-client-skill\scripts run python -m unittest discover
~~~

仪器测试需要设备或模拟器。

## 14. 实施阶段

### 阶段 0：Python 桥接和客户端验证（核心完成，目标客户端待验收）

先用假的手机端点实现 Python MCP Server，并验证目标 AI 客户端可以：

- 通过 stdio 启动它。
- 正确列出工具。
- 调用带类型参数的工具。
- 接收结构化结果。
- 正确显示工具错误。

为什么先做：在 Android 网络开发开始前验证最外层用户链路。

完成标准：

- AI 客户端能启动锁定版本的 SDK Server。
- tools/list 正常。
- tools/call 正常。
- stdout 保持协议纯净。
- Python 只读和写入开关行为正确。

### 阶段 1：端到端最小可用链路（代码完成，真实设备待验收）

实现：

- 前台服务。
- 设置页面。
- 长度前缀 Socket Server。
- 配对和临时 Token。
- 账户查询。
- 历史查询。
- 记录详情。
- 期间汇总和当前余额。
- 生命周期和资源上限。

这一阶段已经能够提供有价值的 AI 账本查询，并且没有写入风险。

### 阶段 2：Journal 化普通账本写入（完成）

增加：

- 收支与转账的新增、修改和删除。
- Room v19 持久化 Journal。
- 严格 LIFO、快照冲突检测和手机端放弃冲突项。
- 手机写入模式提示。
- Python 可选只读模式。
- 幂等测试。
- 乐观并发测试。

### 阶段 3：按真实分析需求补充专用统计

候选包括趋势桶和大额收支排行。只有实际 AI 使用频率证明收益明显时才增加，避免工具面无边界扩张。

### 阶段 4：按真实需求选择增强项

以下功能分别评估，不默认一起开发：

- TLS。
- mDNS。
- 可撤销的永久配对电脑。
- Windows/macOS/Linux 可执行文件。
- 读取访问审计。
- 余额核对写入。
- 手动余额调整写入。

每一项都必须独立证明收益大于维护成本。

## 15. 明确拒绝的捷径

### 15.1 Python 直接读取或修改数据库

拒绝原因：

- 需要复制或暴露数据库。
- 绕过启动迁移 Ready 门禁。
- 绕过所有领域写入规则。
- 容易与手机并发修改冲突。

### 15.2 通用 SQL 命令

拒绝原因：

- AI 没有合理需求必须依赖任意 SQL。
- 会暴露内部表结构。
- 可能绕过软删除和业务约束。
- 维护和安全风险远大于收益。

### 15.3 Router 直接调用 Repository 写入

拒绝原因：

- 可能绕过关闭账户保护。
- 可能跳过活动状态刷新。
- 可能跳过通知同步。
- 可能破坏 operationId 幂等。
- 可能破坏固定对账差额语义。

### 15.4 自然语言 action

不提供：

~~~text
execute_text("把昨天那笔钱改一下")
~~~

自然语言理解属于 AI 客户端。手机只接收类型明确、可校验、可测试的命令。

### 15.5 自定义加密

v1 是带明确警告的可信局域网明文协议。

未来如果需要安全传输，使用标准 TLS，不设计自己的密码系统。

### 15.6 返回完整账本

历史必须分页，分析优先使用聚合接口。

原因：

- 控制隐私暴露。
- 控制 AI 上下文大小。
- 控制手机延迟和耗电。
- 防止意外导出所有数据。

## 16. 实施时必须同步修改的文档

功能真正实现后：

1. 更新 AGENTS.md 和 CLAUDE.md。
2. 把“Manifest 没有 INTERNET 权限”改为“没有互联网后端，只有用户主动开启的局域网服务”。
3. 记录新的 `lan/` 结构和独立 `money-client-skill` 仓库。
4. 在独立仓库增加 Python 测试命令。
5. 更新 App 隐私说明，明确局域网会话会向配对电脑提供精确账本数据。
6. 在 `money-client-skill/README.md` 说明 uv 安装、配对、AI 客户端配置和排错。
7. 明确 Python 不连接任何云端，只访问配置中的手机地址。
8. 如果后续增加 Room 表，必须执行数据库版本、Migration、Schema 和仪器迁移测试流程。

## 17. 最终建议

v1 应被视为两个边界清晰的小产品：

1. **Money LAN Endpoint**：由用户临时开启、使用长度前缀 JSON、调用现有领域能力的局域网服务。
2. **Money MCP Bridge**：运行在电脑本地，通过 stdio 给 AI 提供标准 MCP 的 Python 程序。

最先交付的可用版本应是：

> 账户/流水查询、手机计算的财务汇总、Journal 化的普通收支与转账修改，以及可以逐步执行的严格 LIFO 撤销。

在真实使用证明有需要之前，不应加入：

- Android 远程 MCP。
- OAuth。
- 永久配对。
- TLS。
- mDNS。
- 桌面多平台打包。
- 批量修改。
- 账户生命周期管理。

这种划分保留了方案最核心的价值：AI 可以分析和维护手机里的真实账本；同时把易变化的 MCP 生态放在 Python 侧，把账本正确性牢牢保留在 Android 现有领域层。

## 18. 主要参考资料

- [MCP 官方 Python SDK](https://github.com/modelcontextprotocol/python-sdk)
- [MCP Python SDK：运行 stdio Server](https://github.com/modelcontextprotocol/python-sdk/blob/main/docs/run/index.md)
- [MCP 2026-07-28 stdio 传输规范](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/basic/transports/stdio.mdx)
- [Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 前台服务启动要求](https://developer.android.com/develop/background-work/services/fgs/launch)
- [Android 局域网权限](https://developer.android.com/privacy-and-security/local-network-permission)
