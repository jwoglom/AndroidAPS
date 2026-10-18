package app.aaps.pump.tandem.common.comm.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse

/** Why a Resume insulin request was not sent, or was refused by the pump. UI can show this. */
enum class ResumeBlockedReason {
    /** The pump has no personal profile (insulin delivery profile). */
    NO_PERSONAL_PROFILE,

    /** The profile on the pump could not be read, so Resume was not sent. */
    PROFILE_CHECK_FAILED,

    /** Resume was sent and the pump answered with an error status. */
    PUMP_REJECTED
}

/**
 * Holds a Resume request until the pump confirms that it has an active personal profile.
 * Without a profile the pump refuses to resume, so Resume is not sent in that case.
 *
 * Flow: [requestResume] asks the pump for its profile status. [onProfileStatus] then sends the
 * held Resume request, or reports why not. If no answer comes in time, Resume is not sent.
 */
class ResumeProfileGate(
    private val aapsLogger: AAPSLogger,
    private val send: (Message) -> Boolean,
    private val onBlocked: (ResumeBlockedReason) -> Unit,
    private val scheduleTimeout: (delayMs: Long, task: () -> Unit) -> Unit
) {

    private val lock = Any()
    private var pending: Message? = null
    private var generation = 0

    /** Starts the check. Returns false if the check could not even be started. */
    fun requestResume(resume: Message): Boolean {
        val token = synchronized(lock) {
            pending = resume
            ++generation
        }
        if (!send(ProfileStatusRequest())) {
            synchronized(lock) { pending = null }
            aapsLogger.error(LTag.PUMPCOMM, "Resume not sent: profile status could not be requested.")
            onBlocked(ResumeBlockedReason.PROFILE_CHECK_FAILED)
            return false
        }
        scheduleTimeout(CHECK_TIMEOUT_MS) { expire(token) }
        return true
    }

    /** Called with every profile status response. Does nothing if no Resume is waiting. */
    fun onProfileStatus(status: ProfileStatusResponse) {
        val resume = synchronized(lock) {
            val held = pending
            pending = null
            held
        } ?: return

        val reason = evaluate(status)
        if (reason != null) {
            aapsLogger.error(LTag.PUMPCOMM, "Resume not sent: $reason (numberOfProfiles=${status.numberOfProfiles}, idpSlot0Id=${status.idpSlot0Id}).")
            onBlocked(reason)
            return
        }

        if (!send(resume)) {
            aapsLogger.error(LTag.PUMPCOMM, "Resume could not be sent after the profile check.")
            onBlocked(ResumeBlockedReason.PROFILE_CHECK_FAILED)
        }
    }

    private fun expire(token: Int) {
        val expired = synchronized(lock) {
            if (generation == token && pending != null) {
                pending = null
                true
            } else false
        }
        if (expired) {
            aapsLogger.error(LTag.PUMPCOMM, "Resume not sent: no answer to the profile status request.")
            onBlocked(ResumeBlockedReason.PROFILE_CHECK_FAILED)
        }
    }

    companion object {

        const val CHECK_TIMEOUT_MS = 15_000L

        /** Returns null if the pump has an active profile (slot 0 always holds the active one), else the reason. */
        fun evaluate(status: ProfileStatusResponse): ResumeBlockedReason? =
            if (status.numberOfProfiles <= 0 || status.idpSlot0Id == -1) ResumeBlockedReason.NO_PERSONAL_PROFILE else null
    }
}
