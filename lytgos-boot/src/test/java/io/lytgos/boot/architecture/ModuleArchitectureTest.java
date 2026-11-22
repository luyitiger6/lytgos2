package io.lytgos.boot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

@AnalyzeClasses(packages = "io.lytgos", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  @ArchTest
  static final ArchRule CORE_IS_INDEPENDENT =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("io.lytgos.core..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.lytgos.provider..",
              "io.lytgos.memory..",
              "io.lytgos.tool..",
              "io.lytgos.storage..",
              "io.lytgos.web..",
              "io.lytgos.cli..",
              "io.lytgos.channel.cli..",
              "io.lytgos.boot..");

  @ArchTest
  static final ArchRule WEB_DOES_NOT_DEPEND_ON_ADAPTERS =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("io.lytgos.web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("io.lytgos.storage..", "io.lytgos.provider..");

  @ArchTest
  static final ArchRule MODULE_SLICES_ARE_CYCLE_FREE =
      com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
          .matching("io.lytgos.(*)..")
          .should()
          .beFreeOfCycles();

  @ArchTest
  static final ArchRule NO_STANDARD_STREAMS =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
