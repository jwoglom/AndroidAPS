package app.aaps.pump.tandem.t_mobi.ui.actions.other

enum class SendType(val slug: String) {
    STANDARD("commands"),
    BUST_CACHE("commands-bust-cache"),
    CACHED("cached-commands"),
    DEBUG_PROMPT("commands"),
}