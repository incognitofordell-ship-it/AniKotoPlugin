package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AniKotoProvider : MainAPI() {
    override var mainUrl = "https://anikototv.to"
    override var name = "AniKoto"
    override val supportedTypes = setOf(TvType.Anime)
    override val language = "en"
    override val hasQuickSearch = true

    // Using the public AniKoto API wrapper
    private val apiUrl = "https://anikototvapi.vercel.app/api"
    private val mapper = jacksonObjectMapper()

    // --- Data Classes ---
    data class ApiSearch(
        @JsonProperty("data") val data: List<AnimeItem>?
    )

    data class AnimeItem(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("otherNames") val otherNames: String?
    )

    data class ApiDetail(
        @JsonProperty("data") val data: AnimeDetail?
    )

    data class AnimeDetail(
        @JsonProperty("title") val title: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>?
    )

    data class EpisodeItem(
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("episodeNum") val episodeNum: String?,
        @JsonProperty("title") val title: String?
    )

    data class ApiStream(
        @JsonProperty("data") val data: StreamData?
    )

    data class StreamData(
        @JsonProperty("sources") val sources: List<SourceItem>?
    )

    data class SourceItem(
        @JsonProperty("url") val url: String?,
        @JsonProperty("type") val type: String?
    )

    // --- Search Function ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/search?keyword=$query"
        val response = app.get(url).text
        val results = mapper.readValue(response, ApiSearch::class.java)

        return results.data?.mapNotNull { item ->
            item.slug?.let { slug ->
                AnimeSearchResponse(
                    name = item.title ?: "Unknown",
                    url = slug,
                    this.name,
                    TvType.Anime,
                    fixUrl(item.image ?: ""),
                    year = null,
                    id = item.id,
                    otherNames = item.otherNames
                )
            }
        } ?: emptyList()
    }

    // --- Load Details Function ---
    override suspend fun load(url: String): LoadResponse? {
        val detailUrl = "$apiUrl/anime/$url"
        val response = app.get(detailUrl).text
        val detail = mapper.readValue(response, ApiDetail::class.java).data ?: return null

        val episodeList = detail.episodes?.map { ep ->
            Episode(
                name = ep.title,
                url = "${url}/${ep.episodeId}",
                episode = ep.episodeNum?.toIntOrNull()
            )
        } ?: emptyList()

        return newAnimeLoadResponse(detail.title ?: "Unknown", url, TvType.Anime) {
            posterUrl = fixUrl(detail.image ?: "")
            plot = detail.description
            genres = detail.genres
            addEpisodes(DubStatus.Subbed, episodeList)
        }
    }

    // --- Load Links (Streaming) Function ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("/")
        if (parts.size != 2) return false

        val slug = parts[0]
        val epId = parts[1]

        // Fetch stream URL
        val streamUrl = "$apiUrl/watch/$slug?ep=$epId"
        
        try {
            val response = app.get(streamUrl).text
            val streamData = mapper.readValue(response, ApiStream::class.java).data

            streamData?.sources?.forEach { source ->
                if (!source.url.isNullOrEmpty()) {
                    val isM3u8 = source.type == "m3u8"
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = source.url,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = isM3u8
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return false
        }

        return true
    }
}   
