package app.aaps.pump.tandem.common.comm.defs

import com.jwoglom.pumpx2.pump.messages.Message

interface CommunicationListener {
    fun onReceiveMessage(message: Message)
}