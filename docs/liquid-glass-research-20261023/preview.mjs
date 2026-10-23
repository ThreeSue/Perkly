// Frontend-only preview. Serves a fixed list of research assets on loopback.
import http from "node:http";
import { readFile } from "node:fs/promises";
const files = new Map([
  ["/", ["index.html", "text/html; charset=utf-8"]],
  ["/index.html", ["index.html", "text/html; charset=utf-8"]],
  ["/lab.css", ["lab.css", "text/css; charset=utf-8"]],
  ["/lab.js", ["lab.js", "text/javascript; charset=utf-8"]],
]);
http
  .createServer(async (req, res) => {
    const file = files.get(new URL(req.url, "http://127.0.0.1").pathname);
    if (!file) {
      res.writeHead(404).end();
      return;
    }
    try {
      const body = await readFile(new URL(file[0], import.meta.url));
      res
        .writeHead(200, {
          "Content-Type": file[1],
          "Cache-Control": "no-store",
        })
        .end(body);
    } catch {
      res.writeHead(500).end();
    }
  })
  .listen(9904, "127.0.0.1", () =>
    console.log("折射实验 http://127.0.0.1:9904"),
  );
