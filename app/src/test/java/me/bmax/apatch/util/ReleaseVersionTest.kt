package me.bmax.apatch.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the update check makes of the string a release carries.
 *
 * It only ever becomes a number or nothing, and "nothing" is the case worth having tests for: it is
 * what a mistyped tag or an edited title turns into, and the caller turns it into a check that
 * failed. The bug this replaced returned "up to date" for the whole world instead.
 */
class ReleaseVersionTest {

    @Test
    fun aPlainNumberIsTheVersion() {
        assertEquals(11408, parseVersionNumber("11408"))
    }

    @Test
    fun aLeadingVIsNotPartOfIt() {
        assertEquals(11408, parseVersionNumber("v11408"))
    }

    @Test
    fun surroundingSpaceIsIgnored() {
        assertEquals(11408, parseVersionNumber("  11408  "))
    }

    @Test
    fun textThatIsNotAWholeNumberIsNoVersion() {
        // A tag from another project's scheme, a date, a suffix, or a word: none of them is a
        // version this app can compare, and guessing at one would be worse than admitting it.
        assertNull(parseVersionNumber("kp0.13.8"))
        assertNull(parseVersionNumber("2026-09-16"))
        assertNull(parseVersionNumber("11408-rc1"))
        assertNull(parseVersionNumber("aster"))
    }

    @Test
    fun nothingAtAllIsNoVersion() {
        assertNull(parseVersionNumber(null))
        assertNull(parseVersionNumber(""))
        assertNull(parseVersionNumber("   "))
        assertNull(parseVersionNumber("v"))
    }

    @Test
    fun aReleaseIsReadFromItsTag() {
        val json = JSONObject("""{"tag_name":"11408","name":"Aster 11408"}""")
        assertEquals(11408, releaseVersionCode(json))
    }

    @Test
    fun theTitleStandsInWhenTheTagIsNotANumber() {
        val json = JSONObject("""{"tag_name":"release-latest","name":"11408"}""")
        assertEquals(11408, releaseVersionCode(json))
    }

    @Test
    fun aReleaseWhoseVersionCannotBeReadIsNoRelease() {
        // An edited title, where the tag is not a number either. This is the shape of the bug the
        // tag was added for: the old code read the title alone, threw, had it swallowed, and told
        // every reader they were up to date. It comes back as nothing now, and nothing is a check
        // that failed — visible, and on the screen where the reader can see it.
        val json = JSONObject("""{"tag_name":"aster","name":"Aster 11408 is out"}""")
        assertEquals(-1, releaseVersionCode(json))
    }

    @Test
    fun aReleaseWithoutEitherFieldIsNoRelease() {
        assertEquals(-1, releaseVersionCode(JSONObject("{}")))
    }
}
