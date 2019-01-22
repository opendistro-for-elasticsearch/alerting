package io.pry.gradle.offline_dependencies

import io.pry.gradle.offline_dependencies.maven.PomDependencyModelResolver
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.Model
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.building.*
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.interpolation.StringSearchModelInterpolator
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.path.DefaultPathTranslator
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.path.DefaultUrlNormalizer
import io.pry.gradle.offline_dependencies.repackaged.org.apache.maven.model.validation.DefaultModelValidator
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.UnresolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.ivy.IvyDescriptorArtifact
import org.gradle.ivy.IvyModule
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.util.GFileUtils

import static io.pry.gradle.offline_dependencies.Utils.addToMultimap

class UpdateOfflineRepositoryTask extends DefaultTask {

  def EMPTY_DEPENDENCIES_ARRAY = new Dependency[0]

  @Input
  GString root
  @Input
  Set<String> configurationNames
  @Input
  Set<String> buildscriptConfigurationNames
  @Input
  boolean includeSources
  @Input
  boolean includeJavadocs
  @Input
  boolean includePoms
  @Input
  boolean includeIvyXmls
  @Input
  boolean includeBuildscriptDependencies

  @TaskAction
  void run() {
    withRepositoryFiles { repositoryFiles ->
      // copy collected files to destination directory
      repositoryFiles.each { id, files ->
        def directory = moduleDirectory(id)
        GFileUtils.mkdirs(directory)
        files.each { File file -> GFileUtils.copyFile(file, new File(directory, file.name)) }
      }
    }
  }

  // configurations
  private Set<Configuration> getConfigurations() {
    Set<Configuration> configurations = []

    if (this.getConfigurationNames()) {
      def configurationNames = this.getConfigurationNames()

      logger.trace("Trying to resolve the following project configurations: '${configurationNames.join(",")}'")

      configurationNames.each { name ->
        try {
          configurations.add(project.configurations.getByName(name))
        } catch (UnknownConfigurationException e) {
          logger.warn("Unable to resolve project configuration with name '${name}'")
        }
      }
    } else {
      logger.trace("No project configurations specified, defaulting to all configurations")
      configurations.addAll(project.configurations)
    }

    if (this.getIncludeBuildscriptDependencies()) {
      if (this.getBuildscriptConfigurationNames()) {
        def configurationNames = this.getBuildscriptConfigurationNames()

        logger.trace("Trying to resolve the following buildscript configurations: '${configurationNames.join(",")}'")

        configurationNames.each { name ->
          try {
            configurations.add(project.buildscript.configurations.getByName(name))
          } catch (UnknownConfigurationException e) {
            logger.warn("Unable to resolve buildscript configuration with name '${name}'")
          }
        }
      } else {
        logger.trace("No buildscript configurations specified, defaulting to all configurations")
        configurations.addAll(project.buildscript.configurations)
      }
    } else {
      logger.trace("Skipping buildscript configurations")
    }

    if (!configurations) {
      logger.warn('No configurations found. There are no dependencies to resolve.')
    }

    return configurations
  }

  // collect everything
  private Map<ModuleComponentIdentifier, Set<File>> collectRepositoryFiles(Set<Configuration> configurations) {
    Set<ModuleComponentIdentifier> componentIds = []
    Map<ModuleComponentIdentifier, Set<File>> repositoryFiles = [:]

    for (configuration in configurations) {
      for (dependency in configuration.allDependencies) {
        if (dependency instanceof ExternalModuleDependency) {

          // create a detached configuration for each dependency to get all declared versions of a dependency.
          // resolution would fetch only the newest otherwise.
          //
          // see:
          // * http://stackoverflow.com/questions/29374885/multiple-version-of-dependencies-in-gradle
          // * https://discuss.gradle.org/t/how-to-get-multiple-versions-of-the-same-library/7400
          def cfg = project.configurations.detachedConfiguration([dependency].toArray(EMPTY_DEPENDENCIES_ARRAY))

          cfg.resolvedConfiguration.resolvedArtifacts.forEach({ artifact ->
            def componentId =
                new DefaultModuleComponentIdentifier(
                        DefaultModuleIdentifier.newId(artifact.moduleVersion.id.group, artifact.moduleVersion.id.name),
                    artifact.moduleVersion.id.version
                )

            componentIds.add(componentId)
            logger.trace("Adding artifact for component'{}' (location '{}')", componentId, artifact.file)
            addToMultimap(repositoryFiles, componentId, artifact.file)
          });
        }
      }
    }
    // collect sources and javadocs
    if (this.getIncludeSources() || this.getIncludeJavadocs()) {
      def jvmArtifacts = project.dependencies.createArtifactResolutionQuery()
          .forComponents(componentIds)
          .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
          .execute()

      for (component in jvmArtifacts.resolvedComponents) {
        if (this.getIncludeSources()) {
          def sources = component.getArtifacts(SourcesArtifact)
          if (!sources?.empty) {
            sources*.file.each { File source ->
              logger.trace("Adding sources for component'{}' (location '{}')", component.id, source)
              addToMultimap(repositoryFiles, component.id, source)
            }
          }
        }

        if (this.getIncludeJavadocs()) {
          def javadocs = component.getArtifacts(JavadocArtifact)
          if (!javadocs?.empty) {
            javadocs*.file.each { File javadoc ->
              logger.trace("Adding javadocs for component'{}' (location '{}')", component.id, javadoc)
              addToMultimap(repositoryFiles, component.id, javadoc)
            }
          }
        }
      }
    }

    // collect maven poms (for immediate component ids and parents)
    if (this.getIncludePoms()) {
      collectPoms(componentIds, repositoryFiles)
    }

    // collect ivy xml files
    if (this.getIncludeIvyXmls()) {
      collectIvyXmls(componentIds, repositoryFiles)
    }

    return repositoryFiles
  }

  // adds pom artifacts and their parents for the givens component ids
  private void collectPoms(Set<ComponentIdentifier> componentIds, Map<ComponentIdentifier, Set<File>> repositoryFiles) {
    logger.trace("Collecting pom files")

    def mavenArtifacts = project.dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(MavenModule, MavenPomArtifact)
        .execute()

    def pomModelResolver = new PomDependencyModelResolver(project)

    for (component in mavenArtifacts.resolvedComponents) {
      def poms = component.getArtifacts(MavenPomArtifact)
      if (poms?.empty) {
        continue
      }

      def pomArtifact = poms.first()

      if (pomArtifact instanceof UnresolvedArtifactResult) {
        logger.error("Resolver was unable to resolve artifact '{}'", pomArtifact.id, pomArtifact.getFailure())
        continue
      }
      
      def pomFile = pomArtifact.file as File
      resolvePom(pomModelResolver, pomFile)

      logger.trace("Adding pom for component'{}' (location '{}')", component.id, pomFile)
      addToMultimap(repositoryFiles, component.id, pomFile)
    }

    pomModelResolver.componentCache().each { componentId, pomFile ->
      addToMultimap(repositoryFiles, componentId, pomFile)
    }
  }

  private Model resolvePom(PomDependencyModelResolver pomModelResolver, File pomFile) {
    def modelBuildingRequest = new DefaultModelBuildingRequest();
    modelBuildingRequest.setSystemProperties(System.getProperties())
    modelBuildingRequest.setModelSource(new FileModelSource(pomFile))
    modelBuildingRequest.setModelResolver(pomModelResolver)

    try {
      def modelBuilder = new DefaultModelBuilderFactory().newInstance()

      def modelInterpolator = new StringSearchModelInterpolator()
      modelInterpolator.setUrlNormalizer(new DefaultUrlNormalizer())
      modelInterpolator.setPathTranslator(new DefaultPathTranslator())

      modelBuilder.setModelInterpolator(modelInterpolator)
      modelBuilder.setModelValidator(new DefaultModelValidator())

      def result = modelBuilder.build(modelBuildingRequest)

      if (!result.problems.empty) {
        result.problems.each { this.logModelProblems(it) }
      }

      return result.effectiveModel
    } catch (ModelBuildingException e) {
      logger.error("${e.getMessage()}: ${e.problems}")
    }
  }

  protected void logModelProblems(ModelProblem problem) {
    def message = "$problem.modelId: $problem.message"

    switch (problem.severity) {
      case ModelProblem.Severity.WARNING:
        logger.info(message, problem.exception);
        break;

      case ModelProblem.Severity.ERROR:
      case ModelProblem.Severity.FATAL:
        logger.error(message, problem.exception);
        break;
    }
  }

  private void collectIvyXmls(Set<ComponentIdentifier> componentIds, Map<ComponentIdentifier, Set<File>> repositoryFiles) {
    logger.trace("Collecting ivy xml files")

    def ivyArtifacts = project.dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(IvyModule, IvyDescriptorArtifact)
        .execute()

    for (component in ivyArtifacts.resolvedComponents) {
      def ivyXmls = component.getArtifacts(IvyDescriptorArtifact)

      if (ivyXmls?.empty) {
        continue
      }

      def ivyXmlArtifact = ivyXmls.first()

      if (ivyXmlArtifact instanceof UnresolvedArtifactResult) {
        logger.error("Resolver was unable to resolve artifact '{}'", ivyXmlArtifact.id, ivyXmlArtifact.getFailure())
        continue
      }

      def ivyXml = ivyXmlArtifact.file as File
      logger.trace("Adding ivy artifact for component'{}' (location '{}')", component.id, ivyXml)
      addToMultimap(repositoryFiles, component.id, ivyXml)
    }
  }

  // Activate online repositories and collect dependencies.
  // Switch back to local repository afterwards.
  private def withRepositoryFiles(Closure<Map<ModuleComponentIdentifier, Set<File>>> callback) {
    def originalRepositories = project.repositories.collect()

    project.repositories.clear()

    OfflineDependenciesExtension extension =
        project.extensions.getByName(OfflineDependenciesPlugin.EXTENSION_NAME) as OfflineDependenciesExtension

    project.repositories.addAll(extension.repositoryHandler)

    def files = collectRepositoryFiles(getConfigurations())

    project.repositories.clear()
    project.repositories.addAll(originalRepositories)

    callback(files)
  }

  // Return the offline-repository target directory for the given component (naming follows maven conventions)
  protected File moduleDirectory(ModuleComponentIdentifier ci) {
    new File("${getRoot()}".toString(), "${ci.group.tokenize(".").join("/")}/${ci.module}/${ci.version}")
  }
}

