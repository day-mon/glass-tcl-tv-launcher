# Per-TV debloat lists

One directory per model. Each file lists package names to disable, one per line; `#` starts a
comment. Nothing is uninstalled, so `./gradlew tvRestore -Pdebloat=examples/<tv>/debloat.txt` puts
everything back.

```sh
cd launcher
./gradlew tvDebloat -Pdebloat=../examples/tcl-g10-google-tv/debloat.txt
./gradlew tvRestore -Pdebloat=../examples/tcl-g10-google-tv/debloat.txt
```
