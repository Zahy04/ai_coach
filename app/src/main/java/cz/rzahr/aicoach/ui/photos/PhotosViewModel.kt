package cz.rzahr.aicoach.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.data.repo.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    val photosDesc: StateFlow<List<ProgressPhotoEntity>> = photoRepository.observeAllDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun photoById(id: Long) = photoRepository.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun createCaptureFile(): File = photoRepository.createCaptureFile()

    fun addCapturedPhoto(file: File) {
        viewModelScope.launch {
            runCatching { photoRepository.addCapturedPhoto(file) }
        }
    }

    fun addPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching { photoRepository.addPhotoFromUri(uri) }
        }
    }

    fun delete(photo: ProgressPhotoEntity) {
        viewModelScope.launch { photoRepository.delete(photo) }
    }
}
