package cz.rzahr.aicoach.ui.photos

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.data.repo.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PhotosFolderViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val pose: String = checkNotNull(savedStateHandle["pose"])

    val photos: StateFlow<List<ProgressPhotoEntity>> = photoRepository.observeByPose(pose)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Všechny pózy pro přeřazení: výchozí + uživatelské + reálně použité. */
    val allPoses: StateFlow<List<String>> = combine(
        photoRepository.observeDistinctPoses(),
        photoRepository.observeCustomFolders()
    ) { distinct, custom ->
        buildList {
            addAll(ProgressPhotoEntity.DEFAULT_POSES)
            custom.map { it.name }.filter { it !in this }.forEach { add(it) }
            distinct.filter { it !in this }.forEach { add(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressPhotoEntity.DEFAULT_POSES)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    fun moveToPose(id: Long, newPose: String) {
        viewModelScope.launch { photoRepository.moveToPose(id, newPose) }
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val count = runCatching { photoRepository.addPhotosFromUris(uris, pose) }.getOrDefault(0)
            if (count > 0) _events.tryEmit("+$count")
        }
    }

    fun delete(photo: ProgressPhotoEntity) {
        viewModelScope.launch { photoRepository.delete(photo) }
    }

    suspend fun createCaptureFile(): File = photoRepository.createCaptureFile()

    fun addCaptured(file: File) {
        viewModelScope.launch {
            runCatching { photoRepository.addCapturedPhoto(file, pose = pose) }
        }
    }
}