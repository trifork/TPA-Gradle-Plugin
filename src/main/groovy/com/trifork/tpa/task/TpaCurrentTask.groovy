package com.trifork.tpa.task

import groovy.json.JsonSlurper
import javax.net.ssl.HttpsURLConnection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException

/*
 * TpaInfoTask will consult an exposed TPA webservice, download and display info 
 * about the latest deployment. TpaInfoTask is parameterized such that a varient 
 * for each productFlavor and buildType is created and exposed.
 */
class TpaInfoTask extends AbstractTpaTask {

    @TaskAction
    void executeRequest() {
        
        super.executeRequest()

        // Extract some variables from the TPA DSL and manfest
        //String applicationId = getApplicationId(project)
        //String applicationIdSuffix = project.android.buildTypes[buildType].applicationIdSuffix ?: ''        
        //applicationId = "$applicationId$applicationIdSuffix"
        String infoRequestUri = "https://${project.tpa.server}/rest/versions/${uploadUUID}/${applicationId}${applicationIdSuffix}/?unpublished=true&published=true&max_results=1"

        // Issue HTTP GET request
        URL url = new URL(infoRequestUri);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.setDoOutput(false);

        // Parse and act on HTTP response
        switch(connection.getResponseCode()){
            case HttpsURLConnection.HTTP_OK:
                def currentList = new JsonSlurper().parse(connection.getInputStream())
                if(currentList.empty){
                    println "Track '${applicationId}${applicationIdSuffix}' has no previous deployments on server $project.tpa.server"
                }else{
                    prettyPrintTpaInfoItem(currentList.get(0), "${applicationId}${applicationIdSuffix}")
                }
                break;
            case HttpsURLConnection.HTTP_NOT_FOUND:
                throw new GradleException("No track for '${applicationId}${applicationIdSuffix}' found on server! Did you forget to add this?")
            default:
                throw new GradleException("${connection.getResponseCode()}")
        }
        
        connection.disconnect()
    }
    
    def prettyPrintTpaInfoItem(def tpaInfoItem, def applicationIdWithSuffix){
        
        //println(tpaInfoItem);
        
        println "Current deploy information for variant '${variantName}':\n" +
            "* Track name (applicationId + suffix): ${applicationIdWithSuffix}\n" +
            "* Size: ${humanReadableByteCount(tpaInfoItem.app_size)}\n" +
            "* Published: $tpaInfoItem.published\n" +
            "* Uploaded on: ${fromISO8601(tpaInfoItem.uploaded)}\n" +
            "* Version code: $tpaInfoItem.version_number\n" +
            "* Version name: $tpaInfoItem.version_string\n"
        if(!tpaInfoItem.release_notes.empty){
            println "* Release notes: $tpaInfoItem.release_notes"
        }

        // Store the TpaInfoItem in the project so the later TpaDeployTask can
        // determine if can be skipped or not (avoid deploying versionNo
        // which already exists)
        project.ext {
            previousTpaInfoItem = tpaInfoItem
        }
    }
}