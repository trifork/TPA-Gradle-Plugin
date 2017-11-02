package com.trifork.tpa.task

import com.trifork.tpa.TpaPlugin
import javax.net.ssl.HttpsURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPOutputStream
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/* 
 * TpaDeployTask will deploy an APK through an exposed TPA webservice. The task is
 * parameterized such that a variant for each productFlavor and buildType are created. 
 * <p>
 * TpaDeployTask depends on the assemble task, so that deployment is always done
 * on an up-to-date binary artifact. 
 * <p>
 * TpaDeployTask also depends on the TpaInfoTask, such that there will be no attempt 
 * at deploying an artifact if the previously deployed versionNo is indifferent to 
 * that of the current project - this is essential in a CI/CD setup with Jenkins etc.
 * <p>
 * The above means that the TpaDeployTask will fail (light up red if run by
 * Jenkins) if 1) it can not be built or 2) a new version (not already uploaded) 
 * can not be found on the.
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
            throw new GradleException("Failed to locate APK for upload: '${apkFile.getName()}'")
        }
        if( proguardFile != null && !proguardFile.exists() ) {
            throw new GradleException("Failed to locate ProguardMappingFile for upload: '${proguardFile.getName()}'")
        }
        
        prettyPrintDeployInfo(apkFile, proguardFile, publish)
        
        // Issue HTTP POST request
        HttpsURLConnection connection;
        try{
            URL url = new URL(uploadUrl)
            connection = (HttpsURLConnection) url.openConnection()
            connection.setUseCaches(false)
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Cache-Control", "no-cache")
            //connection.setRequestProperty("Content-Encoding", "gzip")
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" 
                + MAGIC_BOUNDARY_SEPARATOR)
            DataOutputStream requestStream = new DataOutputStream(connection.getOutputStream())
            //DataOutputStream requestStream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(connection.getOutputStream())))
            writeFormData(requestStream, "publish", publish)
            writeFormData(requestStream, "apk", apkFile)
            if(proguardFile != null){
                writeFormData(requestStream, "mapping", proguardFile)
            }
            writeFormDataEnd(requestStream)
            requestStream.flush()
            requestStream.close()

            // Parse and act on HTTP response
            if(connection.getResponseCode() == HttpsURLConnection.HTTP_OK){
                    String responseBody = readBody(connection.getInputStream())
                    println "Server response: $responseBody"
            }else{
                throw GradleException("Server error: ${connection.getResponseCode()}")
            }
        }catch(Throwable t){
            if(connection.getResponseCode() == HttpsURLConnection.HTTP_FORBIDDEN){
                // This should never happen, as the tpaInfo task fails first!
                try{
                    String responseBody = readBody(connection.getInputStream())
                    println "Server response: $responseBody"                        
                }catch(IOException e){
                    println "No track name matching '${applicationId}${applicationIdSuffix}' found on the server! Did you forget to add this?"
                }
            }
            else{
                String responseBody = readBody(connection.getInputStream())
                println "Server response: $responseBody"
                throw new GradleException("${connection.getResponseCode()}")
            }
        }finally{
            connection.disconnect()    
        }
    }

    def prettyPrintDeployInfo(File apkFile, File proguardFile, boolean publish){
        println "Deploying: \n" +
            "* Build variant: ${variantName}\n" +
            "* Track name (applicationId + suffix): ${applicationId}${applicationIdSuffix}\n" +
            "* Version name: ${versionName}\n" +
            "* Version code: ${versionCode}\n" +
            "* APK file name: ${apkFile.getName()}\n" +
            "* APK file size: ${humanReadableByteCount(apkFile.length())}"
        if(proguardFile){
            println "* Proguard file name: ${proguardFile.getName()}"
        }
        println "* Publish flag: ${publish}\n" +
            "* Upload UUID: ${uploadUUID}\n" +
            "* Target server: ${project.tpa.server}"
    }
    
    File getApkFile(def project, String buildTypeName, String productFlavorName = ''){
        def apkPath = "${project.buildDir}/outputs/apk"
        if(productFlavorName.empty){
            File file = new File("${apkPath}/${project.name}-${buildTypeName}.apk")
            if(file.exists()){
                return file;
            }else{
                return new File("${apkPath}/${buildTypeName}/${project.name}-${buildTypeName}.apk")
            }
            
        }
        
        File file = new File("${apkPath}/${project.name}-${productFlavorName}-${buildTypeName}.apk")
            if(file.exists()){
                return file;
            }else{
                return new File("${apkPath}/${productFlavorName}/${buildTypeName}/${project.name}-${productFlavorName}-${buildTypeName}.apk")        
            }
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