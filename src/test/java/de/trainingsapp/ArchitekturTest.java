package de.trainingsapp;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
}
