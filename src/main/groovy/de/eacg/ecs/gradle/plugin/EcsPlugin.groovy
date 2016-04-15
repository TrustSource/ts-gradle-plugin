/*
 *
 * Copyright (c) 2016. Enterprise Architecture Group, EACG
 *
 * SPDX-License-Identifier:	MIT
 *
 */

package de.eacg.ecs.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class EcsPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('ecsPlugin', EcsPluginExtension)
        project.ecsPlugin.projectName = project.name
        project.ecsPlugin.moduleName = project.name
        project.ecsPlugin.moduleId = project.group + ':' + project.name
        project.task('dependency-scan', type: ScanTask)
        project.task('ecsScan', type: ScanTask)   // alias
    }
}



