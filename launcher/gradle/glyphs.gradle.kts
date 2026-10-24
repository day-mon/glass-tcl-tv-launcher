// Turns the monochrome SVGs in launcher/glyphs/ into Android vector drawables.
//
// Simple Icons style SVGs are a 24x24 viewBox with one or more <path> elements, which maps almost
// one-to-one onto <vector>. Doing it here keeps the build self-contained: no Python, no Node.

import javax.xml.parsers.DocumentBuilderFactory

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
            val paths = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path")

            val body = buildString {
                for (index in 0 until paths.length)
                {
                    val node = paths.item(index)
                    val data = (node.attributes.getNamedItem("d")?.nodeValue ?: continue)
                        .replace(Regex("\\s+"), " ").trim()
                    val evenOdd = node.attributes.getNamedItem("fill-rule")?.nodeValue == "evenodd"
                    append("    <path\n        android:fillColor=\"#FFFFFFFF\"")
                    if (evenOdd) append("\n        android:fillType=\"evenOdd\"")
                    append("\n        android:pathData=\"$data\" />\n")
                }
            }

            val name = svg.nameWithoutExtension
            val target = glyphOutput.resolve("glyph_$name.xml")
            target.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
<!-- $name glyph. Source: launcher/glyphs/$name.svg. See NOTICE.md. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${viewBox[2]}"
    android:viewportHeight="${viewBox[3]}">
$body</vector>
"""
            )
            logger.lifecycle("glyph_$name.xml")
        }
    }
}
