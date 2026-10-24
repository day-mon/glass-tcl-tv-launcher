// Talking to the TV, as Gradle tasks.
//
// The TV usually sits on a different VLAN than this Mac, so `tvConnect` forwards a local port to the
// set through a host that can reach it, then points adb at that local port. Every other task
// depends on it. No wrapper scripts, no Python: adb and ssh do the work.
//
// Overrides (gradle.properties, local.properties, or -P):
//   tvIp      the television's address on its own network   (default 192.168.10.208)
//   tvRouter  an ~/.ssh/config host that can reach the TV   (default home-router)
//   tvPort    adb port on the TV                            (default 5555)

val tvIp = (findProperty("tvIp") as String?) ?: "192.168.10.208"
val tvRouter = (findProperty("tvRouter") as String?) ?: "home-router"
val tvPort = (findProperty("tvPort") as String?) ?: "5555"
val tvDevice = "127.0.0.1:$tvPort"
val tvPackage = "me.damon.glasshome"

val adbExecutable: String by lazy {
    val local = rootProject.file("local.properties")
    val sdk = when
    {
        local.exists() -> Regex("sdk\\.dir=(.+)").find(local.readText())?.groupValues?.get(1)?.trim()
        else -> null
    } ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("set sdk.dir in local.properties or ANDROID_HOME")
    "$sdk/platform-tools/adb"
}

/** Runs a command, prints what it said, and returns its exit code. */
fun exec(vararg command: String): Int
{
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    if (output.isNotEmpty()) output.lines().forEach { logger.lifecycle("  $it") }
    return process.exitValue()
}

fun adb(vararg args: String): Int = exec(adbExecutable, "-s", tvDevice, *args)

fun tunnelIsUp(): Boolean = try
{
    java.net.Socket("127.0.0.1", tvPort.toInt()).close()
    true
}
catch (error: Exception)
{
    false
}

tasks.register("tvConnect") {
    group = "tv"
    description = "Forward adb to the TV and register the device."

    doLast {
        if (!tunnelIsUp())
        {
            logger.lifecycle("tunnel 127.0.0.1:$tvPort -> $tvRouter -> $tvIp:$tvPort")
            val started = exec(
                "ssh", "-f", "-N",
                "-L", "127.0.0.1:$tvPort:$tvIp:$tvPort",
                "-o", "BatchMode=yes",
                "-o", "ExitOnForwardFailure=yes",
                tvRouter
            )
            check(started == 0) { "could not open the tunnel; is $tvRouter reachable?" }
            Thread.sleep(1_000)
        }
        check(tunnelIsUp()) { "nothing listening on 127.0.0.1:$tvPort" }
        check(exec(adbExecutable, "connect", tvDevice) == 0) { "adb connect $tvDevice failed" }
    }
}

tasks.register("tvInstall") {
    group = "tv"
    description = "Build and install the launcher on the TV."
    dependsOn("assembleDebug", "tvConnect")

    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        check(apk.exists()) { "no APK at $apk" }
        check(adb("install", "-r", apk.absolutePath) == 0) { "install failed" }
        adb("shell", "pm", "grant", tvPackage, "android.permission.ACCESS_FINE_LOCATION")
    }
}

tasks.register("tvLaunch") {
    group = "tv"
    description = "Open the launcher on the TV."
    dependsOn("tvConnect")

    doLast {
        adb("shell", "am", "force-stop", tvPackage)
        adb("shell", "am", "start", "-n", "$tvPackage/.MainActivity")
    }
}

tasks.register("tvHome") {
    group = "tv"
    description = "Make the launcher the TV's Home app. Reversible: set-home-activity back to your old one."
    dependsOn("tvInstall")

    doLast {
        adb("shell", "cmd", "package", "set-home-activity", "--user", "0", tvPackage)
    }
}

tasks.register("tvTest") {
    group = "tv"
    description = "Run the instrumented tests on the TV."
    dependsOn("tvConnect", "connectedDebugAndroidTest")
}

tasks.register("tvScreenshot") {
    group = "tv"
    description = "Save what is on the TV to build/screen.png."
    dependsOn("tvConnect")

    doLast {
        val out = layout.buildDirectory.file("screen.png").get().asFile
        out.parentFile.mkdirs()
        out.outputStream().use { stream ->
            ProcessBuilder(adbExecutable, "-s", tvDevice, "exec-out", "screencap", "-p")
                .start().inputStream.copyTo(stream)
        }
        logger.lifecycle("saved $out")
    }
}

/** Applies a debloat list: bare package names are disabled (never uninstalled). */
tasks.register("tvDebloat") {
    group = "tv"
    description = "Disable the packages listed in -Pdebloat=<file>."
    dependsOn("tvConnect")

    doLast {
        val list = file((findProperty("debloat") as String?) ?: "examples/tcl-g10-google-tv/debloat.txt")
        check(list.exists()) { "no debloat list at $list" }
        val packages = list.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        logger.lifecycle("disabling ${packages.size} packages from ${list.name}")
        packages.forEach { adb("shell", "pm", "disable-user", "--user", "0", it) }
    }
}

/** Undoes [tvDebloat]. */
tasks.register("tvRestore") {
    group = "tv"
    description = "Re-enable the packages listed in -Pdebloat=<file>."
    dependsOn("tvConnect")

    doLast {
        val list = file((findProperty("debloat") as String?) ?: "examples/tcl-g10-google-tv/debloat.txt")
        check(list.exists()) { "no debloat list at $list" }
        val packages = list.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        logger.lifecycle("re-enabling ${packages.size} packages from ${list.name}")
        packages.forEach { adb("shell", "pm", "enable", it) }
    }
}
