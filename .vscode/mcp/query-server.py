#!/usr/bin/env python3
"""
MCP-based Server Query Tool

This script queries Minecraft server status, console output, and sends commands
via the RVNKDev MCP server.

Usage:
    python query-server.py console [--lines 50] [--server rvnk-test]
    python query-server.py status [--server rvnk-test]
    python query-server.py stats [--server rvnk-test]
    python query-server.py command "say Hello" [--server rvnk-test]
"""

import argparse
import sys
from pathlib import Path

# Add lib directory to path
sys.path.insert(0, str(Path(__file__).parent / "lib"))

from mcp_client import MCPClient, MCPConfig, MCPError, format_output


def cmd_console(args, config: MCPConfig):
    """Get console output from server."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== CONSOLE OUTPUT ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Lines: {args.lines}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("get_console_output", {
            "server_id": server_id,
            "lines": args.lines
        })

        if result.get("success"):
            # Console output is in result.data.console_output
            data = result.get("data", {})
            lines = data.get("console_output", []) if isinstance(data, dict) else data
            if isinstance(lines, list):
                for line in lines:
                    print(line)
            else:
                print(lines)
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_status(args, config: MCPConfig):
    """Get server status."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== SERVER STATUS ===")
    print(f"Server: {server.name} ({server_id})")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("get_server_state", {
            "server_id": server_id
        })

        if args.format == "json":
            print(format_output(result, "json"))
        else:
            # Data is nested inside result.data
            data = result.get('data', result)
            print(f"State: {data.get('raw_state', data.get('status', 'unknown'))}")
            print(f"CPU: {data.get('cpu_usage', 'N/A')}%")
            print(f"Memory: {data.get('memory_percentage', 'N/A')}%")
            print(f"Players: {data.get('players_online', 'N/A')}")
            print(f"Uptime: {data.get('uptime', 'N/A')}")


def cmd_stats(args, config: MCPConfig):
    """Get server statistics (alias for status with JSON output)."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== SERVER STATISTICS ===")
    print(f"Server: {server.name} ({server_id})")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("get_server_state", {
            "server_id": server_id
        })
        print(format_output(result, args.format))


def cmd_command(args, config: MCPConfig):
    """Send console command to server."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== SEND COMMAND ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Command: {args.cmd_text}")
    print()

    if not config.is_write_allowed(args.server):
        print(f"ERROR: Server '{args.server}' is production - command execution blocked", file=sys.stderr)
        print("Only test servers allow command execution.", file=sys.stderr)
        sys.exit(1)

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("send_console_command", {
            "server_id": server_id,
            "command": args.cmd_text
        })

        if result.get("success"):
            print("Command sent successfully")
            if result.get("data"):
                print(f"Response: {result.get('data')}")
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def main():
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="MCP-based Server Query Tool"
    )
    parser.add_argument("--debug", "-d", action="store_true", help="Enable debug output")

    subparsers = parser.add_subparsers(dest="command", help="Query commands")

    # Console command
    console_parser = subparsers.add_parser("console", help="Get console output")
    console_parser.add_argument("--lines", "-l", type=int, default=50,
                                help="Number of lines to retrieve (default: 50)")
    console_parser.add_argument("--server", "-s", default="rvnk-test",
                                help="Server alias or ID (default: rvnk-test)")

    # Status command
    status_parser = subparsers.add_parser("status", help="Get server status")
    status_parser.add_argument("--server", "-s", default="rvnk-test",
                               help="Server alias or ID (default: rvnk-test)")
    status_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                               help="Output format (default: text)")

    # Stats command
    stats_parser = subparsers.add_parser("stats", help="Get server statistics")
    stats_parser.add_argument("--server", "-s", default="rvnk-test",
                              help="Server alias or ID (default: rvnk-test)")
    stats_parser.add_argument("--format", "-f", choices=["text", "json"], default="json",
                              help="Output format (default: json)")

    # Command execution
    cmd_parser = subparsers.add_parser("command", help="Send console command")
    cmd_parser.add_argument("cmd_text", metavar="COMMAND",
                            help="Command to send (without leading /)")
    cmd_parser.add_argument("--server", "-s", default="rvnk-test",
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

    try:
        # Dispatch to command handlers
        if args.command == "console":
            cmd_console(args, config)
        elif args.command == "status":
            cmd_status(args, config)
        elif args.command == "stats":
            cmd_stats(args, config)
        elif args.command == "command":
            cmd_command(args, config)
        else:
            parser.print_help()
    except MCPError as e:
        print(f"MCP Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
