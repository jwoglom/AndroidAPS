package app.aaps.pump.tandem.common.queue

import app.aaps.core.interfaces.queue.CustomCommand

@Deprecated("This class probably not used")
class CommandSuspendDelivery : CustomCommand {

    override val statusDescription = "SUSPEND DELIVERY"
}
