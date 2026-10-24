package me.damon.glasshome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.damon.glasshome.data.HomeRepository
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial

/** Writes appearance choices straight into the repository the home screen is already watching. */
class SettingsViewModel(private val repository: HomeRepository) : ViewModel()
{
    val appearance: StateFlow<Appearance> = repository.state
        .map { it.appearance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value.appearance)

    fun setLayout(mode: LayoutMode) = repository.setLayout(mode)

    fun setMaterial(material: PillMaterial) = repository.setMaterial(material)

    fun setIcons(style: IconStyle) = repository.setIcons(style)

    companion object
    {
        fun factory(repository: HomeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
