package com.trifork.tpa.dsl

import org.gradle.api.NamedDomainObjectContainer

class TpaExtension{
    NamedDomainObjectContainer<TpaProductFlavor> productFlavors
    NamedDomainObjectContainer<TpaBuildType> buildTypes
    String server = ''
    boolean publish = false
    String uploadUUID = ''

    TpaExtension(productFlavors, buildTypes){
        this.productFlavors = productFlavors
        this.buildTypes = buildTypes
    }

    def productFlavors(Closure closure){
        this.productFlavors.configure(closure)
    }

    def buildTypes(Closure closure){
        this.buildTypes.configure(closure)
    }

    def server(Closure closure){
        this.server.configure(closure)
    }

    def publish(Closure closure){
        this.publish.configure(closure)
    }

    def uploadUUID(Closure closure){
        this.uploadUUID.configure(closure)
    }
}
