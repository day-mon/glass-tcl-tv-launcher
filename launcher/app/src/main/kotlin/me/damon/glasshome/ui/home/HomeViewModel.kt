package me.damon.glasshome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import me.damon.glasshome.data.HomeRepository
import me.damon.glasshome.model.AppEntry
import me.damon.glasshome.model.HomeState

/** Holds the home screen's state; the activity only renders it and forwards input. */
class HomeViewModel(private val repository: HomeRepository) : ViewModel()
{
    val state: StateFlow<HomeState> = repository.state

    /** Called when the screen comes back to the front: apps may have been installed or removed. */
    fun reload() = repository.refresh()

    fun saveOrder(entries: List<AppEntry>) = repository.saveOrder(entries.map { it.packageName })

    companion object
    {
        fun factory(repository: HomeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
