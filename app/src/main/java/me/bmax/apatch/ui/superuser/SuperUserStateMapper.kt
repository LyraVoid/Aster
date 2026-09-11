package me.bmax.apatch.ui.superuser

import android.content.pm.ApplicationInfo
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import java.text.Collator
import java.util.Locale

object SuperUserStateMapper {

    fun fromAppInfo(app: SuperUserViewModel.AppInfo): SuperUserItem {
        val appInfo = app.packageInfo.applicationInfo
        val flags = appInfo?.flags ?: 0
        val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val config = app.config

        return SuperUserItem(
            packageName = app.packageName,
            uid = app.uid,
            label = app.label,
            pinyin = app.pinyin,
            isSystemApp = isSystem,
            firstInstallTime = app.packageInfo.firstInstallTime,
            isAllowed = config.allow != 0,
            isExcluded = config.exclude == 1,
            profileUid = config.profile.uid,
            profileToUid = config.profile.toUid,
            profileScontext = config.profile.scontext,
            packageInfo = app.packageInfo,
            rawAppInfo = app,
        )
    }

    fun filterAndSort(
        items: List<SuperUserItem>,
        searchQuery: String,
        showSystemApps: Boolean,
        sortBy: SuperUserSort,
        managerPackageName: String,
        collator: Collator = Collator.getInstance(Locale.getDefault()),
    ): List<SuperUserItem> {
        val trimmedQuery = searchQuery.trim().lowercase()

        val priorityComparator = compareBy<SuperUserItem> {
            when {
                it.isAllowed -> 0
                it.isExcluded -> 1
                else -> 2
            }
        }

        val secondaryComparator = when (sortBy) {
            SuperUserSort.NAME -> compareBy(collator, SuperUserItem::label)
            SuperUserSort.PACKAGE_NAME -> compareBy(collator, SuperUserItem::packageName)
            SuperUserSort.INSTALL_TIME -> compareByDescending(SuperUserItem::firstInstallTime)
        }

        val fullComparator = priorityComparator.then(secondaryComparator)

        return items.asSequence()
            .filter { it.packageName != managerPackageName }
            .filter { item ->
                // Shell UID 2000 is always visible; otherwise check showSystemApps
                item.uid == 2000 || showSystemApps || !item.isSystemApp
            }
            .filter { item ->
                if (trimmedQuery.isEmpty()) {
                    true
                } else {
                    item.label.lowercase().contains(trimmedQuery) ||
                        item.packageName.lowercase().contains(trimmedQuery) ||
                        item.pinyin.lowercase().contains(trimmedQuery)
                }
            }
            .sortedWith(fullComparator)
            .toList()
    }

    fun countAllowedUids(items: List<SuperUserItem>): Int {
        return items.filter { it.isAllowed }.map { it.uid }.distinct().size
    }
}
