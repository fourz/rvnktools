#!/usr/bin/env python3
"""
MCP-based File Operations Tool

This script performs file operations on Minecraft servers
via the RVNKDev MCP server.

Usage:
    python file-ops.py list /plugins [--server rvnk-test]
    python file-ops.py read /plugins/RVNKTools/config.yml [--server rvnk-test]
    python file-ops.py upload ./target/plugin.jar /plugins [--server rvnk-test]
    python file-ops.py delete /plugins/old-plugin.jar [--server rvnk-test]
    python file-ops.py download /plugins/plugin.jar ./local.jar [--server rvnk-test]
"""

import argparse
import sys
from pathlib import Path

# Add lib directory to path
sys.path.insert(0, str(Path(__file__).parent / "lib"))

from mcp_client import MCPClient, MCPConfig, MCPError, format_output


def cmd_list(args, config: MCPConfig):
    """List directory contents."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== LIST DIRECTORY ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Path: {args.path}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("file_read", {
            "action": "list",
            "server_id": server_id,
            "remote_path": args.path
        })

        if result.get("success"):
            data = result.get("data", [])
            if isinstance(data, list):
                for item in data:
                    if isinstance(item, dict):
                        name = item.get("name", item.get("filename", "?"))
                        size = item.get("size", "")
                        ftype = item.get("type", item.get("is_dir", ""))
                        if ftype == True or ftype == "directory":
                            print(f"  [DIR]  {name}")
                        else:
                            print(f"  [FILE] {name} ({size} bytes)" if size else f"  [FILE] {name}")
                    else:
                        print(f"  {item}")
            else:
                print(format_output(data, args.format))
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_read(args, config: MCPConfig):
    """Read file contents."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== READ FILE ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Path: {args.path}")
    print()

    with MCPClient(debug=args.debug) as client:
        result = client.call_tool("file_read", {
            "action": "read",
            "server_id": server_id,
            "remote_path": args.path
        })

        if result.get("success"):
            content = result.get("data", result.get("content", ""))
            print(content)
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_upload(args, config: MCPConfig):
    """Upload file to server."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== UPLOAD FILE ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Local: {args.local_path}")
    print(f"Remote: {args.remote_path}")
    print()

    if not config.is_write_allowed(args.server):
        print(f"ERROR: Server '{args.server}' is production - upload blocked", file=sys.stderr)
        print("Only test servers allow file uploads.", file=sys.stderr)
        sys.exit(1)

    # Verify local file exists
    local_file = Path(args.local_path)
    if not local_file.exists():
        print(f"ERROR: Local file not found: {args.local_path}", file=sys.stderr)
        sys.exit(1)

    with MCPClient(debug=args.debug) as client:
        print(f"Uploading {local_file.name}...")
        result = client.call_tool("file_write", {
            "action": "upload",
            "server_id": server_id,
            "local_path": str(local_file.absolute()),
            "remote_path": args.remote_path
        })

        if result.get("success"):
            print("Upload completed successfully")
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_delete(args, config: MCPConfig):
    """Delete file from server."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== DELETE FILE ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Path: {args.path}")
    print()

    if not config.is_write_allowed(args.server):
        print(f"ERROR: Server '{args.server}' is production - delete blocked", file=sys.stderr)
        print("Only test servers allow file deletion.", file=sys.stderr)
        sys.exit(1)

    if not args.force:
        confirm = input(f"Delete {args.path}? [y/N] ").strip().lower()
        if confirm != 'y':
            print("Cancelled")
            return

    with MCPClient(debug=args.debug) as client:
        print("Deleting file...")
        result = client.call_tool("file_write", {
            "action": "delete",
            "server_id": server_id,
            "remote_path": args.path
        })

        if result.get("success"):
            print("File deleted successfully")
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def cmd_download(args, config: MCPConfig):
    """Download file from server."""
    server_id = config.resolve_server(args.server)
    server = config.get_server(args.server)

    print(f"=== DOWNLOAD FILE ===")
    print(f"Server: {server.name} ({server_id})")
    print(f"Remote: {args.remote_path}")
    print(f"Local: {args.local_path}")
    print()

    with MCPClient(debug=args.debug) as client:
        print("Downloading file...")
        result = client.call_tool("file_read", {
            "action": "download",
            "server_id": server_id,
            "remote_path": args.remote_path,
            "local_path": args.local_path
        })

        if result.get("success"):
            print(f"Downloaded to {args.local_path}")
        else:
            print(f"Error: {result.get('error', 'Unknown error')}", file=sys.stderr)
            sys.exit(1)


def main():
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="MCP-based File Operations Tool"
    )
    parser.add_argument("--debug", "-d", action="store_true", help="Enable debug output")

    subparsers = parser.add_subparsers(dest="command", help="File commands")

    # List command
    list_parser = subparsers.add_parser("list", help="List directory contents")
    list_parser.add_argument("path", nargs="?", default="/",
                             help="Remote path to list (default: /)")
    list_parser.add_argument("--server", "-s", default="rvnk-test",
                             help="Server alias or ID (default: rvnk-test)")
    list_parser.add_argument("--format", "-f", choices=["text", "json"], default="text",
                             help="Output format (default: text)")

    # Read command
    read_parser = subparsers.add_parser("read", help="Read file contents")
    read_parser.add_argument("path", help="Remote file path to read")
    read_parser.add_argument("--server", "-s", default="rvnk-test",
                             help="Server alias or ID (default: rvnk-test)")

    # Upload command
    upload_parser = subparsers.add_parser("upload", help="Upload file to server")
    upload_parser.add_argument("local_path", help="Local file path")
    upload_parser.add_argument("remote_path", help="Remote destination path")
    upload_parser.add_argument("--server", "-s", default="rvnk-test",
                               help="Server alias or ID (default: rvnk-test)")

    # Delete command
    delete_parser = subparsers.add_parser("delete", help="Delete file from server")
    delete_parser.add_argument("path", help="Remote file path to delete")
    delete_parser.add_argument("--server", "-s", default="rvnk-test",
                               help="Server alias or ID (default: rvnk-test)")
    delete_parser.add_argument("--force", "-f", action="store_true",
                               help="Skip confirmation prompt")

    # Download command
    download_parser = subparsers.add_parser("download", help="Download file from server")
    download_parser.add_argument("remote_path", help="Remote file path")
    download_parser.add_argument("local_path", help="Local destination path")
    download_parser.add_argument("--server", "-s", default="rvnk-test",
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
        handlers = {
            "list": cmd_list,
            "read": cmd_read,
            "upload": cmd_upload,
            "delete": cmd_delete,
            "download": cmd_download,
        }
        handlers[args.command](args, config)
    except MCPError as e:
        print(f"MCP Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
