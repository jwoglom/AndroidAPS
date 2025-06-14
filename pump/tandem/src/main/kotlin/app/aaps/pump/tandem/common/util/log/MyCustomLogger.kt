package app.aaps.pump.tandem.common.util.log

import org.slf4j.helpers.MarkerIgnoringBase

class MyCustomLogger() : MarkerIgnoringBase() {

    fun MyCustomLogger(name: String?) {
        this.name = name
    }

    override fun isTraceEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun trace(msg: String?) {
        TODO("Not yet implemented")
    }

    override fun trace(format: String?, arg: Any?) {
        TODO("Not yet implemented")
    }

    override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        TODO("Not yet implemented")
    }

    override fun trace(format: String?, vararg arguments: Any?) {
        TODO("Not yet implemented")
    }

    override fun trace(msg: String?, t: Throwable?) {
        TODO("Not yet implemented")
    }

    override fun isDebugEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun debug(msg: String?) {
        TODO("Not yet implemented")
    }

    override fun debug(format: String?, arg: Any?) {
        TODO("Not yet implemented")
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        TODO("Not yet implemented")
    }

    override fun debug(format: String?, vararg arguments: Any?) {
        TODO("Not yet implemented")
    }

    override fun debug(msg: String?, t: Throwable?) {
        TODO("Not yet implemented")
    }

    override fun isInfoEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun info(msg: String?) {
        TODO("Not yet implemented")
    }

    override fun info(msg: String?, args: Any?) {
        //MyCustomLoggerBackend.log("INFO", name, msg)
        TODO("Not yet implemented")
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        TODO("Not yet implemented")
    }

    override fun info(format: String?, vararg arguments: Any?) {
        TODO("Not yet implemented")
    }

    override fun info(msg: String?, t: Throwable?) {
        TODO("Not yet implemented")
    }

    override fun isWarnEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun warn(msg: String?) {
        TODO("Not yet implemented")
    }

    override fun warn(format: String?, arg: Any?) {
        TODO("Not yet implemented")
    }

    override fun warn(format: String?, vararg arguments: Any?) {
        TODO("Not yet implemented")
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        TODO("Not yet implemented")
    }

    override fun warn(msg: String?, t: Throwable?) {
        TODO("Not yet implemented")
    }

    override fun isErrorEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun error(msg: String?) {
        TODO("Not yet implemented")
        //MyCustomLoggerBackend.log("ERROR", name, msg)
    } // Implement other methods (debug, warn, etc.)

    override fun error(format: String?, arg: Any?) {
        TODO("Not yet implemented")
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        TODO("Not yet implemented")
    }

    override fun error(format: String?, vararg arguments: Any?) {
        TODO("Not yet implemented")
    }

    override fun error(msg: String?, t: Throwable?) {
        TODO("Not yet implemented")
    }

}