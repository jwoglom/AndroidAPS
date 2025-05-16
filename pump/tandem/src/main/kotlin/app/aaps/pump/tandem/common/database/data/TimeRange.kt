package app.aaps.pump.tandem.common.database.data

// TODO do translation
@Deprecated("Different class used")
enum class TimeRange(val description: String) {
    LAST_HOUR("Last Hour"),
    TODAY("Today"),
    LAST_DAY("Last Day"),
    LAST_2_DAYS("Last 2 Days"),
    LAST_WEEK("Last Week"),
    LAST_MONTH("Last Month"),
    THIS_MONTH("This Month");

    fun getDisplayValue(): String {
        return description
    }


}