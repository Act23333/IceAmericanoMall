package org.icedamericanomall.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RotationScheduler unit test — validates cron syntax and the rotation-enabled guard.
 */
@DisplayName("RotationScheduler 密钥轮换检查")
class RotationSchedulerTest {

    @Test
    @DisplayName("rotation default cron is valid Spring cron syntax")
    void shouldHaveValidDefaultCron() {
        JwtProperties props = new JwtProperties(); // Rotation enabled=false by default
        assertFalse(props.getRotation().isEnabled(), "dev 默认不启用轮换");
        assertNotNull(props.getRotation().getCron());
        assertTrue(props.getRotation().getCron().matches("[0-9*/?\\s,]+"), "cron syntax");
    }

    @Test
    @DisplayName("rotation config reads nested keystore + rotation correctly")
    void shouldNestConfigStruct() {
        JwtProperties props = new JwtProperties();
        assertNotNull(props.getKeystore());
        assertNotNull(props.getRotation());
    }
}
