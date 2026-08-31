from __future__ import annotations

import asyncio
import json
import os
import struct
import tempfile
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


PROTOCOL_VERSION = 1
MAX_FRAME_BYTES = 256 * 1024
DEFAULT_TIMEOUT_SECONDS = 15.0


class MoneyBridgeError(RuntimeError):
    """Base error safe to return to an MCP client."""


class MoneyProtocolError(MoneyBridgeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


@dataclass(frozen=True)
class ConnectionConfig:
    host: str
    port: int
    token: str
    session_id: str
    allow_write: bool
    expires_at: int

    def require_active(self) -> None:
        now = int(datetime.now().timestamp() * 1000)
        if now >= self.expires_at:
            raise MoneyBridgeError("手机会话已过期，请重新运行 pair 命令")


def default_config_path() -> Path:
    override = os.environ.get("MONEY_MCP_CONFIG")
    if override:
        return Path(override).expanduser().resolve()
    if os.name == "nt" and os.environ.get("APPDATA"):
        root = Path(os.environ["APPDATA"])
    else:
        root = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return root / "money-mcp" / "connection.json"


def load_config(path: Path | None = None) -> ConnectionConfig:
    target = path or default_config_path()
    try:
        payload = json.loads(target.read_text(encoding="utf-8"))
        config = ConnectionConfig(**payload)
    except (OSError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise MoneyBridgeError(f"无法读取连接配置 {target}，请先运行 pair 命令") from error
    config.require_active()
    return config


def save_config(config: ConnectionConfig, path: Path | None = None) -> Path:
    target = path or default_config_path()
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=target.parent,
        prefix="connection-",
        suffix=".tmp",
        text=True,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(asdict(config), stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        if os.name != "nt":
            temporary.chmod(0o600)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return target


def major_to_minor(amount: str, *, allow_zero: bool = False) -> str:
    """Convert a decimal major-unit amount to an exact two-decimal minor-unit string."""
    try:
        value = Decimal(amount.strip())
    except (InvalidOperation, AttributeError) as error:
        raise MoneyBridgeError("金额必须是十进制字符串，例如 12.34") from error
    if not value.is_finite() or value < 0 or (value == 0 and not allow_zero):
        raise MoneyBridgeError("金额必须是非负数" if allow_zero else "金额必须大于 0")
    minor = value * 100
    integral = minor.to_integral_value()
    if minor != integral:
        raise MoneyBridgeError("金额最多保留两位小数")
    result = int(integral)
    if result > 9_223_372_036_854_775_807:
        raise MoneyBridgeError("金额超出手机账本可表示范围")
    return str(result)


def iso_to_millis(value: str | None) -> int:
    if value is None:
        return int(datetime.now().timestamp() * 1000)
    normalized = value.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise MoneyBridgeError("时间必须是 ISO 8601，例如 2026-08-31T20:30:00+08:00") from error
    if parsed.tzinfo is None:
        raise MoneyBridgeError("时间必须包含 Z 或明确的时区偏移，例如 +08:00")
    return int(parsed.timestamp() * 1000)


class MoneyPhoneClient:
    def __init__(
        self,
        config: ConnectionConfig,
        timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    ) -> None:
        self.config = config
        self.timeout_seconds = timeout_seconds

    async def request(
        self,
        action: str,
        arguments: dict[str, Any] | None = None,
        *,
        authenticated: bool = True,
        request_id: str | None = None,
    ) -> Any:
        if authenticated:
            self.config.require_active()
        stable_request_id = request_id or str(uuid.uuid4())
        request = {
            "version": PROTOCOL_VERSION,
            "requestId": stable_request_id,
            "action": action,
            "arguments": arguments or {},
        }
        if authenticated:
            request["token"] = self.config.token
        encoded = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if len(encoded) > MAX_FRAME_BYTES:
            raise MoneyBridgeError("请求内容过大")

        last_error: BaseException | None = None
        for attempt in range(2):
            try:
                response = await asyncio.wait_for(self._exchange(encoded), self.timeout_seconds)
                break
            except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError) as error:
                last_error = error
                if attempt == 1:
                    raise MoneyBridgeError("无法连接手机，或手机未在超时前返回结果") from error
        else:
            raise MoneyBridgeError("手机连接失败") from last_error

        if response.get("requestId") != stable_request_id:
            raise MoneyBridgeError("手机响应的 requestId 不匹配")
        if response.get("version") != PROTOCOL_VERSION:
            raise MoneyBridgeError("手机响应的协议版本不匹配")
        if not response.get("ok"):
            error = response.get("error") or {}
            raise MoneyProtocolError(
                str(error.get("code", "UNKNOWN_ERROR")),
                str(error.get("message", "手机端请求失败")),
            )
        return response.get("data")

    async def _exchange(self, encoded: bytes) -> dict[str, Any]:
        reader, writer = await asyncio.open_connection(self.config.host, self.config.port)
        try:
            writer.write(struct.pack(">I", len(encoded)))
            writer.write(encoded)
            await writer.drain()
            size = struct.unpack(">I", await reader.readexactly(4))[0]
            if size < 1 or size > MAX_FRAME_BYTES:
                raise MoneyBridgeError("手机返回了无效大小的响应帧")
            payload = await reader.readexactly(size)
            decoded = json.loads(payload.decode("utf-8"))
            if not isinstance(decoded, dict):
                raise MoneyBridgeError("手机响应不是 JSON 对象")
            return decoded
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise MoneyBridgeError("手机响应不是有效 UTF-8 JSON") from error
        finally:
            writer.close()
            await writer.wait_closed()


async def pair_phone(
    host: str,
    port: int,
    code: str,
    client_name: str,
    config_path: Path | None = None,
) -> tuple[ConnectionConfig, Path]:
    placeholder = ConnectionConfig(
        host=host,
        port=port,
        token="",
        session_id="",
        allow_write=False,
        expires_at=2**63 - 1,
    )
    client = MoneyPhoneClient(placeholder)
    info = await client.request("server.info", authenticated=False)
    if info.get("protocolVersion") != PROTOCOL_VERSION:
        raise MoneyBridgeError("手机 App 与 Python 桥接程序的协议版本不兼容")
    paired = await client.request(
        "session.pair",
        {"code": code.replace(" ", ""), "clientName": client_name},
        authenticated=False,
    )
    config = ConnectionConfig(
        host=host,
        port=port,
        token=str(paired["token"]),
        session_id=str(paired["sessionId"]),
        allow_write=bool(paired["allowWrite"]),
        expires_at=int(paired["expiresAt"]),
    )
    return config, save_config(config, config_path)


async def resolve_account(client: MoneyPhoneClient, account: int | str) -> int:
    result = await client.request("accounts.list", {"includeClosed": True})
    accounts = result.get("accounts", [])
    if isinstance(account, int) or (isinstance(account, str) and account.strip().isdigit()):
        account_id = int(account)
        if any(int(row["id"]) == account_id for row in accounts):
            return account_id
        raise MoneyBridgeError(f"找不到 ID 为 {account_id} 的账户")
    name = str(account).strip()
    matches = [row for row in accounts if str(row.get("name", "")) == name]
    if len(matches) == 1:
        return int(matches[0]["id"])
    if not matches:
        raise MoneyBridgeError(f"找不到名为“{name}”的账户")
    raise MoneyBridgeError(f"存在多个名为“{name}”的账户，请改用账户 ID")
