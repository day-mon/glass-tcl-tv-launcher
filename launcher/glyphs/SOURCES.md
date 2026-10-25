# Glyph sources

Bundled white app glyphs, converted to Android vector drawables by `./gradlew generateGlyphs`.

| File | Origin | Licence |
| --- | --- | --- |
| `netflix`, `primevideo`, `youtube`, `disneyplus`, `hbomax`, `spotify`, `appletv` | CleanFrame icon set (which draws on Simple Icons) | CC0 1.0 / MIT, see NOTICE.md |
| `hulu` | Simple Icons 9.21 (dropped from later releases) | CC0 1.0 |
| `jellyfin`, `paramountplus`, `mullvad`, `plex`, `tubi` | [Simple Icons](https://simpleicons.org) | CC0 1.0 |

Brand logos remain the trademarks of their owners; they are used here to identify the apps the
owner already has installed.

Some marks are wider than they are tall (Prime Video's smile, the HBO Max and hulu wordmarks).
`generateGlyphs` keeps each source viewBox's aspect ratio in the generated drawable and the row
layout widens the icon slot for those, so nothing gets stretched.
