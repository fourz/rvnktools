"""
MCP Client Library for RVNKDev Server Management

This library provides a JSON-RPC client for communicating with the RVNKDev MCP server.
It launches the MCP server as a subprocess and communicates via stdin/stdout.

Usage:
    from lib.mcp_client import MCPClient, MCPConfig

    config = MCPConfig()
    with MCPClient() as client:
        result = client.call_tool("get_server_state", {"server_id": "b2bc4d7e"})
        print(result)
"""

import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional
from dataclasses import dataclass
from enum import Enum


# MCP Server configuration
MCP_EXECUTABLE = r"c:\tools\_PROJECTS\Ravenkraft-Dev\repos\rvnkdev-mcp-server\rvnkdev-fastmcp-server\.venv\Scripts\rvnkdev-mcp.exe"
MCP_CONFIG_PATH = r"c:/tools/_PROJECTS/Ravenkraft-Dev/repos/rvnkdev-mcp-server/rvnkdev-fastmcp-server/config.yaml"
MCP_JSON_PATH = r"c:\tools\_PROJECTS\Ravenkraft-Dev\.mcp.json"


def _load_mcp_env() -> Dict[str, str]:
    """Load environment variables from .mcp.json."""
    try:
        with open(MCP_JSON_PATH, 'r') as f:
            config = json.load(f)
        server_config = config.get("mcpServers", {}).get("rvnkdev-minecraft-server", {})
        return server_config.get("env", {})
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


class ServerType(Enum):
    """Server type classification."""
    TEST = "test"
    PRODUCTION = "production"


@dataclass
class ServerInfo:
    """Server information from config."""
    id: str
    name: str
    alias: str
    provider: str
    server_type: ServerType
    description: str


class MCPError(Exception):
    """MCP communication error."""
    pass


class MCPClient:
    """
    MCP Client for JSON-RPC communication with the RVNKDev MCP server.

    Uses subprocess communication over stdin/stdout with JSON-RPC 2.0 protocol.
    """

    def __init__(self, debug: bool = False):
        """
        Initialize MCP client.

        Args:
            debug: Enable debug output
        """
        self.debug = debug
        self.process: Optional[subprocess.Popen] = None
        self._request_id = 0
        self._lock = threading.Lock()

    def __enter__(self):
        """Context manager entry - start MCP server."""
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit - stop MCP server."""
        self.stop()
        return False

    def start(self):
        """Start the MCP server subprocess."""
        if self.process is not None:
            return

        env = os.environ.copy()

        # Load env from .mcp.json (includes BW_SESSION)
        mcp_env = _load_mcp_env()
        env.update(mcp_env)

        # Override some settings
        env["CONFIG_PATH"] = MCP_CONFIG_PATH
        env["LOG_LEVEL"] = "ERROR"  # Reduce noise
        env["FASTMCP_ENV"] = "production"

        if self.debug:
            print(f"[DEBUG] Starting MCP server: {MCP_EXECUTABLE}", file=sys.stderr)

        self.process = subprocess.Popen(
            [MCP_EXECUTABLE, "--transport", "stdio"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=None if self.debug else subprocess.DEVNULL,
            env=env,
            text=True,
            bufsize=1  # Line buffered
        )

        # Initialize MCP connection
        self._initialize()

    def stop(self):
        """Stop the MCP server subprocess."""
        if self.process is None:
            return

        try:
            self.process.stdin.close()
            self.process.terminate()
            self.process.wait(timeout=5)
        except Exception:
            self.process.kill()
        finally:
            self.process = None

    def _next_id(self) -> int:
        """Get next request ID."""
        with self._lock:
            self._request_id += 1
            return self._request_id

    def _send_request(self, method: str, params: Optional[Dict] = None) -> Dict:
        """
        Send JSON-RPC request and wait for response.

        Args:
            method: RPC method name
            params: Method parameters

        Returns:
            Response result
        """
        if self.process is None:
            raise MCPError("MCP server not started")

        request_id = self._next_id()
        request = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
        }
        if params:
            request["params"] = params

        request_json = json.dumps(request)

        if self.debug:
            print(f"[DEBUG] -> {request_json}", file=sys.stderr)

        try:
            self.process.stdin.write(request_json + "\n")
            self.process.stdin.flush()

            # Read response - skip non-JSON lines (log messages, banners)
            max_attempts = 100  # Prevent infinite loops
            for _ in range(max_attempts):
                response_line = self.process.stdout.readline()
                if not response_line:
                    raise MCPError("No response from MCP server")

                line = response_line.strip()

                # Skip empty lines and non-JSON lines
                if not line or not line.startswith('{'):
                    if self.debug:
                        print(f"[DEBUG] skip: {line[:80]}", file=sys.stderr)
                    continue

                if self.debug:
                    print(f"[DEBUG] <- {line}", file=sys.stderr)

                try:
                    response = json.loads(line)

                    # Verify this is a JSON-RPC response for our request
                    if response.get("id") == request_id:
                        if "error" in response:
                            error = response["error"]
                            raise MCPError(f"MCP error {error.get('code')}: {error.get('message')}")
                        return response.get("result", {})
                    elif self.debug:
                        print(f"[DEBUG] skip response id={response.get('id')}", file=sys.stderr)

                except json.JSONDecodeError:
                    if self.debug:
                        print(f"[DEBUG] skip invalid JSON: {line[:80]}", file=sys.stderr)
                    continue

            raise MCPError("Failed to get response after max attempts")

        except BrokenPipeError:
            raise MCPError("MCP server connection lost")

    def _send_notification(self, method: str, params: Optional[Dict] = None):
        """Send JSON-RPC notification (no response expected)."""
        if self.process is None:
            raise MCPError("MCP server not started")

        notification = {
            "jsonrpc": "2.0",
            "method": method,
        }
        if params:
            notification["params"] = params

        notification_json = json.dumps(notification)

        if self.debug:
            print(f"[DEBUG] -> {notification_json}", file=sys.stderr)

        self.process.stdin.write(notification_json + "\n")
        self.process.stdin.flush()

    def _initialize(self):
        """Initialize MCP connection."""
        # Send initialize request
        result = self._send_request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "rvnkdev-cli",
                "version": "1.0.0"
            }
        })

        if self.debug:
            print(f"[DEBUG] Server capabilities: {result}", file=sys.stderr)

        # Send initialized notification
        self._send_notification("notifications/initialized")

    def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict:
        """
        Call an MCP tool.

        Args:
            tool_name: Name of the tool (e.g., "get_server_state")
            arguments: Tool arguments

        Returns:
            Tool result
        """
        result = self._send_request("tools/call", {
            "name": tool_name,
            "arguments": arguments
        })

        # Extract content from MCP tool response
        content = result.get("content", [])
        if content and isinstance(content, list):
            # Get first text content
            for item in content:
                if item.get("type") == "text":
                    text = item.get("text", "")
                    try:
                        return json.loads(text)
                    except json.JSONDecodeError:
                        return {"text": text}
        return result

    def list_tools(self) -> List[Dict]:
        """List available MCP tools."""
        result = self._send_request("tools/list")
        return result.get("tools", [])


class MCPConfig:
    """Configuration helper for MCP scripts."""

    def __init__(self, config_path: Optional[str] = None):
        """
        Load server configuration.

        Args:
            config_path: Path to servers.json. Auto-detected if not provided.
        """
        self.config_path = config_path or self._find_config()
        self.config = self._load_config()
        self.servers = self._parse_servers()

    def _find_config(self) -> str:
        """Find the config file."""
        # Try relative to this file
        lib_dir = Path(__file__).parent
        config_path = lib_dir.parent / "config" / "servers.json"
        if config_path.exists():
            return str(config_path)

        # Try relative to current directory
        cwd_config = Path.cwd() / ".vscode" / "mcp" / "config" / "servers.json"
        if cwd_config.exists():
            return str(cwd_config)

        raise FileNotFoundError("Could not find servers.json")

    def _load_config(self) -> Dict:
        """Load configuration from JSON."""
        with open(self.config_path, 'r') as f:
            return json.load(f)

    def _parse_servers(self) -> Dict[str, ServerInfo]:
        """Parse server configurations."""
        servers = {}
        for alias, data in self.config.get("servers", {}).items():
            server_type = ServerType.TEST if data.get("type") == "test" else ServerType.PRODUCTION
            server = ServerInfo(
                id=data["id"],
                name=data.get("name", alias),
                alias=alias,
                provider=data.get("provider", "unknown"),
                server_type=server_type,
                description=data.get("description", "")
            )
            servers[alias] = server
            servers[data["id"]] = server  # Also index by ID
        return servers

    def resolve_server(self, identifier: str) -> str:
        """
        Resolve server alias or ID to server ID.

        Args:
            identifier: Server alias (e.g., "rvnk-test") or ID (e.g., "b2bc4d7e")

        Returns:
            Server ID string
        """
        if identifier in self.servers:
            return self.servers[identifier].id
        raise ValueError(f"Unknown server: {identifier}")

    def get_server(self, identifier: str) -> ServerInfo:
        """Get full server info by alias or ID."""
        if identifier in self.servers:
            return self.servers[identifier]
        raise ValueError(f"Unknown server: {identifier}")

    def list_servers(self, server_type: Optional[ServerType] = None) -> List[ServerInfo]:
        """List all configured servers."""
        seen_ids = set()
        servers = []
        for server in self.servers.values():
            if server.id in seen_ids:
                continue
            if server_type and server.server_type != server_type:
                continue
            servers.append(server)
            seen_ids.add(server.id)
        return servers

    def get_default_server(self) -> str:
        """Get the default server ID."""
        default_alias = self.config.get("defaults", {}).get("server", "rvnk-test")
        return self.resolve_server(default_alias)

    def is_write_allowed(self, identifier: str) -> bool:
        """Check if write operations are allowed."""
        server = self.get_server(identifier)
        return server.server_type == ServerType.TEST


def format_output(data: Any, output_format: str = "json") -> str:
    """
    Format output data for display.

    Args:
        data: Data to format
        output_format: One of 'json', 'table', 'text'

    Returns:
        Formatted string
    """
    if output_format == "json":
        return json.dumps(data, indent=2, default=str)

    elif output_format == "table":
        if isinstance(data, list) and data:
            # Simple table format
            if isinstance(data[0], dict):
                keys = list(data[0].keys())
                lines = [" | ".join(keys)]
                lines.append("-" * len(lines[0]))
                for row in data:
                    lines.append(" | ".join(str(row.get(k, "")) for k in keys))
                return "\n".join(lines)
        return str(data)

    else:  # text
        if isinstance(data, dict):
            return "\n".join(f"{k}: {v}" for k, v in data.items())
        elif isinstance(data, list):
            return "\n".join(str(item) for item in data)
        return str(data)


def main():
    """CLI entry point - test MCP connection."""
    import argparse

    parser = argparse.ArgumentParser(
        description="MCP Client - Test connection and list tools"
    )
    parser.add_argument("--debug", "-d", action="store_true", help="Enable debug output")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # List servers
    list_parser = subparsers.add_parser("list-servers", help="List configured servers")
    list_parser.add_argument("--format", "-f", choices=["json", "table", "text"], default="table")

    # List tools
    tools_parser = subparsers.add_parser("list-tools", help="List available MCP tools")

    # Test connection
    test_parser = subparsers.add_parser("test", help="Test MCP connection")

    args = parser.parse_args()

    if args.command == "list-servers":
        config = MCPConfig()
        servers = config.list_servers()
        data = [
            {
                "alias": s.alias,
                "name": s.name,
                "id": s.id,
                "provider": s.provider,
                "type": s.server_type.value
            }
            for s in servers
        ]
        print(format_output(data, args.format))

    elif args.command == "list-tools":
        with MCPClient(debug=args.debug) as client:
            tools = client.list_tools()
            for tool in tools:
                print(f"- {tool['name']}: {tool.get('description', 'No description')[:60]}")

    elif args.command == "test":
        print("Testing MCP connection...")
        try:
            with MCPClient(debug=args.debug) as client:
                tools = client.list_tools()
                print(f"SUCCESS: Connected to MCP server, {len(tools)} tools available")
        except MCPError as e:
            print(f"FAILED: {e}")
            sys.exit(1)

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
