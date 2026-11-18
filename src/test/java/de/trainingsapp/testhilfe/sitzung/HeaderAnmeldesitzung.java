package de.trainingsapp.testhilfe.sitzung;

import de.trainingsapp.application.sitzung.Anmeldesitzung;
import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Optional;

/**
 * NUR TESTQUELLEN (E23, Abnahme 6): zweite Umsetzung des Ports {@link Anmeldesitzung}. Statt eines
 * Cookies traegt der Client ein serverseitig gespeichertes Token im Header {@value HeaderSitzungen#HEADER},
 * wie es eine native App taete. Aktiv nur im Testprofil {@code HeaderSitzungProfil}.
 */
@Alternative
@ApplicationScoped
public class HeaderAnmeldesitzung implements Anmeldesitzung {

    private final HeaderSitzungen sitzungen;
    private final CurrentVertxRequest request;

    HeaderAnmeldesitzung(HeaderSitzungen sitzungen, CurrentVertxRequest request) {
        this.sitzungen = sitzungen;
        this.request = request;
    }

    @Override
    public Optional<BenutzerId> aktuellerBenutzer() {
        return sitzungen.pruefe(token());
    }

    @Override
    public void anmelden(BenutzerId benutzer) {
        request.getCurrent().response().putHeader(HeaderSitzungen.HEADER, sitzungen.erzeuge(benutzer));
    }

    @Override
    public void beenden() {
        sitzungen.widerrufe(token());
    }

    private String token() {
        return request.getCurrent().request().getHeader(HeaderSitzungen.HEADER);
    }
}
