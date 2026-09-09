package com.neuroplan.auth.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeploymentSafetyReadinessHealthIndicatorTest {

    @Test
    void reportsUpWhenTheDemoSwitchIsDisabled() {
        var indicator = new DeploymentSafetyReadinessHealthIndicator(false);

        assertEquals("UP", indicator.health().getStatus().getCode());
    }

    @Test
    void reportsDownWhenTheDemoSwitchIsEnabled() {
        var indicator = new DeploymentSafetyReadinessHealthIndicator(true);

        assertEquals("DOWN", indicator.health().getStatus().getCode());
    }
}
