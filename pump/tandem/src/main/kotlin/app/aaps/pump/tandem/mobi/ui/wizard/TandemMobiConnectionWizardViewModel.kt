package app.aaps.pump.tandem.mobi.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.tandem.common.comm.maint.TandemPairingManager
import app.aaps.pump.tandem.common.events.EventTandemPairingStatus
import app.aaps.pump.tandem.common.events.PairingError
import app.aaps.pump.tandem.common.keys.TandemIntPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Tandem Mobi connection wizard
 */
class TandemMobiConnectionWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val preferences: Preferences,
    private val tandemPumpUtil: TandemPumpUtil
) : ViewModel() {

    private val _state = MutableStateFlow(TandemMobiWizardState())
    val state: StateFlow<TandemMobiWizardState> = _state.asStateFlow()

    private val disposable = CompositeDisposable()
    private var pairingManager: TandemPairingManager? = null

    init {
        // Subscribe to pairing status events
        disposable += rxBus
            .toObservable(EventTandemPairingStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event ->
                handlePairingEvent(event)
            }, { throwable ->
                aapsLogger.error(LTag.PUMP, "Error receiving pairing event", throwable)
            })
    }

    fun setPairingManager(manager: TandemPairingManager) {
        this.pairingManager = manager
    }

    fun onIntroductionComplete() {
        _state.update { it.copy(currentStep = WizardStep.SelectDevice) }
    }

    fun onDeviceSelected(address: String, name: String) {
        aapsLogger.info(LTag.PUMP, "Device selected: $name ($address)")
        _state.update {
            it.copy(
                deviceAddress = address,
                deviceName = name,
                currentStep = WizardStep.EnterPIN
            )
        }
    }

    fun onPINChanged(pin: String) {
        // Only allow numeric input, max 6 digits
        val filtered = pin.filter { it.isDigit() }.take(6)
        _state.update { it.copy(enteredPIN = filtered) }
    }

    fun onPINComplete() {
        val pin = _state.value.enteredPIN
        if (pin.length == 6) {
            aapsLogger.info(LTag.PUMP, "PIN entered, starting pairing")
            _state.update { it.copy(currentStep = WizardStep.Pairing) }
            startPairing(pin)
        }
    }

    private fun startPairing(pin: String) {
        viewModelScope.launch {
            pairingManager?.startPairingWithCode(pin)
                ?: run {
                    aapsLogger.error(LTag.PUMP, "PairingManager not set!")
                    _state.update {
                        it.copy(
                            currentStep = WizardStep.Error(PairingError.UnknownError),
                            pairingError = PairingError.UnknownError
                        )
                    }
                    return@launch
                }

            // Monitor pairing timeout
            monitorPairingTimeout()
        }
    }

    private suspend fun monitorPairingTimeout() {
        var elapsedTime = 0L
        val checkInterval = 1000L // Check every second
        val timeout = 30000L // 30 second timeout

        while (elapsedTime < timeout && _state.value.currentStep == WizardStep.Pairing) {
            delay(checkInterval)
            elapsedTime += checkInterval

            // Check if pairing manager detected a timeout
            if (pairingManager?.checkPairingTimeout() == true) {
                // Error event will be sent by pairing manager
                break
            }

            // Update pairing status from preferences
            val status = tandemPumpUtil.getIntPreferenceOrDefault(TandemIntPreferenceKey.PumpPairStatus, -1)
            _state.update { it.copy(pairingStatus = status) }

            if (status == 100) {
                // Success - event will be handled in handlePairingEvent
                break
            }
        }
    }

    private fun handlePairingEvent(event: EventTandemPairingStatus) {
        aapsLogger.info(LTag.PUMP, "Received pairing event: ${event.javaClass.simpleName}")

        when (event) {
            is EventTandemPairingStatus.PairingStarted -> {
                _state.update { it.copy(pairingStatus = 0) }
            }
            is EventTandemPairingStatus.WaitingForCode -> {
                _state.update { it.copy(pairingStatus = 40) }
            }
            is EventTandemPairingStatus.Connecting -> {
                _state.update { it.copy(pairingStatus = 50) }
            }
            is EventTandemPairingStatus.PairingSuccess -> {
                aapsLogger.info(LTag.PUMP, "Pairing successful: ${event.pumpSerial}")
                _state.update { it.copy(
                    currentStep = WizardStep.Complete,
                    pairingStatus = 100,
                    pairedPumpSerial = event.pumpSerial,
                    pairedPumpName = event.pumpName
                )}
            }
            is EventTandemPairingStatus.PairingFailed -> {
                aapsLogger.error(LTag.PUMP, "Pairing failed: ${event.error}")
                _state.update {
                    it.copy(
                        currentStep = WizardStep.Error(event.error),
                        pairingError = event.error,
                        retryCount = it.retryCount + 1
                    )
                }
            }
        }
    }

    fun onRetryPairing() {
        aapsLogger.info(LTag.PUMP, "Retrying pairing with same PIN")
        _state.update { it.copy(
            currentStep = WizardStep.Pairing,
            pairingError = null
        )}
        startPairing(_state.value.enteredPIN)
    }

    fun onEditPIN() {
        aapsLogger.info(LTag.PUMP, "User wants to edit PIN")
        _state.update { it.copy(
            currentStep = WizardStep.EnterPIN,
            pairingError = null
        )}
    }

    fun onCancelAndRescan() {
        aapsLogger.info(LTag.PUMP, "User wants to rescan for devices")
        _state.update { it.copy(
            currentStep = WizardStep.SelectDevice,
            enteredPIN = "",
            pairingError = null,
            deviceAddress = "",
            deviceName = ""
        )}
    }

    fun startRePairing() {
        aapsLogger.info(LTag.PUMP, "Starting re-pairing flow")
        pairingManager?.clearPairingData()
        _state.update {
            TandemMobiWizardState(
                currentStep = WizardStep.Introduction,
                isRePairing = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }
}
