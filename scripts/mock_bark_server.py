#!/usr/bin/env python3
"""Mock Bark (day.app style) server for sms2bark E2E verification.

Listens on 0.0.0.0:8787. Handles:
  POST /push            Bark API v2 push: JSON body {"title","body","device_key",...}
  GET  /<key>/<title>/<body>   Bark legacy GET fallback (also /<key>/<title>/<body>?...)
  GET  /health          liveness check -> {"ok":true,"requests":N}
Every request appends one JSON line to sms2bark/verify/bark_requests.log:
  {"ts":..., "method":..., "path":..., "query":..., "headers":{...}, "body_raw":"...", "json":{...}|null}
and responds like a real Bark server:
  {"code":200,"message":"success","timestamp":<ms>}
Exits cleanly on SIGTERM/SIGINT.
"""
import json
import os
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote

HERE = os.path.dirname(os.path.abspath(__file__))
LOG_PATH = os.path.abspath(os.path.join(HERE, "..", "verify", "bark_requests.log"))
PORT = int(os.environ.get("MOCK_PORT", "8787"))
MAX_BODY = 1 << 20  # 1 MiB

_lock = threading.Lock()
_count = 0
_start = time.time()


def log_line(entry: dict) -> None:
    global _count
    os.makedirs(os.path.dirname(LOG_PATH), exist_ok=True)
    line = json.dumps(entry, ensure_ascii=False, sort_keys=False)
    with _lock:
        _count += 1
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
            f.flush()
    sys.stderr.write("[mock_bark] " + line[:400] + "\n")
    sys.stderr.flush()


def ok_payload() -> dict:
    return {"code": 200, "message": "success", "timestamp": int(time.time() * 1000)}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MockBark/2.0"

    # ---------- helpers ----------
    def _read_body(self) -> bytes:
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        if length <= 0:
            return b""
        return self.rfile.read(min(length, MAX_BODY))

    def _send_json(self, obj: dict, status: int = 200) -> None:
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.wfile.flush()

    def _send_text(self, text: str, status: int = 200) -> None:
        raw = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.wfile.flush()

    def _record(self, body_raw: bytes) -> dict:
        parsed = urlparse(self.path)
        entry = {
            "ts": int(time.time() * 1000),
            "time": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "method": self.command,
            "path": parsed.path,
            "query": parse_qs(parsed.query),
            "headers": {k: v for k, v in self.headers.items()},
            "body_raw": body_raw.decode("utf-8", "replace"),
            "json": None,
        }
        if body_raw:
            try:
                entry["json"] = json.loads(body_raw.decode("utf-8", "replace"))
            except Exception:
                entry["json"] = None
        log_line(entry)
        return entry

    # ---------- methods ----------
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        if path in ("/health", "/", "/ping"):
            self._record(b"")
            self._send_json({"ok": True, "requests": _count, "uptime_s": round(time.time() - _start, 1)})
            return
        # Bark legacy GET: /<device_key>/<title>/<body>  (query may carry group/sound/...)
        body_raw = parse_qs(parsed.query).get("body", [""])[0].encode()
        self._record(body_raw)
        self._send_json(ok_payload())

    def do_POST(self) -> None:
        body_raw = self._read_body()
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") in ("/push", "/push/"):
            self._record(body_raw)
            self._send_json(ok_payload())
        else:
            self._record(body_raw)
            self._send_json({"code": 404, "message": "not found"}, status=404)

    def do_PUT(self) -> None:
        self.do_POST()

    def log_message(self, fmt, *args):  # quiet default stderr noise
        sys.stderr.write("[mock_bark] %s - %s\n" % (self.address_string(), fmt % args))
        sys.stderr.flush()


def main() -> None:
    os.makedirs(os.path.dirname(LOG_PATH), exist_ok=True)
    # truncate log at server start so each run has clean evidence
    with open(LOG_PATH, "w", encoding="utf-8") as f:
        f.write("")
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    srv.daemon_threads = True

    def _shutdown(signum, frame):
        sys.stderr.write("[mock_bark] shutting down on signal %s\n" % signum)
        sys.stderr.flush()
        threading.Thread(target=srv.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)
    sys.stderr.write("[mock_bark] listening on 0.0.0.0:%d log=%s\n" % (PORT, LOG_PATH))
    sys.stderr.flush()
    try:
        srv.serve_forever(poll_interval=0.2)
    finally:
        srv.server_close()
        sys.stderr.write("[mock_bark] stopped\n")
        sys.stderr.flush()


if __name__ == "__main__":
    main()
