package de.trainingsapp.domain.systeminfo;

/**
 * Port der Domain: liefert die Systeminformationen. Die Umsetzung liegt in der
 * Infrastructure-Schicht.
 */
public interface SystemInfoProvider {

    SystemInfo current();
}
