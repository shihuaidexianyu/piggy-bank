from __future__ import annotations

import argparse
import asyncio
import os
import socket
from pathlib import Path
from typing import Any, Literal

from mcp.server import MCPServer
from mcp.types import ToolAnnotations

from money_bridge_core import (
    MoneyBridgeError,
    MoneyPhoneClient,
    iso_to_millis,
    load_config,
    major_to_minor,
    pair_phone,
    resolve_account,
)


mcp = MCPServer(
    "Money LAN",
    instructions=(
        "连接用户手机上的离线 Money 账本。金额输入使用十进制主货币单位，例如 12.34；"
        "返回协议中的金额字段使用最小货币单位整数字符串。修改会自动进入手机端 AI Journal，"
        "可用 money_undo_last_change 严格按栈顶逐步撤销。"
    ),
)

READ_ONLY = ToolAnnotations(read_only_hint=True, idempotent_hint=True, open_world_hint=False)
CREATE = ToolAnnotations(read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False)
UPDATE = ToolAnnotations(read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False)
DELETE = ToolAnnotations(read_only_hint=False, destructive_hint=True, idempotent_hint=False, open_world_hint=False)
UNDO = ToolAnnotations(read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False)


def phone_client() -> MoneyPhoneClient:
    return MoneyPhoneClient(load_config())


def require_write(client: MoneyPhoneClient) -> None:
    if os.environ.get("MONEY_MCP_READ_ONLY", "").lower() in {"1", "true", "yes"}:
        raise MoneyBridgeError("Python 桥接程序以只读模式运行")
    if not client.config.allow_write:
        raise MoneyBridgeError("手机端本次会话未允许写入")


def without_none(**values: Any) -> dict[str, Any]:
    return {key: value for key, value in values.items() if value is not None}


@mcp.tool(title="读取 Money 接口上下文", annotations=READ_ONLY)
async def money_get_context() -> dict[str, Any]:
    """读取手机时区、金额精度、本次会话权限和 Journal 策略。"""
    return await phone_client().request("service.context")


@mcp.tool(title="列出 Money 账户", annotations=READ_ONLY)
async def money_list_accounts(include_closed: bool = False) -> dict[str, Any]:
    """列出账户、当前余额、账户类型以及隐藏/关闭状态。"""
    return await phone_client().request("accounts.list", {"includeClosed": include_closed})


@mcp.tool(title="查询 Money 账目", annotations=READ_ONLY)
async def money_query_records(
    keyword: str = "",
    record_types: list[Literal["cash_flow", "transfer", "balance_update", "balance_adjustment"]] | None = None,
    account: int | str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
    min_amount: str | None = None,
    max_amount: str | None = None,
    amount_direction: Literal["all", "increase", "decrease"] = "all",
    business_semantic: Literal[
        "all", "daily_expense", "investment_pnl", "investment_gain", "investment_loss"
    ] = "all",
    cursor: dict[str, int] | None = None,
    limit: int = 50,
) -> dict[str, Any]:
    """按条件分页查询账目；可按日常消费或投资盈亏等业务含义筛选。"""
    client = phone_client()
    account_id = await resolve_account(client, account) if account is not None else None
    arguments = without_none(
        keyword=keyword,
        recordTypes=record_types or [],
        accountId=account_id,
        startInclusive=iso_to_millis(start_time) if start_time else None,
        endExclusive=iso_to_millis(end_time) if end_time else None,
        minAmount=major_to_minor(min_amount, allow_zero=True) if min_amount is not None else None,
        maxAmount=major_to_minor(max_amount, allow_zero=True) if max_amount is not None else None,
        amountDirection=amount_direction,
        businessSemantic=business_semantic,
        cursor=cursor,
        limit=limit,
    )
    return await client.request("records.list", arguments)


@mcp.tool(title="读取一条 Money 记录", annotations=READ_ONLY)
async def money_get_record(
    kind: Literal["cash_flow", "transfer", "balance_update", "balance_adjustment"],
    record_id: int,
) -> dict[str, Any]:
    """读取账本记录的完整字段和 updatedAt；修改前建议先调用此工具。"""
    return await phone_client().request("records.get", {"kind": kind, "recordId": record_id})


@mcp.tool(title="统计 Money 财务状态", annotations=READ_ONLY)
async def money_financial_summary(
    account: int | str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
    business_semantic: Literal[
        "all", "daily_expense", "investment_pnl", "investment_gain", "investment_loss"
    ] = "all",
) -> dict[str, Any]:
    """统计指定时间和业务含义的账目，并返回当前总资产和账户余额。"""
    client = phone_client()
    account_id = await resolve_account(client, account) if account is not None else None
    return await client.request(
        "ledger.summary",
        without_none(
            accountId=account_id,
            startInclusive=iso_to_millis(start_time) if start_time else None,
            endExclusive=iso_to_millis(end_time) if end_time else None,
            businessSemantic=business_semantic,
        ),
    )


@mcp.tool(title="查看 AI Journal", annotations=READ_ONLY)
async def money_get_journal(limit: int = 50) -> dict[str, Any]:
    """查看最近的 AI 数据修改及其可撤销、已撤销或已保留状态。"""
    return await phone_client().request("journal.list", {"limit": limit})


@mcp.tool(title="撤销最近一步 AI 修改", annotations=UNDO)
async def money_undo_last_change() -> dict[str, Any]:
    """严格按手机端 Journal 栈顶撤销一步；若记录后来被改过则停止而不覆盖。"""
    client = phone_client()
    require_write(client)
    return await client.request("journal.undo_latest")


@mcp.tool(title="新增收支", annotations=CREATE)
async def money_create_cash_flow(
    account: int | str,
    direction: Literal["inflow", "outflow"],
    amount: str,
    note: str = "",
    occurred_at: str | None = None,
) -> dict[str, Any]:
    """新增一笔收入或支出。amount 使用主货币单位十进制字符串，例如 12.34。"""
    client = phone_client()
    require_write(client)
    account_id = await resolve_account(client, account)
    return await client.request(
        "cashflow.create",
        without_none(
            accountId=account_id,
            direction=direction,
            amount=major_to_minor(amount),
            note=note,
            occurredAt=iso_to_millis(occurred_at) if occurred_at is not None else None,
        ),
    )


@mcp.tool(title="修改收支", annotations=UPDATE)
async def money_update_cash_flow(
    record_id: int,
    account: int | str | None = None,
    direction: Literal["inflow", "outflow"] | None = None,
    amount: str | None = None,
    note: str | None = None,
    occurred_at: str | None = None,
) -> dict[str, Any]:
    """修改收支；未提供的字段自动沿用手机中的当前值，并自动携带并发版本。"""
    client = phone_client()
    require_write(client)
    current = await client.request("records.get", {"kind": "cash_flow", "recordId": record_id})
    if current.get("deletedAt") is not None:
        raise MoneyBridgeError("该记录已经删除")
    account_id = await resolve_account(client, account) if account is not None else int(current["accountId"])
    return await client.request(
        "cashflow.update",
        {
            "recordId": record_id,
            "accountId": account_id,
            "direction": direction or current["direction"],
            "amount": major_to_minor(amount) if amount is not None else current["amount"],
            "note": current.get("note", "") if note is None else note,
            "occurredAt": iso_to_millis(occurred_at) if occurred_at else int(current["occurredAt"]),
            "expectedUpdatedAt": int(current["updatedAt"]),
        },
    )


@mcp.tool(title="删除收支", annotations=DELETE)
async def money_delete_cash_flow(record_id: int) -> dict[str, Any]:
    """软删除一笔收支并写入 Journal，可用撤销工具恢复。"""
    client = phone_client()
    require_write(client)
    current = await client.request("records.get", {"kind": "cash_flow", "recordId": record_id})
    return await client.request(
        "cashflow.delete",
        {"recordId": record_id, "expectedUpdatedAt": int(current["updatedAt"])},
    )


@mcp.tool(title="新增转账", annotations=CREATE)
async def money_create_transfer(
    from_account: int | str,
    to_account: int | str,
    amount: str,
    note: str = "",
    occurred_at: str | None = None,
) -> dict[str, Any]:
    """新增账户间转账。amount 使用主货币单位十进制字符串。"""
    client = phone_client()
    require_write(client)
    from_id = await resolve_account(client, from_account)
    to_id = await resolve_account(client, to_account)
    return await client.request(
        "transfer.create",
        without_none(
            fromAccountId=from_id,
            toAccountId=to_id,
            amount=major_to_minor(amount),
            note=note,
            occurredAt=iso_to_millis(occurred_at) if occurred_at is not None else None,
        ),
    )


@mcp.tool(title="修改转账", annotations=UPDATE)
async def money_update_transfer(
    record_id: int,
    from_account: int | str | None = None,
    to_account: int | str | None = None,
    amount: str | None = None,
    note: str | None = None,
    occurred_at: str | None = None,
) -> dict[str, Any]:
    """修改转账；未提供的字段自动沿用当前值，并自动携带并发版本。"""
    client = phone_client()
    require_write(client)
    current = await client.request("records.get", {"kind": "transfer", "recordId": record_id})
    if current.get("deletedAt") is not None:
        raise MoneyBridgeError("该记录已经删除")
    from_id = await resolve_account(client, from_account) if from_account is not None else int(current["fromAccountId"])
    to_id = await resolve_account(client, to_account) if to_account is not None else int(current["toAccountId"])
    return await client.request(
        "transfer.update",
        {
            "recordId": record_id,
            "fromAccountId": from_id,
            "toAccountId": to_id,
            "amount": major_to_minor(amount) if amount is not None else current["amount"],
            "note": current.get("note", "") if note is None else note,
            "occurredAt": iso_to_millis(occurred_at) if occurred_at else int(current["occurredAt"]),
            "expectedUpdatedAt": int(current["updatedAt"]),
        },
    )


@mcp.tool(title="删除转账", annotations=DELETE)
async def money_delete_transfer(record_id: int) -> dict[str, Any]:
    """软删除一笔转账并写入 Journal，可用撤销工具恢复。"""
    client = phone_client()
    require_write(client)
    current = await client.request("records.get", {"kind": "transfer", "recordId": record_id})
    return await client.request(
        "transfer.delete",
        {"recordId": record_id, "expectedUpdatedAt": int(current["updatedAt"])},
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Money 手机账本 MCP 桥接程序")
    subcommands = parser.add_subparsers(dest="command")
    pair = subcommands.add_parser("pair", help="使用手机显示的一次性配对码建立连接")
    pair.add_argument("--host", required=True)
    pair.add_argument("--port", required=True, type=int)
    pair.add_argument("--code", required=True)
    pair.add_argument("--name", default=socket.gethostname())
    pair.add_argument("--config", type=Path)
    subcommands.add_parser("serve", help="通过 stdio 启动 MCP 服务")
    return parser


def main() -> None:
    parser = build_parser()
    arguments = parser.parse_args()
    if arguments.command == "pair":
        try:
            config, path = asyncio.run(
                pair_phone(
                    arguments.host,
                    arguments.port,
                    arguments.code,
                    arguments.name,
                    arguments.config,
                ),
            )
        except MoneyBridgeError as error:
            parser.exit(2, f"配对失败：{error}\n")
        mode = "可读写" if config.allow_write else "只读"
        print(f"配对成功（{mode}），配置已保存到 {path}")
        return
    if arguments.command not in {None, "serve"}:
        parser.error("未知命令")
    mcp.run()


if __name__ == "__main__":
    main()
