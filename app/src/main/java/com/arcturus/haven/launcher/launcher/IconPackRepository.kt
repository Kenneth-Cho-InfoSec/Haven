package com.arcturus.haven.launcher.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/** Reads the common icon-pack appfilter.xml contract without retaining plugin resources. */
internal class IconPackRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val componentMappings = ConcurrentHashMap<String, Map<String, String>>()

    fun iconFor(packPackage: String?, component: ComponentName): Drawable? {
        if (packPackage.isNullOrBlank() || packPackage == DEFAULT_PACK) return null
        val mappings = componentMappings.getOrPut(packPackage) { readComponentMappings(packPackage) }
        val resourceName = mappings[component.flattenToString()] ?: return null
        return runCatching {
            val resources = packageManager.getResourcesForApplication(packPackage)
            val resourceId = resources.getIdentifier(resourceName, "drawable", packPackage)
                .takeIf { it != 0 }
                ?: resources.getIdentifier(resourceName, "mipmap", packPackage).takeIf { it != 0 }
                ?: return null
            ResourcesCompat.getDrawable(resources, resourceId, context.theme)?.mutate()
        }.getOrNull()
    }

    fun availablePacks(): List<Pair<String, String>> = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    ).flatMap { action ->
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(Intent(action), 0).mapNotNull { result ->
            val info = result.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (info.packageName == context.packageName) null
            else info.packageName to packageManager.getApplicationLabel(info).toString()
        }
    }.distinctBy { it.first }.sortedBy { it.second.lowercase() }

    private fun readComponentMappings(packPackage: String): Map<String, String> = runCatching {
        val resources = packageManager.getResourcesForApplication(packPackage)
        val xmlId = resources.getIdentifier("appfilter", "xml", packPackage)
        if (xmlId != 0) {
            resources.getXml(xmlId).use(::parse)
        } else {
            val stream = runCatching {
                context.createPackageContext(packPackage, Context.CONTEXT_RESTRICTED).assets.open("appfilter.xml")
            }.getOrNull() ?: return@runCatching emptyMap()
            stream.use {
                val parser = Xml.newPullParser().apply { setInput(it, "UTF-8") }
                parse(parser)
            }
        }
    }.getOrDefault(emptyMap())

    private fun parse(parser: XmlPullParser): Map<String, String> {
        val mappings = LinkedHashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                val component = parser.getAttributeValue(null, "component")?.let(::componentFromAppFilter)
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && !drawable.isNullOrBlank()) {
                    mappings[component.flattenToString()] = drawable.substringAfterLast('/')
                }
            }
            event = parser.next()
        }
        return mappings
    }

    private fun componentFromAppFilter(value: String): ComponentName? {
        val flattened = value.removePrefix("ComponentInfo{").removeSuffix("}").trim()
        val separator = flattened.indexOf('/')
        if (separator <= 0 || separator == flattened.lastIndex) return null
        val packageName = flattened.substring(0, separator)
        var className = flattened.substring(separator + 1)
        if (className.startsWith('.')) className = packageName + className
        if (className.isBlank()) return null
        return ComponentName(packageName, className)
    }

    companion object {
        const val DEFAULT_PACK = "default"
    }
}
