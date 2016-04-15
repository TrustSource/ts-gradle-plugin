/*
 *
 * Copyright (c) 2016. Enterprise Architecture Group, EACG
 *
 * SPDX-License-Identifier:	MIT
 *
 */

package de.eacg.ecs.gradle.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertTrue

class EcsPluginTest {
    @Test
    public void ecsPluginAddsScanTaskToProject() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'de.eacg.ecsPlugin'

        assertTrue(project.tasks.'dependency-scan' instanceof ScanTask)
    }

    @Test
    public void ecsPluginAddsScanTaskToProjectBackwardcompatibility() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'de.eacg.ecs.plugin.gradle'

        assertTrue(project.tasks.'dependency-scan' instanceof ScanTask)
    }

}