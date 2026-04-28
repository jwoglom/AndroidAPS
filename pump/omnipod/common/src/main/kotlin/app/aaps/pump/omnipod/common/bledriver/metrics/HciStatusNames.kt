package app.aaps.pump.omnipod.common.bledriver.metrics

object HciStatusNames {

    private val table: Map<Int, String> = mapOf(
        0x00 to "SUCCESS",
        0x05 to "AUTHENTICATION_FAILURE",
        0x08 to "CONNECTION_TIMEOUT",
        0x13 to "REMOTE_USER_TERMINATED_CONNECTION",
        0x14 to "REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES",
        0x15 to "REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF",
        0x16 to "CONNECTION_TERMINATED_BY_LOCAL_HOST",
        0x22 to "LMP_RESPONSE_TIMEOUT",
        0x28 to "INSTANT_PASSED",
        0x3B to "CONNECTION_FAILED_ESTABLISHMENT",
        0x3E to "CONNECTION_FAILED_TO_BE_ESTABLISHED",
        0x85 to "ERROR",
        0x100 to "UNKNOWN_HCI_COMMAND"
    )

    fun lookup(status: Int?): String =
        if (status == null) "UNSET" else table[status] ?: "UNKNOWN_${"0x%02X".format(status)}"
}
