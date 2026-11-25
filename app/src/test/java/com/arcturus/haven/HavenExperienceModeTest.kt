package com.arcturus.haven

import com.arcturus.haven.core.HavenExperienceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HavenExperienceModeTest {
    @Test
    fun modeFlagsMatchUserIntent() {
        assertTrue(HavenExperienceMode.BOTH.launcherEnabled)
        assertTrue(HavenExperienceMode.BOTH.isolationEnabled)

        assertTrue(HavenExperienceMode.LAUNCHER_ONLY.launcherEnabled)
        assertFalse(HavenExperienceMode.LAUNCHER_ONLY.isolationEnabled)

        assertFalse(HavenExperienceMode.ISOLATION_ONLY.launcherEnabled)
        assertTrue(HavenExperienceMode.ISOLATION_ONLY.isolationEnabled)
    }

    @Test
    fun storageRoundTripAndFallbackDefaultToBoth() {
        assertEquals(HavenExperienceMode.BOTH, HavenExperienceMode.fromStorage(null))
        assertEquals(HavenExperienceMode.BOTH, HavenExperienceMode.fromStorage("unknown"))
        HavenExperienceMode.entries.forEach { mode ->
            assertEquals(mode, HavenExperienceMode.fromStorage(mode.storageValue))
        }
    }
}
