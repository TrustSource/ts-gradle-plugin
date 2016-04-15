/*
 *
 * Copyright (c) 2016. Enterprise Architecture Group, EACG
 *
 * SPDX-License-Identifier:	MIT
 *
 */

package de.eacg.ecs.gradle.plugin

import de.eacg.ecs.client.Dependency
import de.eacg.ecs.client.JsonProperties
import de.eacg.ecs.client.RestClient
import de.eacg.ecs.client.Scan
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction

class ScanTask extends DefaultTask {

    Map<ModuleVersionIdentifier, DependencyMetaInf> metaInfCache = new HashMap<>()
    def verbose = false
    def prefixString

    @TaskAction
    def scan() {
        def scanExt = project.ecsPlugin
        def projProps = new ProjectProperties()

        def userAgent = "${projProps.getName()}/${projProps.getVersion()}"
        this.verbose = scanExt.verbose
        this.prefixString = 'ecsScan =>'

        JsonProperties apiClientConfig = readAndCheckCredentials(scanExt);

        if (scanExt.skip) {
            println "${prefixString} Skipping execution"
            return
        }

        /**
         *  TODO: allow more than one configurations????
         */

        Configuration configuration = getConfiguration(scanExt);

        ResolutionResult result = configuration.getIncoming().getResolutionResult()
        ResolvedComponentResult root = result.getRoot();

        Configuration pomsConfig = project.configurations.detachedConfiguration()

        setupPomConfig(root, pomsConfig)
        populateMetaInfCache(pomsConfig)

        def ecsRootDependency = mapDependencies(root)
        if (verbose) {
            printDependencies(ecsRootDependency, 0)
        }

        if (scanExt.skipTransfer) {
            println "${prefixString} Skipping transfer."
        } else {
            RestClient restApi = new RestClient(apiClientConfig, userAgent);

            Scan scan = new Scan(scanExt.projectName, scanExt.moduleName, scanExt.moduleId, ecsRootDependency);
            transferScan(restApi, scan)
        }
    }


    public setupPomConfig(ResolvedComponentResult rcr, Configuration pomsConfig) {
        def modVersion = rcr.getModuleVersion()
        if(modVersion != null && (modVersion.group != project.group || modVersion.name != project.name)) {
            pomsConfig.dependencies.add(
                project.dependencies.create(
                    group: modVersion.group,
                    name: modVersion.name,
                    version: modVersion.version,
                    ext: 'pom')
            )
        }
        Set<? extends DependencyResult> children = rcr.getDependencies();
        children.each {DependencyResult child ->
            if(child instanceof ResolvedDependencyResult) {
                ResolvedComponentResult childrc = ((ResolvedDependencyResult)child).getSelected()
                owner.setupPomConfig(childrc, pomsConfig)
            }
        }
    }

    public Dependency mapDependencies(ResolvedComponentResult rcr){
        ModuleVersionIdentifier mvi = rcr.getModuleVersion()
        Dependency.Builder builder = new Dependency.Builder();

        builder.setKey('mvn:' + mvi.group + ':' + mvi.name)
        builder.setName(mvi.name)
        builder.addVersion(mvi.version)

        DependencyMetaInf metaInf = metaInfCache.get(mvi);
        if(metaInf) {
            if(metaInf.description) {
                builder.setDescription(metaInf.description)
            }
            if(metaInf.url) {
                builder.setHomepageUrl(metaInf.url)
            }
            metaInf.licenses.each {
                if(it.name != null && it.url != null) {
                    builder.addLicense(it.name, it.url);
                } else (it.name != null) {
                    builder.addLicense(it.name)
                }
            }
        }

        Set<? extends DependencyResult> children = rcr.getDependencies();
        children.each {DependencyResult child ->
            if(child instanceof ResolvedDependencyResult) {
                builder.addDependency(owner.mapDependencies(child.getSelected()))
            }
        }

        return builder.buildDependency();
    }

    public printDependencies(Dependency d, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(" ");
        }
        println "${sb.toString()}${d.getName()} - ${d.getKey()}"
        println "${sb.toString()}     ${d.getDescription()}, ${d.getVersions()[0]}, ${d.getHomepageUrl()}"
        d.licenses.each {
            println "${sb.toString()}     ${it.getName()}, ${it.getUrl()}"
        }
        d.dependencies.each {
            owner.printDependencies(it, level +1)
        }
    }

    private Configuration getConfiguration(def scanExt) {
        def firstConfig = scanExt.configurations.first()
        println "${prefixString} Scanning with '${firstConfig}' configuration"
        try {
            return project.configurations.getByName(firstConfig)
        } catch (UnknownConfigurationException e) {
            println "${prefixString} Configuration '${firstConfig}' not found."
            throw new StopActionException();
        }
    }

    private void populateMetaInfCache(Configuration pomsConfig) {
        try {
            def resolvedPomsConfig = pomsConfig.resolvedConfiguration.lenientConfiguration
            resolvedPomsConfig.getArtifacts(new Spec<org.gradle.api.artifacts.Dependency>(){
                public boolean isSatisfiedBy(org.gradle.api.artifacts.Dependency element) {
                    return true;
                }
            }).each { ResolvedArtifact pom ->
                def project = new XmlSlurper().parse(pom.file)
                String description = project.description.text()
                String url = project.url.text()
                DependencyMetaInf dmi = new DependencyMetaInf(description, url);

                project.licenses.license.each {
                    dmi.addLicense(it.name.text(), it.url.text())
                }
                owner.metaInfCache.put(pom.moduleVersion.id, dmi);
            }
        } catch(Exception e) {
            println "Exception occured while trying to resolve metatdata, continuing without metatdata. Exception: ${e.toString()}"
        }
    }

    private JsonProperties readAndCheckCredentials(def scanExt) {
        JsonProperties properties

        try {
            properties = new JsonProperties((String)scanExt.credentials);
        } catch (Exception e) {
            println "Evaluation of user credentials failed: ${e.toString()}"
            throw new RuntimeException("Exception while evaluating user credentials", e);
        }
        properties.setUserName(scanExt.userName)
        properties.setApiKey(scanExt.apiKey)
        properties.setBaseUrl(scanExt.baseUrl)
        properties.setApiPath(scanExt.apiPath)

        def missingKeys = properties.validate()
        if(missingKeys.isEmpty() == false) {
            String err = "The mandatory parameter(s) '${missingKeys}' for plugin 'ecs-gradle-plugin' is/are missing or invalid"
            print err
            throw new RuntimeException("Exception: " + err);
        }
        return properties;
    }

    private void transferScan(RestClient client, Scan scan) {
        try {
            String body = client.transferScan(scan);
            println "${prefixString} Response: code: ${client.getResponseStatus()}, message: ${body}"

            if (client.getResponseStatus() != 201) {
                println "Failed : HTTP error code : ${client.getResponseStatus()}"
            }
        } catch (Exception e) {
            println e
        }
    }

    private Set<String> getConfigurations(def scanExt) {
        Set<String> configs = scanExt.configurations
    }


    private static class DependencyLicenseInf {

        String name;
        String url;

        DependencyLicenseInf(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private static class DependencyMetaInf {
        String description;
        String url;
        List<DependencyLicenseInf> licenses;

        DependencyMetaInf(description, url){
            this.description = description;
            this.url = url;
            this.licenses = new LinkedList<>();
        }

        public void addLicense(String name, String url) {
            licenses.add(new DependencyLicenseInf(name, url));
        }
    }
}