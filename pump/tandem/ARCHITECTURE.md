# Tandem Pump Driver Architecture

## Overview

The Tandem module provides an insulin pump driver for Tandem t:slim X2 and Tandem Mobi devices, enabling AndroidAPS to communicate with these pumps via Bluetooth Low Energy (BLE) for closed-loop insulin delivery.

## Module Structure

```
pump/tandem/
├── src/main/kotlin/app/aaps/pump/tandem/
│   ├── common/                     # Shared code for all Tandem pumps
│   │   ├── comm/                   # Communication layer
│   │   ├── data/                   # Data models and DTOs
│   │   ├── database/               # Room database for history/events
│   │   ├── driver/                 # Driver core logic
│   │   ├── events/                 # RxBus event definitions
│   │   ├── keys/                   # Preference key definitions
│   │   ├── queue/                  # Command queue extensions
│   │   ├── service/                # Android service for pump connection
│   │   └── ui/                     # Shared UI components
│   ├── t_mobi/                     # Tandem Mobi specific implementation
│   │   ├── driver/                 # Tandem Mobi driver configuration
│   │   └── ui/                     # Jetpack Compose UI
│   └── t_slim/                     # t:slim X2 implementation (on hold)
└── build.gradle.kts                # Dependencies including pumpX2 library
```

## AndroidAPS Integration

### Plugin Registration

The driver integrates with AndroidAPS through the plugin system:

**Class:** `TandemMobiPumpPlugin` (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/t_mobi/TandemMobiPumpPlugin.kt:106)

**Interfaces Implemented:**
- `PumpPluginAbstract` - Base class for all pump plugins
- `Pump` - Core pump interface for AndroidAPS
- `PluginConstraints` - Provides constraints to AndroidAPS loop
- `PumpDataRefreshCapable` - Enables scheduled data refresh

**Plugin Configuration:**
```kotlin
@Singleton
class TandemMobiPumpPlugin @Inject constructor(...)
  : PumpPluginAbstract(
      pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(TandemMobiPumpFragment::class.java.name),
      pumpType = PumpType.TANDEM_MOBI_BT,
      ...
  ), Pump, PluginConstraints, PumpDataRefreshCapable
```

### Dependency Injection

The module uses Dagger for dependency injection:
- `TandemModule.kt` - Provides all Tandem-specific dependencies
- Components are injected into `TandemMobiPumpPlugin` at construction

### Key AndroidAPS Interfaces

**PumpSync** - Synchronizes pump data with AndroidAPS database:
- Bolus records
- Temporary basal rates
- Pump events

**CommandQueue** - Receives commands from AndroidAPS:
- `deliverBolus()` - TandemMobiPumpPlugin.kt:1187
- `setTempBasalPercent()` / `setTempBasalAbsolute()` - :1303, :1376
- `cancelTempBasal()` - :1425
- `setNewBasalProfile()` - :1518

**RxBus** - Event-driven communication:
- Publishes pump status changes
- Receives configuration change events
- Handles qualifying events

## Architecture Layers

### 1. Plugin Layer

**TandemMobiPumpPlugin** - Main entry point for AndroidAPS

**Responsibilities:**
- Implements pump interface methods
- Manages service connection lifecycle
- Handles scheduled data refreshes
- Enforces safety constraints
- Creates preference screens

**Service Binding:**
```
Plugin.onStart() → bindService(TandemService)
  → onServiceConnected()
  → validate parameters → connect to pump
  → initializePump()
```

### 2. Service Layer

**TandemService** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/service/TandemService.kt:34)

**Responsibilities:**
- Android service for background pump communication
- Parameter validation (shared connection data or pump address + pairing)
- Connection lifecycle management
- Provides service binder for plugin

**Key Methods:**
- `validateParameters()` - :107 - Validates pump configuration
- `connectToPump()` - :229 - Initiates pump connection

### 3. Connection Management Layer

**TandemPumpConnectionManager** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/driver/connector/TandemPumpConnectionManager.kt:40)

**Responsibilities:**
- Manages connection state transitions
- Routes commands to appropriate connector
- Post-processes command responses
- Updates pump state after commands

**Key Methods:**
- `connectToPump()` - :80
- `disconnectFromPump()` - :123
- `getConnector()` - :148 - Returns appropriate connector for command type

### 4. Connector Layer

**TandemPumpConnector** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/driver/connector/TandemPumpConnector.kt)

**Responsibilities:**
- High-level command API
- Request message creation
- Response parsing and conversion
- API version compatibility handling

**Command Categories:**
- **Status Commands:** Firmware version (:227), Configuration (:238), Battery (:303), Insulin (:319)
- **Control Commands:** Bolus (:343), Temporary Basal (:614, :652), Basal Profile (:703, :832)
- **Time Commands:** Get/Set pump time (:1059, :1082)
- **Custom Commands:** Control IQ disable, alert dismiss, settings (:1148)

### 5. Communication Layer

**TandemCommunicationManager** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/comm/TandemCommunicationManager.kt:47)

**Extends:** `TandemPump` from pumpX2 library

**Responsibilities:**
- Low-level Bluetooth communication
- Connection establishment and teardown
- Message send/receive with timeout
- Operation mode management

**Operation Modes:** (:84)
- `ConnectionMode` - During initial BLE connection
- `StandardOperation` - Normal command execution
- `ExternalListenerOperation` - For UI-driven communication

**Key Methods:**
- `connect()` - :92 - Establishes BLE connection via scan
- `disconnect()` - :125 - Closes BLE connection
- `sendCommand()` - :186 - Synchronous command with 30s timeout (:88)

**Connection Flow:**
```
createBluetoothHandler()
  → bluetoothHandler.startScan()
  → onDeviceFound() → connect to GATT
  → authenticate with pairing code
  → receive ApiVersionResponse
  → connection established
```

### 6. Protocol Layer

**pumpX2 Library** (external dependency by jwoglom)

The driver delegates protocol implementation to the pumpX2 library:

**Key Components:**
- `TandemBluetoothHandler` - BLE connection and GATT handling
- `PumpState` - Pairing and authentication state
- `Message` classes - Request/response message definitions
- Protocol serialization/deserialization

**Communication Protocol:**
1. BLE connection to pump
2. GATT service/characteristic discovery
3. Authentication using JPAKE-derived secrets
4. Message exchange via BLE characteristics
5. Response routing based on message ID

**API Versioning:**

Different firmware versions support different API versions (TandemPumpApiVersion.kt):
- VERSION_2_1_to_2_4 - t:slim X2 with Basal IQ
- VERSION_2_5_OR_HIGHER - t:slim X2 with Control IQ
- VERSION_3_5_MOBI, VERSION_3_6_MOBI, VERSION_3_8_MOBI - Tandem Mobi variants

The connector selects appropriate request/response classes based on detected API version.

## State Management

### TandemPumpStatus

**Class:** `TandemPumpStatus` (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/driver/TandemPumpStatus.kt:34)

**Extends:** `PumpStatus` from pump-common

**State Tracked:**
- `tandemPumpFirmware: TandemPumpApiVersion` - (:40) Detected API version
- `serialNumber: Long` - (:41) Pump serial number
- `pumpStatusMirror: HomeScreenMirrorDto` - (:53) Current pump display state
- `settings: Map<PumpConfigurationTypeInterface, Any>` - (:54) Pump settings
- `basalsByHour: DoubleArray` - Current basal profile
- `currentTempBasalInternal: TempBasalPair` - Active temporary basal
- `tandemLastBolus: BolusData` - (:61) Most recent bolus
- `tandemAlerts/tandemAlarms` - (:59, :60) Active notifications
- `tandemSiteReminder: Long` - (:62) Site change reminder timestamp

**Semaphore Flags:** (:65-68)
- `semaphoreNotifications` - New alerts/alarms present
- `semaphoreEvents` - New qualifying events present
- `semaphoreHistory` - New history records present
- `semaphoreNeedsRefresh` - Refresh required

### TandemUIDataStore

**Class:** `TandemUIDataStore` (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/comm/ui/TandemUIDataStore.kt)

Compose-specific observable state for UI updates using `mutableStateOf`.

## Data Flow

### Initialization Flow

```
1. Plugin.onStart()
   ↓
2. bindService(TandemService)
   ↓
3. TandemService.onCreate()
   ↓
4. Plugin.onServiceConnected()
   ↓
5. Service.validateParameters()
   - Check shared connection data OR
   - Check pump address + pairing status
   ↓
6. Service.connectToPump()
   ↓
7. ConnectionManager.connectToPump()
   ↓
8. Connector.connectToPump()
   ↓
9. CommunicationManager.connect()
   - Create BluetoothHandler
   - Start BLE scan
   - Connect to pump
   - Authenticate
   ↓
10. Plugin.initializePump()
    - Read pump time
    - Get pump status
    - Read reservoir level
    - Read battery level
    - Get configuration
    - Get pump info
    - Check for active TBR
    - Get last bolus
    - Get basal profile
    ↓
11. isDriverInitialized = true
```

### Command Execution Flow

```
AndroidAPS CommandQueue
   ↓
Plugin.deliverBolus() / setTempBasal() / etc.
   ↓
ConnectionManager.executeCommand()
   ↓
Connector.<commandMethod>()
   - Create request message
   ↓
CommunicationManager.sendCommand()
   - Send via BLE
   - Wait for response (30s timeout)
   ↓
Connector.parseResponse()
   - Convert to DTO
   ↓
ConnectionManager.postProcessResponse()
   - Update TandemPumpStatus
   ↓
Plugin.syncWithPumpSync()
   - Sync to AndroidAPS database
   ↓
Return PumpEnactResult
```

### Data Refresh Flow

The plugin implements scheduled refresh for various data types:

**Refresh Types:** (TandemMobiPumpPlugin.kt:1559)
- `PumpTime` - Every 5 minutes
- `BatteryStatus` - 55/30/15 min (high/medium/low priority)
- `RemainingInsulin` - 60/30/15 min
- `PumpStatus` - Every 5 minutes
- `GetTemporaryBasal` - Check if TBR cancelled on pump
- Custom refresh actions

## Data Persistence

### TandemPumpDatabase

**Class:** `TandemPumpDatabase` (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/database/TandemPumpDatabase.kt:21)

**Technology:** Room Database (SQLite)

**Entities:**
1. `TandemHistoryRecordEntity` - Pump history log entries
2. `TandemQualifyingEventEntity` - Qualifying events for warranty/support

**DAOs:**
- `TandemHistoryRecordDao` - CRUD operations for history
- `TandemQualifyingEventsDao` - CRUD operations for qualifying events
- `TandemCleanupDao` - Database maintenance queries

**Migrations:** Version 1 → 4 with auto-migrations enabled

### Background Sync Components

**HistoryRetriever** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/comm/history/HistoryRetriever.kt)
- Downloads pump history in background
- Stores to database
- Prevents duplicate entries

**QualifyingEventHandler** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/comm/qe/QualifyingEventHandler.kt)
- Processes qualifying events in real-time
- Stores to database
- Triggers notifications if needed

## User Interface

### Technology

The Tandem UI uses **Jetpack Compose** rather than traditional Android XML layouts.

### Components

**Fragment:**
- `TandemMobiPumpFragment` - Main pump status display in AndroidAPS

**Activities:**
- `ActionsActivity` - Pump management actions
- `DataActivity` - History and data viewing

**Compose Screens:**

**Actions:**
- `Actions.kt` - Main action menu
- `PumpInfo.kt` - Detailed pump information
- `CartridgeActions.kt` - Reservoir management menu
- `ChangeCartridge.kt`, `FillTubing.kt`, `FillCannula.kt` - Reservoir change workflow
- `SiteReminder.kt` - Site change reminder configuration

**Data:**
- `DataDisplayMain.kt` - Main data view
- `History.kt` - Pump history log viewer
- `Notifications.kt` - Alerts/alarms display
- `QualifyingEvents.kt` - Qualifying events viewer

### Preference Management

**Preference Keys:**
- `TandemStringPreferenceKey` - String preferences (address, serial, shared connection data)
- `TandemBooleanPreferenceKey` - Boolean flags (use shared connection, auto-confirm)
- `TandemIntPreferenceKey` - Integer values (max bolus, max basal, pair status)
- `TandemLongNonPreferenceKey` - Long values not stored in preferences

**Dynamic Preference Screen:**
Generated in `TandemMobiPumpPlugin.addPreferenceScreen()` (:1618)

## Special Features

### Pairing and Connection

**Two Connection Modes:**

1. **Shared Connection Data** (recommended)
   - User pairs pump in t:connect mobile app
   - Export shared connection data
   - Import JSON into AndroidAPS
   - Contains: MAC address, pairing code, JPAKE secrets

2. **Direct Pairing**
   - Pair directly via AndroidAPS
   - Enter pump address and pairing code
   - Less reliable than shared connection

**Validation:** TandemService.validateParameters() (:107)

### Settings Enforcement

On initialization, the driver enforces certain settings (TandemMobiPumpPlugin.kt:1084):
- Disables Control IQ (required for closed-loop operation)
- Sets max bolus to configured limit
- Sets max basal to configured limit

### Site Change Reminder

Tracks site change and sends notifications when reminder is due (:289).

### Connection Recovery

**TandemConnectionFixer** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/comm/maint/TandemConnectionFixer.kt)

Handles automatic reconnection attempts when connection is lost (currently disabled per commit af502c1977).

### Custom Commands

**TandemCustomCommand** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/common/driver/connector/def/TandemCustomCommand.kt)

Extended commands beyond standard pump operations:
- `SET_MAX_BOLUS` / `SET_MAX_BASAL` - Safety limit configuration
- `SET_CONTROL_IQ` - Enable/disable Control IQ
- `GET_PUMP_INFO` - Detailed pump information
- `GET_ALERTS` / `GET_ALARMS` / `GET_MALFUNCTIONS` - Notification retrieval
- `DISMISS_ALERT` - Alert dismissal
- `SET_QUICK_BOLUS` - Quick bolus configuration

## Configuration

### Build Configuration

**Dependencies** (build.gradle.kts):
```kotlin
implementation(libs.com.github.jwoglom.pumpx2.android)  // pumpX2 library
implementation(libs.androidx.room.runtime)              // Room database
implementation(libs.androidx.compose.ui)                // Jetpack Compose
```

### Driver Configuration

**TandemMobiPumpDriverConfiguration** (pump/tandem/src/main/kotlin/app/aaps/pump/tandem/t_mobi/driver/TandemMobiPumpDriverConfiguration.kt)

Provides:
- Pump type definition
- BLE selector for device filtering
- Configuration flags

## Event System

### RxBus Events

**Tandem-Specific Events:**
- `EventRefreshPumpData` - Trigger data refresh
- `EventHandleQualifyingEvent` - New qualifying event received
- `EventDatabaseAddQEData` - Add QE to database
- `EventPumpNeedsPairingCode` - Pairing code required
- `EventPumpPairingCodeProvided` - Pairing code provided

**Common Pump Events:**
- `EventPumpFragmentValuesChanged` - Update UI
- `EventPumpConnectionParametersChanged` - Connection config changed
- `EventPumpForceDisconnect` - Force disconnect request

## Thread Model

**Main Thread:**
- UI updates (Compose state changes)
- Event publishing

**Background Threads:**
- BLE communication (pumpX2 library)
- Database operations (Room)
- History retrieval
- Command execution with blocking wait

**Synchronization:**
- Command execution uses blocking wait with 30s timeout
- Operation mode flag prevents concurrent operations
- Database queries use Kotlin coroutines

## Error Handling

**Connection Errors:**
- Timeout after 30 seconds (:88)
- Sets driver state to `ErrorCommunicatingWithPump`
- Stores disconnect data in `TandemPumpStatus.disconnectData`

**Command Errors:**
- Returns `PumpEnactResult` with success/failure status
- Error message stored in result
- Driver state updated accordingly

**State Recovery:**
- Connection fixer attempts reconnection
- Plugin re-initializes on successful reconnection

## Security Considerations

**Pairing:**
- Uses JPAKE (Password Authenticated Key Exchange) for secure pairing
- Secrets derived from pairing code
- Stored encrypted in preferences

**Communication:**
- BLE connection encrypted at transport layer
- Authentication required before command execution
- Pairing code required for initial connection

## Performance Characteristics

**Connection Time:**
- Initial connection: ~5-15 seconds
- Includes BLE scan, GATT discovery, authentication

**Command Latency:**
- Typical command: 1-3 seconds
- Timeout: 30 seconds

**Battery Impact:**
- BLE connection maintained continuously when plugin enabled
- Periodic status queries (5 minute intervals)
- Background history sync

## Limitations

**Current Implementation:**
- Tandem Mobi fully supported for closed-loop
- t:slim X2 implementation on hold
- Control IQ must be disabled for closed-loop operation
- Requires initial pairing via t:connect mobile app (recommended)
- Connection fixer currently disabled

**Pump Constraints:**
- Bolus increment: 0.01 units
- TBR: Percentage-based (20-250%)
- TBR duration: Up to 72 hours
- Basal profile: 24 hourly segments

## Testing

**Unit Tests:**
- `TandemDataConverterTest.kt` - Data conversion logic
- `HistoryRetrieverTest.kt` - History sync logic
- `ProfileSegmentTest.kt` - Profile parsing
- `ProfileTest.kt` - Profile conversion

**Test Coverage:**
- Data conversion
- Profile handling
- History processing

## Key File Reference

| Component | File Path | Key Methods/Lines |
|-----------|-----------|-------------------|
| Main Plugin | `t_mobi/TandemMobiPumpPlugin.kt` | :106 (class), :1187 (bolus), :1303 (TBR) |
| Service | `common/service/TandemService.kt` | :107 (validate), :229 (connect) |
| Connection Manager | `common/driver/connector/TandemPumpConnectionManager.kt` | :80 (connect), :148 (getConnector) |
| Connector | `common/driver/connector/TandemPumpConnector.kt` | :227-1148 (commands) |
| Communication | `common/comm/TandemCommunicationManager.kt` | :92 (connect), :186 (sendCommand) |
| Pump Status | `common/driver/TandemPumpStatus.kt` | :34-118 (state) |
| Database | `common/database/TandemPumpDatabase.kt` | :21-75 (Room DB) |
| Data Converter | `common/comm/TandemDataConverter.kt` | Protocol to DTO conversion |
| History | `common/comm/history/HistoryRetriever.kt` | Background sync |
| QE Handler | `common/comm/qe/QualifyingEventHandler.kt` | Real-time event processing |
| DI Module | `di/TandemModule.kt` | Dependency injection |

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      AndroidAPS Core                        │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ PumpPluginAbstract │ Pump │ CommandQueue │ PumpSync   │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────┘
                           │ implements
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   TandemMobiPumpPlugin                      │
│  - Command handling (bolus, TBR, profile)                   │
│  - Constraint enforcement                                   │
│  - Scheduled refresh (PumpDataRefreshCapable)               │
│  - Event handling (RxBus)                                   │
│  - Preference management                                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ binds to
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     TandemService                           │
│  - Android service lifecycle                                │
│  - Parameter validation                                     │
│  - Connection initiation                                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ uses
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              TandemPumpConnectionManager                    │
│  - Connection state management                              │
│  - Command routing                                          │
│  - Response post-processing                                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ delegates to
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  TandemPumpConnector                        │
│  - High-level command API                                   │
│  - Request creation                                         │
│  - Response parsing                                         │
│  - API version compatibility                                │
└──────────────────────────┬──────────────────────────────────┘
                           │ uses
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              TandemCommunicationManager                     │
│  - BLE connection lifecycle                                 │
│  - Message send/receive (30s timeout)                       │
│  - Operation mode management                                │
└──────────────────────────┬──────────────────────────────────┘
                           │ uses
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  pumpX2 Library (jwoglom)                   │
│  - TandemBluetoothHandler (BLE/GATT)                        │
│  - Request/Response message classes                         │
│  - Protocol serialization                                   │
│  - JPAKE authentication                                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ BLE
                           ↓
                    ┌──────────────┐
                    │ Tandem Pump  │
                    │ Tandem Mobi │
                    └──────────────┘

              Supporting Components:
┌─────────────────────────────────────────────────────────────┐
│ State: TandemPumpStatus (singleton state holder)            │
│ Data: TandemPumpDatabase (Room - history, QE)               │
│ Sync: HistoryRetriever (background history download)        │
│ Events: QualifyingEventHandler (real-time QE processing)    │
│ Convert: TandemDataConverter (protocol ↔ DTO)               │
│ UI: Compose screens (Actions, Data, History, etc.)          │
└─────────────────────────────────────────────────────────────┘
```

## Summary

The Tandem pump driver is a layered architecture that:

1. Integrates with AndroidAPS via standard pump plugin interfaces
2. Manages connection lifecycle through an Android service
3. Routes commands through a multi-layer connector system
4. Delegates protocol handling to the pumpX2 library
5. Maintains state in a singleton status object
6. Persists history and events in a Room database
7. Provides a modern Compose-based UI
8. Supports multiple Tandem pump models with API versioning
9. Enables closed-loop operation with bolus and TBR support
10. Handles background data sync and refresh scheduling
