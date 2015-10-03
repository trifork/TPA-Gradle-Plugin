# TPA Gradle Plugin

## Introduction
This plugin aims at integrating the Android build system with The Perfect App through a proper Gradle plugin, as an alternative to project-dependent scripting using Gradle, Groovy, Ant, Java and Bash. The plugin is still under development and you are encouraged to contribute or report bugs.

Initial contribution donated by clb@trifork.com under a permissive [MIT license](https://en.wikipedia.org/wiki/MIT_License) - which basically means, do with it what you want!


## Installation
In order to use the plugin, all that's needed is inclusion of the dependency and the Maven repository where it can be downloaded from. Modify your existing buildscript content as so:

```
buildscript {
    repositories {
        ...
        maven { url "http://nexus.ci82.trifork.com/content/repositories/releases" }
    }
    dependencies {
        ...
        classpath 'com.trifork.tpa:tpa-gradle-plugin:1.0.65'
    }
}


```

Note: Always try to use the latest version published by checking the [Maven reposatory](http://nexus.ci82.trifork.com/content/repositories/releases/com/trifork/tpa/tpa-gradle-plugin/). Also note, the buildscript is often located in the parent project-wide build.gradle and not the Android app module build.gradle. Also note, this should really be pushed to a public Maven repo like [jcenter](https://bintray.com/bintray/jcenter) or [central](http://search.maven.org/).

##Simple non-flavor configuration

To actually use the plugin, it must be applied and it must be configured through a TPA DSL (Domain Specific Language). For simple projects with only a default 'debug' and 'release' build type and no flavors, it may look like this:

```
apply plugin: 'tpa'
tpa {
    server = 'tpa.trifork.com'
    uploadUUID = '123450d-202c-47e9-9904-8aae8f4115bf'
}


```

The server is the TPA instance you are targeting and the uploadUUID is the unique upload ID generated and exposed by the TPA user interface when you create your project.

By default, the plugin will *not* publish the artifact once deployed to TPA. That is, it will have to be published manually by using the user-interface of the TPA server application. You can override this behavior using the TPA DSL:

```
apply plugin: 'tpa'
tpa {
    server = 'tpa.trifork.com'
    uploadUUID = '123450d-202c-47e9-9904-8aae8f4115bf'
    publish = true
}


```

What if you only wish to publish a special 'preview' build type you are using for your project, in order to provide continious deployment for your customers to see? Easy, you just use different publish configurations between your build types:

```
apply plugin: 'tpa'
tpa {
    server = 'tpa.trifork.com'
    uploadUUID = '123450d-202c-47e9-9904-8aae8f4115bf'
    buildTypes{
        preview{
            publish = true
        }
    }
}

```
With the above configuration, all build types ('debug' and 'release') will remain unpublished, *except* for the 'preview' build type which is published.

##Complex flavor configuration

Just as was the case with build types, the TPA DSL supports a product flavor extension mechanism. This means that you may also use the plugin on multi flavored projects and configure it much as you would expect from having used the Android DSL:

```
apply plugin: 'tpa'
tpa {
    server = 'tpa.trifork.com'
    productFlavors{
        cocacola {
            uploadUUID = '12345f0d-202c-47e9-9904-8aae8f4115bf'
        }
        pepsi {
            uploadUUID = '1234550c-efeb-430b-b523-0781a6b700a3'
        }
    }
    buildTypes{
        develop{
            publish = true
        }
    }
}


```

The example above configures two distinct flavors 'cocacola' and 'pepsi' with each their TPA uploadUUID. However, only the 'develop' build type variants 'cocacolaDevelop' and 'pepsiDevelop' will be published (pushed out actively), while all other variants will have to be published manually by using the TPA web interface.

You may also flip the default if you desire, such that all variants are published except for i.e. the 'release' build type:


```
apply plugin: 'tpa'
tpa {
    server = 'tpa.trifork.com'
    publish = true
    productFlavors{
        cocacola {
            uploadUUID = '12345f0d-202c-47e9-9904-8aae8f4115bf'
        }
        pepsi {
            uploadUUID = '1234550c-efeb-430b-b523-0781a6b700a3'
        }
    }
    buildTypes{
        release{
            publish = false
        }
    }
}


```


## Gradle tasks

Once installed and configured, the plugin will take part of the Gradle build-chain. Your Android and TPA configuration will be analyzed, and tasks will be generated accordingly. To see which tasks will be generated, execute a "gradle task" command:

```
The Perfect App tasks
---------------------
tpaInfoDebug - Fetches info about latest deploy of debug variant
tpaInfoRelease - Fetches info about latest deploy of release variant
tpaDeployDebug - Deploys debug variant
tpaDeployRelease - Deploys release variant

```

There are two types of tasks installed by the TPA plugin, namely tpaInfo tasks and tpaDeploy tasks. These are explained separately below.

##Info tasks

You can use the tpaInfo (or any variant version of it) to learn about the current situation of TPA deployments. An example of running a current task for the 'release' build:

```
~/$ gradle tpaInfoRelease
:app:tpaInfoRelease
Current deploy information for variant 'release':
* Track name/applicationId: com.pepsico.pepsi.game
* Size: 2,4 MB
* Published: false
* Uploaded on: Oct 3, 2015 1:58 PM
* VersionNo: 105
* VersionString: 0.1-54

BUILD SUCCESSFUL

```

The TPA server receives a request to return information about the track name 'com'pepsico.pepsi.game' for the project specified by the assiciated uploadUUID. It responds with key properties useful in a debugging scenario or while preparing for a deployment. 

##Deploy tasks

To actually deploy a variant to TPA, you would use the specific tpaDeploy task.
An example of deploying a new version of the flavor 'pepsi' and build type 'develop' would look like this:

```
~/$ gradle tpaDeployPepsiDevelop
:app:assemblePepsiDevelop
:app:tpaInfoPepsiDevelop
Current deploy information for variant pepsiDevelop:
* Track name/applicationId: com.pepsico.pepsi.game.develop
* Size: 6,7 MB
* Published: true
* Uploaded on: Sep 12, 2015 3:00 PM
* VersionNo: 17
* VersionString: 1.0-3-DEVELOP
:app:tpaDeployPepsiDevelop
Deploying: 
* Build variant: pepsiDevelop
* Track name/applicationId: com.pepsico.pepsi.game.develop
* Version name: 1.0-4-DEVELOP
* Version code: 18
* APK file name: app-debug.apk
* APK file size: 6,8 MB
* Publish flag: true
* Upload UUID: 36732a33-5fef-4dde-84f1-d8e9f2aa40b7
* Target server: tpa.trifork.com
OK

BUILD SUCCESSFUL


```

Notice that the tpaDeployPepsiDevelop triggers the task assemblePepsiDevelop and 
tpaInfoPepsiDevelop to run first. This is done in order to avoid deploying an 
old artifact with the *same* versionNo as the currently deployed version on the TPA server (which would generate an error after the upload anyway). So if we were to execute the tpaDeployPepsiDevelop again with no change in versionNo, the following would happen:

```
:app:tpaInfoPepsiDevelop
Current deploy information for variant pepsiDevelop:
* Track name/applicationId: com.pepsico.pepsi.game.develop
* Size: 6,8 MB
* Published: false
* Uploaded on: Sep 22, 2015 3:00 PM
* VersionNo: 18
* VersionString: 1.0-4-DEVELOP
:app:tpaDeployPepsiDevelop
VersionNo 18 of pepsiDevelop variant already uploaded
:app:tpaDeployAlkaDevelop SKIPPED

BUILD SUCCESSFUL


```

As you can see, the tpaDeployPepsiDevelop task is now completely skipped, since it would be rejected by the TPA server later anyway. Avoiding this failure scenario is paramount in a continuous integration setup, where you wouldn't want Jenkins to consider an up-to-date deployment, a failed job.

## Implementation notes

In order to avoid class versioning issues with other plugins (I'm looking at you, Apache HttpClient), the TPA plugin is implemented with *zero* external dependencies. That is, it relies only on the vanilla JRE7 runtime. It's thus safe to assume the plugin is compatible with any other Gradle plugin and on any platform.

##TODO:
- Handle TpaLib config? (tpa.crashreporting.* properties)
- Handle library format (AAR)
- Test on a Windows box
- Upload progress monitoring
- Support for -Tpublish=true override?

##License
Copyright (c) 2015 Casper Bang <<clb@trifork.com>>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.