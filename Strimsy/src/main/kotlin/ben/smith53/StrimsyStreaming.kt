package ben.smith53

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.*

class StrimsyStreaming : MainAPI() {
    override var mainUrl = "https://strimsy.top"
    override var name = "StrimsyStreaming"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(
        TvType.Live
    )

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    private val dayTranslation = mapOf(
        "PONIEDZIAŁEK" to "Monday",
        "WTOREK" to "Tuesday",
        "ŚRODA" to "Wednesday",
        "CZWARTEK" to "Thursday",
        "PIĄTEK" to "Friday",
        "SOBOTA" to "Saturday",
        "NIEDZIELA" to "Sunday"
    )

    private val eventTranslation = mapOf(
        "pilkanozna" to "Football",
        "koszykowka" to "Basketball",
        "kosz" to "Basketball",
        "nba" to "NBA",
        "hhokej" to "Hockey",
        "walki" to "Fighting"
    )

    private fun fixUrl(url: String, baseUrl: String = mainUrl): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("/")) {
            "$baseUrl$url"
        } else if (url.startsWith("?team=")) {
            "$baseUrl/NBA/$url"
        } else if (url.startsWith("?source=")) {
            "$baseUrl/f1/$url"
        } else if (!url.startsWith("http")) {
            "$baseUrl/$url"
        } else {
            url
        }
    }

    private fun translateEventName(name: String, className: String?): String {
        val lowerName = name.lowercase(Locale.getDefault())
        return if (eventTranslation.containsKey(lowerName)) {
            eventTranslation[lowerName]!!
        } else if (className != null && eventTranslation.containsKey(className.lowercase(Locale.getDefault()))) {
            "${eventTranslation[className.lowercase(Locale.getDefault())]}: $name"
        } else {
            name
        }
    }

    private fun isChatIframe(iframeUrl: String): Boolean {
        return iframeUrl.contains("/layout/chat") || iframeUrl.contains("/chatWalki2.php") || iframeUrl.contains("/chatWalki1.php")
    }

    private suspend fun getTeamPages(sportUrl: String, cookies: Map<String, String>): List<Pair<String, String>> {
        val headersWithCookies = baseHeaders + mapOf("Cookie" to cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val doc = app.get(sportUrl, headers = headersWithCookies).document
        val teamLinks = mutableListOf<Pair<String, String>>()
        doc.select("a[href^='?team=']").forEach { aTag ->
            val href = fixUrl(aTag.attr("href"))
            val teamName = aTag.previousElementSibling()?.text() ?: aTag.text()
            teamLinks.add(Pair(teamName, href))
        }
        return teamLinks
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Fetch the main page and capture cookies
        val response = app.get(mainUrl, headers = baseHeaders)
        val cookies = response.cookies
        val headersWithCookies = baseHeaders + mapOf("Cookie" to cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val document = response.document

        val tabs = document.select("div.tabcontent")
        if (tabs.isEmpty()) throw ErrorLoadingException("No tabcontent found")

        val homePageLists = mutableListOf<HomePageList>()
        tabs.forEachIndexed { index, tab ->
            val tabButton = document.select("button.tablinks")[index]
            val polishDayName = tabButton.text().trim().uppercase(Locale.getDefault())
            val englishDayName = dayTranslation[polishDayName] ?: polishDayName

            val events = mutableListOf<SearchResponse>()
            tab.select("td").forEach { td ->
                val linkElement = td.selectFirst("a") ?: return@forEach
                val href = fixUrl(linkElement.attr("href"))
                val rawName = linkElement.text().trim()
                val className = linkElement.attr("class").ifEmpty { null }
                val translatedName = translateEventName(rawName, className)
                val time = td.text().split(" ")[0].trim().ifEmpty { "Unknown Time" }
                val eventName = "$time - $translatedName"

                if (href.contains("/NBA/") || href.contains("/NHL/")) {
                    val teamPages = getTeamPages(href, cookies)
                    teamPages.forEach { (teamName, teamUrl) ->
                        events.add(
                            newLiveSearchResponse(
                                name = "$eventName - $teamName",
                                url = teamUrl,
                                type = TvType.Live
                            ) {
                                this.posterUrl = null
                            }
                        )
                    }
                } else {
                    events.add(
                        newLiveSearchResponse(
                            name = eventName,
                            url = href,
                            type = TvType.Live
                        ) {
                            this.posterUrl = null
                        }
                    )
                }
            }

            if (events.isNotEmpty()) {
                homePageLists.add(HomePageList(englishDayName, events, true))
            }
        }

        return newHomePageResponse(homePageLists)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Fetch the page and capture cookies
        val response = app.get(data, headers = baseHeaders)
        val cookies = response.cookies
        val headersWithCookies = baseHeaders + mapOf("Cookie" to cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val doc = response.document

        val iframes = doc.select("iframe")
        if (iframes.isEmpty()) {
            println("StrimsyStreaming: No iframes found on page $data")
            return false
        }

        var linksFound = false
        iframes.forEach { iframe ->
            val iframeUrl = iframe.attr("src").trim()
            if (iframeUrl.isEmpty() || isChatIframe(iframeUrl)) {
                println("StrimsyStreaming: Skipping iframe $iframeUrl (empty or chat)")
                return@forEach
            }

            val fixedIframeUrl = fixUrl(iframeUrl)
            println("StrimsyStreaming: Processing iframe $fixedIframeUrl")
            val extractor = StrimsyExtractor()
            val links = extractor.getUrl(fixedIframeUrl, data, cookies)
            if (links != null) {
                links.forEach { link ->
                    println("StrimsyStreaming: Found link ${link.url}")
                    callback(link)
                    linksFound = true
                }
            } else {
                println("StrimsyStreaming: No links found for iframe $fixedIframeUrl")
            }
        }

        return linksFound
    }
}
