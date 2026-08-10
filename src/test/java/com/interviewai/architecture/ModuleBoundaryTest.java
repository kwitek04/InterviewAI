package com.interviewai.architecture;

import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.SessionReportAccess;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal and modular-monolith boundaries the project depends on:
 * infrastructure stays inside adapters, the domain stays framework-free, and every
 * module only reaches the neighbouring packages it is allowed to know about.
 */
class ModuleBoundaryTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.interviewai");

    @Test
    @DisplayName("infrastructure libraries are referenced only from adapters")
    void infrastructureLibrariesStayInAdapters() {
        noClasses()
                .that().resideOutsideOfPackage("..adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "software.amazon..",
                        "dev.langchain4j..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.apache.tika..",
                        "org.springframework.web..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("the domain depends on neither the framework nor outer layers")
    void domainStaysPure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "..application..",
                        "..adapter..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("the application layer never reaches into adapters")
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("modules depend only on their declared neighbours")
    void modulesRespectDeclaredTopology() {
        mustNotDependOn("com.interviewai.shared..",
                "com.interviewai.cv..",
                "com.interviewai.interview..",
                "com.interviewai.session..",
                "com.interviewai.report..");

        mustNotDependOn("com.interviewai.cv..",
                "com.interviewai.interview..",
                "com.interviewai.session..",
                "com.interviewai.report..");

        mustNotDependOn("com.interviewai.interview..",
                "com.interviewai.cv..",
                "com.interviewai.report..",
                "com.interviewai.session.application..",
                "com.interviewai.session.adapter..");

        mustNotDependOn("com.interviewai.session..",
                "com.interviewai.report..",
                "com.interviewai.cv.adapter..",
                "com.interviewai.cv.domain..",
                "com.interviewai.interview.adapter..");

        mustNotDependOn("com.interviewai.report..",
                "com.interviewai.cv..",
                "com.interviewai.interview..",
                "com.interviewai.session.adapter..",
                "com.interviewai.session.domain..");
    }

    @Test
    @DisplayName("the report module uses only the published session API")
    void reportUsesPublishedSessionApiOnly() {
        noClasses()
                .that().resideInAPackage("com.interviewai.report..")
                .should().dependOnClassesThat(new DescribedPredicate<>(
                        "belong to the session module but are not part of its published API") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        String name = javaClass.getName();
                        return name.startsWith("com.interviewai.session.")
                                && !name.startsWith(SessionApplicationService.class.getName())
                                && !name.startsWith(CompletedInterviewSnapshot.class.getName())
                                && !name.startsWith(SessionReportAccess.class.getName());
                    }
                })
                .check(PRODUCTION_CLASSES);
    }

    private static void mustNotDependOn(String modulePackage, String... forbiddenPackages) {
        noClasses()
                .that().resideInAPackage(modulePackage)
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .check(PRODUCTION_CLASSES);
    }
}
