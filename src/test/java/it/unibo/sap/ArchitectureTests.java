package it.unibo.sap;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.common.hexagonal.InputPort;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.common.hexagonal.OutputPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureTests {

    private static JavaClasses classes;

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";

    @BeforeAll
    public static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("it.unibo.sap");
    }

    @Test
    public void cleanArchitecture() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAPackage(APPLICATION)
                .orShould().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
                .check(classes);

        layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .check(classes);
    }

    @Test
    public void hexagonalArchitecture() {
        cleanArchitecture();

        portsInApplicationOrDomain(InputPort.class);
        portsInApplicationOrDomain(OutputPort.class);

        adaptersInInfrastructure(InputAdapter.class);
        adaptersInInfrastructure(OutputAdapter.class);
    }

    private void portsInApplicationOrDomain(final Class<?> marker) {
        classes().that()
                .areAssignableTo(marker)
                .and().areInterfaces()
                .and().resideOutsideOfPackage("..common..")
                .should().resideInAPackage(APPLICATION)
                .orShould().resideInAPackage(DOMAIN)
                .check(classes);
    }

    private void adaptersInInfrastructure(final Class<?> marker) {
        classes().that()
                .areAssignableTo(marker)
                .and().areNotInterfaces()
                .and().resideOutsideOfPackage("..common..")
                .should().resideInAPackage(INFRASTRUCTURE)
                .check(classes);
    }
}