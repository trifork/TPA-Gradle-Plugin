package com.trifork.tpa.task

import com.trifork.tpa.TpaPlugin
import javax.net.ssl.HttpsURLConnection
import java.nio.file.Files
import java.nio.file.Paths
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
        


        String attachmentName = "apk"
        String attachmentFileName = apkFile.getName()
        String crlf = "\r\n"
        String twoHyphens = "--"
        String boundary =  "**TheIceIsMeltingOnThePoles*"
        
        HttpsURLConnection httpUrlConnection = null
        URL url = new URL(uploadUrl)
        httpUrlConnection = (HttpsURLConnection) url.openConnection()
        httpUrlConnection.setUseCaches(false)
        httpUrlConnection.setDoOutput(true)

        httpUrlConnection.setRequestMethod("POST")
        httpUrlConnection.setRequestProperty("Connection", "Keep-Alive")
        httpUrlConnection.setRequestProperty("Cache-Control", "no-cache")
        httpUrlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary)

        DataOutputStream request = new DataOutputStream(httpUrlConnection.getOutputStream())
        
        // From a real request:
        //------WebKitFormBoundaryxWlz4vEKV0iEKxiE
        // Content-Disposition: form-data; name="apk"; filename="fga-app-falck-preview.apk"
        //Content-Type: application/octet-stream
        //
        // <DATA>
        //------WebKitFormBoundaryxWlz4vEKV0iEKxiE
        // Content-Disposition: form-data; name="publish"
        //false
        // ------WebKitFormBoundaryxWlz4vEKV0iEKxiE--

        //println "Content-Disposition: form-data; name=\"${attachmentName}\";filename=\"${attachmentFileName}\""

        request.writeBytes(twoHyphens + boundary + crlf)
        request.writeBytes("Content-Disposition: form-data; name=\"${attachmentName}\";filename=\"${attachmentFileName}\"${crlf}")
        request.writeBytes("Content-Type: application/octet-stream" + crlf)
        request.writeBytes(crlf)
        request.write(Files.readAllBytes(Paths.get(apkFile.getAbsolutePath())));
        request.writeBytes(crlf);
        request.writeBytes("$twoHyphens$boundary$twoHyphens$crlf");

        request.flush()
        request.close()

        InputStream responseStream = new BufferedInputStream(httpUrlConnection.getInputStream())

        BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream))
        String line = ""
        StringBuilder stringBuilder = new StringBuilder()
        while ((line = responseStreamReader.readLine()) != null)
        {
            stringBuilder.append(line).append("\n")
        }
        responseStreamReader.close()

        String response = stringBuilder.toString()

        responseStream.close()
        
        httpUrlConnection.disconnect()
        
        /*
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
        }*/
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