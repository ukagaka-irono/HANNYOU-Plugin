import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
}

bukkitPluginYaml {
    main = "com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin"
    paperPluginLoader = "com.ukagaka.irono.hANNYOUPlugin.HANNYOUPluginLoader"
    apiVersion = "26.1.2"

    load = BukkitPluginYaml.PluginLoadOrder.STARTUP
    authors.addAll("伺々　彩乃")
    website = "https://github.com/ukagaka-irono"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }
}
