package com.pg.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Flux
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.LongStream

internal abstract class BaseAppTest(private val maxItems: Long = 10L) {

    lateinit var requester: RSocketRequester

    abstract fun getAppAddress(): String

    @BeforeEach
    fun setup() {
        requester = RSocketRequester
            .builder()
            .rsocketStrategies {
                it
                    .decoder(Jackson2JsonDecoder())
                    .encoder(Jackson2JsonEncoder())
            }
            .connectWebSocket(URI.create("ws://${getAppAddress()}/rs")).block()!!
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4])
    fun test(mode: Int) {
        val round = UUID.randomUUID().toString()
        println("Round: $round")

        val sendingCounter = AtomicLong(0L)
        val resultFlux = requester
            .route("test.{mode}", mode)
            .data(
                Flux.fromStream(LongStream.rangeClosed(1, maxItems).mapToObj {MyEntry(round, it, "test")})
                    .delayElements(Duration.ofMillis(10))
                .doOnNext {
                    println("Sending #${sendingCounter.incrementAndGet()}: ${it.order}, ${it.fresh}")
                }
                .doOnComplete {
                    println("Done sending ${sendingCounter.get()} elements")
                }
            )
            .retrieveFlux(MyEntry::class.java)

        val receivingCounter = AtomicLong(0L)
        resultFlux
            .doOnNext {
                println("Client received #${receivingCounter.incrementAndGet()}: ${it.order}, ${it.fresh}")
            }
            .doOnComplete {
                println("Client done receiving ${receivingCounter.get()} elements")
            }
            .blockLast()

        Assertions.assertEquals(maxItems, receivingCounter.get())
    }
}
