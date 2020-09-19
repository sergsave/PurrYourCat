package com.sergsave.purryourcat.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.auth.ktx.auth
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.dynamiclinks.ktx.shortLinkAsync
import com.google.firebase.dynamiclinks.ShortDynamicLink
import com.google.firebase.dynamiclinks.ktx.androidParameters
import com.google.firebase.dynamiclinks.ktx.socialMetaTagParameters
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.ktx.storage
import java.io.File
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Completable
import com.sergsave.purryourcat.helpers.ImageUtils
import com.sergsave.purryourcat.helpers.NetworkUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.util.*
import com.sergsave.purryourcat.R

class FirebaseCloudSharingManager(
    private val context: Context,
    val packerFactory: DataPackerFactory
): SharingManager {
    private val cacheDir = File(context.cacheDir, "sharing")
    private val packer = packerFactory.make(cacheDir)

    override fun cleanup(): Completable {
        return Completable.fromCallable {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun makeTakeObservable(pack: Pack): Single<Intent> {
        return packer.pack(pack)
            .flatMap { checkConnection(context).andThen(uploadDataFile(it)) }
            .flatMap {
                createSharingIntent(it, pack.cat.photoUri, pack.cat.name)
            }
    }

    private fun createSharingIntent(downloadLink: Uri, preview: Uri?, catName: String?): Single<Intent> {
        val makeIntentSingle = { previewDonwloadLink: Uri? ->
            val header = context.getString(R.string.sharing_text)
            createDynamicLink(downloadLink, header, previewDonwloadLink, catName)
                .map { makeIntent(it) }
        }

        if (preview == null)
            return makeIntentSingle(null)

        return resizePreview(preview, cacheDir, context)
            .flatMap { uploadPreview(it) }
            .flatMap { makeIntentSingle(it) }
    }

    override fun makeGiveObservable(intent: Intent): Single<Pack> {
        return extractDownloadLink(intent)
            .flatMap { checkConnection(context).andThen(downloadFile(it, cacheDir)) }
            .flatMap { packer.unpack(it) }
    }
}

private fun checkConnection(context: Context): Completable {
    return Completable.create { emitter ->
        if(NetworkUtils.isNetworkAvailable(context))
            emitter.onComplete()
        else
            emitter.onError(IOException("No connection"))
    }
}

private fun uploadFile(file: File, folderName: String): Single<Uri> {
    var uploadTask: UploadTask? = null

    val uploadSingle = Single.create<Uri> { emitter ->
        Firebase.storage.maxUploadRetryTimeMillis = 5000

        val uri = Uri.fromFile(file)
        val uuid = UUID.randomUUID().toString()
        val reference = Firebase.storage.reference.child("$folderName/$uuid/${uri.lastPathSegment}")

        uploadTask = reference.putFile(uri).apply {
            addOnSuccessListener {
                reference.downloadUrl.addOnSuccessListener {
                    emitter.onSuccess(it)
                }
            }.addOnFailureListener {
                emitter.onError(IOException("Uploading error"))
            }
        }
    }.doOnDispose {
        uploadTask?.cancel()
    }

    return authorize().andThen(uploadSingle)
}

private fun uploadDataFile(file: File): Single<Uri> {
    return uploadFile(file, "data")
}

private fun resizePreview(previewUri: Uri, tempDir: File, context: Context): Single<File> {
    val resizedFile = File(tempDir, "preview.jpg")
    // Size for firebase deeplink preview
    val width = 300
    val height = 200

    return Single.create<File> { emitter ->
        if(tempDir.exists().not()) tempDir.mkdirs()

        ImageUtils.loadInto(context, previewUri, resizedFile, width, height) { res ->
            if(res)
                emitter.onSuccess(resizedFile)
            else
                emitter.onError(IllegalStateException("Image load error"))
        }
    }
}

private fun uploadPreview(file: File): Single<Uri> {
    return uploadFile(file, "preview")
}

private fun createDynamicLink(downloadLink: Uri,
                              header: String?,
                              previewLink: Uri?,
                              catName: String?): Single<Uri> {
    return Single.create<Uri> { emitter ->
        Firebase.dynamicLinks.shortLinkAsync(ShortDynamicLink.Suffix.SHORT) {
            link = downloadLink
            domainUriPrefix = "https://purryourcat.page.link"
            androidParameters {  }
            socialMetaTagParameters {
                header?.let { title = it }
                catName?.let { description = it }
                previewLink?.let { imageUrl = it }
            }
        }.addOnSuccessListener { result ->
            emitter.onSuccess(result.shortLink)
        }.addOnFailureListener {
            emitter.onError(IOException("Error in deeplink create"))
        }
    }
}

private fun makeIntent(link: Uri): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        val text = link.toString()
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun extractDownloadLink(intent: Intent): Single<Uri> {
    return Single.create<Uri> { emitter ->
        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener { pendingDynamicLinkData ->
                if (pendingDynamicLinkData != null) {
                    emitter.onSuccess(pendingDynamicLinkData.link)
                }
            }
            .addOnFailureListener { emitter.onError(IOException("Extract link error")) }
    }
}

private fun downloadFile(uri: Uri, dir: File): Single<File> {
    var downloadTask: FileDownloadTask? = null

    val downloadSingle = Single.create<File> { emitter ->
        if(dir.exists().not()) dir.mkdirs()

        Firebase.storage.maxDownloadRetryTimeMillis = 5000

        val reference = Firebase.storage.getReferenceFromUrl(uri.toString())
        val file = File(dir, reference.name)

        downloadTask = reference.getFile(file).apply {
            addOnSuccessListener {
                emitter.onSuccess(file)
            }.addOnFailureListener {
                emitter.onError(IOException("Downloading error"))
            }
        }
    }.doOnDispose {
        downloadTask?.cancel()
    }

    return authorize().andThen(downloadSingle)
}

private fun authorize(): Completable {
    return Completable.create { emitter ->
        Firebase.auth.signInAnonymously()
            .addOnCompleteListener{ task ->
                if (task.isSuccessful) {
                    emitter.onComplete()
                } else {
                    emitter.onError(IOException("Authorize error"))
                }
            }
    }
}
