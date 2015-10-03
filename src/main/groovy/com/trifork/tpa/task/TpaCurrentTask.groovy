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
        applicationId = "$applicationId$applicationIdSuffix"
        String uri = "https://${project.tpa.server}/rest/versions/${uploadUUID}/Android/${applicationId}/?unpublished=true&published=true&max_results=1"

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
                    println "Track '${applicationId}' has no previous deployments on server $project.tpa.server"
                }else{
                    prettyPrintTpaCurrentItem(currentList.get(0), applicationId)
                }
                break;
            case HttpsURLConnection.HTTP_NOT_FOUND:
                throw new GradleException("No track for '${applicationId}' found on server! Did you forget to add this?")
            default:
                throw new GradleException("${connection.getResponseCode()}")
        }
        
        connection.disconnect()
    }
    
    def prettyPrintTpaCurrentItem(def tpaCurrentItem, def applicationId){
        println "Current deploy information for variant '${variantName}':\n" +
            "* Track name/applicationId: ${applicationId}\n" +
            "* Size: ${humanReadableByteCount(tpaCurrentItem.app_size)}\n" +
            "* Published: $tpaCurrentItem.published\n" +
            "* Uploaded on: ${fromISO8601(tpaCurrentItem.uploaded)}\n" +
            "* Version code: $tpaCurrentItem.version_number\n" +
            "* Version name: $tpaCurrentItem.version_string\n"
        if(!tpaCurrentItem.release_notes.empty){
            println "* Release notes: $tpaCurrentItem.release_notes"
        }

        // Store the TpaCurrentItem in the project so the later TpaDeployTask can
        // determine if can be skipped or not (avoid deploying versionNo
        // which already exists)
        project.ext {
            previousTpaCurrentItem = tpaCurrentItem
        }
    }
}