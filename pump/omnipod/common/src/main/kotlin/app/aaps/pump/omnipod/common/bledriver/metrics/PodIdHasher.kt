package app.aaps.pump.omnipod.common.bledriver.metrics

import java.security.MessageDigest

object PodIdHasher {

    fun hashPodId(uniqueId: Long?): String? =
        uniqueId?.let { sha256Prefix(it.toString()) }

    fun hashMac(address: String?): String? =
        address?.let { sha256Prefix(it.lowercase()) }

    private fun sha256Prefix(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(8)
        for (i in 0 until 4) sb.append(String.format("%02x", digest[i]))
        return sb.toString()
    }
}
