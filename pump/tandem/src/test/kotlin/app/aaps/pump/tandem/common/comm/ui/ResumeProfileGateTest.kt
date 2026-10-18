package app.aaps.pump.tandem.common.comm.ui

import app.aaps.shared.tests.TestBase
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResumeProfileGateTest : TestBase() {

    private val sent = mutableListOf<Message>()
    private val blocked = mutableListOf<ResumeBlockedReason>()
    private var sendOk = true
    private var timeoutTask: (() -> Unit)? = null

    private val gate = ResumeProfileGate(
        aapsLogger = aapsLogger,
        send = { sent.add(it); sendOk },
        onBlocked = { blocked.add(it) },
        scheduleTimeout = { _, task -> timeoutTask = task }
    )

    private val noProfile = ProfileStatusResponse(0, -1, -1, -1, -1, -1, -1, -1)
    private val oneProfile = ProfileStatusResponse(1, 4, -1, -1, -1, -1, -1, 0)

    @Test
    fun noPersonalProfile_resumeIsNotSent() {
        val resume = ResumePumpingRequest()

        assertTrue(gate.requestResume(resume))
        gate.onProfileStatus(noProfile)

        assertEquals(listOf(ResumeBlockedReason.NO_PERSONAL_PROFILE), blocked)
        assertEquals(1, sent.size)
        assertTrue(sent[0] is ProfileStatusRequest)
    }

    @Test
    fun activeProfile_resumeIsSentAfterTheCheck() {
        val resume = ResumePumpingRequest()

        gate.requestResume(resume)
        gate.onProfileStatus(oneProfile)

        assertTrue(blocked.isEmpty())
        assertEquals(2, sent.size)
        assertTrue(sent[0] is ProfileStatusRequest)
        assertTrue(sent[1] === resume)
    }

    @Test
    fun slotZeroEmptyEvenIfCountIsSet_isNoProfile() {
        assertEquals(ResumeBlockedReason.NO_PERSONAL_PROFILE, ResumeProfileGate.evaluate(ProfileStatusResponse(1, -1, -1, -1, -1, -1, -1, -1)))
    }

    @Test
    fun statusRequestCannotBeSent_resumeIsNotSent() {
        sendOk = false

        assertEquals(false, gate.requestResume(ResumePumpingRequest()))

        assertEquals(listOf(ResumeBlockedReason.PROFILE_CHECK_FAILED), blocked)
        assertEquals(1, sent.size)
    }

    @Test
    fun noAnswerInTime_resumeIsNotSent_andLateAnswerIsIgnored() {
        gate.requestResume(ResumePumpingRequest())

        timeoutTask!!.invoke()
        gate.onProfileStatus(oneProfile)

        assertEquals(listOf(ResumeBlockedReason.PROFILE_CHECK_FAILED), blocked)
        assertEquals(1, sent.size)
    }

    @Test
    fun statusWithoutWaitingResume_isIgnored() {
        gate.onProfileStatus(noProfile)

        assertTrue(blocked.isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun answerBeforeTimeout_timeoutDoesNothing() {
        gate.requestResume(ResumePumpingRequest())
        gate.onProfileStatus(oneProfile)

        timeoutTask!!.invoke()

        assertTrue(blocked.isEmpty())
    }
}
