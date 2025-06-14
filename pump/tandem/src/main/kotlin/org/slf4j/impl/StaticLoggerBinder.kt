package org.slf4j.impl

import app.aaps.pump.tandem.common.util.log.MyLoggerFactory
import org.slf4j.ILoggerFactory

class StaticLoggerBinder private constructor() {

    val loggerFactory: ILoggerFactory = MyLoggerFactory()

    val loggerFactoryClassStr: String
        get() = MyLoggerFactory::class.java.getName()

    companion object {

        val singleton: StaticLoggerBinder = StaticLoggerBinder()
    }
}
