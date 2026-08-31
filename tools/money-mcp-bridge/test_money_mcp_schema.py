import unittest
from types import SimpleNamespace
from unittest.mock import patch

from mcp import Client

import money_mcp


class FakePhoneClient:
    def __init__(self) -> None:
        self.config = SimpleNamespace(allow_write=True)
        self.requests: list[tuple[str, dict]] = []

    async def request(self, action: str, arguments: dict | None = None) -> dict:
        payload = arguments or {}
        self.requests.append((action, payload))
        if action == "accounts.list":
            return {
                "accounts": [
                    {"id": 1, "name": "现金"},
                    {"id": 2, "name": "银行"},
                ],
            }
        return {"accepted": True}


class MoneyMcpSchemaTest(unittest.IsolatedAsyncioTestCase):
    async def test_expected_tools_and_annotations_are_exposed(self) -> None:
        async with Client(money_mcp.mcp) as client:
            tools = (await client.list_tools()).tools

        by_name = {tool.name: tool for tool in tools}
        self.assertEqual(
            {
                "money_get_context",
                "money_list_accounts",
                "money_query_records",
                "money_get_record",
                "money_financial_summary",
                "money_get_journal",
                "money_undo_last_change",
                "money_create_cash_flow",
                "money_update_cash_flow",
                "money_delete_cash_flow",
                "money_create_transfer",
                "money_update_transfer",
                "money_delete_transfer",
            },
            set(by_name),
        )
        self.assertTrue(by_name["money_get_context"].annotations.read_only_hint)
        self.assertTrue(by_name["money_list_accounts"].annotations.read_only_hint)
        self.assertFalse(by_name["money_delete_cash_flow"].annotations.read_only_hint)
        self.assertTrue(by_name["money_delete_cash_flow"].annotations.destructive_hint)
        self.assertFalse(by_name["money_create_cash_flow"].annotations.open_world_hint)
        expected_semantics = {
            "all",
            "daily_expense",
            "investment_pnl",
            "investment_gain",
            "investment_loss",
        }
        query_semantic = by_name["money_query_records"].input_schema["properties"]["business_semantic"]
        summary_semantic = by_name["money_financial_summary"].input_schema["properties"]["business_semantic"]
        self.assertEqual(expected_semantics, set(query_semantic["enum"]))
        self.assertEqual(expected_semantics, set(summary_semantic["enum"]))

    async def test_create_tools_omit_unspecified_time_for_phone_side_default(self) -> None:
        fake = FakePhoneClient()
        with patch.object(money_mcp, "phone_client", return_value=fake):
            await money_mcp.money_create_cash_flow(
                account=1,
                direction="outflow",
                amount="12.34",
            )
            await money_mcp.money_create_transfer(
                from_account=1,
                to_account=2,
                amount="5.00",
            )

        create_payloads = [payload for action, payload in fake.requests if action.endswith(".create")]
        self.assertEqual(2, len(create_payloads))
        self.assertTrue(all("occurredAt" not in payload for payload in create_payloads))

    async def test_create_tool_keeps_explicit_offset_time(self) -> None:
        fake = FakePhoneClient()
        with patch.object(money_mcp, "phone_client", return_value=fake):
            await money_mcp.money_create_cash_flow(
                account=1,
                direction="inflow",
                amount="1.00",
                occurred_at="2026-08-31T12:00:00Z",
            )

        payload = next(payload for action, payload in fake.requests if action == "cashflow.create")
        self.assertEqual(1788177600000, payload["occurredAt"])


if __name__ == "__main__":
    unittest.main()
