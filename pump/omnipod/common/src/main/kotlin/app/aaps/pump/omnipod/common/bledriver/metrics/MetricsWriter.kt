package app.aaps.pump.omnipod.common.bledriver.metrics

import androidx.annotation.VisibleForTesting
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory

object MetricsWriter {

    private val logger = LoggerFactory.getLogger("dash-metrics")
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
        } catch (_: Throwable) {
            // Metrics must never break the driver. Swallow.
        }
    }
}
