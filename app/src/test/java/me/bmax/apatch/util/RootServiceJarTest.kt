package me.bmax.apatch.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which states of the root service jar are treated as unusable.
 *
 * The one this exists for is the state a user reported: libsu's root process died before it started
 * because the platform refused to load the jar it runs from, and what the reader saw was a manager
 * that had lost root. The jar's mode is the only thing that can be judged from inside the app.
 */
class RootServiceJarTest {

    private val appUid = 10390

    @Test
    fun theModeAFreshWriteLeavesIsFine() {
        // What libsu produces on a working install: owner readable and writable, nothing else.
        assertFalse(rootJarLooksWrong(mode = 0x180, uid = appUid, appUid = appUid))
        // A umask that leaves it group and other readable is still a state nothing refuses.
        assertFalse(rootJarLooksWrong(mode = 0x1A4, uid = appUid, appUid = appUid))
    }

    @Test
    fun aJarOthersCanWriteIsNotUsable() {
        assertTrue(rootJarLooksWrong(mode = 0x1B6, uid = appUid, appUid = appUid)) // 0666
        assertTrue(rootJarLooksWrong(mode = 0x1B2, uid = appUid, appUid = appUid)) // 0662
        assertTrue(rootJarLooksWrong(mode = 0x1B0, uid = appUid, appUid = appUid)) // 0660
    }

    @Test
    fun aJarItsOwnerCannotReadIsNotUsable() {
        assertTrue(rootJarLooksWrong(mode = 0x080, uid = appUid, appUid = appUid)) // 0200
        assertTrue(rootJarLooksWrong(mode = 0x000, uid = appUid, appUid = appUid)) // 0000
    }

    @Test
    fun aJarThatIsNotOursIsNotUsable() {
        // A file another install or another uid left behind: not something this app should run.
        assertTrue(rootJarLooksWrong(mode = 0x180, uid = 0, appUid = appUid))
        assertTrue(rootJarLooksWrong(mode = 0x180, uid = 10123, appUid = appUid))
    }

    @Test
    fun onlyThePermissionBitsAreJudged() {
        // Os.stat hands back the file type as well; the caller masks it off, and this is the shape
        // it passes on, so a regular file with a good mode stays good.
        val regularFile = 0x81A4 // S_IFREG | 0644
        assertFalse(rootJarLooksWrong(mode = regularFile and 0x1FF, uid = appUid, appUid = appUid))
    }
}
