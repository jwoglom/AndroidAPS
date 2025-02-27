package app.aaps.pump.tandem.common.comm.defs

interface TextReceiverListener {

    fun onNewText(state: Int, message: String)

}