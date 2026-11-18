package de.trainingsapp.architektur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
}
