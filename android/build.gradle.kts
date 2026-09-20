import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.kotlin.dsl.configure

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

subprojects {
    // :app sets its own compileSdk/minSdk/targetSdk directly in app/build.gradle.kts;
    // configuring it again here would race with app's own androidComponents.onVariants
    // registration and finalize the DSL too late under AGP 9.
    if (project.name != "app") {
        plugins.withId("com.android.application") {
            extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
                finalizeDsl { extension ->
                    extension.compileSdk = 37
                    extension.defaultConfig {
                        minSdk = 24
                        targetSdk = 37
                    }
                }
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
            finalizeDsl { extension ->
                extension.compileSdk = 37
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
