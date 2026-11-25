package com.arcturus.haven.launcher.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import java.io.File

/** Disk cache for work-profile app icons so frozen apps on Home or in Search keep icons when disconnected. */
class VaultIconCache(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.filesDir, "vault_icons").apply { mkdirs() }

    fun save(packageName: String, bitmap: Bitmap) {
        runCatching {
            iconFile(packageName).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun load(packageName: String): Drawable? =
        iconFile(packageName).takeIf(File::exists)
            ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            ?.toDrawable(appContext.resources)

    fun delete(packageName: String) {
        runCatching { iconFile(packageName).delete() }
    }

    private fun iconFile(packageName: String): File =
        File(cacheDir, "${packageName.replace(SAFE_NAME_REGEX, "_")}.png")

    private companion object {
        val SAFE_NAME_REGEX = Regex("[^a-zA-Z0-9._-]")
    }
}
