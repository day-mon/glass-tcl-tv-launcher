// Turns the monochrome SVGs in launcher/glyphs/ into Android vector drawables.
//
// Simple Icons style SVGs are a 24x24 viewBox with one or more <path> elements, which maps almost
// one-to-one onto <vector>. Doing it here keeps the build self-contained: no Python, no Node.

import javax.xml.parsers.DocumentBuilderFactory

// SVG path data is copied verbatim. Android's PathParser is stricter than SVG and rejects some
// exports (glued arc flags, for example), so fix such a glyph in its .svg source rather than here:
// rewriting paths in the build corrupted logos that had been fine.

/** Keeps generated dp values short: "6.28" rather than "6.276923076923077". */
fun dp(value: Double): String = String.format("%.2f", value).trimEnd('0').trimEnd('.')

val glyphSources = fileTree("glyphs") { include("*.svg") }
val glyphOutput = file("app/src/main/res/drawable")

tasks.register("generateGlyphs") {
    group = "build"
    description = "Convert launcher/glyphs/*.svg into res/drawable/glyph_*.xml"
    inputs.files(glyphSources)
    outputs.dir(glyphOutput)

    doLast {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        glyphSources.files.sortedBy { it.name }.forEach { svg ->
            val document = factory.newDocumentBuilder().parse(svg)
            val root = document.documentElement
            val viewBox = root.getAttribute("viewBox").ifBlank { "0 0 24 24" }
                .split(' ', ',').filter { it.isNotBlank() }
            // Some glyphs crop the viewBox (e.g. "5.6 12.6 13 3.4"); the origin has to be
            // translated away or every path lands outside the canvas and nothing draws.
            val offsetX = viewBox.getOrNull(0)?.toFloatOrNull() ?: 0f
            val offsetY = viewBox.getOrNull(1)?.toFloatOrNull() ?: 0f
            val paths = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path")

            val body = buildString {
                for (index in 0 until paths.length)
                {
                    val node = paths.item(index)
                    // Paths are copied verbatim: Android's parser is stricter than SVG, and
                    // rewriting arcs here corrupted glyphs that were previously fine. The
                    // GlyphIntegrityTest catches anything Android refuses instead.
                    val data = (node.attributes.getNamedItem("d")?.nodeValue ?: continue)
                        .replace(Regex("\\s+"), " ").trim()
                    val evenOdd = node.attributes.getNamedItem("fill-rule")?.nodeValue == "evenodd"
                    append("    <path\n        android:fillColor=\"#FFFFFFFF\"")
                    if (evenOdd) append("\n        android:fillType=\"evenOdd\"")
                    append("\n        android:pathData=\"$data\" />\n")
                }
            }

            // Android stretches the viewport onto the drawable bounds, so a wide glyph declared as
            // a 24x24dp square comes out smeared (the Prime Video smile was a vertical squiggle).
            // Keep the source aspect ratio; ImageView scales it with fitCenter.
            val box = 24f
            val boxWidth = viewBox.getOrNull(2)?.toFloatOrNull() ?: box
            val boxHeight = viewBox.getOrNull(3)?.toFloatOrNull() ?: box
            val wide = boxWidth >= boxHeight
            val width = dp((if (wide) box else box * boxWidth / boxHeight).toDouble())
            val height = dp((if (wide) box * boxHeight / boxWidth else box).toDouble())

            val name = svg.nameWithoutExtension
            val target = glyphOutput.resolve("glyph_$name.xml")
            target.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
<!-- $name glyph. Source: launcher/glyphs/$name.svg. See NOTICE.md. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${width}dp"
    android:height="${height}dp"
    android:viewportWidth="${viewBox[2]}"
    android:viewportHeight="${viewBox[3]}">
    <group android:translateX="${-offsetX}" android:translateY="${-offsetY}">
$body    </group>
</vector>
"""
            )
            logger.lifecycle("glyph_$name.xml")
        }
    }
}
