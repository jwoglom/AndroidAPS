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

    private fun captureNextWrite(block: () -> Unit): String {
        val lines = mutableListOf<String>()
        TestLogCapture.capture(lines, block)
        return lines.first()
    }
}
