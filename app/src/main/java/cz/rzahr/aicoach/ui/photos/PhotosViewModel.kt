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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    data class Folder(
        val key: String,
        val count: Int,
        val latestPhoto: ProgressPhotoEntity?
    )

    /** Složky = 7 výchozích póz + uživatelské složky + případné osiřelé pózy. */
    val folders: StateFlow<List<Folder>> = combine(
        photoRepository.observeAllDesc(),
        photoRepository.observeDistinctPoses(),
        photoRepository.observeCustomFolders()
    ) { photos, distinctPoses, custom ->
        val byPose = photos.groupBy { it.pose }
        val order = buildList {
            addAll(ProgressPhotoEntity.DEFAULT_POSES)
            custom.map { it.name }
                .filter { it !in ProgressPhotoEntity.DEFAULT_POSES }
                .forEach { add(it) }
            distinctPoses
                .filter { it !in this && custom.none { c -> c.name == it } }
                .forEach { add(it) }
        }
        order.map { pose ->
            Folder(
                key = pose,
                count = byPose[pose]?.size ?: 0,
                latestPhoto = byPose[pose]?.firstOrNull()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createFolder(name: String) {
        viewModelScope.launch { photoRepository.createFolder(name) }
    }

    fun photoById(id: Long) = photoRepository.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete(photo: ProgressPhotoEntity) {
        viewModelScope.launch { photoRepository.delete(photo) }
    }

    suspend fun createCaptureFile(): File = photoRepository.createCaptureFile()

    fun addCapturedPhoto(file: File) {
        viewModelScope.launch {
            runCatching { photoRepository.addCapturedPhoto(file, pose = ProgressPhotoEntity.POSE_OTHER) }
        }
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            runCatching { photoRepository.addPhotosFromUris(uris, ProgressPhotoEntity.POSE_OTHER) }
        }
    }
}