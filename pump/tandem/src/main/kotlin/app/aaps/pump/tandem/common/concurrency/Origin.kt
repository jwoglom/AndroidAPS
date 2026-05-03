package app.aaps.pump.tandem.common.concurrency

/**
 * Submission origin determines insertion point in the pump op queue.
 *
 * USER ops jump ahead of all queued AAPS ops (FIFO within origin) so UI taps
 * never wait behind background AAPS-queued work.
 */
enum class Origin {
    USER,
    AAPS
}
