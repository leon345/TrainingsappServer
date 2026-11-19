package de.trainingsapp.domain.system;

/**
 * Port zum Auslesen der Systeminformationen.
 */
public interface SystemInfoProvider {

    SystemInfo lesen();
}
