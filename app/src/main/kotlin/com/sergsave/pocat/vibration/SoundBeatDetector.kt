package com.sergsave.pocat.vibration

import io.reactivex.Completable
import io.reactivex.Observable

// Use prepare() for acquire necessary resources, don't use detect() before prepare()
// Use release() for free any resources, don't use detect() after release()
interface SoundBeatDetector {
    val detectionPeriodMs: Long
    fun prepare(): Completable
    fun detect(): Observable<Unit>

    // Support only sync impls
    fun release()
}