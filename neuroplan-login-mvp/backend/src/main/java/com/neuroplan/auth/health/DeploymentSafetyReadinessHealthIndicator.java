package com.neuroplan.auth.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Controlled readiness failure switch used only for the deployment safety demo.
 * The process stays alive while Kubernetes removes a non-ready Pod from service
 * endpoints. The switch defaults to false and is supplied through ConfigMap.
 */
@Component("deploymentSafety")
public class DeploymentSafetyReadinessHealthIndicator implements HealthIndicator {

    private final boolean readinessFailureEnabled;

    public DeploymentSafetyReadinessHealthIndicator(
            @Value("${app.deployment-safety.readiness-fail:false}") boolean readinessFailureEnabled
    ) {
        this.readinessFailureEnabled = readinessFailureEnabled;
    }

    @Override
    public Health health() {
        if (readinessFailureEnabled) {
            return Health.down()
                    .withDetail("reason", "deployment-safety-demo")
                    .build();
        }

        return Health.up().build();
    }
}
