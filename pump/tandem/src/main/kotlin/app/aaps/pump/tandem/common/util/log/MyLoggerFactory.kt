package app.aaps.pump.tandem.common.util.log

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap



class MyLoggerFactory : ILoggerFactory {

    private val loggerMap: ConcurrentHashMap<String, Logger> = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger {
        return loggerMap.computeIfAbsent(name) { MyCustomLogger() }
    }

}