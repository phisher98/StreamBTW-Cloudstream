package ben.smith53

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.mainPageOf
import org.jsoup.nodes.Document

class StrimsyStreaming : MainAPI() {
    override var mainUrl = "https://strimsy.top"
    override var name = "Strimsy"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true

    private val eventTranslation = mapOf(
        "pilkanozna" to "Football", "koszykowka" to "Basketball", "kosz" to "Basketball",
        "nba" to "NBA", "hhokej" to "Hockey", "walki" to "Fighting", "kolarstwo" to "Cycling",
        "siatkowka" to "Volleyball", "pilkareczna" to "Handball", "bilard" to "Snooker",
        "tenis" to "Tennis", "skoki" to "Ski Jumping", "magazyn" to "Magazine"
    )

    private val dayTranslation = mapOf(
        "Poniedziałek" to "Monday", "Wtorek" to "Tuesday", "Środa" to "Wednesday",
        "Czwartek" to "Thursday", "Piątek" to "Friday", "Sobota" to "Saturday",
        "Niedziela" to "Sunday"
    )

    override val mainPage = mainPageOf(
        "" to "Events"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val tabs = doc.select(".tab button.tablinks")
        val contents = doc.select(".tabcontent")
        val homePages = mutableListOf<HomePageList>()

        tabs.zip(contents).forEach { (button, content) ->
            val day = dayTranslation[button.text()] ?: button.text()
            val events = content.select("table.ramowka td").mapNotNull { row ->
                val text = row.text().trim()
                val link = row.selectFirst("a[href]") ?: return@mapNotNull null
                val match = Regex("""(\d{2}:\d{2})\s*(.*)""").find(text) ?: return@mapNotNull null
                val (time, nameRaw) = match.destructured
                val className = link.className()
                val eventName = eventTranslation[nameRaw.lowercase()] ?: if (className.isNotEmpty() && className.lowercase() in eventTranslation) {
                    "${eventTranslation[className.lowercase()]}: $nameRaw"
                } else nameRaw
                newLiveSearchResponse(
                    name = "$time - $eventName",
                    url = fixUrl(link.attr("href")),
                    type = TvType.Live
                ) {
                    this.apiName = this@StrimsyStreaming.name
                }
            }
            if (events.isNotEmpty()) {
                homePages.add(HomePageList(day, events))
            }
        }
        return newHomePageResponse(homePages)
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$mainUrl/$url"
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val sources = doc.select("a[href*=\"?source=\"]").map {
            fixUrl(it.attr("href"))
        }.ifEmpty { listOf(url) }

        val streams = sources.flatMap { sourceUrl ->
            val sourceDoc = app.get(sourceUrl).document
            sourceDoc.select("iframe[src]").filter { !it.attr("src").contains("/layout/chat", ignoreCase = true) }
                .map { fixUrl(it.attr("src")) }
        }
        return newLiveStreamLoadResponse(
            name = url.split("/").last().removeSuffix(".php"),
            url = url, // Required parameter
            dataUrl = streams.first() // Pass first iframe URL to extractor
        ) {
            this.apiName = this@StrimsyStreaming.name
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        StrimsyExtractor().getUrl(data, referer = mainUrl).forEach(callback)
        return true
    }
}
