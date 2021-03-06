package com.sergsave.pocat.helpers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

open class DisposableViewModel : ViewModel() {
    private val disposable = CompositeDisposable()

    override fun onCleared() {
        disposable.clear()
        super.onCleared()
    }

    fun addDisposable(disposable: Disposable) {
        this.disposable.add(disposable)
    }
}

class ViewModelFactory<T: ViewModel>(private val modelClass: Class<T>,
                                     private val modelSupplier: () -> T): Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return if (modelClass.isAssignableFrom(this.modelClass)) {
            modelSupplier() as T
        } else {
            throw IllegalArgumentException("ViewModel Not Found")
        }
    }
}