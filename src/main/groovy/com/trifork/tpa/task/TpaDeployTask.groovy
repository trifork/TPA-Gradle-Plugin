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
 * 
 */
class TpaDeployTask extends AbstractTpaTask {

    private static final String CRLF = "\r\n"
    private static final String DOUBLE_HYPHENS = "--"
    private static final String MAGIC_BOUNDARY_SEPARATOR =  "**iPhoneSucksAndroidRocks**"
    
    @TaskAction
    void executeRequest() {
        
        super.executeRequest()
        
        // Extract some variables from the TPA DSL
        def apkFile = getApkFile(project, buildType, productFlavor)
        def proguardFile = getProguardFile(project, buildType, productFlavor)
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
        if( proguardFile != null && !proguardFile.exists() ) {ß
            throw new GradleException("Failed to locate ProguardMappingFile for upload: ${proguardFile}")
        }
        
        // Print deploy parameters to standard out
        println "* Deploying ${variantName}"
        println "* APK: ${apkFile}"
        println "* Proguard: ${proguardFile ?: ''}"
        println "* Publish: ${publish}"
        println "* Uploading to: ${uploadUrl}"
        
        // Issue HTTP POST request
        URL url = new URL(uploadUrl)
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection()
        connection.setUseCaches(false)
        connection.setDoOutput(true)
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Connection", "Keep-Alive")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" 
            + MAGIC_BOUNDARY_SEPARATOR)
        DataOutputStream requestStream = new DataOutputStream(connection.getOutputStream())
        writeFormData(requestStream, "publish", publish)
        writeFormData(requestStream, "apk", apkFile)
        if(proguardFile != null){
            writeFormData(requestStream, "mapping", proguardFile)
        }
        writeFormDataEnd(requestStream)
        requestStream.flush()
        requestStream.close()

        // Parse and act on HTTP response
        switch(connection.getResponseCode()){
            case HttpsURLConnection.HTTP_OK:
                String responseBody = readBody(connection.getInputStream())
                println "Server response: $responseBody"
                break;
            default:
                String responseBody = readBody(connection.getInputStream())
                println "Server response: $responseBody"
                throw new GradleException("${connection.getResponseCode()}")
        }
        connection.disconnect()
    }

    File getApkFile(def project, String buildTypeName, String productFlavorName = ''){
        def apkPath = "${project.buildDir}/outputs/apk"
        if(productFlavorName.empty){
            return new File("${apkPath}/${project.name}-${buildTypeName}.apk")
        }
        return new File("${apkPath}/${project.name}-${productFlavorName}-${buildTypeName}.apk")
    }
    
    File getProguardFile(def project, String buildTypeName, String productFlavorName = ''){
        if(project.android.buildTypes[buildTypeName].minifyEnabled){
            def proguardPath = "${project.buildDir}/outputs/mapping"
            if(productFlavorName.empty){
                return new File("${proguardPath}/${buildTypeName}/mapping.txt")
            }
            else{
                return new File("${proguardPath}/${productFlavorName}/${buildTypeName}/mapping.txt")
            }
        }
        // TODO: Handle legacy version?!
        
        return null
    }
    
    def void writeFormData(DataOutputStream outStream, String name, boolean value){
        outStream.writeBytes(DOUBLE_HYPHENS + MAGIC_BOUNDARY_SEPARATOR + CRLF)
        outStream.writeBytes("Content-Disposition: form-data; name=\"${name}\"${CRLF}")
        outStream.writeBytes(CRLF)
        outStream.writeBytes("${String.valueOf(value)}${CRLF}")
    }
    
    def void writeFormData(DataOutputStream outStream, String name, File file){
        outStream.writeBytes(DOUBLE_HYPHENS + MAGIC_BOUNDARY_SEPARATOR + CRLF)
        outStream.writeBytes("Content-Disposition: form-data; name=\"${name}\";filename=\"${file.getName()}\"${CRLF}")
        outStream.writeBytes("Content-Type: application/octet-stream" + CRLF)
        outStream.writeBytes(CRLF)
        outStream.write(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        outStream.writeBytes(CRLF);   
    }
    
    def void writeFormDataEnd(DataOutputStream outStream){
        outStream.writeBytes("$DOUBLE_HYPHENS$MAGIC_BOUNDARY_SEPARATOR$DOUBLE_HYPHENS$CRLF");
    }

    private String readBody(InputStream inStream){
        //InputStream responseStream = new BufferedInputStream(responseStream)

        BufferedReader inStreamReader = new BufferedReader(new InputStreamReader(inStream))
        String line = ""
        StringBuilder stringBuilder = new StringBuilder()
        while ((line = inStreamReader.readLine()) != null)
        {
            stringBuilder.append(line).append("\n")
        }
        inStreamReader.close()
        inStream.close()
        
        return stringBuilder.toString()
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