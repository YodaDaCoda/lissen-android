#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.9"
# ///
"""Mock Audiobookshelf server for testing Lissen on the emulator. Not a real Audiobookshelf.

Usage: ./server.py [port]          (default 13378; the emulator reaches the host at http://10.0.2.2:<port>)
Login: user `demo`, any password. Set MOCK_ABS_LOG=<file> to append a request log.

Serves two generated books ("The Test Book", 3 tracks / 5 chapters; "A Short Story", 1 track) as
synthetic WAV tones with range-request support, plus the endpoints Lissen calls on login, the
library, playback sessions and bookmarks. Anything else is unimplemented.
"""
import json, math, os, re, struct, sys, time, zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.environ.get("MOCK_ABS_LOG")
LIB, USER = "lib-1", "user-1"
NOW = int(time.time() * 1000)
RATE = 8000

def wav(seconds, freq):
    n = RATE * seconds
    period = [int(1500 * math.sin(2 * math.pi * freq * i / RATE)) for i in range(RATE // math.gcd(RATE, freq))]
    body = struct.pack("<%dh" % len(period), *period) * (n // len(period) + 1)
    body = body[: n * 2]
    hdr = b"RIFF" + struct.pack("<I", 36 + len(body)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16) + b"data" + struct.pack("<I", len(body))
    return hdr + body

def png(w=600, h=600):
    rows = []
    for y in range(h):
        r = bytearray([0])
        for x in range(w):
            if y > 460:  # light bottom band, to test overlay contrast
                t = (y - 460) / 140
                c = (int(235 + 15 * t), int(232 + 15 * t), int(220 + 20 * t))
            else:
                c = (40 + x * 180 // w, 60 + y * 120 // 460, 200 - x * 120 // w)
                for cx, cy, rad, col in ((200, 200, 130, (250, 190, 40)), (420, 300, 90, (230, 60, 110)), (300, 120, 50, (80, 220, 170))):
                    if (x - cx) ** 2 + (y - cy) ** 2 < rad * rad:
                        c = col
                if (x + y) % 60 < 6:
                    c = tuple(min(255, v + 30) for v in c)
            r += bytes(c)
        rows.append(bytes(r))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows), 6)) + chunk(b"IEND", b"")

COVER = png()
# (book id, title, subtitle, track (secs, freq) list, chapters (start, title))
BOOKS = {
    "book-1": dict(title="The Test Book", subtitle="A mock for playback", tracks=[(240, 400), (270, 500), (210, 800)],
                   chapters=[(0, "Chapter 1: Beginnings"), (140, "Chapter 2: The Middle"), (300, "Chapter 3: Complications"), (450, "Chapter 4: Resolution"), (600, "Chapter 5: Epilogue")]),
    "book-2": dict(title="A Short Story", subtitle=None, tracks=[(90, 600)], chapters=[(0, "Only Chapter")]),
}
for bid, b in BOOKS.items():
    b["files"] = [wav(s, f) for s, f in b["tracks"]]
    b["duration"] = float(sum(s for s, _ in b["tracks"]))
progress = {"book-1": 0.4 * BOOKS["book-1"]["duration"]}

def ino(bid, i): return f"{bid}-f{i}"
def prog_json(bid):
    d = BOOKS[bid]["duration"]; t = progress[bid]
    return dict(id=f"mp-{bid}", libraryItemId=bid, episodeId=None, duration=d, progress=t / d, currentTime=t,
                isFinished=False, hideFromContinueListening=False, lastUpdate=int(time.time() * 1000), startedAt=NOW, finishedAt=None)
def min_item(bid):
    b = BOOKS[bid]
    return dict(id=bid, libraryId=LIB, mediaType="book", addedAt=NOW, media=dict(
        id=bid + "-media", numChapters=len(b["chapters"]), duration=b["duration"],
        metadata=dict(title=b["title"], subtitle=b["subtitle"], seriesName=None, authorName="A. Author", author="A. Author")))
def full_item(bid):
    b = BOOKS[bid]; ends = [c[0] for c in b["chapters"][1:]] + [int(b["duration"])]
    files = []
    for i, (data, (secs, _)) in enumerate(zip(b["files"], b["tracks"]), start=1):
        files.append(dict(index=i, ino=ino(bid, i), duration=float(secs), mimeType="audio/x-wav",
                          metadata=dict(filename=f"{i:02d}.wav", ext=".wav", size=len(data)), metaTags=dict(tagTitle=f"Track {i}")))
    return dict(id=bid, ino=bid + "-ino", libraryId=LIB, mediaType="book", addedAt=NOW, ctimeMs=NOW, updatedAt=NOW, media=dict(
        duration=b["duration"],
        metadata=dict(title=b["title"], subtitle=b["subtitle"], authors=[dict(id="author-1", name="A. Author")], narrators=["N. Narrator"],
                      genres=["Test"], series=[], description="A generated book used to test playback.", publisher="Mock Press", publishedYear="2024"),
        audioFiles=files,
        chapters=[dict(id=i, start=float(s), end=float(e), title=t) for i, ((s, t), e) in enumerate(zip(b["chapters"], ends))]))

def route(method, path, q, body):
    """Returns (status, obj) for JSON endpoints, or None."""
    if path == "/status": return 200, dict(isInit=True, language="en-us", authMethods=["local"], authFormData={})
    if path == "/ping": return 200, dict(success=True)
    if path in ("/login", "/auth/refresh") and method == "POST":
        u = dict(id=USER, username="demo", type="admin", token="tok", accessToken="mock-access", refreshToken="mock-refresh", mediaProgress=[], bookmarks=[])
        return 200, dict(user=u, userDefaultLibraryId=LIB, serverSettings=dict(version="2.17.0", buildNumber=1))
    if path == "/api/authorize": return 200, dict(user=dict(id=USER, username="demo"), userDefaultLibraryId=LIB, serverSettings=dict(version="2.17.0", buildNumber="1"))
    if path == "/api/me": return 200, dict(id=USER, username="demo", type="admin", mediaProgress=[prog_json(b) for b in progress], bookmarks=[])
    m = re.fullmatch(r"/api/me/progress/([^/]+)", path)
    if m: return (200, prog_json(m[1])) if m[1] in progress else (404, "Not found")
    if re.fullmatch(r"/api/me/item/[^/]+/bookmark", path) and method == "POST":
        return 200, dict(libraryItemId=path.split("/")[4], time=float(body.get("time", 0)), title=body.get("title", ""), createdAt=int(time.time() * 1000))
    if re.fullmatch(r"/api/me/item/[^/]+/bookmark/\d+", path) and method == "DELETE": return 200, {}
    if path == "/api/libraries": return 200, dict(libraries=[dict(id=LIB, name="Audiobooks", mediaType="book", displayOrder=1, folders=[], provider="audible")])
    m = re.fullmatch(r"/api/libraries/([^/]+)", path)
    if m: return 200, dict(library=dict(id=LIB, name="Audiobooks", mediaType="book", displayOrder=1),
                           filterdata=dict(authors=[dict(id="author-1", name="A. Author")], genres=["Test"], tags=[], series=[]))
    m = re.fullmatch(r"/api/libraries/[^/]+/(\w+)", path)
    if m:
        k = m[1]
        if k == "personalized":
            ents = [min_item(b) for b in BOOKS]
            return 200, [dict(id="continue-listening", label="Continue Listening", labelStringKey="LabelContinueListening", type="book", entities=[min_item("book-1")]),
                         dict(id="recently-added", label="Recently Added", labelStringKey="LabelRecentlyAdded", type="book", entities=ents)]
        if k == "items":
            items = [min_item(b) for b in BOOKS]
            return 200, dict(results=items, total=len(items), limit=int(q.get("limit", 0)), page=int(q.get("page", 0)))
        if k == "authors": return 200, dict(results=[dict(id="author-1", name="A. Author", numBooks=2)], page=0, total=1)
        if k == "stats": return 200, dict(genresWithCount=[dict(genre="Test", count=2)])
        if k == "narrators": return 200, dict(narrators=[dict(name="N. Narrator", numBooks=2)])
        if k == "playlists": return 200, dict(results=[], total=0)
        if k == "search": return 200, dict(book=[], authors=[], series=[])
    if path == "/api/items/batch/get":
        return 200, dict(libraryItems=[min_item(i) for i in body.get("libraryItemIds", []) if i in BOOKS])
    m = re.fullmatch(r"/api/authors/([^/]+)", path)
    if m: return 200, dict(id="author-1", name="A. Author", libraryItems=[min_item(b) for b in BOOKS])
    m = re.fullmatch(r"/api/items/([^/]+)", path)
    if m: return (200, full_item(m[1])) if m[1] in BOOKS else (404, "Not found")
    m = re.fullmatch(r"/api/items/([^/]+)/play(/[^/]+)?", path)
    if m and method == "POST":
        bid = m[1]
        if bid not in BOOKS: return 404, "Not found"
        full = full_item(bid)
        return 200, dict(id=f"sess-{bid}", userId=USER, libraryItemId=bid, mediaType="book", playMethod=0, currentTime=progress[bid],
                         duration=BOOKS[bid]["duration"], displayTitle=BOOKS[bid]["title"], displayAuthor="A. Author", chapters=full["media"]["chapters"],
                         audioTracks=[dict(index=f["index"], startOffset=sum(x["duration"] for x in full["media"]["audioFiles"][: f["index"] - 1]), duration=f["duration"],
                                           title=f["metadata"]["filename"], contentUrl=f"/api/items/{bid}/file/{f['ino']}", mimeType=f["mimeType"]) for f in full["media"]["audioFiles"]])
    m = re.fullmatch(r"/api/session/([^/]+)/(sync|close)", path)
    if m and method == "POST":
        bid = m[1].removeprefix("sess-")
        if m[2] == "sync" and bid in progress and "currentTime" in body: progress[bid] = float(body["currentTime"])
        return 200, {}
    if path == "/api/session/local-all" and method == "POST":
        return 200, dict(results=[dict(id=s["id"], success=True) for s in body.get("sessions", [])])
    return None

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass
    def log_request(self, code="-", size="-"):
        if LOG:
            with open(LOG, "a") as f: f.write(f"{time.strftime('%H:%M:%S')} {self.command} {self.path} {code}\n")
    def send_bytes(self, code, data, ctype, extra=None, head=False):
        self.send_response(code)
        self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(data)))
        for k, v in (extra or {}).items(): self.send_header(k, v)
        self.end_headers()
        if not head: self.wfile.write(data)
    def handle_one(self):
        u = urlparse(self.path); q = dict(p.split("=", 1) for p in u.query.split("&") if "=" in p)
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        try: body = json.loads(raw) if raw else {}
        except ValueError: body = {}
        head = self.command == "HEAD"
        m = re.fullmatch(r"/api/items/([^/]+)/cover", u.path)
        if m and m[1] in BOOKS: return self.send_bytes(200, COVER, "image/png", head=head)
        if re.fullmatch(r"/api/authors/[^/]+/image", u.path): return self.send_bytes(200, COVER, "image/png", head=head)
        m = re.fullmatch(r"/api/items/([^/]+)/file/([^/]+)", u.path)
        if m:
            b = BOOKS.get(m[1]); idx = int(m[2].rsplit("-f", 1)[-1]) - 1 if b and m[2].startswith(m[1] + "-f") else -1
            if not b or not 0 <= idx < len(b["files"]): return self.send_bytes(404, b"Not found", "text/plain")
            data = b["files"][idx]; size = len(data)
            hdr = {"Accept-Ranges": "bytes"}
            r = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
            if not r: return self.send_bytes(200, data, "audio/wav", hdr, head)
            a, e = r[1], r[2]
            start, end = (max(0, size - int(e)), size - 1) if not a and e else (int(a or 0), min(int(e), size - 1) if e else size - 1)
            if start >= size or start > end: return self.send_bytes(416, b"", "audio/wav", {"Content-Range": f"bytes */{size}"}, head)
            hdr["Content-Range"] = f"bytes {start}-{end}/{size}"
            return self.send_bytes(206, data[start:end + 1], "audio/wav", hdr, head)
        res = route(self.command, u.path, q, body)
        if res is None: return self.send_bytes(404, b"Not found", "text/plain", head=head)
        code, obj = res
        if isinstance(obj, str): return self.send_bytes(code, obj.encode(), "text/plain", head=head)
        self.send_bytes(code, json.dumps(obj).encode(), "application/json", head=head)
    def do(self):
        try: self.handle_one()
        except (BrokenPipeError, ConnectionResetError): pass
    do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = do_HEAD = do

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 13378
    ThreadingHTTPServer.daemon_threads = True
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
