package app.aaps.pump.tandem.common.comm.command

import dagger.android.HasAndroidInjector
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.connector.commands.response.ResultCommandResponse

import app.aaps.pump.tandem.common.comm.defs.TandemCommandType

class TandemCommand<E>(var injector: HasAndroidInjector,
                    var tandemCommandType: TandemCommandType
) {

    init {

    }





    fun executeWithData() : DataCommandResponse<E>? {
        return null
    }


    fun executeWithResult() : ResultCommandResponse? {
        return null
    }


}