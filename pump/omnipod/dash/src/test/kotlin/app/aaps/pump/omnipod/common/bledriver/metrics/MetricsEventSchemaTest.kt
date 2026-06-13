package app.aaps.pump.omnipod.common.bledriver.metrics

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test

/**
 * Locks the JSONL schema invariant: every event begins with the canonical leading fields
 * in the canonical order ts, mono_ns, session_id, driver, event, pod, mac, ...
 *
 * This is a regression guard. If you reorder DashMetrics.base() or change a field name,
 * this test must change too — and the offline analysis scripts that depend on the
 * field order should be updated in lockstep.
 */
class MetricsEventSchemaTest {

    private val gson = Gson()

    @Test fun `session_start emits canonical leading fields in order`() {
        val captured = captureNextWrite {
            DashMetrics.sessionStart(
                reason = "test",
                priorSecondsSinceLastSession = null,
                btAdapterEnabled = true,
                priorSessionOutcome = null,
                podAgeMinutes = null,
                batteryLevelPct = null,
                appState = null,
                podUniqueIdAtStart = 12345L,
                bluetoothAddressAtStart = "AA:BB:CC:DD:EE:FF"
            )
        }
        val obj = gson.fromJson(captured, JsonObject::class.java)
        val keys = obj.keySet().toList()
        assertThat(keys.subList(0, 7)).containsExactly(
            "ts", "mono_ns", "session_id", "driver", "event", "pod", "mac"
        ).inOrder()
        assertThat(obj.get("driver").asString).isEqualTo(MetricsConfig.DRIVER_VARIANT)
        assertThat(obj.get("event").asString).isEqualTo("session_start")
        assertThat(obj.get("pod").asString).hasLength(8)
        assertThat(obj.get("mac").asString).hasLength(8)
    }

    @Test fun `command lifecycle emits canonical leading fields`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            DashMetrics.commandAttempt("GET_STATUS", 0, "DefaultStatusResponse")
            DashMetrics.commandSendDone()
            DashMetrics.commandResult("ok")
            DashMetrics.sessionEnd("clean_finish", null, 1, 1, 1L)
        }
        // first 7 keys of every captured line must be canonical
        for (line in lines) {
            val obj = gson.fromJson(line, JsonObject::class.java)
            val keys = obj.keySet().toList()
            assertThat(keys.subList(0, 7)).containsExactly(
                "ts", "mono_ns", "session_id", "driver", "event", "pod", "mac"
            ).inOrder()
        }
        assertThat(lines.map { gson.fromJson(it, JsonObject::class.java).get("event").asString })
            .containsExactly("session_start", "command_attempt", "command_result", "session_end")
            .inOrder()
    }

    @Test fun `new events emit canonical leading fields and expected event names`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            DashMetrics.rssiSample(-72, 0, "pre_cmd")
            DashMetrics.mtuNegotiated(247, 0)
            DashMetrics.phyUpdate(1, 1, 0)
            DashMetrics.cccdWrite("CMD", "success", 0)
            DashMetrics.bondPhase("NONE", 1234L, "bonded", true)
            DashMetrics.podStatusSnapshot(
                source = "default_status",
                podStatus = "RUNNING_ABOVE_MIN_VOLUME",
                deliveryStatus = "NORMAL",
                totalPulsesDelivered = 100,
                bolusPulsesRemaining = 0,
                reservoirPulsesRemaining = 200,
                minutesSinceActivation = 60,
                activeAlertsCount = 0,
                podReportedLastSeq = 7
            )
            DashMetrics.alarmSnapshot(
                alarmType = "NONE",
                alarmTime = 0,
                occlusionAlarm = false,
                pulseInfoInvalid = false,
                occlusionType = 0,
                podStatusWhenAlarmOccurred = "UNKNOWN",
                podReportedRssi = 42
            )
            DashMetrics.connectionStateChange(2, 0)
            DashMetrics.busyRejected("GET_STATUS")
            DashMetrics.gattOpRejected("read_rssi", null)
            DashMetrics.sessionEnd("clean_finish", null, 1, 1, 5L)
        }
        for (line in lines) {
            val obj = gson.fromJson(line, JsonObject::class.java)
            val keys = obj.keySet().toList()
            assertThat(keys.subList(0, 7)).containsExactly(
                "ts", "mono_ns", "session_id", "driver", "event", "pod", "mac"
            ).inOrder()
        }
        assertThat(lines.map { gson.fromJson(it, JsonObject::class.java).get("event").asString })
            .containsExactly(
                "session_start", "rssi_sample", "mtu_negotiated", "phy_update",
                "cccd_write", "bond_phase", "pod_status_snapshot", "alarm_snapshot",
                "connection_state_change", "busy_rejected", "gatt_op_rejected",
                "session_end"
            ).inOrder()
    }

    @Test fun `session_end gates eap_aka_sequence_number on phase having run`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            // No eapAkaPhase() call — session_end must null out the SQN even though
            // we pass a non-null value through the parameter.
            DashMetrics.sessionEnd("clean_finish", null, 0, 0, 1L)
        }
        val endObj = gson.fromJson(lines.last(), JsonObject::class.java)
        assertThat(endObj.get("eap_aka_sequence_number").isJsonNull).isTrue()
    }

    @Test fun `session_end emits eap_aka_sequence_number after phase runs`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            DashMetrics.eapAkaPhase(50L, "success", 0)
            DashMetrics.sessionEnd("clean_finish", null, 1, 1, 7L)
        }
        val endObj = gson.fromJson(lines.last(), JsonObject::class.java)
        assertThat(endObj.get("eap_aka_sequence_number").asLong).isEqualTo(7L)
    }

    @Test fun `session_end carries rollup fields`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            DashMetrics.rssiSample(-70, 0, "ready")
            DashMetrics.rssiSample(-80, 0, "idle_poll")
            DashMetrics.mtuNegotiated(185, 0)
            DashMetrics.phyUpdate(1, 1, 0)
            DashMetrics.sessionEnd("clean_finish", null, 1, 1, 1L)
        }
        val endObj = gson.fromJson(lines.last(), JsonObject::class.java)
        assertThat(endObj.get("last_rssi_dbm").asInt).isEqualTo(-80)
        assertThat(endObj.get("min_rssi_dbm").asInt).isEqualTo(-80)
        assertThat(endObj.get("max_rssi_dbm").asInt).isEqualTo(-70)
        assertThat(endObj.get("rssi_samples_count").asInt).isEqualTo(2)
        assertThat(endObj.get("last_mtu_bytes").asInt).isEqualTo(185)
        assertThat(endObj.get("last_phy_tx").asString).isEqualTo("LE_1M")
    }

    @Test fun `pod_status_snapshot seq_matches is null for query-only sessions`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            // GET_STATUS doesn't bump the pod's programming SQN, so a snapshot
            // taken after only a query has no meaningful expected value.
            DashMetrics.commandAttempt("GET_STATUS", 6, "DefaultStatusResponse")
            DashMetrics.podStatusSnapshot(
                source = "default_status",
                podStatus = "RUNNING_ABOVE_MIN_VOLUME",
                deliveryStatus = "BASAL_ACTIVE",
                totalPulsesDelivered = 100,
                bolusPulsesRemaining = 0,
                reservoirPulsesRemaining = 1023,
                minutesSinceActivation = 60,
                activeAlertsCount = 0,
                podReportedLastSeq = 2
            )
        }
        val obj = lines.map { gson.fromJson(it, JsonObject::class.java) }
            .first { it.get("event").asString == "pod_status_snapshot" }
        assertThat(obj.get("expected_last_programming_seq").isJsonNull).isTrue()
        assertThat(obj.get("seq_matches").isJsonNull).isTrue()
    }

    @Test fun `pod_status_snapshot seq_matches compares programming commands`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            DashMetrics.sessionStart("test", null, null, null, null, null, null, 1L, null)
            DashMetrics.commandAttempt("PROGRAM_BOLUS", 7, "DefaultStatusResponse")
            DashMetrics.podStatusSnapshot(
                source = "default_status",
                podStatus = "RUNNING_ABOVE_MIN_VOLUME",
                deliveryStatus = "BASAL_ACTIVE",
                totalPulsesDelivered = 100,
                bolusPulsesRemaining = 0,
                reservoirPulsesRemaining = 1023,
                minutesSinceActivation = 60,
                activeAlertsCount = 0,
                podReportedLastSeq = 7
            )
            // A later GET_STATUS must not clobber the comparison baseline.
            DashMetrics.commandAttempt("GET_STATUS", 8, "DefaultStatusResponse")
            DashMetrics.podStatusSnapshot(
                source = "default_status",
                podStatus = "RUNNING_ABOVE_MIN_VOLUME",
                deliveryStatus = "BASAL_ACTIVE",
                totalPulsesDelivered = 100,
                bolusPulsesRemaining = 0,
                reservoirPulsesRemaining = 1023,
                minutesSinceActivation = 60,
                activeAlertsCount = 0,
                podReportedLastSeq = 7
            )
        }
        val snapshots = lines.map { gson.fromJson(it, JsonObject::class.java) }
            .filter { it.get("event").asString == "pod_status_snapshot" }
        assertThat(snapshots).hasSize(2)
        // First snapshot: programming-cmd seq 7 vs pod-reported 7 → match
        assertThat(snapshots[0].get("expected_last_programming_seq").asInt).isEqualTo(7)
        assertThat(snapshots[0].get("seq_matches").asBoolean).isTrue()
        // Second snapshot: baseline still 7 (GET_STATUS shouldn't have touched it)
        assertThat(snapshots[1].get("expected_last_programming_seq").asInt).isEqualTo(7)
        assertThat(snapshots[1].get("seq_matches").asBoolean).isTrue()
    }

    @Test fun `envSampleIfChanged suppresses unchanged samples and emits on change`() {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines) {
            // session_start seeds baseline: battery=72 bucketed to 70, app=foreground
            DashMetrics.sessionStart(
                reason = "test",
                priorSecondsSinceLastSession = null,
                btAdapterEnabled = true,
                priorSessionOutcome = null,
                podAgeMinutes = null,
                batteryLevelPct = 72,
                appState = "foreground",
                podUniqueIdAtStart = 1L,
                bluetoothAddressAtStart = null,
                powerSaveMode = false,
                deviceIdleMode = false,
                locationServicesOn = true,
                bluetoothAdapterState = "ON",
                isCharging = false
            )
            // No change — must not emit env_sample
            DashMetrics.envSampleIfChanged(72, "foreground", false, false, true, "ON", false)
            // Battery drift within the same 5% bucket (70..74) — still no event
            DashMetrics.envSampleIfChanged(74, "foreground", false, false, true, "ON", false)
            // App moved to background — must emit
            DashMetrics.envSampleIfChanged(74, "background", false, false, true, "ON", false)
            // Same as previous — no emit
            DashMetrics.envSampleIfChanged(74, "background", false, false, true, "ON", false)
            // Power save flipped — must emit
            DashMetrics.envSampleIfChanged(74, "background", true, false, true, "ON", false)
            // Charger plugged in — must emit
            DashMetrics.envSampleIfChanged(74, "background", true, false, true, "ON", true)
        }
        val envEvents = lines.map { gson.fromJson(it, JsonObject::class.java) }
            .filter { it.get("event").asString == "env_sample" }
        assertThat(envEvents).hasSize(3)
        val firstChanged = envEvents[0].get("changed_fields").asJsonArray.map { it.asString }
        assertThat(firstChanged).containsExactly("app_state")
        val secondChanged = envEvents[1].get("changed_fields").asJsonArray.map { it.asString }
        assertThat(secondChanged).containsExactly("power_save_mode")
        val thirdChanged = envEvents[2].get("changed_fields").asJsonArray.map { it.asString }
        assertThat(thirdChanged).containsExactly("is_charging")
        assertThat(envEvents[2].get("is_charging").asBoolean).isTrue()
    }

    private fun captureNextWrite(block: () -> Unit): String {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines, block)
        return lines.first()
    }
}
