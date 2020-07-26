package com.sergsave.purryourcat.sharing

import android.content.Context
import com.sergsave.purryourcat.helpers.NetworkUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.io.*
import java.net.URL
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.schedulers.Schedulers
import com.estmob.android.sendanywhere.sdk.ReceiveTask
import com.estmob.android.sendanywhere.sdk.SendTask
import com.estmob.android.sendanywhere.sdk.Task

class SendAnywhereNetworkService(private val context: Context): INetworkService {

    init {
        val apiKey = "dfe91eb770456c0a269625ce8e0128ad2b4a5fb0"
        Task.init(apiKey)
    }

    private val connectionError = IOException("No connection")

    override fun makeUploadObservable(file: File): Single<URL> {
        var task: SendTask? = null

        return Single.create<URL> { emitter ->
            if(NetworkUtils.isNetworkAvailable(context).not())
                emitter.onError(connectionError)

            task = SendTask(context, arrayOf(file), true).also {
                initSendTask(it, emitter)
                it.start()
            }
        }.doOnDispose {
            task?.cancel()
            task = null
        }
    }

    private fun initSendTask(task: SendTask, emitter: SingleEmitter<URL>) {
        var link: URL? = null

        val handleSuccess = { _link: URL -> emitter.onSuccess(_link) }
        val handleError = { emitter.onError(IOException("Upload failed")) }

        task.setOnTaskListener { state, detailedState, obj ->
            when (state) {
                Task.State.PREPARING -> {
                    val key = obj as? String
                    if (detailedState == SendTask.DetailedState.PREPARING_UPDATED_KEY && key != null)
                        (task.getValue(Task.Value.LINK_URL) as? String)?.let { link = URL(it) }
                }
                Task.State.TRANSFERRING -> {
                    // Progress not supported
                }
                Task.State.FINISHED -> {
                    when (detailedState) {
                        SendTask.DetailedState.FINISHED_SUCCESS ->
                            link?.let { handleSuccess(it) } ?: run { handleError() }
                        SendTask.DetailedState.FINISHED_ERROR -> handleError()
                    }
                }
            }
        }
    }

    override fun makeDownloadObservable(url: URL, destDir: File): Single<File> {
        var task: ReceiveTask? = null

        return Single.create<File> { emitter ->
            if(NetworkUtils.isNetworkAvailable(context).not())
                emitter.onError(connectionError)

            task = ReceiveTask(context, url.toString(), destDir).also {
                initReceiveTask(it, emitter)
                it.start()
            }
        }.doOnDispose {
            task?.cancel()
            task = null
        }
    }

    private fun initReceiveTask(task: ReceiveTask, emitter: SingleEmitter<File>) {
        var file: File? = null

        val handleSuccess = { _file: File -> emitter.onSuccess(_file) }
        val handleError = { emitter.onError(IOException("Download failed")) }

        task.setOnTaskListener { state, detailedState, obj ->
            when (state) {
                Task.State.TRANSFERRING -> {
                    val fileState = obj as? Task.FileState
                    if (fileState != null)
                        file = fileState.file.path?.let { File(it) }
                }
                Task.State.FINISHED -> {
                    when (detailedState) {
                        SendTask.DetailedState.FINISHED_SUCCESS ->
                            file?.let { handleSuccess(it) } ?: run { handleError() }
                        SendTask.DetailedState.FINISHED_ERROR -> handleError()
                    }
                }
            }
        }
    }
}