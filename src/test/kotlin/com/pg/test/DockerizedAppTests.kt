package com.pg.test

import org.junit.jupiter.api.Disabled
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile

@Disabled
@Testcontainers
internal class DockerizedAppTests: BaseAppTest() {

    override fun getAppAddress() = "${app.containerIpAddress}:${app.firstMappedPort}"

    companion object {
        val mongoImage = "mongo:4.4.0-bionic" //"percona/percona-server-mongodb:4.4.1-2"

        @Container
        val mongo = GenericContainer<Nothing>(mongoImage).apply {
            withExposedPorts(27017)
            withNetwork(Network.SHARED)
            withNetworkAliases("mongo")
        }

        @Container
        val app = GenericContainer<Nothing>("amazoncorretto:11.0.8").apply {
            withCopyFileToContainer(
                MountableFile.forHostPath("build/libs/RSocketFluxStream-0.0.1-SNAPSHOT.jar"),
                "/app/app.jar"
            )
            withLogConsumer {
                if (it?.bytes != null) {
                    print("[app]")
                    System.out.write(it.bytes)
                }
            }
            withExposedPorts(8080)
            withEnv(
                mapOf(
                    Pair("SPRING_DATA_MONGODB_HOST", "mongodb://mongo"),
                    Pair("SPRING_DATA_MONGODB_PORT", "27017")
                )
            )
            withNetwork(Network.SHARED)
            dependsOn(mongo)
            withCommand("java", "-jar", "/app/app.jar")
        }
    }
}
