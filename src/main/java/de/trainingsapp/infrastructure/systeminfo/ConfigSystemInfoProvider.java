package de.trainingsapp.infrastructure.systeminfo;

import de.trainingsapp.domain.systeminfo.SystemInfo;
import de.trainingsapp.domain.systeminfo.SystemInfoProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Liest die Systeminformationen aus der Quarkus-Konfiguration.
 */
@ApplicationScoped
public class ConfigSystemInfoProvider implements SystemInfoProvider {

    private final String name;
    private final String version;

    public ConfigSystemInfoProvider(
            @ConfigProperty(name = "quarkus.application.name") String name,
            @ConfigProperty(name = "quarkus.application.version") String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public SystemInfo current() {
        return new SystemInfo(name, version);
    }
}
