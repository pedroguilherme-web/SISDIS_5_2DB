package aisafe.flights;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "aisafe.flights", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {
    @ArchTest
    static final ArchRule domainDependsOnNoOtherLayerNorFramework = noClasses()
            .that().resideInAPackage("aisafe.flights.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "aisafe.flights.application..",
                    "aisafe.flights.infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..");

    @ArchTest
    static final ArchRule applicationKnowsNothingAboutHttpOrInfrastructure = noClasses()
            .that().resideInAPackage("aisafe.flights.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "aisafe.flights.infrastructure..",
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "jakarta.servlet..",
                    "jakarta.persistence..");

    @ArchTest
    static final ArchRule onlyThePersistenceAdapterUsesJpa = noClasses()
            .that().resideOutsideOfPackage("aisafe.flights.infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule controllersDoNotTouchPersistence = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAPackage("aisafe.flights.infrastructure.persistence..");
}
