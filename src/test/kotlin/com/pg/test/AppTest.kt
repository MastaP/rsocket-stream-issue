package com.pg.test

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ContextConfiguration(initializers = [AppTest.PropsInit::class])
internal class AppTest(@LocalServerPort val serverPort: Int): BaseAppTest(100) {

    override fun getAppAddress() = "localhost:$serverPort"

    companion object {
        private const val mongoImage = "mongo:4.4.0-bionic" //"percona/percona-server-mongodb:4.4.1-2"

        @Container
        val mongo = GenericContainer<Nothing>(mongoImage).apply {
            withExposedPorts(27017)
        }
    }

    class PropsInit : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.host=mongodb://${mongo.containerIpAddress}",
                "spring.data.mongodb.port=${mongo.firstMappedPort}"
            )
        }
    }
}
