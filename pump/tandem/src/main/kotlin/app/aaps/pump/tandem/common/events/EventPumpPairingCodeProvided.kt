package app.aaps.pump.tandem.common.events

import app.aaps.core.interfaces.rx.events.Event

@Deprecated("No longer used")
class EventPumpPairingCodeProvided(var pairingCode: String) : Event()