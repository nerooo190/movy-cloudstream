export const manifest = {
  id: "de.movy.stremio",
  version: "1.0.0",
  name: "Movy",
  description: "Movy-Quellen für Filme und Serien.",
  resources: ["stream"],
  types: ["movie", "series"],
  idPrefixes: ["tt"],
  catalogs: [],
};

const movyRoute = /^\/resolve(\/movie\/[1-9]\d{0,8}|\/tv\/[1-9]\d{0,8}\/\d{1,3}\/[1-9]\d{0,3})$/;
const stremioMovie = /^\/stream\/movie\/(tt\d{7,12})\.json$/;
const stremioEpisode = /^\/stream\/series\/(tt\d{7,12}):(\d{1,3}):([1-9]\d{0,3})\.json$/;

export function parseRoute(path) {
  if (path === "/manifest.json") return { kind: "manifest" };

  const movy = path.match(movyRoute);
  if (movy) return { kind: "resolve", path: movy[1] };

  const movie = path.match(stremioMovie);
  if (movie) return { kind: "movie", imdbId: movie[1] };

  const episode = path.match(stremioEpisode);
  if (episode) {
    const [, imdbId, season, number] = episode;
    return { kind: "tv", imdbId, season: Number(season), episode: Number(number) };
  }
  return null;
}

export function toStremioStream(source) {
  return {
    name: "Movy",
    description: source.type === "hls" ? "HLS" : "DASH",
    url: source.url,
    behaviorHints: {
      notWebReady: true,
      proxyHeaders: { request: source.headers },
    },
  };
}
