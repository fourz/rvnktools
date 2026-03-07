#!/usr/bin/env python3
"""
MCP-based Server Control Tool

This script controls Minecraft server state (start/stop/restart/reload)
via the RVNKDev MCP server.

Usage:
    python control-server.py start [--server rvnk-test]
    python control-server.py stop [--server rvnk-test]
    python control-server.py restart [--server rvnk-test]
    python control-server.py reload [--server rvnk-test]
"""

import argparse
import sys
import time
from pathlib import Path

# Add lib directory to path
sys.path.insert(0, str(Path(__file__).parent / "lib"))

from mcp_client import MCPClient, MCPConfig, MCPError, format_output


# Wait times before verifying state change
WAIT_TIMES = {
    "stop": 30,
    "start": 45,
    "restart": 60,
}


def cmd_control(args, config: MCPConfig, action: str):
    """Execute server control command."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    action_names = {
        "start": "START SERVER",
        "stop": "STOP SERVER",
        "restart": "RESTART SERVER",
        "reload": "RELOAD PLUGINS"
    }

    print(f"=== {action_names[action]} ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Type: {server.server_type.value}")
    print()

    if not config.is_write_allowed(args.server):
        print(f"ERROR: Server '{args.server}' is production - control operations blocked", file=sys.stderr)
        print("Only test servers allow control operations.", file=sys.stderr)
        sys.exit(1)

    with MCPClient(debug=args.debug) as client:
        if action == "reload":
            # Reload uses console command
            print("Sending reload command...")
            result = client.call_tool("send_console_command", {
                "server_id": server_id,
                "command": "reload"
            })
            if result.get("success"):
                print("Reload command sent successfully")
            else:
                print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
                sys.exit(1)
        else:
            # Start/stop/restart use set_server_state
            print(f"Sending {action} command...")
            result = client.call_tool("set_server_state", {
                "server_id": server_id,
                "action": action
            })

            if result.get("success"):
                print(f"Command accepted. Current state: {result.get('state', 'unknown')}")

                if args.wait:
                    wait_time = WAIT_TIMES.get(action, 30)
                    print(f"\nWaiting {wait_time}s for operation to complete...")
                    time.sleep(wait_time)

                    # Verify final state
                    print("Verifying final state...")
                    status = client.call_tool("get_server_state", {
                        "server_id": server_id
                    })
                    print(f"Final state: {status.get('state', 'unknown')}")

                    expected = "offline" if action == "stop" else "running"
                    if status.get("state") == expected:
                        print(f"SUCCESS: Server is now {expected}")
                    else:
                        print(f"WARNING: Expected {expected}, got {status.get('state')}")
            else:
                print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
                sys.exit(1)


def main():
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="MCP-based Server Control Tool"
    )
    parser.add_argument("--debug", "-d", action="store_true", help="Enable debug output")

    subparsers = parser.add_subparsers(dest="command", help="Control commands")

    # Start command
    start_parser = subparsers.add_parser("start", help="Start the server")
    start_parser.add_argument("--server", "-s", default="rvnk-test",
                              help="Server alias or ID (default: rvnk-test)")
    start_parser.add_argument("--wait", "-w", action="store_true",
                              help="Wait and verify state change")

    # Stop command
    stop_parser = subparsers.add_parser("stop", help="Stop the server")
    stop_parser.add_argument("--server", "-s", default="rvnk-test",
                             help="Server alias or ID (default: rvnk-test)")
    stop_parser.add_argument("--wait", "-w", action="store_true",
                             help="Wait and verify state change")

    # Restart command
    restart_parser = subparsers.add_parser("restart", help="Restart the server")
    restart_parser.add_argument("--server", "-s", default="rvnk-test",
                                help="Server alias or ID (default: rvnk-test)")
    restart_parser.add_argument("--wait", "-w", action="store_true",
                                help="Wait and verify state change")

    # Reload command
    reload_parser = subparsers.add_parser("reload", help="Reload plugins (no restart)")
    reload_parser.add_argument("--server", "-s", default="rvnk-test",
                               help="Server alias or ID (default: rvnk-test)")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        print("\n--- Available Servers ---")
        try:
            config = MCPConfig()
            for server in config.list_servers():
                access = "Full access" if config.is_write_allowed(server.alias) else "Read-only"
                print(f"  {server.alias}: {server.name} ({server.id}) [{access}]")
        except FileNotFoundError:
            print("  (Configuration not found)")
        return

    try:
        config = MCPConfig()
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    # Add wait default for reload (not applicable)
    if not hasattr(args, 'wait'):
        args.wait = False

    try:
        cmd_control(args, config, args.command)
    except MCPError as e:
        print(f"MCP Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
