package de.trainingsapp.architektur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import de.trainingsapp.application.sitzung.Anmeldesitzung;
import de.trainingsapp.infrastructure.sitzung.CookieAnmeldesitzung;

/** Schichtregeln mit ArchUnit, gegen die Hauptquellen (ohne Tests). */
@AnalyzeClasses(packages = "de.trainingsapp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitekturRegelnTest {

    private static final String[] OHNE_TECHNIK = {
            "de.trainingsapp.domain..", "de.trainingsapp.application..", "de.trainingsapp.api.v1.."};

    /** E26: Transaktionen nur ueber den Port {@code application.Transaktion}. */
    @ArchTest
    static final ArchRule keineJtaTypenAusserhalbVonInfrastructure = noClasses()
            .that().resideInAnyPackage(OHNE_TECHNIK)
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.transaction..")
            .because("Transaktionen laufen ueber den Port application.Transaktion (E26)");

    /**
     * E23, Abnahme 5: Vert.x-, Extension- und SecurityIdentity-Typen nur in infrastructure.
     * {@code @Authenticated} und {@code @RolesAllowed} bleiben in api.v1 erlaubt.
     */
    @ArchTest
    static final ArchRule keineSitzungsTechnikAusserhalbVonInfrastructure = noClasses()
            .that().resideInAnyPackage(OHNE_TECHNIK)
            .should().dependOnClassesThat().resideInAnyPackage("io.vertx..", "io.quarkus.security.webauthn..")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("io.quarkus.security.identity.SecurityIdentity")
            .because("application kennt nur BenutzerId; die Sitzung liegt hinter dem Port Anmeldesitzung (E23)");

    /** E23, Abnahme 6/8: In den Hauptquellen gibt es genau eine Sitzung, die Cookie-Sitzung. */
    @ArchTest
    static final ArchRule nurDieCookieSitzungInDenHauptquellen = classes()
            .that().implement(Anmeldesitzung.class)
            .should().haveFullyQualifiedName(CookieAnmeldesitzung.class.getName())
            .because("die Header-Sitzung existiert nur in den Testquellen (E23)");
}
