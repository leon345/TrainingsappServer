package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainException;
import java.util.List;
import java.util.Objects;

/**
 * Einzelne Übung innerhalb eines Workouts inkl. optionaler Calisthenics-Progressionsvariante (z. B. "Tuck").
 */
public record Uebung(
        UebungId id,
        int position,
        String name,
        String variante,
        List<Satz> saetze
) {

    public static final String FEHLERCODE = "UNGUELTIGE_UEBUNG";
    public static final int MAX_NAME_LAENGE = 100;
    public static final int MAX_VARIANTE_LAENGE = 100;

    public Uebung {
        Objects.requireNonNull(id, "UebungId darf nicht null sein");
        if (position < 0) {
            throw new DomainException(FEHLERCODE, "Die Übungs-Position darf nicht negativ sein.");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException(FEHLERCODE, "Der Übungsname darf nicht leer sein.");
        }
        String bereinigterName = name.trim();
        if (bereinigterName.length() > MAX_NAME_LAENGE) {
            throw new DomainException(
                    FEHLERCODE,
                    "Der Übungsname darf höchstens " + MAX_NAME_LAENGE + " Zeichen lang sein."
            );
        }
        name = bereinigterName;

        if (variante == null || variante.isBlank()) {
            variante = null;
        } else {
            String bereinigteVariante = variante.trim();
            if (bereinigteVariante.length() > MAX_VARIANTE_LAENGE) {
                throw new DomainException(
                        FEHLERCODE,
                        "Die Übungsvariante darf höchstens " + MAX_VARIANTE_LAENGE + " Zeichen lang sein."
                );
            }
            variante = bereinigteVariante;
        }

        saetze = saetze == null ? List.of() : List.copyOf(saetze);
    }
}
