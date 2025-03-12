package ben.smith53

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class StrimsyExtractor : ExtractorApi() {
    override val mainUrl = "https://strimsy.top"
    override val name = "Strimsy"
    override val requiresReferer = true
    
    private val userAgent = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to (referer ?: mainUrl),
            "User-Agent" to userAgent
        )
        
        // Fetch main page
        val matchPage = app.get(url, headers = headers).text
        
        // Extract iframe src
        val iframeSrc = Regex("""iframe src=["'](/live/[^"']+)["']""")
            .find(matchPage)?.groupValues?.get(1)
            ?: return
            
        val iframeUrl = "$mainUrl$iframeSrc"
        
        // Fetch iframe content
        val iframeResponse = app.get(iframeUrl, headers = headers).text
        
        // Extract m3u8 URL
        val m3u8Url = Regex("""playbackURL = ["']([^"']+)["']""")
            .find(iframeResponse)?.groupValues?.get(1)
            ?: return
            
        val finalHeaders = mapOf(
            "Referer" to iframeUrl,
            "Origin" to mainUrl,
            "User-Agent" to userAgent
        )
        
        callback(
            ExtractorLink(
                source = name,
                name = name,
                url = m3u8Url,
                referer = iframeUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = finalHeaders
            )
        )
    }
}
