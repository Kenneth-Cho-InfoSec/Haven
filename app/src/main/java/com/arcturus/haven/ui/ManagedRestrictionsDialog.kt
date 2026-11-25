package com.arcturus.haven.ui

import android.app.Activity
import android.content.Context
import android.content.RestrictionEntry
import android.os.Bundle
import android.text.InputType
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.BundleCompat
import com.arcturus.haven.R
import com.arcturus.haven.services.IHavenService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Shared editor dialog for manifest-declared managed app restrictions in the work profile. */
object ManagedRestrictionsDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        workService: IHavenService,
        packageName: String,
        appLabel: String,
        useMaterialTheme: Boolean = false,
    ) {
        val themedContext: Context = if (useMaterialTheme) activity else ContextThemeWrapper(activity, R.style.AppTheme)
        Thread {
            try {
                val response = workService.getManagedApplicationRestrictions(packageName)
                val current = response.getBundle("values") ?: Bundle()
                val schema = BundleCompat.getParcelableArrayList(response, "schema", Bundle::class.java)
                runIfAlive(activity) {
                    val dialogTitle = activity.getString(R.string.managed_restrictions_title, appLabel)
                    if (schema.isNullOrEmpty()) {
                        dialog(themedContext, dialogTitle, activity.getString(R.string.managed_restrictions_none))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        return@runIfAlive
                    }
                    val labels = schema.map { it.displayTitle() }.toTypedArray()
                    dialog(themedContext, dialogTitle)
                        .setItems(labels) { _, which ->
                            editRestriction(activity, themedContext, workService, packageName, schema[which], current)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            } catch (_: Exception) {
                runIfAlive(activity) {
                    Toast.makeText(activity, R.string.managed_restrictions_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun editRestriction(
        activity: Activity,
        themedContext: Context,
        workService: IHavenService,
        packageName: String,
        entry: Bundle,
        current: Bundle,
    ) {
        val key = entry.getString("key") ?: return
        val title = entry.displayTitle(key)
        val description = entry.getString("description")
        fun saveUpdate(mutate: Bundle.() -> Unit) = save(activity, workService, packageName, Bundle(current).apply(mutate))

        when (entry.getInt("type", RestrictionEntry.TYPE_NULL)) {
            RestrictionEntry.TYPE_BOOLEAN -> {
                val enabled = if (current.containsKey(key)) current.getBoolean(key) else entry.getBoolean("defaultBoolean")
                val options = arrayOf(
                    activity.getString(R.string.managed_restrictions_disabled),
                    activity.getString(R.string.managed_restrictions_enabled),
                )
                dialog(themedContext, title, description)
                    .setSingleChoiceItems(options, if (enabled) 1 else 0) { d, which ->
                        saveUpdate { putBoolean(key, which == 1) }
                        d.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            RestrictionEntry.TYPE_CHOICE, RestrictionEntry.TYPE_MULTI_SELECT -> {
                val labels = entry.getStringArray("choiceEntries")
                val choices = entry.getStringArray("choiceValues")
                if (labels == null || choices == null || labels.isEmpty() || labels.size != choices.size) {
                    showUnsupported(activity, themedContext, title, description)
                    return
                }
                val selected = selectedValues(current, entry, key)
                if (entry.getInt("type", RestrictionEntry.TYPE_NULL) == RestrictionEntry.TYPE_CHOICE) {
                    dialog(themedContext, title, description)
                        .setSingleChoiceItems(labels, choices.indexOf(selected.firstOrNull())) { d, which ->
                            saveUpdate { putStringArray(key, arrayOf(choices[which])) }
                            d.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    val checked = BooleanArray(choices.size) { choices[it] in selected }
                    val updatedValues = selected.toMutableSet()
                    dialog(themedContext, title, description)
                        .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                            if (isChecked) updatedValues.add(choices[which]) else updatedValues.remove(choices[which])
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            saveUpdate { putStringArray(key, updatedValues.toTypedArray()) }
                        }
                        .show()
                }
            }
            RestrictionEntry.TYPE_STRING, RestrictionEntry.TYPE_INTEGER -> {
                val isInteger = entry.getInt("type", RestrictionEntry.TYPE_NULL) == RestrictionEntry.TYPE_INTEGER
                val input = EditText(themedContext).apply {
                    isSingleLine = true
                    if (isInteger) {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                        val value = if (current.containsKey(key)) current.getInt(key) else entry.getInt("defaultInteger")
                        setText(String.format(Locale.ROOT, "%d", value))
                    } else {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        setText(current.getString(key, entry.getString("defaultString", "")))
                    }
                }
                dialog(themedContext, title, description)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val text = input.text.toString()
                        if (isInteger) {
                            val parsed = text.toIntOrNull()
                            if (parsed == null) {
                                Toast.makeText(activity, R.string.managed_restrictions_save_failed, Toast.LENGTH_SHORT).show()
                                return@setPositiveButton
                            }
                            saveUpdate { putInt(key, parsed) }
                        } else {
                            saveUpdate { putString(key, text) }
                        }
                    }
                    .show()
            }
            else -> showUnsupported(activity, themedContext, title, description)
        }
    }

    private fun Bundle.displayTitle(fallbackKey: String? = null): String =
        getString("title")?.takeIf(String::isNotBlank) ?: fallbackKey ?: getString("key").orEmpty()

    private fun dialog(context: Context, title: String, message: String? = null) =
        MaterialAlertDialogBuilder(context).setTitle(title).apply { message?.let(::setMessage) }

    private fun selectedValues(current: Bundle, entry: Bundle, key: String): Set<String> =
        (current.getStringArray(key)
            ?: current.getString(key)?.let(::arrayOf)
            ?: entry.getStringArray("defaultStrings")
            ?: entry.getString("defaultString")?.let(::arrayOf))?.toSet().orEmpty()

    private fun showUnsupported(activity: Activity, themedContext: Context, title: String, description: String?) {
        val message = listOfNotNull(
            description?.takeIf(String::isNotEmpty),
            activity.getString(R.string.managed_restrictions_unsupported),
        ).joinToString("\n\n")
        dialog(themedContext, title, message).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun save(activity: Activity, workService: IHavenService, packageName: String, restrictions: Bundle) {
        Thread {
            val saved = runCatching { workService.setManagedApplicationRestrictions(packageName, restrictions) }.isSuccess
            runIfAlive(activity) {
                Toast.makeText(
                    activity,
                    if (saved) R.string.managed_restrictions_saved else R.string.managed_restrictions_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.start()
    }

    private inline fun runIfAlive(activity: Activity, crossinline block: () -> Unit) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) block()
        }
    }
}
