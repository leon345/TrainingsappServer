package de.trainingsapp.testhilfe.sitzung;

import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import java.util.Set;

/** Testprofil: Datenbank wie {@link MitDatenbank}, Sitzung per Header statt Cookie. */
public class HeaderSitzungProfil extends MitDatenbank {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(HeaderAnmeldesitzung.class, HeaderAuthMechanismus.class);
    }
}
