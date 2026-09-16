package me.bmax.apatch.ui.superuser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

class SuperUserStateMapperTest {

    private val collator = Collator.getInstance(Locale.ROOT)
    private val managerPackage = "me.bmax.apatch"

    private fun createItem(
        packageName: String,
        uid: Int,
        label: String,
        pinyin: String = label.lowercase(),
        isSystemApp: Boolean = false,
        firstInstallTime: Long = 1000L,
        isAllowed: Boolean = false,
        isExcluded: Boolean = false,
        // Mirrors what the view model records when it loads the list.
        sortRank: Int = when {
            isAllowed -> 0
            isExcluded -> 1
            else -> 2
        },
    ): SuperUserItem {
        return SuperUserItem(
            packageName = packageName,
            uid = uid,
            label = label,
            pinyin = pinyin,
            isSystemApp = isSystemApp,
            firstInstallTime = firstInstallTime,
            isAllowed = isAllowed,
            isExcluded = isExcluded,
            sortRank = sortRank,
            profileUid = uid,
            profileToUid = 0,
            profileScontext = "",
        )
    }

    @Test
    fun filterAndSort_filtersOutManagerPackage() {
        val items = listOf(
            createItem("com.example.app", 10001, "Example App"),
            createItem(managerPackage, 10000, "APatch"),
        )
        val result = SuperUserStateMapper.filterAndSort(
            items = items,
            searchQuery = "",
            showSystemApps = true,
            sortBy = SuperUserSort.NAME,
            managerPackageName = managerPackage,
            collator = collator,
        )
        assertEquals(1, result.size)
        assertEquals("com.example.app", result[0].packageName)
    }

    @Test
    fun filterAndSort_hidesSystemAppsUnlessEnabled() {
        val userApp = createItem("com.user.app", 10001, "User App", isSystemApp = false)
        val systemApp = createItem("com.android.settings", 1000, "Settings", isSystemApp = true)
        val items = listOf(userApp, systemApp)

        val hiddenResult = SuperUserStateMapper.filterAndSort(
            items = items,
            searchQuery = "",
            showSystemApps = false,
            sortBy = SuperUserSort.NAME,
            managerPackageName = managerPackage,
            collator = collator,
        )
        assertEquals(1, hiddenResult.size)
        assertEquals("com.user.app", hiddenResult[0].packageName)

        val shownResult = SuperUserStateMapper.filterAndSort(
            items = items,
            searchQuery = "",
            showSystemApps = true,
            sortBy = SuperUserSort.NAME,
            managerPackageName = managerPackage,
            collator = collator,
        )
        assertEquals(2, shownResult.size)
    }

    @Test
    fun filterAndSort_alwaysKeepsShellUid2000() {
        val shell = createItem("com.android.shell", 2000, "Shell", isSystemApp = true)
        val regularSystem = createItem("com.android.systemui", 1000, "System UI", isSystemApp = true)
        val items = listOf(shell, regularSystem)

        val result = SuperUserStateMapper.filterAndSort(
            items = items,
            searchQuery = "",
            showSystemApps = false,
            sortBy = SuperUserSort.NAME,
            managerPackageName = managerPackage,
            collator = collator,
        )
        assertEquals(1, result.size)
        assertEquals(2000, result[0].uid)
    }

    @Test
    fun filterAndSort_matchesSearchQueryByLabelPackageOrPinyin() {
        val appA = createItem("com.termux", 10050, "Termux", pinyin = "termux")
        val appB = createItem("org.fdroid", 10051, "F-Droid", pinyin = "fdroid")
        val appC = createItem("com.tencent.mm", 10052, "微信", pinyin = "weixin")
        val items = listOf(appA, appB, appC)

        val matchLabel = SuperUserStateMapper.filterAndSort(items, "Term", true, SuperUserSort.NAME, managerPackage, collator)
        assertEquals(listOf("com.termux"), matchLabel.map { it.packageName })

        val matchPkg = SuperUserStateMapper.filterAndSort(items, "fdroid", true, SuperUserSort.NAME, managerPackage, collator)
        assertEquals(listOf("org.fdroid"), matchPkg.map { it.packageName })

        val matchPinyin = SuperUserStateMapper.filterAndSort(items, "wei", true, SuperUserSort.NAME, managerPackage, collator)
        assertEquals(listOf("com.tencent.mm"), matchPinyin.map { it.packageName })
    }

    @Test
    fun filterAndSort_prioritizesAllowedThenExcludedThenRegular() {
        val normal = createItem("com.a.normal", 1001, "Alpha Normal", isAllowed = false, isExcluded = false)
        val excluded = createItem("com.b.excluded", 1002, "Beta Excluded", isAllowed = false, isExcluded = true)
        val allowed = createItem("com.c.allowed", 1003, "Gamma Allowed", isAllowed = true, isExcluded = false)
        val items = listOf(normal, excluded, allowed)

        val result = SuperUserStateMapper.filterAndSort(
            items = items,
            searchQuery = "",
            showSystemApps = true,
            sortBy = SuperUserSort.NAME,
            managerPackageName = managerPackage,
            collator = collator,
        )
        assertEquals("com.c.allowed", result[0].packageName)
        assertEquals("com.b.excluded", result[1].packageName)
        assertEquals("com.a.normal", result[2].packageName)
    }

    @Test
    fun filterAndSort_respectsSortModes() {
        val item1 = createItem("org.z", 1001, "B_Label", firstInstallTime = 3000L)
        val item2 = createItem("org.a", 1002, "A_Label", firstInstallTime = 1000L)
        val item3 = createItem("org.m", 1003, "C_Label", firstInstallTime = 2000L)
        val items = listOf(item1, item2, item3)

        val byName = SuperUserStateMapper.filterAndSort(items, "", true, SuperUserSort.NAME, managerPackage, collator)
        assertEquals(listOf("A_Label", "B_Label", "C_Label"), byName.map { it.label })

        val byPackage = SuperUserStateMapper.filterAndSort(items, "", true, SuperUserSort.PACKAGE_NAME, managerPackage, collator)
        assertEquals(listOf("org.a", "org.m", "org.z"), byPackage.map { it.packageName })

        val byTime = SuperUserStateMapper.filterAndSort(items, "", true, SuperUserSort.INSTALL_TIME, managerPackage, collator)
        assertEquals(listOf("org.z", "org.m", "org.a"), byTime.map { it.packageName })
    }

    @Test
    fun filterAndSort_keepsARowInPlaceWhenItsSwitchMoves() {
        // The list puts allowed rows first. Sorting on that live state moved a row to another
        // section the moment its switch moved, and a lazy list follows the row it is anchored to,
        // so turning the top row off scrolled the reader into the section below — a screen of
        // switches that are all off, which reads as "the tap turned everything off". The rank is
        // settled when the list loads, so the order has to survive the switch.
        val first = createItem("com.first", 1001, "A First", isAllowed = true)
        val second = createItem("com.second", 1002, "B Second", isAllowed = true)

        val before = SuperUserStateMapper.filterAndSort(
            listOf(first, second), "", true, SuperUserSort.NAME, managerPackage, collator,
        )
        assertEquals(listOf("com.first", "com.second"), before.map { it.packageName })

        // The reader turns the top row off: its state changes, its rank does not.
        val toggled = listOf(first.copy(isAllowed = false), second)
        val after = SuperUserStateMapper.filterAndSort(
            toggled, "", true, SuperUserSort.NAME, managerPackage, collator,
        )
        assertEquals(listOf("com.first", "com.second"), after.map { it.packageName })
        assertEquals(false, after.first().isAllowed)
    }

    @Test
    fun countAllowedUids_deduplicatesSharedUids() {
        val app1 = createItem("pkg.one", 10001, "App One", isAllowed = true)
        val app2 = createItem("pkg.two", 10001, "App Two", isAllowed = true) // shared UID
        val app3 = createItem("pkg.three", 10002, "App Three", isAllowed = true)
        val app4 = createItem("pkg.four", 10003, "App Four", isAllowed = false)

        val count = SuperUserStateMapper.countAllowedUids(listOf(app1, app2, app3, app4))
        assertEquals(2, count)
    }
}
