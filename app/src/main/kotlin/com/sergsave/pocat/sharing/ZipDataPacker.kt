package com.sergsave.pocat.sharing

import android.content.Context
import android.net.Uri
import com.sergsave.pocat.helpers.FileUtils
import com.sergsave.pocat.models.extractContent
import com.sergsave.pocat.models.withUpdatedContent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException

private const val BUNDLE_FILE_NAME = "bundle.json"
private const val BUNDLE_ACTUAL_VERSION = 1
private const val VERSION_KEY = "version"
private const val PACK_KEY = "pack"

private fun savePackToBundleFile(pack: Pack, file: File) {
    if(file.exists())
        file.delete()

    val jsonMap = mapOf(
        VERSION_KEY to JsonLiteral(BUNDLE_ACTUAL_VERSION),
        PACK_KEY to Json(JsonConfiguration.Stable).toJson(pack)
    )

    file.writeText(JsonObject(jsonMap).toString())
}

private fun readPackFromBundleFile(file: File): Pack? {
    if(!file.exists())
        return null

    val text = file.readText()

    try {
        val jsoner = Json(JsonConfiguration.Stable)
        val jsonObject = jsoner.parseJson(text).jsonObject

        val version = jsonObject.getPrimitive(VERSION_KEY).int
        if(version > BUNDLE_ACTUAL_VERSION)
            return null

        val packJsonObj = jsonObject.getObject(PACK_KEY)
        if(version < BUNDLE_ACTUAL_VERSION) {
            // adapt pack here
        }

        return jsoner.fromJson(packJsonObj)
    }
    catch (e: Exception) {
        when(e) {
            is NoSuchElementException, is JsonException, is SerializationException -> return null
            else -> throw e
        }
    }
}

class ZipDataPacker(private val context: Context): DataPacker {

    private fun packSync(pack: Pack, buildDir: File): File? {
        if(!buildDir.exists()) buildDir.mkdirs()

        val zipPath = File(buildDir, "cat.zip").path

        val contentUris = pack.cat.extractContent().map { it }
        val withFixedUris = pack.cat.withUpdatedContent { uri ->
            val _name = uri?.let { FileUtils.resolveContentFileName(context, it) }
            _name?.let { Uri.parse(it) }
        }

        val bundleFile = File(buildDir, BUNDLE_FILE_NAME)
        savePackToBundleFile(Pack(withFixedUris), bundleFile)

        val zipped = contentUris + Uri.fromFile(bundleFile)

        FileUtils.zip(context, zipped.toTypedArray(), zipPath)
        return File(zipPath)
    }

    private fun unpackSync(file: File, buildDir: File): Pack? {
        if(!buildDir.exists()) buildDir.mkdirs()

        if(!file.exists())
            return null

        FileUtils.unzip(file.path, buildDir.path)

        val bundleFile = File(buildDir, BUNDLE_FILE_NAME)
        val pack = readPackFromBundleFile(bundleFile)
        if(pack == null)
            return null

        val withFixedUris = pack.cat.withUpdatedContent { uri ->
            uri?.let { Uri.fromFile(File(buildDir, it.toString())) }
        }
        return Pack(withFixedUris)
    }

    override fun pack(pack: Pack, buildDir: File): Single<File> {
        return Single.create<File> { emitter ->
            val file = packSync(pack, buildDir)
            if(file != null)
                emitter.onSuccess(file)
            else
                emitter.onError(IOException("Packing error"))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun unpack(file: File, buildDir: File): Single<Pack> {
        return Single.create<Pack> { emitter ->
            val pack = unpackSync(file, buildDir)
            if(pack != null)
                emitter.onSuccess(pack)
            else
                emitter.onError(IOException("Unpacking error"))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}