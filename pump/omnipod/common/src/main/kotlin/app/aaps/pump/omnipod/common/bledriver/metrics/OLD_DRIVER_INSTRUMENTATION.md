# Porting the DASH metrics instrumentation to the old (raw Android BLE) driver

This document is the checklist for landing the metrics instrumentation on a
branch off `dev` so the prior raw-Android-BLE driver writes the **same** JSONL
schema as the new BLESSED driver. After both branches have been deployed and
collected data, the two `AndroidAPS-dash-metrics.jsonl` files merge cleanly and
can be compared side-by-side using the `jq` queries at the bottom.

The metrics library itself (under `pump/omnipod/common/.../bledriver/metrics/`)
is **identical** on both branches. Only the call sites differ, and only at the
layers that diverge between the two drivers.

---

## 0. Branch & toggle

- [ ] Create the dev branch: `git checkout -b claude/dash-ble-metrics-android-ble dev`.
- [ ] Cherry-pick (or copy) the metrics module commit that introduced
      `MetricsConfig.kt`, `DashMetrics.kt`, `MetricsWriter.kt`,
      `SessionContext.kt`, `SessionContextHolder.kt`, `PodIdHasher.kt`,
      `HciStatusNames.kt`, the unit tests, and the `app/src/main/assets/logback.xml`
      change adding the `dash-metrics-file` appender + `<logger name="dash-metrics">`.
- [ ] Cherry-pick the `pump/omnipod/common/build.gradle.kts` change that adds
      `implementation(libs.org.slf4j.api)`.
- [ ] **Edit `MetricsConfig.kt`**: set `DRIVER_VARIANT = "android-ble"` (the
      single source-level distinguisher between the two branches).
- [ ] Confirm `METRICS_ENABLED = true`.

## 1. Files identical on `dev` — paste DashMetrics calls verbatim

For these files the surrounding code shape on `dev` is the same as on the new
branch, so the metrics calls can be ported one-for-one. The line numbers are
approximate — locate the methods by name.

| File (dev) | Methods to instrument | Events emitted |
|---|---|---|
| `pump/omnipod/dash/.../driver/OmnipodDashManagerImpl.kt` | `PodEventInterceptor.handleResponse`, `PodEventInterceptor.accept` | B4 `nak_received`, hash filling on `PodEvent.Paired` |
| `pump/omnipod/common/.../comm/OmnipodDashBleManagerImpl.kt` (or the dev-branch equivalent) | `sendCommand`, `connect`, `pairNewPod`, `establishSession`, `disconnect` | A1, A6, A7, A8, B1, B3, F2, E2, plus `startMetricsSession` and `endMetricsSession` helpers and `disconnectInternal` |
| `pump/omnipod/common/.../comm/session/Session.kt` | `sendCommand` retry loop | B2 `command_send_retry`, `rememberSendRetries` |
| `pump/omnipod/common/.../comm/message/MessageIO.kt` | `sendMessage`, `receiveMessage`, `peekForNack`, `expectBlePacket` | C1 `message_send`, C2 `message_receive`, C3 `crc_mismatch`, C4 `nack_packet`, C5 `rts_cts_failure` |
| `pump/omnipod/common/.../comm/session/SessionEstablisher.kt` | (no direct emission — outcomes classified inside `establishSession` in the manager) | — |
| `pump/omnipod/common/.../comm/pair/LTKExchanger.kt` | `negotiateLTK` sub-phases | F1 `pairing_phase` |

Diff the new-branch versions of these files against the dev-branch versions; the
metric call additions are mechanical and unaffected by the BLE layer change.

## 2. Files diverging on `dev` — port to the raw-GATT equivalents

These call sites live in the Blessed-only files on the new branch and need to
be re-implemented on the dev branch in the corresponding raw-Android-BLE
classes. The events and field shapes are identical; only the local API
(BluetoothGattCallback vs BluetoothPeripheralCallback, BluetoothGatt vs
BluetoothPeripheral) changes.

| New-branch file (this branch) | Dev-branch counterpart (verify after branching) | Events |
|---|---|---|
| `BlessedConnection.kt` | `Connection.kt` (raw Android BLE Connection class) | A4 `connect_phase`, A5 `discover_phase`, E1 `unexpected_disconnect`, session_end on link loss |
| `BlessedBondingHelper.kt` | inline `BluetoothDevice.createBond()` / `BluetoothBonder` helper | A3 `bond_phase` |
| `BlessedBleCallbacks.kt` | the `BluetoothGattCallback` impl (likely `BleCommCallbacks` or similar) | D3 `gatt_error` on non-SUCCESS status in `onCharacteristicWrite`, `onCharacteristicChanged`, `onDescriptorWrite` |
| `BlessedBleIO.kt` | `BleIO` / `CmdBleIO` / `DataBleIO` raw-GATT base classes | D1 `ble_write`, D2 `ble_read_timeout` |
| `BlessedPodScanner.kt` | `PodScanner` (uses `BluetoothLeScanner` + `ScanCallback`) | A2 `scan_phase` |

### 2.1 A4 `connect_phase` — port pattern

In the raw `Connection.connect()` (or wherever `gatt = device.connectGatt(...)` is
called):

- [ ] Capture `val tStart = System.nanoTime()` immediately before `connectGatt`.
- [ ] Call `DashMetrics.setLifecycle("connect")`.
- [ ] In `BluetoothGattCallback.onConnectionStateChange(... newState, status)`:
  - If `newState == STATE_CONNECTED`: store `lastConnectFailHci = null`, signal
    success latch.
  - If `newState == STATE_DISCONNECTED && status != GATT_SUCCESS`: store
    `lastConnectFailHci = status`, signal failure latch.
- [ ] After the latch returns, emit:
  ```kotlin
  DashMetrics.connectPhase(
      durationMs = (System.nanoTime() - tStart) / 1_000_000L,
      outcome    = if (succeeded) "success" else if (timedOut) "timeout" else "failed",
      hciStatusCode = lastConnectFailHci
  )
  ```

Note: Android's `onConnectionStateChange` `status` field uses the same numeric
HCI codes as Blessed's `HciStatus.value`, so `HciStatusNames.lookup(status)`
yields the same names on both branches.

### 2.2 A5 `discover_phase` — port pattern

Wrap the `gatt.discoverServices()` + `onServicesDiscovered` round-trip:

- [ ] `val tStart = System.nanoTime()` before `discoverServices()`.
- [ ] `DashMetrics.setLifecycle("discover")`.
- [ ] In `onServicesDiscovered`, count services and find both characteristics.
- [ ] After the latch (or timeout), emit:
  ```kotlin
  DashMetrics.discoverPhase(
      durationMs = (System.nanoTime() - tStart) / 1_000_000L,
      outcome    = if (timedOut) "timeout"
                   else if (cmdChar != null && dataChar != null) "success"
                   else "characteristic_missing",
      servicesCount = gatt.services.size,
      cmdCharFound  = cmdChar != null,
      dataCharFound = dataChar != null
  )
  ```

### 2.3 A3 `bond_phase` — port pattern

In the dev-branch bonding helper (or wherever `device.createBond()` is invoked):

- [ ] Read `device.bondState` (`BluetoothDevice.BOND_NONE / BOND_BONDING / BOND_BONDED`)
      before initiating; that string is `priorBondState`.
- [ ] `val tStart = System.nanoTime()` before `createBond()`.
- [ ] Subscribe to `BluetoothDevice.ACTION_BOND_STATE_CHANGED` broadcast for the
      result, with a 15-second timeout.
- [ ] Emit in finally:
  ```kotlin
  DashMetrics.bondPhase(
      priorBondState = priorBondStateName,
      durationMs     = (System.nanoTime() - tStart) / 1_000_000L,
      outcome        = "bonded" | "already_bonded" | "failed" | "timeout" | "peripheral_null",
      useBondingPref = true   // helper is only called when the pref is on
  )
  ```

### 2.4 D1 `ble_write` — port pattern

Wrap each `gatt.writeCharacteristic(...)` + `onCharacteristicWrite(... status)`:

- [ ] `val tStart = System.nanoTime()` before `writeCharacteristic`.
- [ ] In the matching `onCharacteristicWrite(... status)` callback:
  - status == `GATT_SUCCESS` → emit `bleWrite(charType, ackMs, null)`.
  - status != `GATT_SUCCESS` → emit `bleWrite(charType, ackMs, status.toString())`
    AND `gattError("write", status.toString(), charType)`.
- [ ] If `writeCharacteristic` itself returns false:
  ```kotlin
  DashMetrics.bleWrite(charType, 0, "writeCharacteristic_returned_false")
  DashMetrics.gattError("write", "writeCharacteristic_returned_false", charType)
  ```

Do **not** include the payload bytes in the metric (concise variant).

### 2.5 D2 `ble_read_timeout` — port pattern

Wherever the receive queue's `BlockingQueue.poll(timeoutMs, ...)` returns null
(equivalent of `BlessedBleIO.receivePacket`):

- [ ] `if (packet == null) DashMetrics.bleReadTimeout(charType, timeoutMs)`.

### 2.6 D3 `gatt_error` — port pattern

In every `BluetoothGattCallback.onXxx(... status)` with `status != GATT_SUCCESS`:

- [ ] `DashMetrics.gattError(op, status.toString(), charType)` where `op` is one
      of `write`, `notify`, `descriptor`, `discover`, `connect`.

### 2.7 E1 `unexpected_disconnect` — port pattern

In `onConnectionStateChange(... STATE_DISCONNECTED, status)` when this is NOT
the result of an explicit `gatt.disconnect()` call (i.e. unexpected):

- [ ] Read the current lifecycle value (the dev-branch driver should already
      track its own connection state machine; map it onto the same vocabulary
      used by `SessionContext.lifecycle`).
- [ ] Emit:
  ```kotlin
  DashMetrics.unexpectedDisconnect(
      hciStatus        = status,
      whereInLifecycle = null,   // null lets DashMetrics fall back to ctx.lifecycle
      commandInFlight  = null    // null lets DashMetrics fall back to ctx.commandInFlight
  )
  DashMetrics.sessionEnd(
      endReason            = "unexpected_disconnect",
      hciStatusAtDisconnect = status,
      successfulConnections = podState.successfulConnections,
      connectionAttempts    = podState.connectionAttempts,
      eapAkaSequenceNumber  = podState.eapAkaSequenceNumber
  )
  ```

### 2.8 A2 `scan_phase` — port pattern

Wrap the `BluetoothLeScanner.startScan(...)` / `stopScan(...)` cycle:

- [ ] `val tStart = System.nanoTime()` before `startScan`.
- [ ] In `ScanCallback.onScanResult`, accumulate `ScanResult`s into a map.
- [ ] In `ScanCallback.onScanFailed(errorCode)`, set `failureReason = "scan_failure_$errorCode"`.
- [ ] After `stopScan` and validation, emit (in finally):
  ```kotlin
  DashMetrics.scanPhase(
      durationMs       = (System.nanoTime() - tStart) / 1_000_000L,
      candidatesFound  = found.size,
      foundPodRssi     = pickedPod?.rssi,
      scanFailureReason = failureReason   // null on success, "not_found" / "too_many" / "scan_failure_<code>" otherwise
  )
  ```

## 3. Verification on dev branch

- [ ] Build the dev-branch APK and install on a phone with a DASH pod.
- [ ] Run a known scenario: one cold connect, one `getStatus`, one bolus, one
      explicit disconnect.
- [ ] `adb pull /sdcard/AndroidAPS-dash-metrics.jsonl` and sanity-check:
  ```
  jq -s 'group_by(.driver) | map({driver: .[0].driver, n: length})' AndroidAPS-dash-metrics.jsonl
  # expect a single row {"driver":"android-ble","n":<count>}
  jq -s 'map(.event) | group_by(.) | map({event: .[0], n: length})' AndroidAPS-dash-metrics.jsonl
  # expect: session_start, scan_phase (only on pair), bond_phase (only if bonding pref on),
  #         connect_phase, discover_phase, eap_aka_phase, session_ready,
  #         command_attempt, message_send, message_receive, ble_write, command_result,
  #         explicit_disconnect, session_end — and matching events on the new branch
  ```
- [ ] Diff the event histograms across the two branches; counts of mandatory
      lifecycle events should match for equivalent scenarios.

## 4. PR

- [ ] Title: `dash: BLE metrics instrumentation (android-ble)`.
- [ ] Body: link to this checklist file and the new-branch PR for cross-reference.
- [ ] Include in the description the exact dev-branch SHA the metrics module was
      cherry-picked from, so the two branches' schemas remain comparable.

## Comparison `jq` snippets

After collecting JSONL from both branches and concatenating them into a single
file (`cat new.jsonl old.jsonl > combined.jsonl`):

```sh
# Success rate per driver
jq -s 'group_by(.session_id) | map({sid: .[0].session_id, driver: .[0].driver,
       end: (map(select(.event=="session_end"))[0].end_reason)})
       | group_by(.driver)
       | map({driver: .[0].driver,
              total: length,
              clean: (map(select(.end=="clean_finish")) | length),
              unexpected: (map(select(.end=="unexpected_disconnect")) | length),
              error: (map(select(.end=="error_recovery")) | length)})' combined.jsonl

# p50 / p95 connect_phase by driver
jq -s 'group_by(.driver)
       | map({driver: .[0].driver,
              durs: (map(select(.event=="connect_phase" and .outcome=="success") | .duration_ms) | sort)})
       | map({driver: .driver, n: (.durs | length),
              p50: .durs[(.durs|length)*0.5|floor],
              p95: .durs[(.durs|length)*0.95|floor]})' combined.jsonl

# NAK error type distribution per driver
jq -s 'map(select(.event=="nak_received"))
       | group_by(.driver)
       | map({driver: .[0].driver,
              types: (group_by(.nak_error_type) | map({type: .[0].nak_error_type, n: length}))})' combined.jsonl

# Unexpected disconnect rate per session
jq -s 'group_by(.driver)
       | map({driver: .[0].driver,
              sessions: (map(select(.event=="session_end")) | length),
              unexpected: (map(select(.event=="unexpected_disconnect")) | length)})
       | map(. + {rate: (.unexpected / .sessions)})' combined.jsonl

# GATT error code distribution per driver
jq -s 'map(select(.event=="gatt_error"))
       | group_by(.driver)
       | map({driver: .[0].driver,
              by_op: (group_by(.op) | map({op: .[0].op,
                                           statuses: (group_by(.status) | map({status: .[0].status, n: length}))}))})' combined.jsonl
```

A statistically meaningful comparison probably requires a few weeks of
real-world usage on each branch with at least one pod each. Plot the per-day
counts of `unexpected_disconnect` and the p95 of `connect_phase` as a
time series to see whether driver behaviour is stable or drifting.
