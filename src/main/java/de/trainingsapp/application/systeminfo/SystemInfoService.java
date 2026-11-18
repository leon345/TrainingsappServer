package de.trainingsapp.application.systeminfo;

import de.trainingsapp.domain.systeminfo.SystemInfo;
import de.trainingsapp.domain.systeminfo.SystemInfoProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Anwendungsfall: Systeminformationen abfragen.
 */
@ApplicationScoped
public class SystemInfoService {

    private final SystemInfoProvider provider;

    public SystemInfoService(SystemInfoProvider provider) {
        this.provider = provider;
    }

    public SystemInfo getSystemInfo() {
        return provider.current();
    }
}
