#!/usr/bin/env python3
"""
MCP-based Database Operations Tool

This script performs database operations via the RVNKDev MCP server.

Usage:
    python database-ops.py list-tables
    python database-ops.py list-databases
    python database-ops.py describe players
    python database-ops.py query "SELECT * FROM players LIMIT 10"
    python database-ops.py test
"""

import argparse
import sys
from pathlib import Path

# Add lib directory to path
sys.path.insert(0, str(Path(__file__).parent / "lib"))

from mcp_client import MCPClient, MCPConfig, MCPError, format_output


def cmd_list_tables(args):
    """List all database tables."""
    print(f"=== LIST TABLES ===")
    print(f"Connection: {args.connection}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("database_tools", {
            "action": "list_tables",
            "connection_name": args.connection
        })

        if result.get("success"):
            tables = result.get("data", [])
            if isinstance(tables, list):
                print(f"Found {len(tables)} tables:")
                for table in tables:
                    print(f"  - {table}")
            else:
                print(format_output(tables, args.format))
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_list_databases(args):
    """List available databases."""
    print(f"=== LIST DATABASES ===")
    print(f"Connection: {args.connection}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("database_tools", {
            "action": "list_databases",
            "connection_name": args.connection
        })

        if result.get("success"):
            databases = result.get("data", [])
            if isinstance(databases, list):
                print(f"Found {len(databases)} databases:")
                for db in databases:
                    print(f"  - {db}")
            else:
                print(format_output(databases, args.format))
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_describe(args):
    """Describe table structure."""
    print(f"=== DESCRIBE TABLE ===")
    print(f"Table: {args.table}")
    print(f"Connection: {args.connection}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("database_tools", {
            "action": "describe",
            "table_name": args.table,
            "connection_name": args.connection
        })

        if result.get("success"):
            schema = result.get("data", [])
            if isinstance(schema, list) and schema:
                # Format as table
                print(f"{'Column':<20} {'Type':<20} {'Null':<6} {'Key':<6} {'Default':<15}")
                print("-" * 70)
                for col in schema:
                    if isinstance(col, dict):
                        print(f"{col.get('Field', col.get('column', '')):<20} "
                              f"{col.get('Type', col.get('type', '')):<20} "
                              f"{col.get('Null', col.get('nullable', '')):<6} "
                              f"{col.get('Key', col.get('key', '')):<6} "
                              f"{str(col.get('Default', col.get('default', ''))):<15}")
                    else:
                        print(f"  {col}")
            else:
                print(format_output(schema, args.format))
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_query(args):
    """Execute SQL query."""
    print(f"=== EXECUTE QUERY ===")
    print(f"Query: {args.sql}")
    print(f"Connection: {args.connection}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("database_tools", {
            "action": "query",
            "query": args.sql,
            "connection_name": args.connection
        })

        if result.get("success"):
            data = result.get("data", [])
            if args.format == "json":
                print(format_output(data, "json"))
            elif isinstance(data, list) and data:
                if isinstance(data[0], dict):
                    # Format as table
                    keys = list(data[0].keys())
                    header = " | ".join(f"{k:<15}" for k in keys)
                    print(header)
                    print("-" * len(header))
                    for row in data:
                        print(" | ".join(f"{str(row.get(k, '')):<15}" for k in keys))
                    print(f"\n{len(data)} rows returned")
                else:
                    for row in data:
                        print(row)
            else:
                print(f"Result: {data}")
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_test(args):
    """Test database connection."""
    print(f"=== TEST CONNECTION ===")
    print(f"Connection: {args.connection}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("database_tools", {
            "action": "test",
            "connection_name": args.connection
        })

        if result.get("success"):
            print("Connection successful!")
            data = result.get("data", {})
            if isinstance(data, dict):
                for key, value in data.items():
                    print(f"  {key}: {value}")
        else:
            print(f"Connection failed: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def main():
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="MCP-based Database Operations Tool"
    )
    parser.add_argument("--debug", "-d", action="store_true", help="Enable debug output")

    subparsers = parser.add_subparsers(dest="command", help="Database commands")

    # List tables command
    list_tables_parser = subparsers.add_parser("list-tables", help="List database tables")
    list_tables_parser.add_argument("--connection", "-c", default="default",
                                    help="Connection name (default: default)")
    list_tables_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                                    help="Output format (default: text)")

    # List databases command
    list_dbs_parser = subparsers.add_parser("list-databases", help="List available databases")
    list_dbs_parser.add_argument("--connection", "-c", default="default",
                                 help="Connection name (default: default)")
    list_dbs_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                                 help="Output format (default: text)")

    # Describe table command
    describe_parser = subparsers.add_parser("describe", help="Describe table structure")
    describe_parser.add_argument("table", help="Table name to describe")
    describe_parser.add_argument("--connection", "-c", default="default",
                                 help="Connection name (default: default)")
    describe_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                                 help="Output format (default: text)")

    # Query command
    query_parser = subparsers.add_parser("query", help="Execute SQL query")
    query_parser.add_argument("sql", help="SQL query to execute")
    query_parser.add_argument("--connection", "-c", default="default",
                              help="Connection name (default: default)")
    query_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                              help="Output format (default: text)")

    # Test connection command
    test_parser = subparsers.add_parser("test", help="Test database connection")
    test_parser.add_argument("--connection", "-c", default="default",
                             help="Connection name (default: default)")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        print("\n--- Database Operations ---")
        print("  list-tables    - List all tables")
        print("  list-databases - List available databases")
        print("  describe       - Describe table structure")
        print("  query          - Execute SQL query")
        print("  test           - Test connection")
        return

    try:
        handlers = {
            "list-tables": cmd_list_tables,
            "list-databases": cmd_list_databases,
            "describe": cmd_describe,
            "query": cmd_query,
            "test": cmd_test,
        }
        handlers[args.command](args)
    except MCPError as e:
        print(f"MCP Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
