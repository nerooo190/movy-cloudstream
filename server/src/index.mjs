import { createServer } from "node:http";
import { createResolver, ResolverBusy } from "./resolver.mjs";
import { manifest, parseRoute, toStremioStream } from "./protocol.mjs";

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 7000);
const token = process.env.MOVY_ACCESS_TOKEN?.trim()
  .replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
if (token && !/^[A-Za-z0-9_-]+$/.test(token)) {
  throw new Error("MOVY_ACCESS_TOKEN muss aus Buchstaben, Ziffern, _ oder - bestehen.");
}

const resolver = await createResolver();
const server = createServer(async (request, response) => {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  response.setHeader("Cache-Control", "no-store");

  function send(status, data) {
    response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
    response.end(JSON.stringify(data));
  }

  if (request.method === "OPTIONS") {
    response.writeHead(204).end();
    return;
  }
  if (request.method !== "GET") return send(405, { error: "Method not allowed" });

  let path;
  try {
    path = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  } catch {
    return send(400, { error: "Invalid path" });
  }
  if (path === "/health") return send(200, { status: "ok" });

  if (token) {
    const prefix = "/" + token;
    if (!path.startsWith(prefix + "/")) return send(404, { error: "Not found" });
    path = path.slice(prefix.length);
  }

  const route = parseRoute(path);
  if (!route) return send(404, { error: "Not found" });
  if (route.kind === "manifest") return send(200, manifest);

  try {
    if (route.kind === "resolve") {
      return send(200, { stream: await resolver.resolve(route.path) });
    }

    const tmdb = await resolver.findImdb(route.imdbId, route.kind);
    if (tmdb === null) return send(200, { streams: [] });
    const playerPath = route.kind === "movie"
      ? "/movie/" + tmdb
      : "/tv/" + tmdb + "/" + route.season + "/" + route.episode;
    const source = await resolver.resolve(playerPath);
    return send(200, { streams: source ? [toStremioStream(source)] : [] });
  } catch (error) {
    if (error instanceof ResolverBusy) {
      response.setHeader("Retry-After", "3");
      return send(503, { error: "Resolver busy" });
    }
    return send(502, { error: "Upstream unavailable" });
  }
});

server.listen(port, host, () => console.log("Movy resolver listening on " + host + ":" + port));

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, async () => {
    server.close();
    await resolver.close();
  });
}
