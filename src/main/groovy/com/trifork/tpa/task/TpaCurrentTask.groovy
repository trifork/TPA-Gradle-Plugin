package com.trifork.tpa.task

import groovy.json.JsonSlurper
import javax.net.ssl.HttpsURLConnection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException

/*
 * TpaCurrentTask will consult an exposed TPA webservice, download and display info 
 * about the latest deployment. TpaCurrentTask is parameterized such that a varient 
 * for each productFlavor and buildType is created and exposed.
 * 
 * TODO: Apache HttpClient is deprecated in Android M, so rewrite to use vanilla
 * HttpURLConnection
 */
class TpaCurrentTask extends AbstractTpaTask {

    @TaskAction
    void executeRequest() {
        
        super.executeRequest()

        // Extract some variables from the TPA DSL and manfest
        String applicationId = getApplicationId(project)
        String applicationIdSuffix = project.android.buildTypes[buildType].applicationIdSuffix ?: ''
        String packageName = "$applicationId$applicationIdSuffix"
        String uri = "https://${project.tpa.server}/rest/versions/${uploadUUID}/Android/${packageName}/?unpublished=true&published=true&max_results=1"

        // Issue HTTP GET request
        URL url = new URL(uri);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.setDoOutput(false);

        // Parse and act on HTTP response
        switch(connection.getResponseCode()){
            case HttpsURLConnection.HTTP_OK:
                def currentList = new JsonSlurper().parse(connection.getInputStream())
                if(currentList.empty){
                    println "Project $packageName has no previous deployments on server $project.tpa.server"
                }else{
                    prettyPrintTpaCurrentItem(currentList.get(0), packageName)
                }
                break;
            case HttpsURLConnection.HTTP_NOT_FOUND:
                println "No project for $packageName found on server $project.tpa.server"
                break;
            default:
                throw new GradleException("${connection.getResponseCode()}")
        }
        
        connection.disconnect()
    }
    
    def prettyPrintTpaCurrentItem(def tpaCurrentItem, def packageName){
        
        println "Current deploy information for variant ${variantName}:"
        println "* Package name: ${packageName}\n" +
            "* Size: ${humanReadableByteCount(tpaCurrentItem.app_size)}\n" +
            "* Published: $tpaCurrentItem.published\n" +
            "* Uploaded on: ${fromISO8601(tpaCurrentItem.uploaded)}\n" +
            "* VersionNo: $tpaCurrentItem.version_number\n" +
            "* VersionString: $tpaCurrentItem.version_string\n" +
            "* Release notes: $tpaCurrentItem.release_notes"

        // Store the TpaCurrentItem in the project so the later TpaDeployTask can
        // determine if can be skipped or not (avoid deploying versionNo
        // which already exists)
        project.ext {
            previousTpaCurrentItem = tpaCurrentItem
        }
    }
}