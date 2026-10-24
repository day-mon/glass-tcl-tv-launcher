package me.damon.glasshome.ext

/** Moves the element at [from] to [to], shifting everything between. False when nothing changed. */
fun <T> MutableList<T>.move(from: Int, to: Int): Boolean
{
    if (from == to || from !in indices || to !in indices) return false
    add(to, removeAt(from))
    return true
}
