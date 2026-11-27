import assert from "node:assert/strict";
import test from "node:test";
import { manifest, parseRoute, toStremioStream } from "../src/protocol.mjs";

test("Stremio sends IMDb IDs for supported movies and episodes", () => {
  assert.deepEqual(manifest.types, ["movie", "series"]);
  assert.deepEqual(parseRoute("/stream/movie/tt0084516.json"), {
    kind: "movie", imdbId: "tt0084516",
  });
  assert.deepEqual(parseRoute("/stream/series/tt0903747:1:1.json"), {
    kind: "tv", imdbId: "tt0903747", season: 1, episode: 1,
  });
  assert.equal(parseRoute("/stream/series/tt0903747.json"), null);
});

test("Cloudstream passes a fixed Movy title or episode path", () => {
  assert.deepEqual(parseRoute("/resolve/movie/609"), { kind: "resolve", path: "/movie/609" });
  assert.deepEqual(parseRoute("/resolve/tv/1396/1/1"), { kind: "resolve", path: "/tv/1396/1/1" });
  assert.equal(parseRoute("/resolve/https://example.com"), null);
});

test("Stremio uses the headers needed for native playback", () => {
  const headers = { Referer: "https://www.movy.sx/", Origin: "https://www.movy.sx", "User-Agent": "test" };
  const url = "https://example.com/stream.m3u8";
  assert.deepEqual(toStremioStream({ url, type: "hls", headers }), {
    name: "Movy",
    description: "HLS",
    url,
    behaviorHints: { notWebReady: true, proxyHeaders: { request: headers } },
  });
});
