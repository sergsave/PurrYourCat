package com.sergsave.pocat.screens.catcard

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.sergsave.pocat.R
import com.sergsave.pocat.content.ContentRepository
import com.sergsave.pocat.helpers.DisposableViewModel
import com.sergsave.pocat.helpers.Event
import com.sergsave.pocat.models.Card
import com.sergsave.pocat.models.CatData
import com.sergsave.pocat.persistent.CatDataRepository
import com.sergsave.pocat.screens.catcard.analytics.CatCardAnalyticsHelper
import timber.log.Timber

class FormViewModel(
    private val catDataRepository: CatDataRepository,
    private val contentRepository: ContentRepository,
    private val card: Card? = null,
    private val analytics: CatCardAnalyticsHelper
) : DisposableViewModel() {

    private var backup: CatData? = null

    private val _name = MutableLiveData<String>()
    val name: LiveData<String>
        get() = _name

    private val _photoUri = MutableLiveData<Uri>()
    val photoUri: LiveData<Uri>
        get() = _photoUri

    private val _audioUri = MutableLiveData<Uri>()
    val audioUri: LiveData<Uri>
        get() = _audioUri

    enum class SoundButtonType { ADD_SOUND, SOUND_IS_ADDED }

    val soundButtonType: LiveData<SoundButtonType> = Transformations.map(_audioUri) { uri ->
        if(uri == null)
            SoundButtonType.ADD_SOUND
        else
            SoundButtonType.SOUND_IS_ADDED
    }

    private val _unsavedChangesMessageEvent = MutableLiveData<Event<Unit>>()
    val unsavedChangesMessageEvent: LiveData<Event<Unit>>
        get() = _unsavedChangesMessageEvent

    private val _snackbarMessageEvent = MutableLiveData<Event<Int>>()
    val snackbarMessageEvent: LiveData<Event<Int>>
        get() = _snackbarMessageEvent

    private val _openCardEvent = MutableLiveData<Event<Card>>()
    val openCardEvent: LiveData<Event<Card>>
        get() = _openCardEvent

    init {
        if(card != null) assert (card.persistentId != null)

        val data = card?.data ?: CatData()
        updateData(data)
        backup = data
    }

    private fun currentData() = CatData(_name.value, _photoUri.value, _audioUri.value)

    val toolbarTitleStringId: Int
        get() {
            return if(card == null) R.string.form_new_cat_title else R.string.form_edit_cat_title
        }

    fun changeName(name: String) {
        // Empty text is null for correct comparing data with backup
        _name.value = if(name.isNotEmpty()) name else null
    }

    fun changePhoto(uri: Uri?) {
        analytics.onChangePhoto()

        if (uri == null) {
            onPhotoChangeError()
            return
        }

        if(uri != _photoUri.value) {
            addDisposable(contentRepository.addImage(uri).subscribe(
                { newUri -> _photoUri.value = newUri },
                {
                    Timber.e(it, "Photo add failed")
                    onPhotoChangeError()
                }
            ))
        }
    }

    private fun onPhotoChangeError() {
        _snackbarMessageEvent.value = Event(R.string.form_popup_file_add_failed)
        analytics.onPhotoChangeError()
    }

    fun changeAudio(uri: Uri?) {
        analytics.onChangeAudio()

        if (uri == null) {
            onAudioChangeError()
            return
        }

        if(_audioUri.value != null)
            _snackbarMessageEvent.value = Event(R.string.form_popup_audio_changed)

        if(uri != _audioUri.value) {
            addDisposable(contentRepository.addAudio(uri).subscribe(
                { newUri -> _audioUri.value = newUri },
                {
                    Timber.e(it, "Audio add failed")
                    onAudioChangeError()
                }
            ))
        }
    }

    private fun onAudioChangeError() {
        _snackbarMessageEvent.value = Event(R.string.form_popup_file_add_failed)
        analytics.onAudioChangeError()
    }

    fun onApplyPressed() {
        analytics.onTryApplyChanges(isCurrentDataValid())

        if (!isCurrentDataValid()) {
            _snackbarMessageEvent.value = Event(R.string.form_popup_fill_the_form)
            return
        }

        syncDataWithRepo()
    }

    fun handleBackPressed(): Boolean {
        if(!wereChangesAfterBackup())
            return true

        _unsavedChangesMessageEvent.value = Event(Unit)
        return false
    }

    fun onDiscardChanges() {
        restoreFromBackup()
    }

    private fun syncDataWithRepo() {
        val data = currentData()
        val currentCard = card?.copy(data = data)
        val id = currentCard?.persistentId

        if(currentCard != null && id != null) {
            addDisposable(catDataRepository.update(id, currentCard.data).subscribe(
                { _openCardEvent.value = Event(currentCard) }
                // Don't handle error because it's critical bug
            ))
            return
        }

        addDisposable(catDataRepository.add(data)
            .doOnSuccess { analytics.onCatAdded() }
            .subscribe(
            { newId -> _openCardEvent.value = Event(Card(newId, data, true, true)) }
            // Don't handle error because it's critical bug
        ))
    }

    private fun updateData(data: CatData?) {
        _name.value = data?.name
        _photoUri.value = data?.photoUri
        _audioUri.value = data?.purrAudioUri
    }

    private fun isCurrentDataValid(): Boolean {
        return _name.value != null && _photoUri.value != null && _audioUri.value != null
    }

    private fun restoreFromBackup() {
        backup?.let{ updateData(it) }
    }

    private fun wereChangesAfterBackup(): Boolean {
        return currentData() != backup
    }
}
