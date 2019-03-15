package com.trifork.tpa.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import com.trifork.tpa.TpaPlugin
import java.text.DateFormat

/*
 * Common base class for all TPA tasks 
*/
abstract class AbstractTpaTask extends DefaultTask {
    
    @Input String productFlavor
    
    @Input String buildType

    String uploadUUID
    
    String variantName

    Integer versionCode

    String versionName
    
    String applicationIdSuffix;
    
    String applicationId
    
    @TaskAction
    void executeRequest() {

        variantName = TpaPlugin.getVariantName(buildType, productFlavor)
        versionCode = getVersionCode()
        versionName = getVersionName()
        applicationId = getApplicationId(project)
        applicationIdSuffix = getApplicationIdSuffix(project);              
        
        if(project.tpa.server == null || project.tpa.server.trim().isEmpty()){
           throw new GradleException("You need to specify 'tpa.server'")
        }

        if(project.tpa.productFlavors == null || project.tpa.productFlavors.empty){
            uploadUUID = project.tpa.uploadUUID
            if(uploadUUID == null || uploadUUID.trim().isEmpty()){
               throw new GradleException("You need to specify 'tpa.uploadUUID'")
            }    
        }else{
            uploadUUID = project.tpa.productFlavors[productFlavor].uploadUUID
            if(uploadUUID == null || uploadUUID.trim().isEmpty()){
               throw new GradleException("You need to specify 'tpa.productFlavors.${productFlavor}.uploadUUID'")
            }
        }
        
        //println "versionCode:" + versionCode;
        //println "versionName:" + versionName;
        //println "applicationId:" + applicationId;
    }
    
    String toEntityString(def response){
        StringBuilder sb = new StringBuilder()
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()), 65728)
            String line = null
            while ((line = reader.readLine()) != null) {
                sb.append(line)
            }
        }
        catch (IOException e) { e.printStackTrace() }
        catch (Exception e) { e.printStackTrace() }

        return sb.toString()
    }
    
    public static String fromISO8601(final String iso8601string) {
        Calendar calendar = GregorianCalendar.getInstance()
        String s = iso8601string.substring(0, 19)
        def date = Date.parse("yyyy-MM-dd'T'HH:mm:ss", s)
        DateFormat dateFormatter = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, 
            DateFormat.SHORT, 
            Locale.getDefault())
        return dateFormatter.format(date)
    }
    
    public static String humanReadableByteCount(long bytes) {
        int unit = 1024
        if (bytes < unit){
            return bytes + " B"
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit))
        return String.format('%.1f %sB', bytes / Math.pow(unit, exp), \
            'KMGTPE'.charAt(exp-1))
    }    

    public String getApplicationId(def project){
        
        if(project.android.productFlavors.empty){
            if(project.android.defaultConfig.applicationId == null){
                def manifestFile = new File("${project.projectDir}/src/main/AndroidManifest.xml")
                if(manifestFile.exists()){
                    def manifest = new XmlSlurper().parse(manifestFile)
                    return manifest.@'package'
                }
                throw new GradleException("Unable to determine applicationId or locate manifest file!")
            }
            return project.android.defaultConfig.applicationId
        }else{
            // Null check the flavoured applicationId attribute
            def flavouredAppId = project.android.productFlavors[productFlavor].applicationId
            def defaultAppId = project.android.defaultConfig.applicationId
            def appId = flavouredAppId != null ? flavouredAppId : defaultAppId

            // Check if the flavoured appIdSuffix is set
            def flavouredSuffix = project.android.productFlavors[productFlavor].applicationIdSuffix
            def appIdSuffix += flavouredSuffix != null ? flavouredSuffix : ""
            return "$appId$appIdSuffix"
        }
    }
    
    public String getApplicationIdSuffix(def project){
        return project.android.buildTypes[buildType].applicationIdSuffix ?: ''        
    }
    
    public Integer getVersionCode(){
        if(project.android.productFlavors.empty || project.android.productFlavors[productFlavor].versionCode == null){
            return project.android.defaultConfig.versionCode.toInteger()
        }else{
            return project.android.productFlavors[productFlavor].versionCode.toInteger()
        }
    }

    public String getVersionName(){
        if(project.android.productFlavors.empty || project.android.productFlavors[productFlavor].versionName == null){            
            return project.android.defaultConfig.versionName
        }else{
            return project.android.productFlavors[productFlavor].versionName
        }
    }
    
}    

