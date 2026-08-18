package com.jasper.facemirror.speech

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Копирует `assets/model-ru` в filesDir, чтобы нативный Vosk мог открыть файлы с диска.
 * Повторно не копирует, пока совпадает `uuid`.
 */
internal object VoskModelStore {
    const val ASSET_DIR = "model-ru"

    fun unpack(context: Context): File {
        val dest = File(context.filesDir, "vosk-model-ru")
        val uuid = context.assets.open("$ASSET_DIR/uuid").bufferedReader().use { it.readLine().trim() }
        val marker = File(dest, "uuid")
        if (marker.exists() && marker.readText().trim() == uuid && File(dest, "am").isDirectory) {
            return dest
        }
        dest.deleteRecursively()
        dest.mkdirs()
        copyDir(context.assets, ASSET_DIR, dest)
        return dest
    }

    private fun copyDir(assets: AssetManager, assetPath: String, dest: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) {
            copyDir(assets, "$assetPath/$child", File(dest, child))
        }
    }
}
