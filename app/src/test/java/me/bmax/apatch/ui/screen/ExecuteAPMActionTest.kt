package me.bmax.apatch.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteAPMActionTest {
    @Test fun aFailedActionKeepsItsLogWhateverTheReaderAskedFor() {
        assertFalse(shouldLeaveActionPage(success = false, stayOnPage = false))
        assertFalse(shouldLeaveActionPage(success = false, stayOnPage = true))
    }

    @Test fun aSuccessfulActionLeavesOnlyWhenStayingIsTurnedOff() {
        assertTrue(shouldLeaveActionPage(success = true, stayOnPage = false))
        assertFalse(shouldLeaveActionPage(success = true, stayOnPage = true))
    }
}
