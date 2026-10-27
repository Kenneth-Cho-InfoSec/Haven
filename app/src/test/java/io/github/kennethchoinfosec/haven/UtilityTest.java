package io.github.kennethchoinfosec.haven;

import io.github.kennethchoinfosec.haven.util.Utility;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UtilityTest {
    @Test
    public void fileExtension_isReadFromLastDot() {
        assertEquals("gz", Utility.getFileExtension("archive.tar.gz"));
        assertEquals("apk", Utility.getFileExtension("/tmp/Haven-release.apk"));
    }

    @Test
    public void fileExtension_isNullWhenNoExtensionExists() {
        assertNull(Utility.getFileExtension("README"));
        assertNull(Utility.getFileExtension(".profile"));
    }

    @Test
    public void fileExtension_allowsEmptyExtension() {
        assertEquals("", Utility.getFileExtension("document."));
    }
}
