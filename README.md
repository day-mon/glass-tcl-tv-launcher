# glass-tcl-tv-launcher

A quiet home screen for a TCL Google TV: a photograph, the time, and a left-aligned list of apps
that the remote walks down. One glass pill springs between rows; long-press a row to rearrange.

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
./gradlew testDebugUnitTest  # 17 JVM tests (Robolectric), ~11 s, no device
./gradlew tvInstall        # build + install on the TV
./gradlew tvLaunch         # open it
./gradlew tvHome           # make it the Home app
./gradlew tvTest           # 7 instrumented tests, on the TV
./gradlew tvScreenshot     # save the current screen to app/build/screen.png
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

## Code layout

```
launcher/app/src/main/kotlin/me/damon/glasshome/
├── MainActivity.kt             wires the screen together
├── ext/list.kt, ext/view.kt     small helpers
├── model/                       AppEntry, AppOrder (saved order), AppSorter
└── ui/
    ├── AppListAdapter.kt        rows, focus opacity, launching, rearranging
    ├── GlassPillController.kt   sizing, springs and fading of the pill
    ├── GlassPillView.kt         the drawn glass slab
    ├── GlyphIcons.kt            white-glyph extraction for unmatched icons
    ├── IconLibrary.kt           bundled glyph per package
    ├── CornerLinks.kt           D-pad behaviour of the bottom-right stack
    ├── ConnectionLabel.kt       Wi-Fi name or connection type
    ├── InputsMenu.kt            HDMI/AV/Live TV dropdown
    └── RemoteLongPress.kt       long-press detection for a remote's select key

launcher/app/src/test/           JVM tests (Robolectric)
launcher/app/src/androidTest/    instrumented tests, run on a device
```

## Licence

MIT — see [LICENSE](LICENSE). Upstream copyrights and asset credits are in [NOTICE.md](NOTICE.md).
