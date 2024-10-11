package app.aaps.pump.tandem.common.events

import app.aaps.core.interfaces.rx.events.Event


class EventPumpPairingCodeProvided(var pairingCode: String) : Event()