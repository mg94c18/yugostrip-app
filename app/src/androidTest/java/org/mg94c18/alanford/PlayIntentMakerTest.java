package org.mg94c18.alanford;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayIntentMakerTest {
    @Test
    public void intentHasCorrectFields() {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = PlayIntentMaker.createPlayIntent(context);

        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertTrue(intent.getData().toString().contains(context.getPackageName()));
        assertEquals("com.android.vending", intent.getPackage());
    }
}
