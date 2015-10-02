package com.trifork.tpa.task

import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
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
        
        // Construct and execute HTTP GET request
        HttpResponse response = new DefaultHttpClient().execute(new HttpGet(uri))        
        
        // Parse and act on HTTP response
        switch(response.getStatusLine().getStatusCode()){
            case 200:
                def currentList = new JsonSlurper().parse( response.getEntity().getContent() );
                if(currentList.empty){
                    println "Project $packageName has no previous deployments on server $project.tpa.server"
                }else{
                    prettyPrintTpaCurrentItem(currentList.get(0), packageName)
                }
                break;
            case 404:
                println "No project for $packageName found on server $project.tpa.server"
                break;
            default:
                throw new GradleException("${response.getStatusLine().getStatusCode()}")
        }
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