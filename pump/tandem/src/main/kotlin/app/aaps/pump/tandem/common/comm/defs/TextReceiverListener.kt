package app.aaps.pump.tandem.common.comm.defs

@Deprecated("Possibly unused")
interface TextReceiverListener {

    fun onNewText(state: Int, message: String)

}