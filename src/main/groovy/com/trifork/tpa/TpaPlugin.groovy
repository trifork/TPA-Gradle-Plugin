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
 * 
 * TODO: 
 *      Legacy minify support
 *      GZip being used to optimize upload? Nope, server probably incapable!
 *      Add progress for upload
 *      Add commandline property override
 */
class TpaPlugin implements Plugin<Project> {
    
    static String GRADLE_PLUGIN_GROUP = 'The Perfect App'

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
        
        installTasks(project, tpaProductFlavors, tpaBuildTypes)
    }

    private void installTasks(Project project,
        NamedDomainObjectContainer<TpaProductFlavor> tpaProductFlavors,
        NamedDomainObjectContainer<TpaBuildType> tpaBuildTypes) {

        def tpaCurrentTaskAll = project.task('tpaCurrent',
                description: 'Fetches info about current deploy of all variants',
                group: GRADLE_PLUGIN_GROUP) << { /* No-Op */ }

        // Sanity checking android.buildTypes vs. tpa.buildTypes
        project.tpa.buildTypes.all{ buildType ->
            if(!project.android.buildTypes.hasProperty(buildType.name)){
                throw new GradleException("tpa.buildTypes.${buildType.name} is specified" +
                    ", but no matching android.buildType.${buildType.name} found!")
            }
        }

        if(project.android.productFlavors.empty){
            project.android.buildTypes.all { buildType ->
        
                def tpaCurrentTask = installTpaCurrentTask(project, buildType.name)
                def tpaDeployTask = installTpaDeployTask(project, buildType.name)

                tpaDeployTask.onlyIf {
                    deployingNewVersionNo(project, getVariantName(buildType.name))
                }

                // The TpaCurrent command must contains *all* possible TpaCurrentTask variants
                tpaCurrentTaskAll.dependsOn << tpaCurrentTask
            }
        }else{
            
            tpaProductFlavors.all { tpaProductFlavor ->
                project.android.productFlavors.all { productFlavor ->
                    if (productFlavor.name.equals(tpaProductFlavor.name)) {
                        project.android.buildTypes.all { buildType ->
                            def variantName = getVariantName(buildType.name, productFlavor.name)
                            def tpaCurrentTask = installTpaCurrentTask(project, buildType.name, productFlavor.name)
                            def tpaDeployTask = installTpaDeployTask(project, buildType.name, productFlavor.name)

                            // Make sure tpaDeployTask's are only executed if it would result in a new versionNo
                            tpaDeployTask.onlyIf {
                                deployingNewVersionNo(project, variantName)
                            }

                            // The TpaCurrent command must contains *all* possible TpaCurrentTask variants
                            tpaCurrentTaskAll.dependsOn << tpaCurrentTask
                        }
                    }
                }
            }
        }
    }
    
    private Task installTpaCurrentTask(Project project, String buildTypeName, 
            String productFlavorName = ''){

        def variantName = getVariantName(buildTypeName, productFlavorName)
        
        project.task("tpaCurrent${capitalize(variantName)}",
                description: "Fetches info about latest deploy of ${buildTypeName} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaCurrentTask) {
            buildType = buildTypeName
            productFlavor = productFlavorName
        }
    }

    private Task installTpaDeployTask(Project project, String buildTypeName, 
            String productFlavorName = ''){
        
        def variantName = getVariantName(buildTypeName, productFlavorName)
        def capitalizedVariantName = capitalize(variantName)
        
        project.task("tpaDeploy${capitalizedVariantName}",
                description: "Deploys ${variantName} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaDeployTask,
                dependsOn: [\
                        "assemble${capitalizedVariantName}", \
                        "tpaCurrent${capitalizedVariantName}"]) {
            buildType = buildTypeName
            productFlavor = productFlavorName
        }
    }

    private boolean deployingNewVersionNo(def project, String variantName){
        // Bug lurking: versionCodes could be defined locally per build/flavor?
        def versionCode = project.android.defaultConfig.versionCode.toInteger()
        def skip = project.hasProperty('previousTpaCurrentItem') && 
                project.previousTpaCurrentItem.version_number.toInteger() == versionCode

        if (skip) {
            println "VersionCode ${versionCode} of ${variantName} variant already uploaded"
        }
        !skip
    }
    
    public static String getVariantName(String buildTypeName, String productFlavorName = ''){
        if(productFlavorName.empty){
            return buildTypeName
        }else{
            return productFlavorName + capitalize(buildTypeName)
        }
    }

    public static String capitalize(String string){
        return string[0].toUpperCase() + string[1..-1]
    }
}