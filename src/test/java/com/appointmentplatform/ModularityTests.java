package com.appointmentplatform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModularityTests {
    @Test
    void modulesHaveNoCyclesOrInternalAccess() {
        ApplicationModules.of(AppointmentPlatformApplication.class).verify();
    }

    @Test
    void webDoesNotAccessPersistenceAndDomainDoesNotDependOnWeb() {
        var classes = new ClassFileImporter().importPackages("com.appointmentplatform");
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage("com.appointmentplatform..persistence..", "com.appointmentplatform..domain..")
                .check(classes);
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.appointmentplatform..web..", "com.appointmentplatform..application..", "com.appointmentplatform..persistence..")
                .check(classes);
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .check(classes);
    }
}
