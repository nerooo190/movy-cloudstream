package de.movy.cloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class MovyProvider internal constructor(private val playback: MovyPlayback) : MainAPI() {
    override var mainUrl = "https://www.movy.sx"
    override var name = "Movy"
    override var lang = "de"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "trending" to "Diese Woche im Trend",
        "popularMovies" to "Beliebte Filme",
        "popularShowTV" to "Beliebte Serien",
        "recent4k" to "Neu in 4K",
        "topratedmovie" to "Am besten bewertete Filme",
        "topratedtv" to "Am besten bewertete Serien",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val entries = if (page == 1) {
            val props = pageProps("$mainUrl/de")
            when (request.data) {
                "recent4k" -> props.path("recent4k")
                else -> sequenceOf("trendingSections", "defaultSections")
                    .flatMap { props.path(it).asSequence() }
                    .firstOrNull { it.text("name") == request.data }
                    ?.path("movies")
            }
        } else {
            null
        }
        return newHomePageResponse(request.name, entries?.let(::titles).orEmpty(), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return titles(metadata("/search/multi", mapOf("query" to term)).path("results"))
    }

    override suspend fun load(url: String): LoadResponse {
        val route = mediaRoute(url) ?: throw ErrorLoadingException("Ungültiger Movy-Titel.")
        val (kind, id) = route.destructured
        val detailsUrl = "$mainUrl/de/$kind/$id"
        val details = pageProps(detailsUrl).path("details")
        val title = details.text("title") ?: throw ErrorLoadingException("Titel auf Movy nicht gefunden.")

        val response = if (kind == "movie") {
            newMovieLoadResponse(title, detailsUrl, TvType.Movie, dataUrl = detailsUrl) {
                duration = details.path("duration").asInt().takeIf { it > 0 }
            }
        } else {
            val episodes = seriesEpisodes(id, details.path("seasons"))
            newTvSeriesLoadResponse(title, detailsUrl, TvType.TvSeries, episodes)
        }

        return response.apply {
            posterUrl = movyImage(details.text("poster"))
            backgroundPosterUrl = movyImage(details.text("background"))
            year = details.text("year")?.toIntOrNull()
            plot = details.text("desc")
            tags = details.path("categories").mapNotNull { it.asText().takeIf(String::isNotBlank) }
            score = details.rating("score")
            actors = details.path("actors").mapNotNull { entry ->
                val actorName = entry.text("name") ?: return@mapNotNull null
                ActorData(Actor(actorName, movyImage(entry.text("avatar"))), roleString = entry.text("label"))
            }
            recommendations = titles(details.path("recommendations"))
            addTMDbId(id)
            addImdbId(details.text("imdbId"))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val route = mediaRoute(data) ?: return false
        val path = route.value
        val (stream, subtitles) = playback.resolve("$mainUrl/de$path?play=true")
            ?: playback.resolve("https://www.vidy.st$path?autoplay=true")
            ?: return false

        val fileName = stream.url.pathSegments.last()
        val streamType = if (fileName.endsWith(".mpd", ignoreCase = true)) {
            ExtractorLinkType.DASH
        } else {
            ExtractorLinkType.M3U8
        }
        callback(newExtractorLink(name, name, stream.url.toString(), streamType) {
            referer = stream.headers.getValue("Referer")
            headers = stream.headers - "Referer"
            quality = qualityInFileName.find(fileName)?.groupValues?.get(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
        })

        subtitles.forEach { subtitle ->
            val language = subtitle.url.pathSegments.last().substringBeforeLast('.')
            subtitleCallback(newSubtitleFile(language, subtitle.url.toString()) {
                headers = subtitle.headers
            })
        }
        return true
    }

    private suspend fun pageProps(url: String): JsonNode {
        val document = app.get(url).document
        val data = document.selectFirst("script#__NEXT_DATA__")?.data()
        return tryParseJson<JsonNode>(data)?.path("props")?.path("pageProps")
            ?: throw ErrorLoadingException("Movy hat keine Titeldaten geliefert.")
    }

    private suspend fun metadata(path: String, params: Map<String, String> = emptyMap()): JsonNode {
        val response = app.get(
            "https://db.wecollege.net/3$path",
            params = params + ("language" to "de-DE"),
        )
        return tryParseJson<JsonNode>(response.text)
            ?: throw IOException("Ungültige Antwort vom Movy-Katalog.")
    }

    private suspend fun seriesEpisodes(id: String, seasons: JsonNode): List<Episode> = coroutineScope {
        val seasonNumbers = seasons.map { it.path("season_number").asInt(-1) }
            .filter { it >= 0 }.distinct().sorted()

        seasonNumbers.chunked(4).flatMap { batch ->
            batch.map { number ->
                async { seasonEpisodes(id, number) }
            }.awaitAll().flatten()
        }
    }

    private suspend fun seasonEpisodes(id: String, number: Int): List<Episode> {
        val seasonData = try {
            metadata("/tv/$id/season/$number")
        } catch (_: IOException) {
            return emptyList()
        }
        return seasonData.path("episodes").mapNotNull { entry ->
            val episodeNumber = entry.path("episode_number").asInt().takeIf { it > 0 }
                ?: return@mapNotNull null
            newEpisode(url = "$mainUrl/de/tv/$id/$number/$episodeNumber", initializer = {
                season = number
                episode = episodeNumber
                name = entry.text("name")
                description = entry.text("overview")
                posterUrl = tmdbImage(entry.text("still_path"))
                runTime = entry.path("runtime").asInt().takeIf { it > 0 }
                addDate(entry.text("air_date"))
            })
        }
    }

    private fun titles(entries: JsonNode): List<SearchResponse> =
        entries.mapNotNull(::searchResult).distinctBy { it.url }

    private fun searchResult(entry: JsonNode): SearchResponse? {
        val id = entry.path("id").asInt().takeIf { it > 0 } ?: return null
        val kind = entry.text("mediaType") ?: entry.text("media_type")
            ?: entry.text("slug")?.split('/')?.getOrNull(1)
        val title = entry.text("title") ?: entry.text("name") ?: return null
        val link = "$mainUrl/de/$kind/$id"
        val poster = movyImage(entry.text("poster")) ?: tmdbImage(entry.text("poster_path"))
        val rating = entry.rating("rating") ?: entry.rating("vote_average")
        val releaseDate = entry.text("release_date") ?: entry.text("first_air_date")
        val releaseYear = releaseDate?.take(4)?.toIntOrNull()
        return when (kind) {
            "movie" -> newMovieSearchResponse(title, link) {
                posterUrl = poster
                score = rating
                year = releaseYear
            }
            "tv" -> newTvSeriesSearchResponse(title, link) {
                posterUrl = poster
                score = rating
                year = releaseYear
            }
            else -> null
        }
    }

    private fun mediaRoute(url: String): MatchResult? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.host !in setOf("movy.sx", "www.movy.sx")) return null
        val path = parsed.encodedPath.removePrefix("/de").trimEnd('/')
        return titlePath.matchEntire(path)
    }

    private fun movyImage(path: String?): String? = path?.let(::fixUrl)

    private fun tmdbImage(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }

    private fun JsonNode.text(key: String): String? =
        path(key).asText(null)?.takeIf(String::isNotBlank)

    private fun JsonNode.rating(key: String): Score? =
        Score.from(path(key).asDouble().takeIf { it > 0.0 && it <= 10.0 }, 10)

    private companion object {
        val titlePath = Regex("""/(movie|tv)/([1-9]\d*)(?:/\d+/[1-9]\d*)?""")
        val qualityInFileName = Regex("""(?:^|[-_])(2160|1440|1080|720|576|540|480|360)p(?:[-_.]|$)""")
    }
}
