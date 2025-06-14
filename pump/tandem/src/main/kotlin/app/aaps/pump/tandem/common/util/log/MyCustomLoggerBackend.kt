package app.aaps.pump.tandem.common.util.log

class MyCustomLoggerBackend {

    //private static
    val allowedPackages: Set<String> = setOf(
        "com.myapp.auth",
        "com.myapp.service",
        "com.jwoglom.pumpx2",
    )

    fun log(level: String?, loggerName: String, message: String?) {
        // Only log if the loggerName starts with allowed package prefix
        val allowed = allowedPackages.stream()
            .anyMatch { s: String? -> loggerName.startsWith(s!!) }

        if (!allowed) return

        // Custom logging output (file, DB, etc.)
        System.out.printf("[%s] %s - %s%n", level, loggerName, message)
    }



}