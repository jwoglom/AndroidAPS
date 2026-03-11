# DASH BLE Dual-Implementation Migration Plan

## Overview

Migrate the Omnipod DASH BLE layer so that **both** the BLESSED library implementation and the native Android BLE implementation coexist. The driver will explicitly select which implementation to use via a preference.

**Base branch:** `dash_ble_migration` (or `cursor/dash-ble-native-implementation-e00e`)  
**Source of native implementation:** `dev` branch

---

## Current State Analysis

### dash_ble_migration Branch (BLESSED Implementation)

- **OmnipodDashBleManager** – Interface with: `sendCommand`, `getStatus`, `connect` (2 overloads), `pairNewPod`, `disconnect`, `removeBond`
- **OmnipodDashBleManagerImpl** – Implementation using:
  - `BlessedConnection` (BLESSED `BluetoothCentralManager`)
  - `BlessedPodScanner` (implements `PodScanner`)
  - `BlessedBondingHelper`
- **Abstractions:** `PodScanner`, `CmdBleIO`, `DataBleIO`, `BleCharacteristicIO` interfaces
- **Shared protocol layer:** `Session`, `SessionEstablisher`, `MessageIO`, `LTKExchanger`, `EnDecrypt`, etc.
- **Connection.kt** – Contains only `ConnectionState` and `ConnectionWaitCondition`; the native `Connection` class was removed

### dev Branch (Native Android BLE Implementation)

- **OmnipodDashBleManagerImpl** – Uses:
  - `Connection` (native `BluetoothGatt`)
  - `PodScanner` (concrete class using `BluetoothLeScanner`)
  - Native bonding via `BluetoothDevice.createBond()` / reflection
- **Connection** – Uses `BluetoothGatt`, `BleCommCallbacks`, `ServiceDiscoverer`, `CmdBleIO`, `DataBleIO`
- **BleIO/CmdBleIO/DataBleIO** – Concrete classes (no interfaces), extend base `BleIO`
- **BleCommCallbacks** – GATT callback handling
- **ServiceDiscoverer** – Discovers GATT services/characteristics
- **ScanCollector** – Collects scan results from `BluetoothLeScanner`

---

## Migration Strategy

### Phase 1: Restore Native BLE Stack

Port the native BLE components from `dev` and adapt them to the interfaces used on `dash_ble_migration`.

#### 1.1 Create Native Connection Layer

| File | Action |
|------|--------|
| `session/NativeConnection.kt` | New class: port logic from dev's `Connection.kt`. Uses `BluetoothGatt`, `BleCommCallbacks`. Same API as `BlessedConnection`: `connect()`, `disconnect()`, `connectionState()`, `establishSession()`, `session`, `msgIO`. |
| `Connection.kt` | Keep as-is (only `ConnectionState`, `ConnectionWaitCondition`). |

#### 1.2 Create Native BLE I/O (Interface Implementations)

| File | Action |
|------|--------|
| `io/NativeBleIO.kt` | New base class: port from dev's `BleIO.kt`. Implements `BleCharacteristicIO` using `BluetoothGatt`/`BluetoothGattCharacteristic`. |
| `io/NativeCmdBleIO.kt` | New: implements `CmdBleIO` interface. Extends/uses `NativeBleIO` logic. Port from dev's `CmdBleIO.kt`. |
| `io/NativeDataBleIO.kt` | New: implements `DataBleIO` interface. Extends/uses `NativeBleIO` logic. Port from dev's `DataBleIO.kt`. |

#### 1.3 Restore/Adapt Supporting Classes

| File | Action |
|------|--------|
| `callbacks/BleCommCallbacks.kt` | **Restore from dev** – Not present on dash_ble_migration (only `BlessedBleCallbacks` exists). Native GATT callbacks. |
| `ServiceDiscoverer.kt` | **Restore from dev** – Not present on dash_ble_migration. GATT service/characteristic discovery. |
| `scan/NativePodScanner.kt` | **New**: implements `PodScanner` interface. Port from dev's `PodScanner.kt` + `ScanCollector.kt`. |
| `scan/ScanCollector.kt` | **Restore from dev** – Not present on dash_ble_migration. Used by `NativePodScanner`. |

#### 1.4 Shared Constant

- Define `BASE_CONNECT_TIMEOUT_MS` in a shared location (e.g. `Connection.kt` companion or dedicated constants file) so both BLESSED and native use the same value. `BlessedConnection` and `NativeConnection` should reference it.

---

### Phase 2: Create Native BLE Manager Implementation

| File | Action |
|------|--------|
| `OmnipodDashBleManagerNativeImpl.kt` | New class implementing `OmnipodDashBleManager`. Port logic from dev's `OmnipodDashBleManagerImpl.kt`, using `NativeConnection` and `NativePodScanner` instead of Blessed equivalents. Key differences from BLESSED version: native bonding (no `BlessedBondingHelper`), native `PodScanner`, native `Connection`. |

#### 2.1 Rename BLESSED Implementation (Optional)

- Option A: Rename `OmnipodDashBleManagerImpl` → `OmnipodDashBleManagerBlessedImpl` for clarity.
- Option B: Keep `OmnipodDashBleManagerImpl` as BLESSED; `OmnipodDashBleManagerNativeImpl` as native.

**Recommendation:** Option B to minimize changes. Both implement `OmnipodDashBleManager`.

---

### Phase 3: Add Implementation Selection

#### 3.1 Preference Key

| File | Action |
|------|--------|
| `DashBooleanPreferenceKey.kt` | Add enum value: `UseBlessedBle("AAPS.Omnipod.Dash.use_blessed_ble", true)` (or `UseNativeBle` with default false). Default to BLESSED for stability. |

#### 3.2 UI

| File | Action |
|------|--------|
| `OmnipodDashPumpPlugin.kt` (or equivalent preferences UI) | Add toggle: "Use BLESSED BLE library" / "Use native Android BLE". Document trade-offs (BLESSED: potentially more reliable; Native: no extra dependency). |

#### 3.3 Dependency Injection

| File | Action |
|------|--------|
| `OmnipodDashModule.kt` | Replace `@Binds` for `OmnipodDashBleManager` with `@Provides` that selects implementation based on preference: |
| | ```kotlin |
| | @Provides |
| | @Singleton |
| | fun provideOmnipodDashBleManager( |
| |     blessedImpl: OmnipodDashBleManagerBlessedImpl,  // or OmnipodDashBleManagerImpl |
| |     nativeImpl: OmnipodDashBleManagerNativeImpl, |
| |     preferences: Preferences |
| | ): OmnipodDashBleManager = |
| |     if (preferences.get(DashBooleanPreferenceKey.UseBlessedBle)) blessedImpl else nativeImpl |
| | ``` |
| | Both implementations remain `@Singleton`. |

---

### Phase 4: Fix Coupling and References

#### 4.1 Controller ID

- `OmnipodDashBleManagerImpl.CONTROLLER_ID` is referenced by `BlessedCmdBleIO`, `Ids.kt`.
- Move `CONTROLLER_ID` to a shared place (e.g. `OmnipodDashBleManager` companion or `Ids.kt`) so `OmnipodDashBleManagerNativeImpl` can use it. `NativeCmdBleIO` (when porting from dev) already references this; ensure both implementations use the same constant.

#### 4.2 Bonding

- **BLESSED:** Uses `BlessedBondingHelper` (BLESSED-specific).
- **Native:** Uses `BluetoothDevice.createBond()` and reflection for `removeBond()` as on dev.
- Both respect `DashBooleanPreferenceKey.UseBonding`.

---

### Phase 5: Testing

| Area | Action |
|------|--------|
| Unit tests | `OmnipodDashBleManagerImplTest.kt` – already validates interface. Add/adapt tests for `OmnipodDashBleManagerNativeImpl` if needed. |
| Integration | Ensure both implementations work with `OmnipodDashManagerImpl` (which depends only on `OmnipodDashBleManager`). |
| Manual | Test pairing, connect, send command, disconnect with both BLESSED and native selected. |

---

## File Change Summary

### New Files

1. `pump/omnipod/common/.../session/NativeConnection.kt`
2. `pump/omnipod/common/.../io/NativeBleIO.kt`
3. `pump/omnipod/common/.../io/NativeCmdBleIO.kt`
4. `pump/omnipod/common/.../io/NativeDataBleIO.kt`
5. `pump/omnipod/common/.../scan/NativePodScanner.kt`
6. `pump/omnipod/common/.../comm/OmnipodDashBleManagerNativeImpl.kt`

### Restored from dev

1. `callbacks/BleCommCallbacks.kt` (if not present or different)
2. `ServiceDiscoverer.kt` (if not present or different)
3. `scan/ScanCollector.kt` (if not present)

### Modified Files

1. `OmnipodDashModule.kt` – Switch from `@Binds` to `@Provides` for `OmnipodDashBleManager`
2. `DashBooleanPreferenceKey.kt` – Add `UseBlessedBle`
3. `OmnipodDashPumpPlugin.kt` (or preferences fragment) – Add UI for BLE implementation choice
4. `Connection.kt` – Add `BASE_CONNECT_TIMEOUT_MS` if shared, or keep in both Connection implementations
5. `OmnipodDashBleManager.kt` – Ensure default `connect(timeoutMs)` uses shared constant

---

## Dependency Notes

- **BLESSED:** `blessed-kotlin` is already in `pump/omnipod/common/build.gradle.kts`. Native stack uses only Android APIs; no new dependency.

---

## Risk Mitigation

- Default to BLESSED (`UseBlessedBle = true`) so existing users keep current behavior.
- Keep both implementations in the same module to avoid complex module boundaries.
- Share `Session`, `SessionEstablisher`, `MessageIO`, `LTKExchanger` so protocol behavior is identical; only transport differs.

---

## Implementation Order

1. Restore `BleCommCallbacks`, `ServiceDiscoverer`, `ScanCollector` from dev.
2. Create `NativeBleIO`, `NativeCmdBleIO`, `NativeDataBleIO`.
3. Create `NativeConnection`.
4. Create `NativePodScanner`.
5. Create `OmnipodDashBleManagerNativeImpl`.
6. Add `UseBlessedBle` preference and UI.
7. Update `OmnipodDashModule` with conditional `@Provides`.
8. Run tests and manual verification for both implementations.
