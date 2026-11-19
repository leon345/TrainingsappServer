package de.trainingsapp.infrastructure.system;

import de.trainingsapp.domain.system.SystemInfo;
import de.trainingsapp.domain.system.SystemInfoProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Liest Name und Version der Anwendung aus der Quarkus-Konfiguration.
 */
@ApplicationScoped
public class ConfigSystemInfoProvider implements SystemInfoProvider {

    private final String applicationName;
    private final String applicationVersion;

    public ConfigSystemInfoProvider(
            @ConfigProperty(name = "quarkus.application.name") String applicationName,
            @ConfigProperty(name = "quarkus.application.version") String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @Override
    public SystemInfo lesen() {
        return new SystemInfo(applicationName, applicationVersion);
    }
}
