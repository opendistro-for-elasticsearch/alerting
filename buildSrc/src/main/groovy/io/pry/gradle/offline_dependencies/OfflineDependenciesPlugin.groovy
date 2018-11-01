package io.pry.gradle.offline_dependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.internal.reflect.Instantiator

class OfflineDependenciesPlugin implements Plugin<Project> {

  static final String EXTENSION_NAME = 'offlineDependencies'

  @Override
  void apply(Project project) {

    if (!project.hasProperty("offlineRepositoryRoot")) {
      project.ext.offlineRepositoryRoot = "${project.projectDir}/offline-repository"
    }

    RepositoryHandler repositoryHandler = new DefaultRepositoryHandler(
        project.services.get(BaseRepositoryFactory.class) as BaseRepositoryFactory,
        project.services.get(Instantiator.class) as Instantiator
    )

    def extension = project.extensions.create(EXTENSION_NAME, OfflineDependenciesExtension, repositoryHandler)

    project.logger.info("Offline dependencies root configured at '${project.offlineRepositoryRoot}'")

    project.task('updateOfflineRepository', type: UpdateOfflineRepositoryTask) {
      conventionMapping.root = { "${project.offlineRepositoryRoot}" }
      conventionMapping.configurationNames = { extension.configurations }
      conventionMapping.buildscriptConfigurationNames = { extension.buildScriptConfigurations }
      conventionMapping.includeSources = { extension.includeSources }
      conventionMapping.includeJavadocs = { extension.includeJavadocs }
      conventionMapping.includePoms = { extension.includePoms }
      conventionMapping.includeIvyXmls = { extension.includeIvyXmls }
      conventionMapping.includeBuildscriptDependencies = { extension.includeBuildscriptDependencies }
    }
  }
}
