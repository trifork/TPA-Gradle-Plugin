package com.trifork.tpa

import com.trifork.tpa.dsl.TpaBuildType
import com.trifork.tpa.dsl.TpaExtension
import com.trifork.tpa.dsl.TpaProductFlavor
import com.trifork.tpa.task.TpaDeployTask
import com.trifork.tpa.task.TpaCurrentTask
import org.gradle.api.*

/**
 * The TpaPlugin generates TpaCurrentTask's and TpaDeployTask's based on the Android
 * plugin configuration and the TPA configuration DSL.
 * 
 * To avoid class-loading issues, it's using a minimum of dependencies and only 
 * relies on the Apache httpmime (and the httpclient-android which is already in 
 * the build-chain classpath)
 */
class TpaPlugin implements Plugin<Project> {
    
    static String GRADLE_PLUGIN_GROUP = 'The Perfect App'

    static def capitalize(String string){
        return string.toLowerCase().tokenize().collect { it.capitalize() }.join(' ')
    }
    
    def void apply(Project project) {

        if(!project.plugins.hasPlugin('android')){
            throw new GradleException("Failed to locate android plugin. The tpaDeploy \n\
task works in unison with Android, so please apply the 'com.android.application' plugin.")
        }
        
        def tpaProductFlavors = project.container(TpaProductFlavor)
        def tpaBuildTypes = project.container(TpaBuildType)
        
        project.configure(project){
            extensions.create("tpa", TpaExtension, tpaProductFlavors, tpaBuildTypes)
        }

        installTasks(project, tpaProductFlavors)
    }

    private void installTasks(Project project, NamedDomainObjectContainer<TpaProductFlavor> tpaProductFlavors) {

        def tpaCurrentTaskAll = project.task('tpaCurrent',
                description: 'Fetches info about current deploy of all variants',
                group: GRADLE_PLUGIN_GROUP) << { /* No-Op */ }

        tpaProductFlavors.all { tpaProductFlavor ->
            project.android.productFlavors.all { productFlavor ->
                if (productFlavor.name.equals(tpaProductFlavor.name)) {
                    project.android.buildTypes.all { buildType ->
                        def tpaCurrentTask = installTpaCurrentTask(project, productFlavor.name, buildType.name)
                        def tpaDeployTask = installTpaDeployTask(project, productFlavor.name, buildType.name)

                        // Make sure tpaDeployTask's are only executed if it would result in a new versionNo
                        tpaDeployTask.onlyIf {
                            def skip = project.hasProperty('previousTpaCurrentItem') && project.previousTpaCurrentItem.version_number.toInteger() == project.android.defaultConfig.versionCode.toInteger()

                            if (skip) {
                                println "VersionNo ${project.android.defaultConfig.versionCode} of ${productFlavor.name}${capitalize(buildType.name)} variant already uploaded"
                            } else {
                                println "Uploading VersionNo ${project.android.defaultConfig.versionCode} of ${productFlavor.name}${capitalize(buildType.name)} variant"
                            }
                            !skip
                        }

                        // The TpaCurrent command must contains *all* possible TpaCurrentTask variants
                        tpaCurrentTaskAll.dependsOn << tpaCurrentTask
                    }
                }
            }
        }
    }
    
    // Creates a TpaCurrent variant task
    private Task installTpaCurrentTask(Project project, String productFlavorName, String buildTypeName){
        project.task("tpaCurrent${capitalize(productFlavorName)}${capitalize(buildTypeName)}",
                description: "Fetches info about latest deploy of ${productFlavorName}${capitalize(buildTypeName)} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaCurrentTask) {
            productFlavor = productFlavorName
            buildType = buildTypeName
        }
    }

    // Creates a tpaDeploy variant task
    private Task installTpaDeployTask(Project project, String productFlavorName, String buildTypeName){   
        project.task("tpaDeploy${capitalize(productFlavorName)}${capitalize(buildTypeName)}",
                description: "Deploys ${productFlavorName}${capitalize(buildTypeName)} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaDeployTask,
                dependsOn: [\
                        "assemble${capitalize(productFlavorName)}${capitalize(buildTypeName)}", \
                        "tpaCurrent${capitalize(productFlavorName)}${capitalize(buildTypeName)}"]) {
            productFlavor = productFlavorName
            buildType = buildTypeName
        }
    }
}