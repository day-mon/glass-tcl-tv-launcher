package me.damon.glasshome.model

/**
 * Seed order is pinned-then-alphabetical. Once the owner has rearranged the list, that saved order
 * wins and anything new lands at the end alphabetically.
 */
object AppSorter
{
    fun sort(entries: List<AppEntry>, pinned: List<String>, saved: List<String>): List<AppEntry> =
        entries.sortedWith(if (saved.isEmpty()) seedOrder(pinned) else savedOrder(saved))

    private fun seedOrder(pinned: List<String>): Comparator<AppEntry> = compareBy(
        { pinned.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
        { it.label.lowercase() }
    )

    private fun savedOrder(saved: List<String>): Comparator<AppEntry>
    {
        val rank = saved.withIndex().associate { (index, packageName) -> packageName to index }
        return compareBy(
            { rank[it.packageName] ?: Int.MAX_VALUE },
            { it.label.lowercase() }
        )
    }
}
