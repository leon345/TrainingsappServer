package de.trainingsapp.domain.benutzer;

import java.util.Objects;

/**
 * Value Object: fachliche Kennung eines Benutzers.
 *
 * <p>Bewusst als opaker String gehalten, weil das Benutzermodell erst mit S-04
 * (Passkey-Authentifizierung) festgelegt wird. Aufrufer kennen nur diesen Typ,
 * nicht die interne Darstellung; ein spaeterer Wechsel (z. B. auf UUID) bleibt
 * so auf diese Klasse beschraenkt.
 */
public record BenutzerId(String wert) {

    public BenutzerId {
        Objects.requireNonNull(wert, "wert");
        if (wert.isBlank()) {
            throw new IllegalArgumentException("BenutzerId darf nicht leer sein");
        }
    }

    @Override
    public String toString() {
        return wert;
    }
}
