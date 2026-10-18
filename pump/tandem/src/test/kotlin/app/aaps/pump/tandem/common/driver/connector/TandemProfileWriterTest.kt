package app.aaps.pump.tandem.common.driver.connector

import app.aaps.pump.tandem.common.data.IDPSegmentDto
import app.aaps.shared.tests.TestBase
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest.IDPSegmentOperation
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CreateIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetActiveIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses a small fake pump with synthetic data only. */
class TandemProfileWriterTest : TestBase() {

    private class Seg(var start: Int, var basal: Int, var carb: Long, var target: Int, var isf: Int)

    private class FakeProfile(val id: Int, var name: String, var duration: Int, val segments: MutableList<Seg>)

    private class FakePump {

        val profiles = mutableListOf<FakeProfile>() // index = slot, slot 0 is active
        var nextId = 3
        val seen = mutableListOf<Message>()

        /** Lets a test replace the answer to a request. Return a non-null pair to override. */
        var override: (Message) -> Pair<Boolean, Message?>? = { null }

        /** Runs after every segment write, so a test can damage the stored data. */
        var afterSegmentWrite: (FakePump) -> Unit = {}

        fun handle(request: Message): Message? {
            seen.add(request)
            override(request)?.let { return it.second }
            return when (request) {
                is ProfileStatusRequest -> {
                    val ids = IntArray(6) { profiles.getOrNull(it)?.id ?: -1 }
                    ProfileStatusResponse(profiles.size, ids[0], ids[1], ids[2], ids[3], ids[4], ids[5], if (profiles.isEmpty()) -1 else 0)
                }
                is IDPSettingsRequest   -> {
                    val p = profiles.first { it.id == request.idpId }
                    IDPSettingsResponse(p.id, p.name, p.segments.size, p.duration, 0, false)
                }
                is IDPSegmentRequest    -> {
                    val p = profiles.first { it.id == request.idpId }
                    val s = p.segments[request.segmentIndex]
                    IDPSegmentResponse(p.id, request.segmentIndex, s.start, s.basal, s.carb, s.target, s.isf, 31)
                }
                is CreateIDPRequest     -> {
                    val p = FakeProfile(
                        nextId++, request.profileName, request.profileInsulinDuration,
                        mutableListOf(Seg(0, request.firstSegmentProfileBasalRate, request.firstSegmentProfileCarbRatio.toLong(), request.firstSegmentProfileTargetBG, request.firstSegmentProfileISF))
                    )
                    profiles.add(0, p)
                    CreateIDPResponse(0, p.id)
                }
                is SetIDPSegmentRequest -> {
                    val p = profiles.first { it.id == request.idpId }
                    when (request.operation) {
                        IDPSegmentOperation.CREATE_SEGMENT    -> p.segments.add(Seg(request.profileStartTime, request.profileBasalRate, request.profileCarbRatio, request.profileTargetBG, request.profileISF))
                        IDPSegmentOperation.MODIFY_SEGMENT_ID -> p.segments[request.segmentIndex].let {
                            it.start = request.profileStartTime; it.basal = request.profileBasalRate; it.carb = request.profileCarbRatio
                            it.target = request.profileTargetBG; it.isf = request.profileISF
                        }
                        IDPSegmentOperation.DELETE_SEGMENT_ID -> p.segments.removeAt(request.segmentIndex)
                        else                                  -> Unit
                    }
                    p.segments.sortBy { it.start }
                    afterSegmentWrite(this)
                    SetIDPSegmentResponse(byteArrayOf(0, 0))
                }
                is SetActiveIDPRequest  -> {
                    val p = profiles.first { it.id == request.idpId }
                    profiles.remove(p)
                    profiles.add(0, p)
                    SetActiveIDPResponse(0)
                }
                else                    -> null
            }
        }
    }

    private fun seg(start: Int, basal: Int) = IDPSegmentDto().also {
        it.profileStartTime = start
        it.profileBasalRate = basal
        it.profileCarbRatio = 12000
        it.profileTargetBG = 110
        it.profileISF = 45
    }

    private val threeSegments = listOf(seg(0, 800), seg(360, 950), seg(1080, 700))

    private fun writer(pump: FakePump) = TandemProfileWriter(aapsLogger) { pump.handle(it) }

    private fun statusReject(): SetIDPSegmentResponse = SetIDPSegmentResponse(byteArrayOf(1, 0))

    @Test
    fun emptyPump_createsProfile_writesSegments_andVerifiesReadBack() {
        val pump = FakePump()

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertTrue(result.success, result.errorDescription)
        assertEquals(1, pump.seen.count { it is CreateIDPRequest })
        // the first segment came with the create request
        assertEquals(listOf(IDPSegmentOperation.CREATE_SEGMENT, IDPSegmentOperation.CREATE_SEGMENT), pump.seen.filterIsInstance<SetIDPSegmentRequest>().map { it.operation })
        // new profile is already in slot 0, so no activation is needed
        assertEquals(0, pump.seen.count { it is SetActiveIDPRequest })
        // the read back came after the last write
        assertTrue(pump.seen.indexOfLast { it is IDPSegmentRequest } > pump.seen.indexOfLast { it is SetIDPSegmentRequest })
        val stored = pump.profiles.single()
        assertEquals(300, stored.duration)
        assertEquals(listOf(0, 360, 1080), stored.segments.map { it.start })
        assertEquals(listOf(800, 950, 700), stored.segments.map { it.basal })
    }

    @Test
    fun emptyPump_createRejected_failsAndWritesNoSegment() {
        val pump = FakePump()
        pump.override = { if (it is CreateIDPRequest) Pair(true, CreateIDPResponse(1, 0)) else null }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertNotNull(result.errorDescription)
        assertEquals(0, pump.seen.count { it is SetIDPSegmentRequest })
    }

    @Test
    fun emptyPump_pumpStillHasNoProfileAfterCreate_fails() {
        val pump = FakePump()
        pump.override = { if (it is CreateIDPRequest) Pair(true, CreateIDPResponse(0, 3)) else null }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertEquals(0, pump.seen.count { it is SetIDPSegmentRequest })
    }

    @Test
    fun emptyPump_noInsulinDuration_failsBeforeCreate() {
        val pump = FakePump()

        val result = writer(pump).writeProfile(threeSegments, null)

        assertFalse(result.success)
        assertEquals(0, pump.seen.count { it is CreateIDPRequest })
    }

    @Test
    fun segmentRejectedByPump_failsAndStopsWriting() {
        val pump = FakePump()
        pump.override = { if (it is SetIDPSegmentRequest) Pair(true, statusReject()) else null }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertTrue(result.errorDescription!!.contains("status=1"))
        assertEquals(1, pump.seen.count { it is SetIDPSegmentRequest })
    }

    @Test
    fun errorResponse_isReportedAndDoesNotThrow() {
        val pump = FakePump()
        pump.override = { if (it is CreateIDPRequest) Pair(true, ErrorResponse(it.opCode().toInt(), 1)) else null }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertNotNull(result.errorDescription)
    }

    @Test
    fun noResponse_isReportedAsFailure() {
        val pump = FakePump()
        pump.override = { if (it is CreateIDPRequest) Pair(true, null) else null }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertTrue(result.errorDescription!!.contains("null"))
    }

    @Test
    fun readBackMismatch_failsEvenIfEveryWriteWasAccepted() {
        val pump = FakePump()
        // the pump accepts the write but stores a different basal rate
        pump.afterSegmentWrite = { p -> p.profiles.first().segments.last().basal += 50 }

        val result = writer(pump).writeProfile(threeSegments, 300)

        assertFalse(result.success)
        assertTrue(result.errorDescription!!.contains("does not match"))
    }

    @Test
    fun existingProfile_isChangedToTheNewSegments_andVerified() {
        val pump = FakePump()
        pump.profiles.add(FakeProfile(1, "Old", 240, mutableListOf(Seg(0, 500, 9000, 100, 30), Seg(300, 600, 9000, 100, 30), Seg(600, 700, 9000, 100, 30))))

        val result = writer(pump).writeProfile(listOf(seg(0, 800), seg(720, 900)), null)

        assertTrue(result.success, result.errorDescription)
        assertEquals(0, pump.seen.count { it is CreateIDPRequest })
        val stored = pump.profiles.single()
        assertEquals(listOf(0, 720), stored.segments.map { it.start })
        assertEquals(listOf(800, 900), stored.segments.map { it.basal })
        assertEquals(listOf(12000L, 12000L), stored.segments.map { it.carb })
    }

    @Test
    fun existingProfile_deleteRejected_fails() {
        val pump = FakePump()
        pump.profiles.add(FakeProfile(1, "Old", 240, mutableListOf(Seg(0, 500, 9000, 100, 30), Seg(300, 600, 9000, 100, 30))))
        pump.override = { if (it is SetIDPSegmentRequest && it.operation == IDPSegmentOperation.DELETE_SEGMENT_ID) Pair(true, statusReject()) else null }

        val result = writer(pump).writeProfile(listOf(seg(0, 800)), null)

        assertFalse(result.success)
    }

    @Test
    fun tooManySegments_failsBeforeAnyWrite() {
        val pump = FakePump()
        val segments = (0..16).map { seg(it * 60, 800) }

        val result = writer(pump).writeProfile(segments, 300)

        assertFalse(result.success)
        assertTrue(pump.seen.isEmpty())
    }

    @Test
    fun firstSegmentNotAtMidnight_failsBeforeAnyWrite() {
        val pump = FakePump()

        val result = writer(pump).writeProfile(listOf(seg(60, 800)), 300)

        assertFalse(result.success)
        assertTrue(pump.seen.isEmpty())
    }

    @Test
    fun readProfile_emptyPump_isNewScenario() {
        val pump = FakePump()

        val dto = writer(pump).readProfile()

        assertTrue(dto.success)
        assertTrue(dto.isNewScenario)
        assertNull(dto.idpSettingsResponse)
    }
}
