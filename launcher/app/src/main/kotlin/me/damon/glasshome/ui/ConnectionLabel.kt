package me.damon.glasshome.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import me.damon.glasshome.R

/** The Wi-Fi network name when it can be read, otherwise the connection type. */
object ConnectionLabel
{
    fun of(context: Context): String
    {
        val fallback = context.getString(R.string.wifi)
        return try
        {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            when
            {
                capabilities == null -> context.getString(R.string.offline)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    context.getString(R.string.ethernet)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ssid(context, fallback)
                else -> fallback
            }
        }
        catch (ignored: Exception)
        {
            fallback
        }
    }

    private fun ssid(context: Context, fallback: String): String
    {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val name = manager?.connectionInfo?.ssid?.replace("\"", "")?.trim()
        if (name.isNullOrEmpty() || name.contains("unknown") || name == "0x") return fallback
        return name
    }
}
