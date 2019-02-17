package de.eacg.ecs.gradle.plugin

import de.eacg.ecs.client.CheckResults
import de.eacg.ecs.client.JsonProperties
import de.eacg.ecs.client.RestClient
import de.eacg.ecs.client.Scan
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction

class CheckTask extends ScanTask{

    @TaskAction
    def scan() {
        def scanExt = project.ecsPlugin
        def projProps = new ProjectProperties()

        def userAgent = "${projProps.getName()}/${projProps.getVersion()}"
        this.verbose = scanExt.verbose
        this.prefixString = 'ecsCheck =>'

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

        CheckResults results = null

        RestClient restClient = new RestClient(apiClientConfig, userAgent);
        Scan scan = new Scan(scanExt.projectName, scanExt.moduleName, scanExt.moduleId, ecsRootDependency);

        try {
            results = restClient.checkScan(scan);
        } catch (RestClient.RestClientException e) {
            println "WARNING: ${e.getMessage()}"
        }

        if (restClient.getResponseStatus() == 200 && results != null ) {
            evaluateResults(results);
        }
    }

    private void evaluateResults(CheckResults results) throws GradleException {
        def scanExt = project.ecsPlugin

        for (CheckResults.Warning w : results.getWarnings()) {
            String cStr = w.getComponent()
            String vStr = w.getVersion()

            String msg = String.format("Component \"%s %s\"", cStr != null ? cStr : "", vStr != null ? vStr : "")

            if (w.isComponentNotFound()) {
                println "WARNING: ${msg} not found"
            }

            if (w.isVersionNotFound()) {
                println "WARNING: ${msg} version not found"
            }

            if (w.isLicenseNotFound()) {
                println "WARNING: ${msg} license not found"
            }
        }

        if (!scanExt.allowBreakBuild) {
            return
        }

        if (scanExt.breakOnLegalIssues) {
            int violations = 0
            int warnings = 0

            for (CheckResults.Result result : results.getData()) {
                String msg = result.getComponent().getName() + " " + result.getComponent().getVersion()
                List<CheckResults.Violation> legalViolations
                if (scanExt.assumeComponentsModified) {
                    legalViolations = result.getChanged().getViolations()
                } else {
                    legalViolations = result.getNot_changed().getViolations()
                }

                for (CheckResults.Violation v : legalViolations) {
                    if (v.isViolation()) {
                        println "${msg} : ${v.getMessage()}"
                        violations++
                    } else if (v.isWarning()) {
                        println "${msg} : ${v.getMessage()}"
                        warnings++
                    }
                }
            }

            if (scanExt.breakOnViolationsAndWarnings && (warnings > 0 || violations > 0)) {
                throw new GradleException("Found legal violations")
            }

            if (scanExt.breakOnViolationsOnly && (violations > 0)) {
                throw new GradleException("Found legal violations")
            }

        }

        if (scanExt.breakOnVulnerabilities) {
            int violations = 0
            int warnings = 0

            for (CheckResults.Result result : results.getData()) {
                String componentStr = result.getComponent().getName() + " " + result.getComponent().getVersion()
                for (CheckResults.Vulnerabilities v : result.getVulnerabilities()) {
                    String msg = componentStr + ": [" + v.getName() + "] " + v.getDescription()
                    if (v.isViolation()) {
                        println msg
                        violations++
                    } else if (v.isWarning()) {
                        println msg
                        warnings++
                    }
                }
            }

            if (scanExt.breakOnViolationsAndWarnings && (warnings > 0 || violations > 0)) {
                throw new StopActionException("Found vulnerabilities")
            }

            if (scanExt.breakOnViolationsOnly && (violations > 0)) {
                throw new StopActionException("Found vulnerabilities")
            }

        }
    }
}
