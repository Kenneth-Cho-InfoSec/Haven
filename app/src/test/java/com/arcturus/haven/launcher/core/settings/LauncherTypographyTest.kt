package com.arcturus.haven.launcher.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherTypographyTest {
    @Test
    fun poppinsIsTheDefaultAndEverySelectableFamilyHasALabel() {
        assertEquals("poppins", LauncherTypography.DEFAULT_FAMILY)
        assertEquals("Poppins", LauncherTypography.labelFor(LauncherTypography.DEFAULT_FAMILY))
        assertEquals(
            listOf("Poppins", "System default", "Sans serif", "Serif", "Monospace"),
            LauncherTypography.selectableFamilies.map(LauncherTypography::labelFor),
        )
    }

    @Test
    fun missingOrUnknownFontUsesPoppins() {
        assertEquals(LauncherTypography.DEFAULT_FAMILY, LauncherTypography.normalizeFamily(null))
        assertEquals(LauncherTypography.DEFAULT_FAMILY, LauncherTypography.normalizeFamily("future-font"))
    }

    @Test
    fun supportedAndLegacyFamiliesArePreserved() {
        listOf("poppins", "system", "sans", "serif", "mono").forEach { family ->
            assertEquals(family, LauncherTypography.normalizeFamily(family))
        }
    }
}
