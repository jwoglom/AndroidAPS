package app.aaps.pump.tandem.common.driver.connector

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.data.IDPSegmentDto
import app.aaps.pump.tandem.common.data.PumpProfileDto
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest.IDPSegmentOperation
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse.IDPSegmentStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse

/**
 * Reads and writes the personal profile (IDP) of the pump.
 *
 * Write order: create the profile if the pump has none, write the segments, activate it if needed,
 * then read everything back and compare it with what was meant to be written.
 * Every response is checked. Any failure stops the write and is returned to the caller.
 */
class TandemProfileWriter(
    private val aapsLogger: AAPSLogger,
    private val send: (Message) -> Message?
) {

    class WriteResult(val success: Boolean, val errorDescription: String? = null)

    private val tag = LTag.PUMPCOMM

    private val idpStatusId = IDPSegmentStatus.toBitmask(
        IDPSegmentStatus.BASAL_RATE,
        IDPSegmentStatus.START_TIME,
        IDPSegmentStatus.CARB_RATIO,
        IDPSegmentStatus.TARGET_BG,
        IDPSegmentStatus.CORRECTION_FACTOR
    )

    /** Returns null if the response is a good one, else a text that says what is wrong. */
    fun checkResponse(responseMessage: Message?, description: String): String? {
        val responseText = when (responseMessage) {
            null          -> "Response for $description was null (timeout or some other problem)."
            is ErrorResponse -> "Received Error Response: ${responseMessage.errorCode.name} on $description."
            else          -> return null
        }
        aapsLogger.error(tag, responseText)
        return responseText
    }

    /** Same as [checkResponse], and also needs a status of 0 from the pump. */
    private fun checkStatusResponse(responseMessage: Message?, description: String): String? {
        checkResponse(responseMessage, description)?.let { return it }
        if (responseMessage is StatusMessage && !responseMessage.isStatusOK) {
            val responseText = "Pump rejected $description (status=${responseMessage.status})."
            aapsLogger.error(tag, responseText)
            return responseText
        }
        return null
    }

    /** Reads profile status and, if a profile exists, its settings and all its segments. */
    fun readProfile(): PumpProfileDto {
        val pumpProfileDto = PumpProfileDto()

        val statusMessage = send(ProfileStatusRequest())
        checkResponse(statusMessage, "ProfileStatusRequest")?.let { return failed(pumpProfileDto, it) }
        if (statusMessage !is ProfileStatusResponse) return failed(pumpProfileDto, "Unexpected response to ProfileStatusRequest.")

        pumpProfileDto.profileStatusResponse = statusMessage
        val idpId = statusMessage.idpSlot0Id
        pumpProfileDto.activeIdpId = idpId
        aapsLogger.debug(tag, "ProfileStatus: numberOfProfiles=${statusMessage.numberOfProfiles}, idpSlot0Id=$idpId")

        if (idpId == -1 || statusMessage.numberOfProfiles <= 0) {
            aapsLogger.info(tag, "No profiles exists on pump, NEW scenario needs to run")
            pumpProfileDto.isNewScenario = true
            return pumpProfileDto
        }

        val settingsMessage = send(IDPSettingsRequest(idpId))
        checkResponse(settingsMessage, "IDPSettingsRequest (idpId=$idpId)")?.let { return failed(pumpProfileDto, it) }
        if (settingsMessage !is IDPSettingsResponse) return failed(pumpProfileDto, "Unexpected response to IDPSettingsRequest.")
        pumpProfileDto.idpSettingsResponse = settingsMessage

        val numberOfSegments = settingsMessage.numberOfProfileSegments
        aapsLogger.debug(tag, "IDPSettings has $numberOfSegments segments.")

        for (i in 0 until numberOfSegments) {
            val segmentMessage = send(IDPSegmentRequest(idpId, i))
            checkResponse(segmentMessage, "IDPSegmentRequest [idpId=$idpId, segmentNumber=$i]")?.let { return failed(pumpProfileDto, it) }
            if (segmentMessage !is IDPSegmentResponse) return failed(pumpProfileDto, "Unexpected response to IDPSegmentRequest.")
            pumpProfileDto.mapSegments[i] = segmentMessage
        }

        return pumpProfileDto
    }

    private fun failed(pumpProfileDto: PumpProfileDto, text: String): PumpProfileDto {
        pumpProfileDto.success = false
        pumpProfileDto.errorDescription = text
        return pumpProfileDto
    }

    private fun fail(text: String): WriteResult {
        aapsLogger.error(tag, "writeProfile: $text")
        return WriteResult(false, text)
    }

    /**
     * Writes [idpSegments] to the pump and checks the result.
     *
     * @param insulinDurationMinutes only needed when the pump has no profile yet and one is created.
     */
    fun writeProfile(idpSegments: List<IDPSegmentDto>, insulinDurationMinutes: Int?): WriteResult {

        if (idpSegments.isEmpty()) return fail("The profile has no basal segments.")
        if (idpSegments.size > MAX_SEGMENTS) return fail("The profile has ${idpSegments.size} basal segments, the pump supports only $MAX_SEGMENTS.")
        if (idpSegments[0].profileStartTime != 0) return fail("The first basal segment must start at midnight.")

        val pumpProfileDto = readProfile()
        if (!pumpProfileDto.success) return fail(pumpProfileDto.errorDescription ?: "Could not read the profile from the pump.")

        val idpId: Int
        if (pumpProfileDto.isNewScenario) {
            if (insulinDurationMinutes == null || insulinDurationMinutes <= 0) return fail("The profile has no insulin duration, the pump profile cannot be created.")
            aapsLogger.info(tag, "writeProfile - NEW Scenario - Create Initial IDP")
            val first = idpSegments[0]
            val createResponse = send(
                CreateIDPRequest(
                    "AAPS Profile", first.profileCarbRatio.toInt(), first.profileBasalRate,
                    first.profileTargetBG, first.profileISF, insulinDurationMinutes,
                    0 // for AAPS profile we are setting this to 0
                )
            )
            checkStatusResponse(createResponse, "CreateIDPRequest")?.let { return fail(it) }

            // The id in the create response is not reliable, so ask the pump which profile it has now.
            val afterCreate = readProfileStatus() ?: return fail("Could not read the profile status after creating the profile.")
            if (afterCreate.numberOfProfiles <= 0 || afterCreate.idpSlot0Id == -1) return fail("The pump still reports no profile after creating it.")
            idpId = afterCreate.idpSlot0Id
        } else {
            idpId = pumpProfileDto.activeIdpId!!
            // delete all segments except the first one, last one first
            for (index in pumpProfileDto.mapSegments.size - 1 downTo 1) {
                deleteSegment(pumpProfileDto.mapSegments[index]!!, index)?.let { return fail(it) }
            }
        }

        val slotIndex = slotIndexOf(idpId) ?: return fail("The profile (id=$idpId) is not in the profile slots of the pump.")

        for (index in idpSegments.indices) {
            if (pumpProfileDto.isNewScenario && index == 0) continue // the first segment came with the new profile
            val operation = if (index == 0) IDPSegmentOperation.MODIFY_SEGMENT_ID else IDPSegmentOperation.CREATE_SEGMENT
            aapsLogger.info(tag, "writeProfile - SetIDPSegmentRequest [index=$index,operation=$operation]")
            val segment = idpSegments[index]
            val response = send(
                SetIDPSegmentRequest(
                    idpId, slotIndex, 0, operation,
                    segment.profileStartTime, segment.profileBasalRate, segment.profileCarbRatio,
                    segment.profileTargetBG, segment.profileISF, idpStatusId
                )
            )
            checkStatusResponse(response, "SetIDPSegmentRequest (idpId=$idpId,segmentIndex=$index,operation=$operation)")?.let { return fail(it) }
        }

        activateIfNeeded(idpId)?.let { return fail(it) }

        return verifyProfile(idpId, idpSegments, if (pumpProfileDto.isNewScenario) insulinDurationMinutes else null)
    }

    private fun readProfileStatus(): ProfileStatusResponse? {
        val message = send(ProfileStatusRequest())
        if (checkResponse(message, "ProfileStatusRequest") != null) return null
        return message as? ProfileStatusResponse
    }

    private fun slotIndexOf(idpId: Int): Int? {
        val status = readProfileStatus() ?: return null
        val slots = listOf(status.idpSlot0Id, status.idpSlot1Id, status.idpSlot2Id, status.idpSlot3Id, status.idpSlot4Id, status.idpSlot5Id)
        val index = slots.indexOf(idpId)
        return if (index >= 0) index else null
    }

    private fun deleteSegment(segment: IDPSegmentResponse, index: Int): String? {
        val response = send(
            SetIDPSegmentRequest(
                segment.idpId, 0, index, IDPSegmentOperation.DELETE_SEGMENT_ID,
                segment.profileStartTime, segment.profileBasalRate, segment.profileCarbRatio,
                segment.profileTargetBG, segment.profileISF, idpStatusId
            )
        )
        return checkStatusResponse(response, "SetIDPSegmentRequest (idpId=${segment.idpId},segmentIndex=$index,operation=DELETE_SEGMENT_ID)")
    }

    /** The active profile is always in slot 0. Only if ours is elsewhere, it has to be activated. */
    private fun activateIfNeeded(idpId: Int): String? {
        val status = readProfileStatus() ?: return "Could not read the profile status before activating the profile."
        if (status.idpSlot0Id == idpId) return null

        val slots = listOf(status.idpSlot0Id, status.idpSlot1Id, status.idpSlot2Id, status.idpSlot3Id, status.idpSlot4Id, status.idpSlot5Id)
        val slotIndex = slots.indexOf(idpId)
        if (slotIndex < 0) return "The profile (id=$idpId) is not in the profile slots of the pump."

        aapsLogger.info(tag, "writeProfile - SetActiveIDPRequest [idpId=$idpId,slot=$slotIndex]")
        val response = send(SetActiveIDPRequest(idpId, slotIndex))
        return checkStatusResponse(response, "SetActiveIDPRequest (idpId=$idpId)")
    }

    /** Reads the profile back from the pump and compares it with the intended segments. */
    private fun verifyProfile(idpId: Int, expected: List<IDPSegmentDto>, expectedInsulinDuration: Int?): WriteResult {
        val actual = readProfile()
        if (!actual.success) return fail("Could not read the profile back from the pump: ${actual.errorDescription}")

        val problem = compareProfile(idpId, actual, expected, expectedInsulinDuration)
        if (problem != null) return fail("Profile read back from the pump does not match: $problem")

        aapsLogger.info(tag, "writeProfile - profile read back from the pump matches (idpId=$idpId, segments=${expected.size})")
        return WriteResult(true)
    }

    /** Returns null if the pump profile is the same as the expected segments, else the first difference. */
    private fun compareProfile(idpId: Int, actual: PumpProfileDto, expected: List<IDPSegmentDto>, expectedInsulinDuration: Int?): String? {
        val status = actual.profileStatusResponse
        if (actual.isNewScenario || status == null) return "the pump has no profile"
        if (status.idpSlot0Id != idpId) return "the written profile is not the active profile"
        val settings = actual.idpSettingsResponse ?: return "no profile settings were read"
        if (settings.numberOfProfileSegments != expected.size) return "segment count is ${settings.numberOfProfileSegments}, expected ${expected.size}"
        if (expectedInsulinDuration != null && settings.insulinDuration != expectedInsulinDuration)
            return "insulin duration is ${settings.insulinDuration}, expected $expectedInsulinDuration"

        val actualSegments = actual.mapSegments.values.sortedBy { it.profileStartTime }
        if (actualSegments.size != expected.size) return "read ${actualSegments.size} segments, expected ${expected.size}"

        for (i in expected.indices) {
            val e = expected[i]
            val a = actualSegments[i]
            if (a.profileStartTime != e.profileStartTime) return "segment $i start time is ${a.profileStartTime}, expected ${e.profileStartTime}"
            if (a.profileBasalRate != e.profileBasalRate) return "segment $i basal rate is ${a.profileBasalRate}, expected ${e.profileBasalRate}"
            if (a.profileCarbRatio != e.profileCarbRatio) return "segment $i carb ratio is ${a.profileCarbRatio}, expected ${e.profileCarbRatio}"
            if (a.profileTargetBG != e.profileTargetBG) return "segment $i target BG is ${a.profileTargetBG}, expected ${e.profileTargetBG}"
            if (a.profileISF != e.profileISF) return "segment $i sensitivity is ${a.profileISF}, expected ${e.profileISF}"
        }
        return null
    }

    companion object {
        const val MAX_SEGMENTS = 16
    }
}
