package de.trainingsapp.infrastructure.persistenz;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Bricht den Start ab, wenn keine Datenbank konfiguriert ist.
 *
 * <p>Fehlt die JDBC-URL (im Prod-Profil: {@code QUARKUS_DATASOURCE_JDBC_URL}), schaltet
 * Quarkus die Datasource still ab, Flyway ueberspringt die Migration und die Anwendung
 * liefe ohne Datenbank. Der Zugriff auf die Datasource loest bei einer inaktiven
 * Datasource eine Ausnahme aus, die den Start mit Quarkus' eigener Begruendung beendet.
 * Nur Tests ohne Datenbankbezug schalten die Pruefung ab ({@code trainingsapp.datenbank.pflicht}).
 */
@ApplicationScoped
public class DatenbankPflichtPruefung {

    private static final Logger LOG = Logger.getLogger(DatenbankPflichtPruefung.class);

    void beimStart(@Observes StartupEvent ereignis,
            @ConfigProperty(name = "trainingsapp.datenbank.pflicht") boolean pflicht,
            Instance<AgroalDataSource> dataSource) {
        if (!pflicht) {
            LOG.debug("Datenbank-Pflichtpruefung abgeschaltet");
            return;
        }
        // Wirft bei inaktiver (nicht konfigurierter) Datasource eine Ausnahme.
        dataSource.get().getConfiguration();
    }
}
