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
        // Step 1: Compilar + unit tests + ITs com Testcontainers + JaCoCo check (unit + IT combinados)
        maven {
            name = "Build, Testes e Cobertura"
            id = "Maven_Build_Test"
            goals = "clean verify"
            runnerArgs = "-pl auth-service,cadastro-service,partner-service,billing-service -am"
            param("teamcity.coverage.jacoco.classpath", """
                auth-service/target/classes
                cadastro-service/target/classes
                partner-service/target/classes
                billing-service/target/classes
                common/target/classes
            """.trimIndent())
        }

        // Step 2: Análise estática Qodana (OWASP, SQL Injection, serialização, cobertura)
        dockerCommand {
            name = "Qodana — Análise Estática"
            id = "Qodana_Analysis"
            commandType = other {
                subCommand = "run"
                commandArgs = "--rm " +
                        "-v %teamcity.build.checkoutDir%:/data/project " +
                        "-v %teamcity.build.checkoutDir%/.qodana/results:/data/results " +
                        "-e QODANA_TOKEN=%env.QODANA_TOKEN% " +
                        "jetbrains/qodana-jvm:2025.3 " +
                        "--fail-threshold 0"
            }
        }

        // Step 4: Build da imagem Docker do auth-service
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

        // Step 5: Publicar imagem no Docker Hub
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
                +:/cadastro-service/**
                +:/partner-service/**
                +:/billing-service/**
                +:/common/**
                +:/pom.xml
            """.trimIndent()
            branchFilter = ""
            perCheckinTriggering = true
            enableQueueOptimization = false
        }
    }

    failureConditions {
        // Cobertura de classes não pode cair mais de 5% em relação ao último build
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