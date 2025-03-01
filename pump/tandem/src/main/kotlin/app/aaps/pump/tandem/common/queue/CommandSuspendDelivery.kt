package app.aaps.pump.tandem.common.queue

import app.aaps.core.interfaces.queue.CustomCommand

class CommandSuspendDelivery : CustomCommand {

    override val statusDescription = "SUSPEND DELIVERY"
}
