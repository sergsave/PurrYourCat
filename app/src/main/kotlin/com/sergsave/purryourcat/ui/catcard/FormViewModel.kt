package com.sergsave.purryourcat.ui.catcard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.sergsave.purryourcat.R
import com.sergsave.purryourcat.persistent.CatRepository
import com.sergsave.purryourcat.content.ContentRepository
import com.sergsave.purryourcat.helpers.Event
import com.sergsave.purryourcat.helpers.DisposableViewModel
import com.sergsave.purryourcat.models.CatData
import com.sergsave.purryourcat.models.Card
import com.sergsave.purryourcat.models.Cat

class FormViewModel(
    private val catRepository: CatRepository,
    private val contentRepository: ContentRepository,
    private val card: Card? = null
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

    private val _notValidDataMessageEvent = MutableLiveData<Event<Unit>>()
    val notValidDataMessageEvent: LiveData<Event<Unit>>
        get() = _notValidDataMessageEvent

    private val _audioChangedMessageEvent = MutableLiveData<Event<Unit>>()
    val audioChangedMessageEvent: LiveData<Event<Unit>>
        get() = _audioChangedMessageEvent

    private val _openCardEvent = MutableLiveData<Event<Card>>()
    val openCardEvent: LiveData<Event<Card>>
        get() = _openCardEvent

    init {
        val data = card?.cat?.data ?: CatData()
        updateData(data)
        backup = data
    }

    private fun currentData() = CatData(_name.value, _photoUri.value, _audioUri.value)

    val toolbarTitleStringId: Int
        get() {
            return if(card == null) R.string.add_new_cat else R.string.edit_cat
        }

    fun changeName(name: String) {
        // Empty text is null for correct comparing data with backup
        _name.value = if(name.isNotEmpty()) name else null
    }

    fun changePhoto(uri: Uri) {
        if(uri != _photoUri.value) {
            addDisposable(contentRepository.addImage(uri).subscribe { newUri ->
                _photoUri.value = newUri
            })
        }
    }

    fun changeAudio(uri: Uri) {
        if(_audioUri.value != null)
            _audioChangedMessageEvent.value = Event(Unit)

        if(uri != _audioUri.value) {
            addDisposable(contentRepository.addAudio(uri).subscribe { newUri ->
                _audioUri.value = newUri
            })
        }
    }

    fun onApplyPressed() {
        if (isCurrentDataValid().not()) {
            _notValidDataMessageEvent.value = Event(Unit)
            return
        }

        syncDataWithRepo()
    }

    fun handleBackPressed(): Boolean {
        if(wereChangesAfterBackup().not())
            return true

        _unsavedChangesMessageEvent.value = Event(Unit)
        return false
    }

    fun onDiscardChanges() {
        restoreFromBackup()
    }

    private fun syncDataWithRepo() {
        card?.let {
            val disposable = catRepository.update(it.cat).subscribe(
                { _openCardEvent.value = Event(card) },
                { assert(false) }
            )

            addDisposable(disposable)
        } ?: run {
            val newCat = Cat(data = currentData())
            addDisposable(catRepository.add(newCat).subscribe {
                _openCardEvent.value = Event(Card(newCat, true, true))
            })
        }
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
