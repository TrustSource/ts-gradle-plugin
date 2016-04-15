/*
 *
 * Copyright (c) 2016. Enterprise Architecture Group, EACG
 *
 * SPDX-License-Identifier:	MIT
 *
 */

package de.eacg.ecs.gradle.plugin;

import java.io.IOException;
import java.util.Properties;

public class ProjectProperties extends Properties {

    public ProjectProperties() throws IOException {
        load(ProjectProperties.class.getResourceAsStream("/project.properties"));
    }

    public String getName() {
        return this.getProperty("artifactId");
    }

    public String getVersion() {
        return this.getProperty("version");
    }
}
