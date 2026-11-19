package de.trainingsapp;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Einhaltung der DDD-Schichtenarchitektur und verbotener Abhängigkeiten.
 */
class ArchitekturTest {

    private static final DescribedPredicate<JavaClass> VERBOTENE_INFRASTRUKTUR_TYPEN =
            resideInAnyPackage(
                    "jakarta.persistence..",
                    "io.vertx..",
                    "io.quarkus.security.webauthn.."
            ).or(name("io.quarkus.security.identity.SecurityIdentity"));

    private static JavaClasses produktionsKlassen;

    @BeforeAll
    static void ladeKlassen() {
        produktionsKlassen = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("de.trainingsapp");
    }

    @Test
    void domainDarfVonKeinerAnderenSchichtAbhaengen() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure..",
                        "..api.."
                )
                .check(produktionsKlassen);
    }

    @Test
    void domainDarfKeineFrameworkTypenNutzen() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.quarkus..",
                        "io.vertx..",
                        "jakarta.persistence..",
                        "jakarta.transaction..",
                        "jakarta.ws.rs..",
                        "jakarta.enterprise..",
                        "jakarta.inject..",
                        "org.eclipse.microprofile..",
                        "org.jboss.."
                )
                .check(produktionsKlassen);
    }

    @Test
    void applicationDarfNichtVonInfrastructureOderApiAbhaengen() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..api.."
                )
                .check(produktionsKlassen);
    }

    @Test
    void applicationDarfKeineVerbotenenInfrastrukturTypenNutzen() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat(VERBOTENE_INFRASTRUKTUR_TYPEN)
                .check(produktionsKlassen);
    }

    @Test
    void apiDarfNichtVonInfrastructureAbhaengen() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(produktionsKlassen);
    }

    @Test
    void apiV1DarfKeineVerbotenenInfrastrukturTypenNutzen() {
        noClasses()
                .that().resideInAPackage("..api.v1..")
                .should().dependOnClassesThat(VERBOTENE_INFRASTRUKTUR_TYPEN)
                .check(produktionsKlassen);
    }

    @Test
    void alleFachlichenPaketeSindInDerArchUnitPruefungErfasst() {
        for (String paket : List.of(
                "de.trainingsapp.domain.benutzer",
                "de.trainingsapp.domain.passkey",
                "de.trainingsapp.domain.timer",
                "de.trainingsapp.domain.workout",
                "de.trainingsapp.domain.zugriff",
                "de.trainingsapp.application.aufraeumen",
                "de.trainingsapp.application.benutzer",
                "de.trainingsapp.application.dsgvo",
                "de.trainingsapp.application.erweiterung",
                "de.trainingsapp.application.passkey",
                "de.trainingsapp.application.profil",
                "de.trainingsapp.application.timer",
                "de.trainingsapp.application.workout",
                "de.trainingsapp.infrastructure.aufraeumen",
                "de.trainingsapp.infrastructure.benutzer",
                "de.trainingsapp.infrastructure.passkey",
                "de.trainingsapp.infrastructure.timer",
                "de.trainingsapp.infrastructure.workout",
                "de.trainingsapp.infrastructure.zugriff",
                "de.trainingsapp.api.v1.auth",
                "de.trainingsapp.api.v1.benutzer",
                "de.trainingsapp.api.v1.dsgvo",
                "de.trainingsapp.api.v1.passkey",
                "de.trainingsapp.api.v1.profil",
                "de.trainingsapp.api.v1.timer",
                "de.trainingsapp.api.v1.workout"
        )) {
            boolean vorhanden = produktionsKlassen.stream()
                    .anyMatch(klasse -> klasse.getPackageName().equals(paket));
            assertTrue(vorhanden, "Erwartetes Produktionspaket fehlt in der ArchUnit-Prüfung: " + paket);
        }
    }
}
