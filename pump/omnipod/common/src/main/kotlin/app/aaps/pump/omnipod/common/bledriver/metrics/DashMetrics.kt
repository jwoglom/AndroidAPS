package app.aaps.pump.omnipod.common.bledriver.metrics

object DashMetrics {

    fun sessionStart(
        reason: String,
        priorSecondsSinceLastSession: Long?,
        btAdapterEnabled: Boolean?,
        priorSessionOutcome: String?,
        podAgeMinutes: Long?,
        batteryLevelPct: Int?,
        appState: String?,
        podUniqueIdAtStart: Long?,
        bluetoothAddressAtStart: String?,
        powerSaveMode: Boolean? = null,
        deviceIdleMode: Boolean? = null,
        locationServicesOn: Boolean? = null,
        bluetoothAdapterState: String? = null,
        isCharging: Boolean? = null
    ): SessionContext? {
        if (!MetricsConfig.METRICS_ENABLED) return null
        val ctx = SessionContext()
        ctx.fillPodHashIfMissing(podUniqueIdAtStart)
        ctx.fillMacHashIfMissing(bluetoothAddressAtStart)
        ctx.lifecycle = "starting"
        SessionContextHolder.set(ctx)
        val e = base(ctx, "session_start")
        e["reason"] = reason
        e["prior_seconds_since_last_session"] = priorSecondsSinceLastSession
        e["bt_adapter_enabled"] = btAdapterEnabled
        e["prior_session_outcome"] = priorSessionOutcome
        e["pod_age_minutes"] = podAgeMinutes
        e["battery_level_pct"] = batteryLevelPct
        e["app_state"] = appState
        e["power_save_mode"] = powerSaveMode
        e["device_idle_mode"] = deviceIdleMode
        e["location_services_on"] = locationServicesOn
        e["bluetooth_adapter_state"] = bluetoothAdapterState
        e["is_charging"] = isCharging
        // Seed env-change-detection baselines so the idle env_sample poll only
        // fires when something has actually changed since session_start.
        ctx.lastEnvBatteryBucket = bucketBattery(batteryLevelPct)
        ctx.lastEnvAppState = appState
        ctx.lastEnvPowerSave = powerSaveMode
        ctx.lastEnvDeviceIdle = deviceIdleMode
        ctx.lastEnvLocationOn = locationServicesOn
        ctx.lastEnvBtAdapterState = bluetoothAdapterState
        ctx.lastEnvIsCharging = isCharging
        MetricsWriter.write(e)
        return ctx
    }

    /**
     * Emit an env_sample only if any of the supplied fields differs from the
     * last seen value on the session. Battery is bucketed to 5% to avoid the
     * idle poll firing every tick as the device discharges. The event lists
     * which fields changed in `changed_fields` so analysts can filter quickly.
     */
    fun envSampleIfChanged(
        batteryLevelPct: Int?,
        appState: String?,
        powerSaveMode: Boolean?,
        deviceIdleMode: Boolean?,
        locationServicesOn: Boolean?,
        bluetoothAdapterState: String?,
        isCharging: Boolean?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val battBucket = bucketBattery(batteryLevelPct)
        val changed = mutableListOf<String>()
        if (battBucket != ctx.lastEnvBatteryBucket) changed += "battery_level_pct"
        if (appState != ctx.lastEnvAppState) changed += "app_state"
        if (powerSaveMode != ctx.lastEnvPowerSave) changed += "power_save_mode"
        if (deviceIdleMode != ctx.lastEnvDeviceIdle) changed += "device_idle_mode"
        if (locationServicesOn != ctx.lastEnvLocationOn) changed += "location_services_on"
        if (bluetoothAdapterState != ctx.lastEnvBtAdapterState) changed += "bluetooth_adapter_state"
        if (isCharging != ctx.lastEnvIsCharging) changed += "is_charging"
        if (changed.isEmpty()) return

        ctx.lastEnvBatteryBucket = battBucket
        ctx.lastEnvAppState = appState
        ctx.lastEnvPowerSave = powerSaveMode
        ctx.lastEnvDeviceIdle = deviceIdleMode
        ctx.lastEnvLocationOn = locationServicesOn
        ctx.lastEnvBtAdapterState = bluetoothAdapterState
        ctx.lastEnvIsCharging = isCharging

        val e = base(ctx, "env_sample")
        e["battery_level_pct"] = batteryLevelPct
        e["app_state"] = appState
        e["power_save_mode"] = powerSaveMode
        e["device_idle_mode"] = deviceIdleMode
        e["location_services_on"] = locationServicesOn
        e["bluetooth_adapter_state"] = bluetoothAdapterState
        e["is_charging"] = isCharging
        e["changed_fields"] = changed
        MetricsWriter.write(e)
    }

    private fun bucketBattery(pct: Int?): Int? = pct?.let { (it / 5) * 5 }

    fun scanPhase(
        durationMs: Long,
        candidatesFound: Int,
        foundPodRssi: Int?,
        scanFailureReason: String?,
        scanFailureCode: Int? = null,
        scanFailureClass: String? = null,
        candidateRssis: List<Int>? = null
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "scan_phase")
        e["duration_ms"] = durationMs
        e["candidates_found"] = candidatesFound
        e["found_pod_rssi"] = foundPodRssi
        e["scan_failure_reason"] = scanFailureReason
        e["scan_failure_code"] = scanFailureCode
        e["scan_failure_class"] = scanFailureClass
        e["candidate_rssis"] = candidateRssis
        MetricsWriter.write(e)
    }

    /**
     * Map BluetoothLeScanner.ScanCallback error codes to named categories.
     * Note SCAN_FAILED_SCANNING_TOO_FREQUENTLY is the Android-throttle case
     * (5 scans within 30s on API 30+), distinct from "no candidates found".
     */
    fun classifyScanFailure(errorCode: Int?): String? = when (errorCode) {
        null                                                            -> null
        android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED   -> "already_started"
        android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app_registration_failed"
        android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "feature_unsupported"
        android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR    -> "internal_error"
        android.bluetooth.le.ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "out_of_hw_resources"
        android.bluetooth.le.ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "throttle"
        else                                                            -> "unknown_$errorCode"
    }

    fun bondPhase(
        priorBondState: String?,
        durationMs: Long,
        outcome: String,
        useBondingPref: Boolean
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "bond_phase")
        e["prior_bond_state"] = priorBondState
        e["duration_ms"] = durationMs
        e["outcome"] = outcome
        e["use_bonding_pref"] = useBondingPref
        MetricsWriter.write(e)
    }

    fun connectPhase(
        durationMs: Long,
        outcome: String,
        hciStatusCode: Int?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "connect_phase")
        e["duration_ms"] = durationMs
        e["outcome"] = outcome
        e["hci_status_code"] = hciStatusCode
        e["hci_status_name"] = hciStatusCode?.let { HciStatusNames.lookup(it) }
        MetricsWriter.write(e)
    }

    fun discoverPhase(
        durationMs: Long,
        outcome: String,
        servicesCount: Int?,
        cmdCharFound: Boolean?,
        dataCharFound: Boolean?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "discover_phase")
        e["duration_ms"] = durationMs
        e["outcome"] = outcome
        e["services_count"] = servicesCount
        e["cmd_char_found"] = cmdCharFound
        e["data_char_found"] = dataCharFound
        MetricsWriter.write(e)
    }

    fun eapAkaPhase(
        durationMs: Long,
        outcome: String,
        resyncCount: Int
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        ctx.eapAkaPhaseOccurred = true
        val e = base(ctx, "eap_aka_phase")
        e["duration_ms"] = durationMs
        e["outcome"] = outcome
        e["resync_count"] = resyncCount
        MetricsWriter.write(e)
    }

    fun sessionReady(totalSetupMs: Long) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        ctx.lifecycle = "idle"
        val e = base(ctx, "session_ready")
        e["total_setup_ms"] = totalSetupMs
        MetricsWriter.write(e)
    }

    fun sessionEnd(
        endReason: String,
        hciStatusAtDisconnect: Int?,
        successfulConnections: Int?,
        connectionAttempts: Int?,
        eapAkaSequenceNumber: Long?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        if (!ctx.endEmitted.compareAndSet(false, true)) return
        val totalMs = (System.nanoTime() - ctx.tStartMonoNs) / 1_000_000L
        val sent = ctx.cmdSent.get()
        val failed = ctx.cmdFailed.get()
        val total = sent + failed
        val failureRate = if (total > 0) failed.toDouble() / total else null
        val idleGapMs = ctx.lastCommandOkMonoNs?.let { (System.nanoTime() - it) / 1_000_000L }
        val e = base(ctx, "session_end")
        e["total_duration_ms"] = totalMs
        e["commands_sent"] = sent
        e["commands_failed"] = failed
        e["cmd_failure_rate"] = failureRate
        e["idle_ms_before_end"] = idleGapMs
        e["end_reason"] = endReason
        e["hci_status_at_disconnect"] = hciStatusAtDisconnect
        e["hci_status_at_disconnect_name"] = hciStatusAtDisconnect?.let { HciStatusNames.lookup(it) }
        e["successful_connections"] = successfulConnections
        e["connection_attempts"] = connectionAttempts
        // Gate the pod's EAP-AKA SQN on the phase actually having run this session;
        // PodState defaults the field to 1 so otherwise a first/aborted session
        // would falsely report sqn=1.
        e["eap_aka_sequence_number"] = if (ctx.eapAkaPhaseOccurred) eapAkaSequenceNumber else null
        e["last_rssi_dbm"] = ctx.lastRssiDbm
        e["min_rssi_dbm"] = ctx.minRssiDbm
        e["max_rssi_dbm"] = ctx.maxRssiDbm
        e["rssi_samples_count"] = ctx.rssiSamplesCount.get()
        e["last_mtu_bytes"] = ctx.lastMtuBytes
        e["last_phy_tx"] = ctx.lastPhyTx
        e["last_phy_rx"] = ctx.lastPhyRx
        MetricsWriter.write(e)
        SessionContextHolder.clearAfterEnd(endReason)
    }

    fun commandAttempt(commandType: String, seq: Int, expectedResponseType: String?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        ctx.commandInFlight = commandType
        ctx.tCmdStartMonoNs = System.nanoTime()
        ctx.tSendDoneMonoNs = null
        ctx.lastSentSeq = seq
        ctx.lifecycle = "cmd"
        val e = base(ctx, "command_attempt")
        e["command_type"] = commandType
        e["seq"] = seq
        e["expected_response_type"] = expectedResponseType
        MetricsWriter.write(e)
    }

    fun commandSendRetry(retryIndex: Int, priorResultKind: String) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "command_send_retry")
        e["retry_index"] = retryIndex
        e["prior_result_kind"] = priorResultKind
        MetricsWriter.write(e)
    }

    fun commandSendDone() {
        val ctx = SessionContextHolder.current() ?: return
        ctx.tSendDoneMonoNs = System.nanoTime()
    }

    fun commandResult(outcome: String) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val cmdStart = ctx.tCmdStartMonoNs
        val sendDone = ctx.tSendDoneMonoNs
        val now = System.nanoTime()
        val totalMs = cmdStart?.let { (now - it) / 1_000_000L }
        val sendMs = if (cmdStart != null && sendDone != null) (sendDone - cmdStart) / 1_000_000L else null
        val receiveMs = if (sendDone != null) (now - sendDone) / 1_000_000L else null
        if (outcome == "ok") {
            ctx.cmdSent.incrementAndGet()
            ctx.lastCommandOkMonoNs = now
        } else {
            ctx.cmdFailed.incrementAndGet()
        }
        val e = base(ctx, "command_result")
        e["command_type"] = ctx.commandInFlight
        e["outcome"] = outcome
        e["total_ms"] = totalMs
        e["send_ms"] = sendMs
        e["receive_ms"] = receiveMs
        e["retries_used"] = ctx.lastSendRetries
        MetricsWriter.write(e)
        ctx.commandInFlight = null
        ctx.tCmdStartMonoNs = null
        ctx.tSendDoneMonoNs = null
        ctx.lifecycle = "idle"
    }

    fun podStatusSnapshot(
        source: String,
        podStatus: String?,
        deliveryStatus: String?,
        totalPulsesDelivered: Int?,
        bolusPulsesRemaining: Int?,
        reservoirPulsesRemaining: Int?,
        minutesSinceActivation: Int?,
        activeAlertsCount: Int?,
        podReportedLastSeq: Int?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val expected = ctx.lastSentSeq
        val e = base(ctx, "pod_status_snapshot")
        e["source"] = source
        e["pod_status"] = podStatus
        e["delivery_status"] = deliveryStatus
        e["total_pulses_delivered"] = totalPulsesDelivered
        e["bolus_pulses_remaining"] = bolusPulsesRemaining
        e["reservoir_pulses_remaining"] = reservoirPulsesRemaining
        e["minutes_since_activation"] = minutesSinceActivation
        e["active_alerts_count"] = activeAlertsCount
        e["pod_reported_last_seq"] = podReportedLastSeq
        e["expected_last_seq"] = expected
        e["seq_matches"] = if (expected != null && podReportedLastSeq != null) expected == podReportedLastSeq else null
        MetricsWriter.write(e)
    }

    fun alarmSnapshot(
        alarmType: String?,
        alarmTime: Int?,
        occlusionAlarm: Boolean,
        pulseInfoInvalid: Boolean,
        occlusionType: Int?,
        podStatusWhenAlarmOccurred: String?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "alarm_snapshot")
        e["alarm_type"] = alarmType
        e["alarm_time"] = alarmTime
        e["occlusion_alarm"] = occlusionAlarm
        e["pulse_info_invalid"] = pulseInfoInvalid
        e["occlusion_type"] = occlusionType
        e["pod_status_when_alarm_occurred"] = podStatusWhenAlarmOccurred
        MetricsWriter.write(e)
    }

    fun nakReceived(
        nakErrorType: String?,
        alarmType: String?,
        podStatus: String?,
        secNakSyncCount: Int?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "nak_received")
        e["command_type"] = ctx.commandInFlight
        e["nak_error_type"] = nakErrorType
        e["alarm_type"] = alarmType
        e["pod_status"] = podStatus
        e["sec_nak_sync_count"] = secNakSyncCount
        MetricsWriter.write(e)
    }

    fun messageSend(packetsCount: Int, totalPayloadBytes: Int, ms: Long, outcome: String) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "message_send")
        e["packets_count"] = packetsCount
        e["total_payload_bytes"] = totalPayloadBytes
        e["ms"] = ms
        e["outcome"] = outcome
        MetricsWriter.write(e)
    }

    fun messageReceive(packetsCount: Int, totalPayloadBytes: Int, ms: Long, outcome: String, outOfOrderCount: Int) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "message_receive")
        e["packets_count"] = packetsCount
        e["total_payload_bytes"] = totalPayloadBytes
        e["ms"] = ms
        e["outcome"] = outcome
        e["out_of_order_count"] = outOfOrderCount
        MetricsWriter.write(e)
    }

    fun crcMismatch(direction: String, packetIndex: Int?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "crc_mismatch")
        e["direction"] = direction
        e["packet_index"] = packetIndex
        MetricsWriter.write(e)
    }

    fun nackPacket(direction: String, packetIndex: Int?, reason: String?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "nack_packet")
        e["direction"] = direction
        e["packet_index"] = packetIndex
        e["reason"] = reason
        MetricsWriter.write(e)
    }

    fun rtsCtsFailure(which: String, context: String?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "rts_cts_failure")
        e["which"] = which
        e["context"] = context
        MetricsWriter.write(e)
    }

    fun mtuNegotiated(mtu: Int, status: Int) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            ctx.lastMtuBytes = mtu
        }
        val e = base(ctx, "mtu_negotiated")
        e["mtu_bytes"] = mtu
        e["gatt_status"] = status
        e["success"] = status == android.bluetooth.BluetoothGatt.GATT_SUCCESS
        MetricsWriter.write(e)
    }

    fun phyUpdate(txPhy: Int, rxPhy: Int, status: Int) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val tx = phyName(txPhy)
        val rx = phyName(rxPhy)
        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            ctx.lastPhyTx = tx
            ctx.lastPhyRx = rx
        }
        val e = base(ctx, "phy_update")
        e["tx_phy"] = tx
        e["rx_phy"] = rx
        e["gatt_status"] = status
        e["success"] = status == android.bluetooth.BluetoothGatt.GATT_SUCCESS
        MetricsWriter.write(e)
    }

    private fun phyName(phy: Int): String = when (phy) {
        android.bluetooth.BluetoothDevice.PHY_LE_1M    -> "LE_1M"
        android.bluetooth.BluetoothDevice.PHY_LE_2M    -> "LE_2M"
        android.bluetooth.BluetoothDevice.PHY_LE_CODED -> "LE_CODED"
        else                                           -> "UNKNOWN_$phy"
    }

    fun cccdWrite(charType: String?, outcome: String, gattStatus: Int?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "cccd_write")
        e["char"] = charType
        e["outcome"] = outcome
        e["gatt_status"] = gattStatus
        MetricsWriter.write(e)
    }

    fun rssiSample(rssiDbm: Int, status: Int, sampleContext: String) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            ctx.recordRssiSample(rssiDbm)
        }
        val e = base(ctx, "rssi_sample")
        e["rssi_dbm"] = rssiDbm
        e["gatt_status"] = status
        e["sample_context"] = sampleContext
        e["success"] = status == android.bluetooth.BluetoothGatt.GATT_SUCCESS
        MetricsWriter.write(e)
    }

    fun bleWrite(charType: String, ackMs: Long, gattStatusOnError: String?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "ble_write")
        e["char"] = charType
        e["ack_ms"] = ackMs
        e["gatt_status_on_error"] = gattStatusOnError
        MetricsWriter.write(e)
    }

    fun bleReadTimeout(charType: String, waitedMs: Long) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "ble_read_timeout")
        e["char"] = charType
        e["waited_ms"] = waitedMs
        MetricsWriter.write(e)
    }

    fun gattError(op: String, status: String, charType: String?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "gatt_error")
        e["op"] = op
        e["status"] = status
        e["char"] = charType
        MetricsWriter.write(e)
    }

    fun unexpectedDisconnect(
        hciStatus: Int?,
        whereInLifecycle: String?,
        commandInFlight: String?
    ) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "unexpected_disconnect")
        e["hci_status"] = hciStatus
        e["hci_status_name"] = HciStatusNames.lookup(hciStatus)
        e["where_in_lifecycle"] = whereInLifecycle ?: ctx.lifecycle
        e["command_in_flight"] = commandInFlight ?: ctx.commandInFlight
        MetricsWriter.write(e)
    }

    fun explicitDisconnect(reason: String, closeGatt: Boolean) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "explicit_disconnect")
        e["reason"] = reason
        e["close_gatt"] = closeGatt
        MetricsWriter.write(e)
    }

    fun pairingPhase(subPhase: String, durationMs: Long, outcome: String) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "pairing_phase")
        e["sub_phase"] = subPhase
        e["duration_ms"] = durationMs
        e["outcome"] = outcome
        MetricsWriter.write(e)
    }

    fun eapResync(firstOrSecond: String, oldSqn: Long?, newSqn: Long?) {
        if (!MetricsConfig.METRICS_ENABLED) return
        val ctx = SessionContextHolder.current() ?: return
        val e = base(ctx, "eap_resync")
        e["first_or_second"] = firstOrSecond
        e["old_sqn"] = oldSqn
        e["new_sqn"] = newSqn
        MetricsWriter.write(e)
    }

    fun fillPodHashIfMissing(uniqueId: Long?) {
        SessionContextHolder.current()?.fillPodHashIfMissing(uniqueId)
    }

    fun fillMacHashIfMissing(address: String?) {
        SessionContextHolder.current()?.fillMacHashIfMissing(address)
    }

    fun rememberSendRetries(retries: Int) {
        SessionContextHolder.current()?.lastSendRetries = retries
    }

    fun setLifecycle(name: String) {
        SessionContextHolder.current()?.lifecycle = name
    }

    private fun base(ctx: SessionContext, eventName: String): LinkedHashMap<String, Any?> {
        val e = LinkedHashMap<String, Any?>(16)
        e["ts"] = System.currentTimeMillis()
        e["mono_ns"] = System.nanoTime()
        e["session_id"] = ctx.sessionId
        e["driver"] = MetricsConfig.DRIVER_VARIANT
        e["event"] = eventName
        e["pod"] = ctx.podHash
        e["mac"] = ctx.macHash
        return e
    }
}
