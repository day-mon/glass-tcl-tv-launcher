# glass-tcl-tv-launcher

A quiet home screen for a TCL Google TV: a photograph, the time, and a left-aligned list of apps
that the remote walks down. One glass pill springs between rows; long-press a row to rearrange, and
a settings screen switches between the list and a dock, three pill materials and glyphs-or-names.

Modelled on [CleanFrame](https://github.com/nthnclrk/CleanFrame) (Samsung Tizen, HTML/CSS), rebuilt
as a native Android launcher. Code derived from [tv-vibehack](https://github.com/Gathin23/tv-vibehack).
Attribution and asset credits: [NOTICE.md](NOTICE.md).

![The launcher running on the TV](docs/running.jpg)

## The TV

| | |
| --- | --- |
| Hardware | TCL smart TV, Android TV 12 (SDK 31), 4K panel with the UI overridden to 1920×1080 @ 320dpi |
| Package | `me.damon.glasshome` |
| Replaces | the stock Google TV launcher, or any third-party launcher |

## Everything is a Gradle task

Requires JDK 25 (or 17), the Android SDK (platform 36, build-tools 36), and `adb`. No Python, no
Node, no wrapper scripts.

```sh
cd launcher

./gradlew generateGlyphs   # SVGs in glyphs/ -> res/drawable/glyph_*.xml
./gradlew testDebugUnitTest  # 37 JVM tests (Robolectric), ~11 s, no device
./gradlew tvInstall        # build + install on the TV
./gradlew tvLaunch         # open it
./gradlew tvHome           # make it the Home app
./gradlew tvTest           # 12 instrumented tests, on the TV
./gradlew tvScreenshot     # save the current screen to app/build/screen.png
./gradlew tvWallpapers -Pphotos=<folder>   # copy a photo album to the TV
./gradlew tvWallpapersClear                 # empty it again
./gradlew tvDebloat        # apply examples/<tv>/debloat.txt
./gradlew tvRestore        # undo it
```

The TV usually sits on a different VLAN than this Mac, so `tvConnect` (a dependency of every `tv*`
task) forwards a local port to the set through an SSH host that can reach it, then points `adb` at
that port. Override the defaults with `-P`:

```sh
./gradlew tvInstall -PtvIp=192.168.1.50 -PtvRouter=my-router
```

Revert Home to a previous launcher at any time:

```sh
adb shell cmd package set-home-activity --user 0 <old.package>
```

## Your own photos as the background

The background can be your own photographs. They live **on the TV**, in the launcher's own folder,
so personal photos never enter this repository:

```sh
./gradlew tvWallpapers -Pphotos="$HOME/Desktop/trips"
```

Each photo is resized to 1920x1080, centre cropped, and pushed over the same tunnel the other tasks
use. On the TV, open **Settings -> Background -> Photos** to pick one, or turn on **Shuffle** to get
a new photo each time you come back to the home screen. **Built-in** is always offered first, so the
album can be switched off again.

Sizes, measured on this set: a photo costs about **240 KB**, because the launcher renders at 1920x1080
(the UI is composed at 1080p and scaled to the panel). A trip's worth of 500 photos is roughly
120 MB; the million-photo case is not a worry, but `tvWallpapers` still refuses to run if it would
leave less than 500 MB free on the TV.

Android TV ships no document picker (this set has only a stub for `OPEN_DOCUMENT_TREE`), so there is
no folder browser to hand the choice to the system. The album folder is the contract instead:
anything else that can write files to the TV can fill it.

## Diagnostics

**Settings -> Diagnostics** shows storage, memory, this app's CPU share and uptime, refreshed every
two seconds while the screen is open (the polling stops when it is not).

A reading costs about **2 ms**, and a test fails the build if it ever exceeds 8 ms. That is not
fussiness: the obvious Android APIs for these facts (`ActivityManager.getMemoryInfo`,
`Debug.getMemoryInfo`) are binder round trips that cost **90 ms** per reading on this TV, so the
reader uses `/proc/meminfo`, `/proc/self/statm` and `Process.getElapsedCpuTime` instead.

One honest limitation: Android 12 on this set denies apps `/proc/stat` and `/proc/loadavg` (a shell
can read them, an app cannot), so there is no system-wide CPU percentage to show. The screen reports
this app's own share and says so, rather than printing a confident 0%.

## Configure it

`app/src/main/res/values/config.xml`:

| Setting | Meaning |
| --- | --- |
| `home_apps` | Seed order: pinned first, everything else alphabetical. |
| `hidden_apps` | Never listed (the Live TV tuner is covered by the Inputs menu). |
| `live_tv_package` | Appended to the Inputs dropdown. |
| `show_icons` / `glass_icons` | Names only, or white glyphs extracted from the app icons. |

Once you rearrange the list on the TV, that order is stored in `SharedPreferences` and wins over
`home_apps`.

Bundled glyphs live in `glyphs/*.svg`; add one there and run `./gradlew generateGlyphs`, then map
the package to it in `ui/IconLibrary.kt`.

## How it is put together

Single source of truth, no DI framework:

```
GlassHomeApplication  ->  AppContainer  ->  HomeRepository  (StateFlow<HomeState>)
                                                 |
                        HomeViewModel / SettingsViewModel  (collect, expose state)
                                                 |
                        MainActivity / SettingsActivity  (render, forward input)
```

`HomeRepository` owns the installed-app scan, the saved order and the appearance choices.
`SettingsActivity` writes to it; `MainActivity` collects from it, so a change made in settings is the
state the home screen resumes with — no re-reading preferences in `onResume`.

```
launcher/app/src/main/kotlin/me/damon/glasshome/
├── GlassHomeApplication.kt      application + tiny container
├── data/
│   ├── AppOrder.kt              saved order of the list
│   ├── HomeSettings.kt          appearance storage
│   └── HomeRepository.kt        scan + state
├── model/                       AppEntry, AppSorter, Appearance/HomeState
├── ui/home/                     MainActivity, HomeViewModel, AppListAdapter
├── ui/settings/                 SettingsActivity, SettingsViewModel
└── ui/                          GlassPill*, GlyphIcons, IconLibrary, CornerLinks,
                                 ConnectionLabel, InputsMenu, RemoteLongPress

launcher/app/src/test/           JVM tests (Robolectric)
launcher/app/src/androidTest/    instrumented tests, run on a device
```

## Settings screen

Corner link **SETTINGS** → Up/Down between rows, Left/Right between choices, OK to apply.

| Option | Values |
| --- | --- |
| Layout | `List` (left column) · `Dock` (row along the bottom, links move under the clock) |
| Material | `Glass` · `Clear` · `Solid` |
| Icons | `Glyphs` (bundled white logos) · `Names` |
| System | opens the Android settings |

## Performance

Measured on the TV itself, not in a simulator. `glasshome:*` slices are permanent trace markers
(`android.os.Trace`), so any capture shows them.

```sh
# how long the launcher takes to put a frame up (cold, three runs)
adb shell am force-stop me.damon.glasshome
adb shell am start -W -n me.damon.glasshome/.ui.home.MainActivity

# frame history
adb shell dumpsys gfxinfo me.damon.glasshome reset

# ahead-of-time compile: the biggest cold-start lever on this TV
cd launcher && ./gradlew tvCompile
```

A Perfetto capture with FrameTimeline, then SQL over it:

```sh
cat > /tmp/glass.cfg <<'EOF'
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "wm"
  atrace_categories: "am" atrace_categories: "dalvik" atrace_categories: "res"
  atrace_apps: "me.damon.glasshome" } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
duration_ms: 9000
EOF
cat /tmp/glass.cfg | adb shell "perfetto -c - --txt -o /data/misc/perfetto-traces/g.pftrace"
```

### What the numbers said

| Stage | Before | After | Change |
| --- | --- | --- | --- |
| Cold start (`am start -W`) | 1684 ms | ~820 ms (best 676) | −51% |
| Icon loads during scan | 160 ms | 0 (lazy) | gone |
| Scan total | 373 ms | 136 ms | −64% |
| Main-thread row binds | 90 ms / 11 rows (8 ms each) | 31 ms / 22 rows (1.4 ms each) | −83% |
| `onCreate` | 327 ms | 210 ms | −36% |
| Janky frames (trace) | 47% | 36% | −11 pts |

The wins, in order of size:

1. **Ahead-of-time compile** (`tvInstall` runs `cmd package compile -m speed -f`): −400 ms.
2. **Scan off the main thread** (`HomeRepository.refresh` on `Dispatchers.Default`): the largest
   single main-thread stall was the app scan in `onResume`.
3. **Lazy icons + background warm**: icons are no longer loaded for every installed app, only for
   rows that get drawn, and they are decoded/masked off the main thread.
4. **1280×720 wallpaper** instead of 1920×1080: less JPEG decode inside `onCreate`.

## Licence

MIT — see [LICENSE](LICENSE). Upstream copyrights and asset credits are in [NOTICE.md](NOTICE.md).
