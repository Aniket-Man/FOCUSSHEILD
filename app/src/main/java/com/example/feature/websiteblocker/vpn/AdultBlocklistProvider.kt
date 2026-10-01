package com.example.feature.websiteblocker.vpn

import com.example.feature.websiteblocker.engine.AdultLexicalClassifier

/**
 * Provider for built-in 18+ domain blocklists and trusted allowlists.
 */
object AdultBlocklistProvider {

    val ADULT_TLDS = setOf(
        "xxx", "porn", "adult", "sex", "sexy", "cam", "webcam",
        "fetish", "erotica", "tube"
    )

    val DEFAULT_ALLOWLIST = setOf(
        // Educational & Government
        "wikipedia.org", "wikimedia.org", "khanacademy.org", "coursera.org",
        "edx.org", "mit.edu", "stanford.edu", "harvard.edu", "nih.gov", "cdc.gov",
        "who.int", "nasa.gov", "gov.uk", "whitehouse.gov",
        // Search & Infrastructure
        "google.com", "google.co.in", "google.co.uk", "bing.com", "duckduckgo.com",
        "yahoo.com", "cloudflare.com", "one.one.one.one", "dns.google",
        // Major Tech & Productivity
        "github.com", "githubusercontent.com", "gitlab.com", "stackoverflow.com",
        "stackexchange.com", "microsoft.com", "apple.com", "android.com",
        "amazon.com", "aws.amazon.com", "slack.com", "notion.so", "zoom.us",
        // Common false-positive domains containing adult substrings
        "essex.gov.uk", "sussex.ac.uk", "middlesex.edu", "peacocktv.com",
        "cocktailflow.com", "analytica.org", "titans.com", "dickssportinggoods.com"
    )

    // Curated high-impact adult domain corpus
    val CORE_ADULT_DOMAINS = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "brazzers.com", "thumbzilla.com", "spankbang.com", "rule34.xxx",
        "chaturbate.com", "onlyfans.com", "stripchat.com", "livejasmin.com", "beeg.com",
        "eporner.com", "xhamster2.com", "tub8.com", "sunporno.com", "tnaflix.com",
        "drtuber.com", "eroprofile.com", "motherless.com", "heavy-r.com", "redytube.com",
        "faphouse.com", "camsoda.com", "cam4.com", "bongacams.com", "myfreecams.com",
        "flirt4free.com", "javhd.com", "porntrex.com", "adultfriendfinder.com", "fetlife.com",
        "hqporner.com", "porn.com", "xrated.com", "hentaihaven.org", "nhentai.net",
        "hanime.tv", "multporn.net", "gelbooru.com", "e-hentai.org", "luscious.net",
        "youjizz.com", "txxx.com", "tube8.com", "fuq.com", "daftsex.com",
        "xcafe.com", "slutload.com", "playvids.com", "anysex.com", "xfree.com",
        "porngo.com", "porndig.com", "hdporn92.com", "pornone.com", "javlibrary.com",
        "missav.com", "javsub.co", "fapello.com", "coomer.party", "coomer.su",
        "kemono.party", "kemono.su", "rule34.paheal.net", "danbooru.donmai.us", "hypnohub.net",
        "fakku.net", "hitomi.la", "tsumino.com", "simply-hentai.com", "pururin.io",
        "hentai2read.com", "hentaifox.com", "hentaihere.com", "doujins.com", "asmhentai.com",
        "freeones.com", "clips4sale.com", "manyvids.com", "camwhores.tv", "camwhores.to",
        "streamate.com", "imlive.com", "liveprivates.com", "jasmin.com", "fancentric.com",
        "fansly.com", "loyalfans.com", "justforfans.com", "pornhd.com", "porntube.com",
        "4tube.com", "pornmd.com", "nuvid.com", "pornerbros.com", "porn300.com",
        "badjojo.com", "cumlouder.com", "upornia.com", "zenra.net", "alohatube.com",
        "empflix.com", "pornrox.com", "vshare.io", "xxxbunker.com", "porndoe.com",
        "hclips.com", "pornhat.com", "pornky.com", "shemalez.com", "trannyvideosx.com",
        "gayporno.tv", "manhub.com", "boyfriendtv.com", "xtube.com", "keezmovies.com",
        "extremetube.com", "spankwire.com", "drsubno.com", "tubeplus.me", "mofosex.com",
        "porn7.xxx", "javfinder.biz", "javmenu.com", "javhihi.world", "subjav.com",
        "bravoteens.com", "pornktube.com", "pornhoarder.tv", "vrporn.com", "sexlikereal.com",
        "wankzvr.com", "naughtyamerica.com", "realitykings.com", "mofos.com", "bangbros.com",
        "twistys.com", "digitalplayground.com", "wicked.com", "evilangel.com", "hustler.com",
        "playboy.com", "penthouse.com", "babes.com", "rk.com", "blacked.com",
        "tushy.com", "vixen.com", "deeper.com", "slayxx.com", "julesjordan.com",
        "teamskeet.com", "pervertgallery.com", "porngem.com", "pornobae.com", "fapvid.com",
        "porndish.com", "xxvideoss.org", "xvideos2.com", "xnxx2.com", "xhamster1.com",
        "xhamster3.com", "pornhubpremium.com", "pornhub.org", "redtube.net", "youporn.net",
        "taboolink.com", "pornzog.com", "porntop.com", "bestporn.com", "hdsex.com",
        "freeporn.com", "tubegalore.com", "porncore.com", "fucktube.com", "xporn.to",
        "xmovies.to", "sexhub.com", "porn555.com", "pornbraze.com", "fapbox.com",
        "fapfolder.com", "fappinghd.com", "pornwatch.ws", "fullporner.com", "erome.com",
        "anonib.al", "cyberdrop.me", "bunkr.is", "bunkr.la", "bunkr.si", "coomer.fan",
        "phncdn.com", "xhcdn.com", "ypncdn.com", "rtcdn.net"
    )

    /**
     * Creates and populates a new [DomainMatcher] with all 18+ domains, adult TLDs,
     * allowlists, and optional user-specified custom blocked domains.
     */
    fun createMatcher(customBlocked: Collection<String> = emptyList()): DomainMatcher {
        val matcher = DomainMatcher()

        // 1. Populate allowlist first
        matcher.addAllowedDomains(DEFAULT_ALLOWLIST)

        // 2. Populate core adult domains
        matcher.addBlockedDomains(CORE_ADULT_DOMAINS)

        // 3. User custom domains
        matcher.addBlockedDomains(customBlocked)

        return matcher
    }

    /**
     * Checks if a domain has an adult TLD (e.g. .xxx, .porn).
     */
    fun hasAdultTld(domain: String): Boolean {
        val lastDot = domain.lastIndexOf('.')
        if (lastDot == -1 || lastDot == domain.length - 1) return false
        val tld = domain.substring(lastDot + 1).lowercase()
        return ADULT_TLDS.contains(tld)
    }
}
