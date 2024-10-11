package app.aaps.pump.tandem.common.events

import app.aaps.core.interfaces.rx.events.Event

class EventPumpNeedsPairingCode(var instructions: String) : Event()