import asyncio
import json
import struct
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from money_bridge_core import (
    ConnectionConfig,
    MoneyBridgeError,
    MoneyPhoneClient,
    iso_to_millis,
    load_config,
    major_to_minor,
    save_config,
)


class MoneyBridgeCoreTest(unittest.TestCase):
    def test_major_to_minor_is_exact(self) -> None:
        self.assertEqual("1234", major_to_minor("12.34"))
        self.assertEqual("1", major_to_minor("0.01"))
        with self.assertRaises(MoneyBridgeError):
            major_to_minor("1.001")
        with self.assertRaises(MoneyBridgeError):
            major_to_minor("NaN")

    def test_iso_to_millis_accepts_offset_and_z(self) -> None:
        expected = int(datetime(2026, 8, 31, 12, 0, tzinfo=timezone.utc).timestamp() * 1000)
        self.assertEqual(expected, iso_to_millis("2026-08-31T20:00:00+08:00"))
        self.assertEqual(expected, iso_to_millis("2026-08-31T12:00:00Z"))
        with self.assertRaises(MoneyBridgeError):
            iso_to_millis("2026-08-31T20:00:00")

    def test_config_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "connection.json"
            config = ConnectionConfig(
                host="192.168.1.9",
                port=12345,
                token="secret",
                session_id="session",
                allow_write=True,
                expires_at=2**63 - 1,
            )
            self.assertEqual(path, save_config(config, path))
            self.assertEqual(config, load_config(path))


class MoneyPhoneClientTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        self.requests = []
        self.connection_count = 0

        async def handle(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
            self.connection_count += 1
            size = struct.unpack(">I", await reader.readexactly(4))[0]
            request = json.loads((await reader.readexactly(size)).decode("utf-8"))
            self.requests.append(request)
            if self.connection_count == 1:
                writer.close()
                await writer.wait_closed()
                return
            response = {
                "version": 1,
                "requestId": request["requestId"],
                "ok": True,
                "data": {"accepted": True},
            }
            payload = json.dumps(response).encode("utf-8")
            writer.write(struct.pack(">I", len(payload)) + payload)
            await writer.drain()
            writer.close()
            await writer.wait_closed()

        self.server = await asyncio.start_server(handle, "127.0.0.1", 0)
        port = self.server.sockets[0].getsockname()[1]
        self.client = MoneyPhoneClient(
            ConnectionConfig(
                host="127.0.0.1",
                port=port,
                token="temporary-token",
                session_id="session",
                allow_write=True,
                expires_at=2**63 - 1,
            ),
        )

    async def asyncTearDown(self) -> None:
        self.server.close()
        await self.server.wait_closed()

    async def test_unknown_response_retry_reuses_request_id(self) -> None:
        result = await self.client.request(
            "cashflow.create",
            {"amount": "1234"},
            request_id="stable-request",
        )

        self.assertEqual({"accepted": True}, result)
        self.assertEqual(2, len(self.requests))
        self.assertEqual({"stable-request"}, {request["requestId"] for request in self.requests})
        self.assertTrue(all(request["token"] == "temporary-token" for request in self.requests))


if __name__ == "__main__":
    unittest.main()
