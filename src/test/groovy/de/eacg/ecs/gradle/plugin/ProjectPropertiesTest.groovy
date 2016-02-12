/*
 *
 * Copyright (c) 2016. Enterprise Architecture Group, EACG
 *
 * SPDX-License-Identifier:	MIT
 *
 */

package de.eacg.ecs.gradle.plugin

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals

public class ProjectPropertiesTest {

    private ProjectProperties properties;


    @Before
    public void setUp() throws Exception {
        properties = new ProjectProperties();
    }


    @Test
    public void testGetName() throws Exception {
        assertEquals("ecs-gradle-plugin", properties.getName());

    }

    @Test
    public void testGetVersion() throws Exception {
        assertEquals(String.class, properties.getVersion().getClass());
    }
}
