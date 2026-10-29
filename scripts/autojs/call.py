"""Small loopback-only AutoJS client; no third-party Python packages needed."""
import argparse
import json
import os
from pathlib import Path
import urllib.request


def call(tool, arguments, port=25585):
    payload = {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
               "params": {"name": tool, "arguments": arguments}}
    headers = {"Content-Type": "application/json"}
    if os.environ.get("AUTOJS_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["AUTOJS_TOKEN"]
    request = urllib.request.Request(f"http://127.0.0.1:{port}/mcp",
                                     data=json.dumps(payload).encode(), headers=headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        envelope = json.load(response)
    if "error" in envelope:
        raise RuntimeError(envelope["error"])
    result = envelope["result"]
    texts = [part["text"] for part in result.get("content", []) if part["type"] == "text"]
    decoded = [json.loads(text) for text in texts]
    if result.get("isError") or any(isinstance(x, dict) and x.get("ok") is False for x in decoded):
        raise RuntimeError(json.dumps(decoded, ensure_ascii=False))
    return decoded[0] if len(decoded) == 1 else decoded


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=25585)
    parser.add_argument("--side", choices=["client", "server", "auto"], default="server")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--status", action="store_true")
    group.add_argument("--file", type=Path)
    args = parser.parse_args()
    result = call("status", {}, args.port) if args.status else call("eval_js", {
        "side": args.side, "code": args.file.read_text(encoding="utf-8-sig"), "timeout_ms": 10000}, args.port)
    print(json.dumps(result, ensure_ascii=False, indent=2))
