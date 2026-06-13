package app.aaps.pump.omnipod.common.bledriver.metrics

import androidx.annotation.VisibleForTesting
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory

object MetricsWriter {

    private val logger = LoggerFactory.getLogger("dash-metrics")

    // Routed through the root appender (i.e. AndroidAPS.log) so failures inside the
    // dedicated metrics pipeline still surface where developers and testers look.
    private val fallbackLogger = LoggerFactory.getLogger(MetricsWriter::class.java)
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    /**
     * Test-only override. When non-null, write() routes JSON lines to this sink instead
     * of the SLF4J logger — avoiding any logback runtime dependency in unit tests.
     */
    @VisibleForTesting
    var testSink: ((String) -> Unit)? = null

    fun write(event: LinkedHashMap<String, Any?>) {
        if (!MetricsConfig.METRICS_ENABLED) return
        try {
            val json = gson.toJson(event)
            val sink = testSink
            if (sink != null) sink(json) else logger.info(json)
        } catch (t: Throwable) {
            try {
                fallbackLogger.error("Failed to write dash-metrics event ${event["event"]}", t)
            } catch (_: Throwable) {
                // Even the fallback logger failed — give up to avoid breaking the driver.
            }
        }
    }
}
