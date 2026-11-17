package com.obddroid.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.WindowManager;

import org.junit.Test;

public class EdgeToEdgeInsetsTest {

    @Test
    public void resizeAndUnspecifiedPadForKeyboard() {
        assertTrue(EdgeToEdgeInsets.padsForKeyboard(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE));
        assertTrue(EdgeToEdgeInsets.padsForKeyboard(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED));
    }

    @Test
    public void panAndNothingKeepTheirBehaviour() {
        assertFalse(EdgeToEdgeInsets.padsForKeyboard(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN));
        assertFalse(EdgeToEdgeInsets.padsForKeyboard(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING));
    }

    @Test
    public void stateBitsDoNotAffectTheAdjustMode() {
        int panWithVisibleState = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        int resizeWithHiddenState = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        assertFalse(EdgeToEdgeInsets.padsForKeyboard(panWithVisibleState));
        assertTrue(EdgeToEdgeInsets.padsForKeyboard(resizeWithHiddenState));
    }
}
