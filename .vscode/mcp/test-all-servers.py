#!/usr/bin/env python3
"""
MCP Server Test Suite

This script provides a test matrix for validating MCP tools against all configured servers.
Actual API calls are handled by the RVNKDev MCP server via Claude Code.

Usage:
    python test-all-servers.py [--verbose]

This script generates a comprehensive test plan with MCP tool invocation instructions
for each configured server.
"""

import argparse
import sys
from pathlib import Path
from datetime import datetime

# Add lib directory to path
sys.path.insert(0, str(Path(__file__).parent / "lib"))

from mcp_client import MCPConfig, ServerType


def print_header(title: str):
    """Print a formatted header."""
    print()
    print("=" * 60)
    print(f"  {title}")
    print("=" * 60)


def print_section(title: str):
    """Print a formatted section header."""
    print()
    print(f"--- {title} ---")


def generate_test_plan(server_id: str, server_name: str, server_type: ServerType, verbose: bool = False):
    """Generate test plan for a specific server."""

    print_section(f"Server: {server_name} ({server_id})")
    print(f"Type: {server_type.value}")
    print(f"Access: {'Full' if server_type == ServerType.TEST else 'Read-only'}")

    tests = [
        {
            "name": "Server Status",
            "tool": "get_server_state",
            "read_only": True,
            "call": f'mcp__rvnkdev-minecraft-server__get_server_state(server_id="{server_id}")',
            "expected": "Returns state, cpu_percent, memory_percent, players_online"
        },
        {
            "name": "Console Output",
            "tool": "get_console_output",
            "read_only": True,
            "call": f'mcp__rvnkdev-minecraft-server__get_console_output(server_id="{server_id}", lines=10)',
            "expected": "Returns recent console log lines"
        },
        {
            "name": "File List (root)",
            "tool": "file_read",
            "read_only": True,
            "call": f'mcp__rvnkdev-minecraft-server__file_read(action="list", server_id="{server_id}", remote_path="/")',
            "expected": "Returns directory listing"
        },
        {
            "name": "File List (plugins)",
            "tool": "file_read",
            "read_only": True,
            "call": f'mcp__rvnkdev-minecraft-server__file_read(action="list", server_id="{server_id}", remote_path="/plugins")',
            "expected": "Returns plugin directory listing"
        },
    ]

    # Add write tests for test servers only
    if server_type == ServerType.TEST:
        tests.extend([
            {
                "name": "Send Command (list)",
                "tool": "send_console_command",
                "read_only": False,
                "call": f'mcp__rvnkdev-minecraft-server__send_console_command(server_id="{server_id}", command="list")',
                "expected": "Returns command execution result"
            },
            {
                "name": "Server Restart (CAUTION)",
                "tool": "set_server_state",
                "read_only": False,
                "call": f'mcp__rvnkdev-minecraft-server__set_server_state(server_id="{server_id}", action="restart")',
                "expected": "Initiates server restart (wait 60s for completion)"
            },
        ])

    print()
    print("Test Cases:")
    for i, test in enumerate(tests, 1):
        access = "[R]" if test["read_only"] else "[W]"
        print(f"  {i}. {access} {test['name']}")
        if verbose:
            print(f"      Tool: {test['tool']}")
            print(f"      Call: {test['call']}")
            print(f"      Expected: {test['expected']}")
            print()


def main():
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="MCP Server Test Suite",
        epilog="Generates test plan for Claude Code MCP tool validation."
    )
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Show detailed test instructions")

    args = parser.parse_args()

    print_header("MCP Server Test Suite")
    print(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    try:
        config = MCPConfig()
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    servers = config.list_servers()
    test_servers = [s for s in servers if s.server_type == ServerType.TEST]
    prod_servers = [s for s in servers if s.server_type == ServerType.PRODUCTION]

    print(f"\nConfigured Servers: {len(servers)} total")
    print(f"  - Test servers: {len(test_servers)} (full access)")
    print(f"  - Production servers: {len(prod_servers)} (read-only)")

    # Test servers first
    if test_servers:
        print_header("TEST SERVERS (Full Access)")
        for server in test_servers:
            generate_test_plan(server.id, server.name, server.server_type, args.verbose)

    # Production servers (read-only)
    if prod_servers:
        print_header("PRODUCTION SERVERS (Read-Only)")
        for server in prod_servers:
            generate_test_plan(server.id, server.name, server.server_type, args.verbose)

    print_header("Test Execution Instructions")
    print("""
To run these tests, use Claude Code with the MCP tools listed above.

Test Order:
1. Start with read-only tests on all servers
2. Verify all servers respond correctly
3. For test servers only, run write operations
4. Verify restart completes successfully (wait 60s)

Database Tests (separate from server tests):
  mcp__rvnkdev-minecraft-server__database_tools(action="test")
  mcp__rvnkdev-minecraft-server__database_tools(action="list_tables")
  mcp__rvnkdev-minecraft-server__database_tools(action="list_databases")

Report any failures to the development team.
""")


if __name__ == "__main__":
    main()
