package com.pg.test

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

@SpringBootApplication
@Controller
class RSocketFluxStreamApplication(val mongo: ReactiveMongoTemplate) {

    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun onStartup(event: ApplicationReadyEvent) {
        val key = mongo.save(MyKey()).block()
        println("Added key: $key")
    }

    @MessageMapping("test.{mode}")
    fun testFluxStream(@DestinationVariable mode: Int, flux: Flux<MyEntry>): Flux<MyEntry> {
        val counter = AtomicLong(0L)
        println("testFluxStream(), mode $mode")
        return process(mode, flux)
            .doOnNext {
                println("testFluxStream(): next #${counter.incrementAndGet()} '${it.order}', ${it.fresh}")
            }
            .doOnComplete {
                println("testFluxStream(): done for ${counter.get()}")
            }
    }

    fun process(mode: Int, flux: Flux<MyEntry>): Flux<MyEntry> {
        val key: Mono<MyKey> = Mono
            .defer {
                println("getKey: defer")
                getKey(mode)
            }
//            .cache()
            //.log()

        return Flux.combineLatest<MyKey, MyEntry, Pair<MyKey, MyEntry>>(key, flux, BiFunction { r, e -> Pair(r, e) })
            .map { (k, e) ->
                MyEntry(e.myId, e.order, "${k.key}: ${e.value}", e.fresh.not())
            }
    }

    fun getKey(mode: Int): Mono<MyKey> = when (mode) {
        1 -> getKeyFromMongo() // fails
        2 -> Mono.fromFuture(CompletableFuture.supplyAsync { getKeyFromMongo().block() }) // yuck, fails
        3 -> Mono.just(CompletableFuture.supplyAsync { getKeyFromMongo().block() }.get()!!) // yuck, works
        else -> Mono.just(MyKey("Direct")) // works
    }

    fun getKeyFromMongo(): Mono<MyKey> = mongo
        .findAll(MyKey::class.java)
        .doOnNext {
            println("getKey: findAll: $it")
        }
        //.subscribeOn(Schedulers.elastic())
        .next()
}

data class MyEntry(val myId: String, val order: Long, val value: String, val fresh: Boolean = true)

data class MyKey(val key: String = "Processed")

@Configuration
class MongoDbConfiguration(
    @Value("\${spring.data.mongodb.host}") val host: String,
    @Value("\${spring.data.mongodb.port}") val port: Int,
    @Value("\${spring.data.mongodb.dbname}") val name: String
) : AbstractReactiveMongoConfiguration() {

    @Bean
    override fun reactiveMongoClient(): MongoClient = MongoClients.create("$host:$port")

    override fun getDatabaseName(): String = name
}

fun main(args: Array<String>) {
    runApplication<RSocketFluxStreamApplication>(*args)
}
