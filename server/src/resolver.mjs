import { chromium, request } from "playwright";

const manifestExtensions = /\.(m3u8|mpd)$/i;
const maxSessions = Math.max(1, Number(process.env.MOVY_BROWSER_SESSIONS) || 3);
const sourceTimeoutMs = 7_000;

export class ResolverBusy extends Error {}

export async function createResolver() {
  const proxy = process.env.HTTPS_PROXY
    ? { server: process.env.HTTPS_PROXY }
    : undefined;
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || undefined,
    proxy,
    args: ["--autoplay-policy=no-user-gesture-required"],
  });
  const api = await request.newContext({ proxy });
  const active = new Map();

  async function findImdb(imdbId, kind) {
    const response = await api.get("https://db.wecollege.net/3/find/" + imdbId, {
      params: { external_source: "imdb_id" },
      timeout: 4_000,
    });
    try {
      if (response.status() === 404) return null;
      if (!response.ok()) throw new Error("Fehler bei der IMDb-Zuordnung.");
      const data = await response.json();
      const id = data[kind === "movie" ? "movie_results" : "tv_results"]?.[0]?.id;
      return Number.isSafeInteger(id) && id > 0 ? id : null;
    } finally {
      await response.dispose();
    }
  }

  function resolve(path) {
    if (active.has(path)) return active.get(path);
    if (active.size >= maxSessions) throw new ResolverBusy();

    const pending = (async () => {
      const context = await browser.newContext({ viewport: { width: 960, height: 540 } });
      try {
        return await findStream(context, "https://www.movy.sx/de" + path + "?play=true")
          ?? await findStream(context, "https://www.vidy.st" + path + "?autoplay=true");
      } finally {
        await context.close();
      }
    })().finally(() => active.delete(path));

    active.set(path, pending);
    return pending;
  }

  return {
    resolve,
    findImdb,
    async close() {
      await Promise.all([api.dispose(), browser.close()]);
    },
  };
}

async function findStream(context, playerUrl) {
  const page = await context.newPage();
  const origin = new URL(playerUrl).origin;
  const result = Promise.withResolvers();
  const timer = setTimeout(() => result.resolve(null), sourceTimeoutMs);

  async function inspect(response) {
    const url = new URL(response.url());
    const extension = url.pathname.match(manifestExtensions)?.[1].toLowerCase();
    if (!extension || !response.ok() || url.protocol !== "https:") return;

    try {
      const body = (await response.text()).trimStart();
      const valid = extension === "m3u8" ? body.startsWith("#EXTM3U") : /<MPD[\s>]/.test(body);
      if (!valid) return;

      const headers = response.request().headers();
      result.resolve({
        url: url.href,
        type: extension === "m3u8" ? "hls" : "dash",
        headers: {
          Origin: headers.origin || origin,
          Referer: headers.referer || origin + "/",
          "User-Agent": headers["user-agent"] || await page.evaluate(() => navigator.userAgent),
        },
      });
    } catch {
      // A failed request can be followed by another playlist from the player.
    }
  }

  try {
    page.on("response", inspect);
    page.goto(playerUrl, { waitUntil: "commit", timeout: sourceTimeoutMs }).catch(() => result.resolve(null));
    return await result.promise;
  } finally {
    clearTimeout(timer);
    page.off("response", inspect);
    await page.close();
  }
}
