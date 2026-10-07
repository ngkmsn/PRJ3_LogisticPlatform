package com.logistics.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonUtilsTest {

    @Test
    void isBlank_nullInput_returnsTrue() {
        assertTrue(CommonUtils.isBlank(null));
    }

    @Test
    void isBlank_emptyString_returnsTrue() {
        assertTrue(CommonUtils.isBlank(""));
    }

    @Test
    void isBlank_whitespaceOnly_returnsTrue() {
        assertTrue(CommonUtils.isBlank("   "));
    }

    @Test
    void isBlank_nonEmptyString_returnsFalse() {
        assertFalse(CommonUtils.isBlank("hello"));
    }

    @Test
    void isNotBlank_nonEmptyString_returnsTrue() {
        assertTrue(CommonUtils.isNotBlank("world"));
    }

    @Test
    void isNotBlank_nullInput_returnsFalse() {
        assertFalse(CommonUtils.isNotBlank(null));
    }
}
