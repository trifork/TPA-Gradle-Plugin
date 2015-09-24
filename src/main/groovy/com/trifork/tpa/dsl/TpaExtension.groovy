package com.trifork.tpa.dsl

import org.gradle.api.NamedDomainObjectContainer

class TpaExtension{
    final NamedDomainObjectContainer<TpaProductFlavor> productFlavors
    final NamedDomainObjectContainer<TpaBuildType> buildTypes
    String server = ''
    boolean defaultPublish = true

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

    def defaultPublish(Closure closure){
        this.defaultPublish.configure(closure)
    }
}
