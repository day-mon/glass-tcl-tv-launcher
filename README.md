# glass-tcl-tv-launcher

A quiet home screen for a TCL Google TV: a photograph, the time, and a left-aligned list of apps
that the remote walks down. One glass pill springs between rows.

Modelled on [CleanFrame](https://github.com/nthnclrk/CleanFrame) (Samsung Tizen, HTML/CSS),
rebuilt as a native Android launcher on top of [tv-vibehack](https://github.com/Gathin23/tv-vibehack).
See [NOTICE.md](NOTICE.md) for attribution.

![The launcher running on the TV](docs/running.jpg)

## The TV

| | |
| --- | --- |
| Hardware | TCL smart TV, Android TV 12 (SDK 31), 4K panel with the UI overridden to 1920×1080 @ 320dpi |
| Package | `me.damon.glasshome` |
| Replaces | the stock Google TV launcher, or any third-party launcher (Projectivy here) |

## Build and install

Needs JDK 25 (or 17), the Android SDK with platform 36 and build-tools 36, and the Python helpers in
`tools/`.

```sh
~/.local/bin/uv venv tools/venv
~/.local/bin/uv pip install --python tools/venv/bin/python -r tools/requirements.txt
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# If the Mac cannot reach the TV's VLAN, forward ADB through a host that can:
scripts/adb-tunnel.sh

TV_HOST=127.0.0.1 launcher/build-and-install.sh          # install and open
SET_HOME=1 TV_HOST=127.0.0.1 launcher/build-and-install.sh   # also make it the Home app
```

Revert Home to the previous launcher at any time:

```sh
TV_HOST=127.0.0.1 tools/venv/bin/python -I tools/tv.py \
  'cmd package set-home-activity --user 0 com.spocky.projengmenu'
```

## Configure it

`launcher/app/src/main/res/values/config.xml`:

| Setting | Meaning |
| --- | --- |
| `home_apps` | Pinned first, in order. Everything else is alphabetical. |
| `hidden_apps` | Never listed (the Live TV tuner is covered by the Inputs menu). |
| `live_tv_package` | Appended to the Inputs dropdown. |
| `show_icons` | Off by default: TV icons are coloured tiles, and a grey silhouette reads as a box. |

Wallpapers live in `launcher/app/src/main/res/drawable-nodpi/`.

## Layout of the code

```
launcher/app/src/main/kotlin/me/damon/glasshome/
├── MainActivity.kt          wires the screen together
├── ext/view.kt              dp helper
├── model/AppEntry.kt        one launchable app
└── ui/
    ├── AppListAdapter.kt    rows, focus opacity, launch intents
    ├── GlassPillController.kt sizing, springs and fading of the pill
    ├── GlassPillView.kt     the drawn glass slab
    ├── CornerLinks.kt       D-pad behaviour of the bottom-right stack
    ├── ConnectionLabel.kt   Wi-Fi name or connection type
    └── InputsMenu.kt        HDMI/AV/Live TV dropdown
```

## Tools

`tools/tv.py` is a pure-Python ADB client (works where the `adb` binary is blocked),
`tools/b64push.py` uploads APKs in chunks, `tools/disable.sh` / `tools/restore.sh` apply and undo a
debloat list (`examples/tcl-g10-google-tv/debloat.txt`).

## Licence

MIT — see [LICENSE](LICENSE). Upstream copyrights and asset credits are in [NOTICE.md](NOTICE.md).
