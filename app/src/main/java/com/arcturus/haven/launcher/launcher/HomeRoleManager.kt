package com.arcturus.haven.launcher.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class HomeRoleManager(private val context: Context) {
    fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            return roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
    }

    fun requestIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
            return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS).takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
    }
}
