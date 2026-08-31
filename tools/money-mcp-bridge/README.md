# Money MCP Python 桥接程序

这个目录把电脑上的标准 MCP stdio 请求转换为手机 Money App 的局域网协议。账本数据和业务写入仍只发生在手机上；Python 不读取数据库文件。

## 1. 配对

1. 在 Money 中打开“设置 → 数据 → 局域网 AI 服务”。
2. 选择本次会话是否允许修改账目，然后启动服务。
3. 在本目录执行手机页面给出的命令：

```powershell
uv run money_mcp.py pair --host 192.168.1.20 --port 41234 --code 12345678
```

配对配置默认保存在当前用户的应用配置目录。Token 只对本次手机会话有效，手机停止服务或会话到期后需要重新配对。可以用环境变量 `MONEY_MCP_CONFIG` 指定其他配置文件。

## 2. 配置 MCP 客户端

将 MCP 客户端的命令设为 `uv`，参数使用绝对路径：

```json
{
  "mcpServers": {
    "money": {
      "command": "uv",
      "args": [
        "run",
        "--project",
        "C:/absolute/path/to/tools/money-mcp-bridge",
        "C:/absolute/path/to/tools/money-mcp-bridge/money_mcp.py",
        "serve"
      ]
    }
  }
}
```

若希望电脑端额外限制为只读，可在该 MCP 配置中加入环境变量 `MONEY_MCP_READ_ONLY=1`。

## 3. 工具语义

- AI 输入金额使用主货币单位十进制字符串，例如 `"12.34"`；脚本会无损转换成手机使用的分。
- 时间必须是带 `Z` 或明确偏移的 ISO 8601 字符串；可先用 `money_get_context` 查看手机时区。
- 新增收支或转账时省略时间，由手机在事务内使用自己的当前时间；只有明确指定历史时间时，电脑才发送 `occurredAt`。这样不会受电脑与手机的时钟偏差影响。
- 历史查询返回 `nextCursor` 时，可把它原样传给下一次 `money_query_records` 调用。
- `business_semantic` 提供派生业务筛选：`daily_expense` 是日常账户的现金支出；`investment_pnl` 是投资账户的全部非零对账差额；`investment_gain` / `investment_loss` 分别只保留正、负投资差额。它不会修改原始账目分类。
- `money_query_records` 与 `money_financial_summary` 使用同一套 `business_semantic` 定义，其他筛选条件与业务筛选按“且”组合。
- 更新和删除工具会先读取最新记录，自动携带 `updatedAt`，降低覆盖并发修改的风险。
- 每次成功的新增、修改、删除都会与账本变化一起原子写入手机 AI Journal。
- `money_undo_last_change` 只撤销全局栈顶一步。如果记录后来被用户或其他流程改过，手机会返回冲突并保持数据不变。
- 查询与统计不进入 Journal。

## 4. 测试

核心协议辅助代码只使用 Python 标准库：

```powershell
python -m unittest test_money_bridge_core.py
python -m py_compile money_bridge_core.py money_mcp.py
uv run python -m unittest test_money_mcp_schema.py
```

`--project` 会使用目录中的 `pyproject.toml` 和 `uv.lock`。MCP 服务使用官方 Python SDK v2，要求 Python 3.10 或更高版本。运行 MCP 时 stdout 是协议通道，不要在脚本中添加普通 `print` 日志。
