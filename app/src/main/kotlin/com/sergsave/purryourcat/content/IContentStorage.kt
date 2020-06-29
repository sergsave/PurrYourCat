package com.sergsave.purryourcat.content

import android.net.Uri

interface IContentStorage {
    fun store(sourceContent: Uri, outputName: String): Uri?
    fun store(sourceContent: Uri): Uri? // keep source name
    fun read(): List<Uri>?
    fun remove(uri: Uri): Boolean
}