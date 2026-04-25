import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.projectFeatures.dockerRegistry
import jetbrains.buildServer.configs.kotlin.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2025.11"

project {

    buildType(Build)

    features {
        dockerRegistry {
            id = "PROJECT_EXT_3"
            name = "Docker Registry"
            url = "https://index.docker.io"
            userName = "vitorff1234"
            password = "credentialsJSON:9cd22375-1696-4251-8670-dc5f24766f3a"
        }
    }
}

object Build : BuildType({
    name = "Build"

    artifactRules = "auth-service/target/site/jacoco/** => coverage-report"

    params {
        password("env.DOCKER_PASSWORD", "credentialsJSON:9cd22375-1696-4251-8670-dc5f24766f3a")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        maven {
            name = "Compilar e Testar Auth Service"
            id = "Maven2"
            goals = "clean package"
            runnerArgs = "-pl auth-service -am -Dmaven.test.failure.ignore=true -X"
            param("teamcity.coverage.jacoco.classpath", """
                auth-service/target/classes
                common/target/classes
            """.trimIndent())
        }
        dockerCommand {
            name = "Construir Imagem Docker"
            id = "Construir_Imagem_Docker"
            commandType = build {
                source = file {
                    path = "auth-service/Dockerfile"
                }
                contextDir = "auth-service"
                namesAndTags = "vitorff1234/auth-service:%build.number%"
                commandArgs = "--pull"
            }
        }
        script {
            name = "Publicar Imagem"
            id = "Publicar_Imagem"
            scriptContent = """
                mkdir -p ~/.docker
                AUTH_STRING=${'$'}(printf '%s:%s' 'vitorff1234' '%env.DOCKER_PASSWORD%' | base64 -w0)
                printf '{"auths":{"https://index.docker.io/v1/":{"auth":"%s"}}}' "${'$'}AUTH_STRING" > ~/.docker/config.json
                docker push vitorff1234/auth-service:%build.number%
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            triggerRules = """
                -:.
                +:/auth-service/**
                +:/common/**
                +:/pom.xml
            """.trimIndent()
            branchFilter = ""
            perCheckinTriggering = true
            enableQueueOptimization = false
        }
    }

    failureConditions {
        failOnMetricChange {
            metric = BuildFailureOnMetric.MetricType.COVERAGE_CLASS_PERCENTAGE
            threshold = 5
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.LESS
            compareTo = build {
                buildRule = lastSuccessful()
            }
            stopBuildOnFailure = true
        }
    }

    features {
        perfmon {
        }
        dockerRegistryConnections {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_3"
            }
        }
    }
})
