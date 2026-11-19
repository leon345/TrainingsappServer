package de.trainingsapp.application.system;

import de.trainingsapp.domain.system.SystemInfo;
import de.trainingsapp.domain.system.SystemInfoProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Anwendungsservice für den Abruf der Systeminformationen.
 */
@ApplicationScoped
public class SystemInfoService {

    private final SystemInfoProvider systemInfoProvider;

    public SystemInfoService(SystemInfoProvider systemInfoProvider) {
        this.systemInfoProvider = systemInfoProvider;
    }

    public SystemInfo lesen() {
        return systemInfoProvider.lesen();
    }
}
