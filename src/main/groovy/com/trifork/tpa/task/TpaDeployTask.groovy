package com.trifork.tpa.task

import com.trifork.tpa.TpaPlugin
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.entity.mime.HttpMultipartMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/* 
 * TpaDeployTask will deploy an APK through an exposed TPA webservice. The task is
 * parameterized such that a varient for each productFlavor and buildType are created. 
 * 
 * TpaDeployTask depends on the assemble task, so that deployment is always done
 * on an up-to-date binary artifact. 
 * 
 * TpaDeployTask also depends on the TpaCurrentTask, such that there will be no attempt 
 * at deploying an artifact if the previously deployed versionNo is indifferent to 
 * that of the current project - this is essential in a CI/CD setup with Jenkins etc.
 */
class TpaDeployTask extends AbstractTpaTask {

    @TaskAction
    void executeRequest() {
        
        super.executeRequest()
        
        // Extract some variables from the TPA DSL
        def proguardMappingFile = (project.android.buildTypes[buildType].minifyEnabled) ? 
                new File("${project.buildDir}/outputs/mapping/${buildType}/mapping.txt") : null                
        def apkFile = getApkFile(project, buildType, productFlavor)
        def uploadUrl = "https://${project.tpa.server}/${uploadUUID}/upload"
        
        // Determine publication setting for the artifact
        def publish = project.tpa.publish
        if(project.tpa.buildTypes.findByName(buildType)){
            if(project.tpa.buildTypes[buildType].hasProperty('publish')){
                publish = project.tpa.buildTypes[buildType].publish
            }
        }
        
        // Do a bit of sanity checking
        if( !apkFile.exists() ) {
            throw new GradleException("Failed to locate APK for upload $apkFile")
        }
        if( proguardMappingFile != null && !proguardMappingFile.exists() ) {
            throw new GradleException("Failed to locate ProguardMappingFile for upload proguardMappingFile")
        }
        
        // Print deploy parameters to standard out
        println "* Deploying ${variantName}"
        println "* APK: ${apkFile}"
        println "* Proguard: ${proguardMappingFile ?: ''}"
        println "* Publish: ${publish}"
        println "* Uploading to: ${uploadUrl}"
        
        // Construct and execute HTTP POST request
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create()
        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        entityBuilder.addBinaryBody("apk", apkFile)
        if(proguardMappingFile != null){
            entityBuilder.addBinaryBody("mapping", proguardMappingFile)
        }
        entityBuilder.addTextBody("publish", String.valueOf(publish)) 
        HttpEntity entity = entityBuilder.build()
        HttpPost post = new HttpPost(uploadUrl)        
        post.setEntity(entity)
        
        HttpResponse response = new DefaultHttpClient().execute(post)
        
        // Parse and act on HTTP response
        String entityString = toEntityString(response)
        if(response.getStatusLine().getStatusCode() == 200){
            println entityString
        }else{
            throw new GradleException(entityString)            
        }
    }
    
    def File getApkFile(def project, String buildTypeName, String productFlavorName = ''){
        if(productFlavorName.empty){
            return new File("${project.buildDir}/outputs/apk/${project.name}-${buildTypeName}.apk")
        }
        return new File("${project.buildDir}/outputs/apk/${project.name}-${productFlavorName}-${buildTypeName}.apk")
    }
    
    /*
    String getUploadUUID() {
        if (project.hasProperty('uploadUUID')) {
            return project.property('uploadUUID')
        } else {
            throw new RuntimeException('ERROR: uploadUUID not set! Set with i.e. -PuploadUUID=\"abc3750c-efec-430b-b523-0781a6b700a5\"')
        }
    }*/
}