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

/** A port that accepts a connection; on its own this does not prove the far end is alive. */
fun tunnelIsUp(): Boolean = try
{
    java.net.Socket("127.0.0.1", tvPort.toInt()).close()
    true
}
catch (error: Exception)
{
    false
}

/** True only when adb can actually run a command on the TV, not merely open a socket. */
fun deviceAnswers(): Boolean =
    exec(adbExecutable, "-s", tvDevice, "shell", "true") == 0

fun openTunnel()
{
    val started = exec(
        "ssh", "-f", "-N",
        "-L", "127.0.0.1:$tvPort:$tvIp:$tvPort",
        "-o", "BatchMode=yes",
        "-o", "ExitOnForwardFailure=yes",
        // Without these a dead link keeps the local port open and every adb call fails as "closed".
        "-o", "ServerAliveInterval=15",
        "-o", "ServerAliveCountMax=3",
        tvRouter
    )
    check(started == 0) { "could not open the tunnel; is $tvRouter reachable?" }
    Thread.sleep(1_000)
}

fun closeStaleTunnel()
{
    if (!tunnelIsUp()) return
    logger.lifecycle("tunnel is stale; reopening")
    exec("pkill", "-f", "127.0.0.1:$tvPort:$tvIp:$tvPort")
    Thread.sleep(500)
}

tasks.register("tvConnect") {
    group = "tv"
    description = "Forward adb to the TV and register the device."

    doLast {
        if (!tunnelIsUp())
        {
            logger.lifecycle("tunnel 127.0.0.1:$tvPort -> $tvRouter -> $tvIp:$tvPort")
            openTunnel()
        }
        check(tunnelIsUp()) { "nothing listening on 127.0.0.1:$tvPort" }
        exec(adbExecutable, "connect", tvDevice)

        if (!deviceAnswers())
        {
            // The port was open but the far end was gone: rebuild the tunnel once.
            closeStaleTunnel()
            openTunnel()
            exec(adbExecutable, "connect", tvDevice)
            check(deviceAnswers()) {
                "the TV did not answer through the tunnel; check that $tvRouter can reach $tvIp"
            }
        }
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
        // A reinstalled app loses its ahead-of-time output; the home screen is opened constantly.
        adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", tvPackage)
    }
}

tasks.register("tvCompile") {
    group = "tv"
    description = "Compile the installed app ahead of time (dex2oat speed). Cuts cold start roughly in half on this TV."
    dependsOn("tvConnect")

    doLast {
        check(adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", tvPackage) == 0) {
            "dex2oat compile failed"
        }
    }
}

tasks.register("tvLaunch") {
    group = "tv"
    description = "Open the launcher on the TV."
    dependsOn("tvConnect")

    doLast {
        adb("shell", "am", "force-stop", tvPackage)
        adb("shell", "am", "start", "-n", "$tvPackage/.ui.home.MainActivity")
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
    description = "Run the instrumented tests on the TV and fail if any of them does."
    dependsOn("assembleDebug", "assembleDebugAndroidTest", "tvConnect")

    doLast {
        val app = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val test = layout.buildDirectory.file("outputs/apk/androidTest/debug/app-debug-androidTest.apk").get().asFile
        check(app.exists()) { "no app APK; run assembleDebug" }
        check(test.exists()) { "no test APK; run assembleDebugAndroidTest" }
        check(adb("install", "-r", app.absolutePath) == 0) { "app install failed" }
        check(adb("install", "-r", test.absolutePath) == 0) { "test install failed" }

        // connectedDebugAndroidTest reports failures its own XML does not contain on this device, so
        // the runner is driven directly and its summary parsed.
        val process = ProcessBuilder(
            adbExecutable, "-s", tvDevice, "shell", "am", "instrument", "-w",
            "$tvPackage.test/androidx.test.runner.AndroidJUnitRunner"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        output.filter { it.isNotBlank() }.takeLast(25).forEach { logger.lifecycle("  $it") }

        val summary = output.joinToString("\n")
        val failed = summary.contains("FAILURES!!!") || summary.contains("INSTRUMENTATION_FAILED") ||
                summary.contains("Error:") || !summary.contains("OK (")
        check(!failed) { "instrumented tests failed; see the runner output above" }
    }
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
