package com.trifork.tpa

import org.gradle.api.*
import com.trifork.tpa.dsl.*
import com.trifork.tpa.task.*

/**
 * The TpaPlugin generates TpaInfoTask's and TpaDeployTask's based on the Android
 * plugin configuration and the TPA configuration DSL.
 */
class TpaPlugin implements Plugin<Project> {
    
    static String GRADLE_PLUGIN_GROUP = 'The Perfect App'

    def void apply(Project project) {

        if(!project.plugins.hasPlugin('android')){
            throw new GradleException("Failed to locate android plugin. The TPA plugin works in unison with Android, so please apply the 'com.android.application' plugin.")
        }
        
        def tpaProductFlavors = project.container(TpaProductFlavor)
        def tpaBuildTypes = project.container(TpaBuildType)
        
        println "tpaProductFlavors: " + tpaProductFlavors.toString();
        println "tpaBuildTypes: " + tpaBuildTypes.toString();
        
        project.configure(project){
            extensions.create("tpa", TpaExtension, tpaProductFlavors, tpaBuildTypes)
        }
        
        installTasks(project, tpaProductFlavors, tpaBuildTypes)
    }

    private void installTasks(Project project,
        NamedDomainObjectContainer<TpaProductFlavor> tpaProductFlavors,
        NamedDomainObjectContainer<TpaBuildType> tpaBuildTypes) {

        // Sanity checking android.buildTypes vs. tpa.buildTypes
        project.tpa.buildTypes.all{ buildType ->
            if(!project.android.buildTypes.hasProperty(buildType.name)){
                throw new GradleException("tpa.buildTypes.${buildType.name} is specified" +
                    ", but no matching android.buildType.${buildType.name} found!")
            }
        }

        // If we have no product flavors defined (must be a simply non-flavor project)
        if(project.android.productFlavors.empty){
            println "ProductFlavors NOT detected!"
            project.android.buildTypes.all { buildType ->
        
                def tpaInfoTask = installTpaInfoTask(project, buildType.name)
                def tpaDeployTask = installTpaDeployTask(project, buildType.name)

                // Make sure tpaDeployTask's are only executed if it would result in a new versionCode
                tpaDeployTask.onlyIf {
                    deployingNewVersionNo(project, getVariantName(buildType.name))
                }
            }
        }else{
            println "ProductFlavors detected!"
            tpaProductFlavors.all { tpaProductFlavor ->
                project.android.productFlavors.all { productFlavor ->
                    if (productFlavor.name.equals(tpaProductFlavor.name)) {
                        project.android.buildTypes.all { buildType ->
                            def variantName = getVariantName(buildType.name, productFlavor.name)
                            def tpaInfoTask = installTpaInfoTask(project, buildType.name, productFlavor.name)
                            def tpaDeployTask = installTpaDeployTask(project, buildType.name, productFlavor.name)

                            println "Flavor: " + variantName
                            // Make sure tpaDeployTask's are only executed if it would result in a new versionCode
                            tpaDeployTask.onlyIf {
                                deployingNewVersionNo(project, variantName, productFlavor.name)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private Task installTpaInfoTask(Project project, String buildTypeName, 
            String productFlavorName = ''){

        def variantName = getVariantName(buildTypeName, productFlavorName)
        
        project.task("tpaInfo${capitalize(variantName)}",
                description: "Fetches info about latest deploy of ${buildTypeName} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaInfoTask) {
            buildType = buildTypeName
            productFlavor = productFlavorName
        }
    }

    private Task installTpaDeployTask(Project project, String buildTypeName, String productFlavorName = ''){
        
        def variantName = getVariantName(buildTypeName, productFlavorName)
        def capitalizedVariantName = capitalize(variantName)
        
        project.task("tpaDeploy${capitalizedVariantName}",
                description: "Deploys ${variantName} variant",
                group: GRADLE_PLUGIN_GROUP,
                type: TpaDeployTask,
                dependsOn: [\
                        "assemble${capitalizedVariantName}", \
                        "tpaInfo${capitalizedVariantName}"]) {
            buildType = buildTypeName
            productFlavor = productFlavorName
        }
    }

    private boolean deployingNewVersionNo(def project, String variantName, String flavorName = ''){

        def versionCode = getVersionCode(project, flavorName)
        def skip = project.hasProperty('previousTpaInfoItem') && 
                project.previousTpaInfoItem.version_number.toInteger() == versionCode

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

    public Integer getVersionCode(def project, def productFlavor = ''){
        if(project.android.productFlavors.empty){            
            return project.android.defaultConfig.versionCode.toInteger()
        }else{
            return project.android.productFlavors[productFlavor].versionCode.toInteger()
        }
    }    
}