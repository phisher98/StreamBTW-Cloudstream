// use an integer for version numbers
version = 5


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "Live streams from the StreamBTW."
    authors = listOf("Ben")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://streambtw.com/favicon.ico"
}
