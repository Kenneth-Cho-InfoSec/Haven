package io.github.kennethchoinfosec.haven.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class MainActivityIntentTest {
    @Test
    public void restartIntentIsExplicitAndContainsNoForwardedInput() {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent restartIntent = MainActivity.createRestartIntent(context);

        assertEquals(new ComponentName(context, MainActivity.class), restartIntent.getComponent());
        assertNull(restartIntent.getAction());
        assertNull(restartIntent.getData());
        assertFalse(restartIntent.hasExtra("untrusted"));
    }
}
