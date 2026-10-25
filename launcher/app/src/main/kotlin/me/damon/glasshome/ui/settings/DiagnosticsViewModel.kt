package me.damon.glasshome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import me.damon.glasshome.data.SystemStatsReader
import me.damon.glasshome.model.SystemStats

/**
 * Keeps a reading of the TV in flight while the screen is open. The polling stops when the screen
 * goes away, so an idle settings page costs nothing.
 */
class DiagnosticsViewModel(private val reader: SystemStatsReader) : ViewModel()
{
    val stats: StateFlow<SystemStats> = flow {
        while (true)
        {
            emit(reader.read())
            delay(REFRESH_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(RESUBSCRIBE_MS), reader.read())

    companion object
    {
        /** A television is read from the sofa, not monitored: once every two seconds is enough. */
        private const val REFRESH_MS = 2_000L
        private const val RESUBSCRIBE_MS = 5_000L

        fun factory(reader: SystemStatsReader): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiagnosticsViewModel(reader) }
        }
    }
}
