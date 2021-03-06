package com.sergsave.pocat.screens.catcard.analytics

import android.net.Uri
import com.sergsave.pocat.analytics.AnalyticsTracker
import com.sergsave.pocat.sharing.Pack
import com.sergsave.pocat.sharing.WebSharingManager.*
import java.util.concurrent.TimeUnit

private fun diffTimeInSec(startTime: Long) =
    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime)

class CatCardAnalyticsHelper(
    private val tracker: AnalyticsTracker,
    private val fileSizeByteCalculator: (Uri) -> Long) {
    private var touchTime: Long? = null
    private var uploadTime: Long? = null
    private var downloadTime: Long? = null

    private var uploadingPack: Pack? = null

    fun onTouchStarted() {
        touchTime = System.currentTimeMillis()
    }

    fun onTouchFinished() {
        touchTime?.let { tracker.sendEvent(CatTouch(diffTimeInSec(it))) }
        touchTime = null
    }

    fun onShareClicked() = tracker.sendEvent(ShareActionClick())
    fun onEditClicked() = tracker.sendEvent(EditActionClick())
    fun onSaveClicked() = tracker.sendEvent(SaveActionClick())

    fun onChangePhoto() = tracker.sendEvent(PhotoAdded())
    fun onChangeAudio() = tracker.sendEvent(AudioAdded())

    fun onPhotoChangeError() = tracker.sendEvent(PhotoAddingError())
    fun onAudioChangeError() = tracker.sendEvent(AudioAddingError())

    fun onCatAdded() = tracker.sendEvent(NewCatAdded())

    fun onTryApplyChanges(result: Boolean) = tracker.sendEvent(TryApplyFormChanges(result))

    fun onVibratorCreateFailed() = tracker.sendEvent(VibrationNotWorkingError())

    private fun makeTransferInfo(pack: Pack, transferStartTime: Long) = SharingTransferInfo(
        diffTimeInSec(transferStartTime),
        pack.cat.photoUri?.let { fileSizeByteCalculator(it) } ?: 0,
        pack.cat.purrAudioUri?.let { fileSizeByteCalculator(it) } ?: 0
    )

    fun onUploadStarted(pack: Pack) {
        uploadTime = System.currentTimeMillis()
        uploadingPack = pack
    }

    fun onUploadFinished() {
        val event = uploadTime?.let { time ->
            uploadingPack?.let { SharingDataUpload(makeTransferInfo(it, time)) }
        }

        event?.let { tracker.sendEvent(it) }
        uploadTime = null
    }

    fun onUploadCanceled() {
        uploadTime = null
    }

    fun onUploadFailed(throwable: Throwable) {
        val cause = when(throwable) {
            is NoConnectionException -> SharingError.NO_CONNECTION
            is DailyQuotaExceededException -> SharingError.QUOTA_EXCEEDED
            else -> SharingError.UNKNOWN
        }
        tracker.sendEvent(SharingDataUploadError(cause))
    }

    fun onDownloadStarted() {
        downloadTime = System.currentTimeMillis()
    }

    fun onDownloadCanceled() {
        downloadTime = null
    }

    fun onDownloadFinished(pack: Pack) {
        downloadTime?.let {
            tracker.sendEvent(SharingDataDownload(makeTransferInfo(pack, it)))
        }
        downloadTime = null
    }

    fun onDownloadFailed(throwable: Throwable) {
        val cause = when(throwable) {
            is NoConnectionException -> SharingError.NO_CONNECTION
            is InvalidLinkException -> SharingError.INVALID_LINK
            else -> SharingError.UNKNOWN
        }
        tracker.sendEvent(SharingDataDownloadError(cause))
    }

    fun onInvalidDataExtracted() = tracker.sendEvent(InvalidSharingDataDownloadedError())
}