package app.aaps.pump.omnipod.common.bledriver.metrics

/**
 * Test helper that hooks MetricsWriter's test sink and captures every JSON line emitted
 * during [block] into [into].
 */
object TestLogCapture {

    fun capture(into: MutableList<String>, block: () -> Unit) {
        val previous = MetricsWriter.testSink
        MetricsWriter.testSink = { line -> into.add(line) }
        try {
            block()
        } finally {
            MetricsWriter.testSink = previous
            // Reset session so subsequent tests start clean.
            SessionContextHolder.set(null)
        }
    }
}
