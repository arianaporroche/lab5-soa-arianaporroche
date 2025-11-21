@file:Suppress("WildcardImport", "NoWildcardImports", "MagicNumber")

package soa

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.PublishSubscribeChannelSpec
import org.springframework.integration.dsl.integrationFlow
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("soa.CronOddEvenDemo")

/**
 * Spring Integration configuration for demonstrating Enterprise Integration Patterns.
 * This application implements a message flow that processes numbers and routes them
 * based on whether they are even or odd.
 *
 * **Your Task**: Analyze this configuration, create an EIP diagram, and compare it
 * with the target diagram to identify and fix any issues.
 */
@SpringBootApplication
@EnableIntegration
@EnableScheduling
class IntegrationApplication(
    private val sendNumber: SendNumber,
) {
    /**
     * Channel that acts as the main entry point for all numbers (positive or negative).
     * Messages sent here are routed to either the evenChannel or oddChannel based on parity.
     */
    private val positiveCounter = AtomicInteger(0)

    /**
     * Defines a publish-subscribe channel for odd numbers.
     * Multiple subscribers can receive messages from this channel.
     */
    @Bean
    fun oddChannel(): PublishSubscribeChannelSpec<*> = MessageChannels.publishSubscribe()

    /**
     * Defines a channel for initial numers.
     */
    @Bean
    fun numberInputChannel() = MessageChannels.direct()

    /**
     * Main integration flow that routes incoming numbers from the numberInputChannel.
     * Numbers are directed to evenChannel if they are even, or to oddChannel if they are odd.
     * Acts as the central router for all numbers before further processing.
     */
    @Bean
    fun myFlow(): IntegrationFlow =
        integrationFlow("numberInputChannel") {
            route { p: Int ->
                val channel = if (p % 2 == 0) "evenChannel" else "oddChannel"
                logger.info("Router: {} -> {}", p, channel)
                channel
            }
        }

    /**
     * Integration flow for processing even numbers.
     * Transforms integers to strings and logs the result.
     */
    @Bean
    fun evenFlow(): IntegrationFlow =
        integrationFlow("evenChannel") {
            filter({ p: Int ->
                val passes = p >= 0
                passes
            }, { discardChannel("discardChannel") })

            transform { obj: Int ->
                logger.info("   Even Transformer: {} -> 'Number {}'", obj, obj)
                "Number $obj"
            }

            handle { p ->
                logger.info("  Even Handler: Processed [{}]", p.payload)
            }
        }

    /**
     * Integration flow for processing odd numbers.
     * Applies a filter before transformation and logging.
     * Note: Examine the filter condition carefully.
     */
    @Bean
    fun oddFlow(): IntegrationFlow =
        integrationFlow("oddChannel") {
            filter({ p: Int ->
                val passes = p > 0
                logger.info("  Odd Filter: checking {} -> {}", p, if (passes) "PASS" else "REJECT")
                passes
            }, { discardChannel("discardChannel") })

            transform { obj: Int ->
                logger.info("   Odd Transformer: {} -> 'Number {}'", obj, obj)
                "Number $obj"
            }

            handle { p ->
                // logger.info("  Odd Handler: Processed [{}]", p.payload)
            }
        }

    /**
     * Integration flow for handling discarded messages.
     */
    @Bean
    fun discarded(): IntegrationFlow =
        integrationFlow("discardChannel") {
            handle { p ->
                logger.info("  Discard Handler: [{}]", p.payload)
            }
        }

    /**
     * Scheduled task that periodically sends negative random numbers via the gateway.
     */
    @Scheduled(fixedRate = 1000)
    fun sendNegativeNumber() {
        val number = -Random.nextInt(100)
        logger.info("Gateway injecting: {}", number)
        sendNumber.sendNumber(number)
    }

    /**
     * Scheduled task that periodically sends positive random numbers via the gateway.
     */
    @Scheduled(fixedRate = 100)
    fun sendPositiveNumber() {
        val number = positiveCounter.getAndIncrement()
        logger.info("Source generated number: {}", number)
        sendNumber.sendNumber(number)
    }
}

/**
 * Service component that processes messages from the odd channel.
 * Uses @ServiceActivator annotation to connect to the integration flow.
 */
@Component
class SomeService {
    @ServiceActivator(inputChannel = "oddChannel")
    fun handleOdd(p: Any) {
        logger.info("  Service Activator: Received [{}] (type: {})", p, p.javaClass.simpleName)
    }
}

/**
 * Messaging Gateway for sending numbers into the integration flow.
 * This provides a simple interface to inject messages into the system.
 * Note: Check which channel this gateway sends messages to.
 */
@MessagingGateway
interface SendNumber {
    @Gateway(requestChannel = "numberInputChannel")
    fun sendNumber(number: Int)
}

fun main() {
    runApplication<IntegrationApplication>()
}
