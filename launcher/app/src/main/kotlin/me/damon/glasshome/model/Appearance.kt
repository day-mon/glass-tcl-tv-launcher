package me.damon.glasshome.model

/** A vertical list down the left, or a row of tiles along the bottom. */
enum class LayoutMode { LIST, DOCK }

/** How the focus pill is drawn. */
enum class PillMaterial { GLASS, CLEAR, SOLID }

/** Bundled glyphs, or just the app's name. */
enum class IconStyle { GLYPHS, NAMES }

/** Everything the settings screen can change. */
data class Appearance(
    val layout: LayoutMode = LayoutMode.LIST,
    val material: PillMaterial = PillMaterial.GLASS,
    val icons: IconStyle = IconStyle.GLYPHS
)

/** What the home screen draws. */
data class HomeState(
    val appearance: Appearance = Appearance(),
    val entries: List<AppEntry> = emptyList()
)
