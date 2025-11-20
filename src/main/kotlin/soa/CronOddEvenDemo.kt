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
import org.springframework.util.ErrorHandler
import java.util.UUID
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
     * Creates an atomic integer source that generates sequential numbers.
     */
    @Bean
    fun integerSource(): AtomicInteger = AtomicInteger()

    /**
     * Defines a publish-subscribe channel for odd numbers.
     * Multiple subscribers can receive messages from this channel.
     */
    @Bean
    fun oddChannel(): PublishSubscribeChannelSpec<*> = MessageChannels.publishSubscribe()

    /**
     * Defines a publish-subscribe channel for failed messages.
     */
    @Bean
    fun deadLetterChannel(): PublishSubscribeChannelSpec<*> = MessageChannels.publishSubscribe()

    /**
     * Defines a channel for initial numers.
     */
    @Bean
    fun numberInputChannel() = MessageChannels.direct()

    @Bean
    fun customErrorHandler(): ErrorHandler =
        ErrorHandler { e ->
            logger.warn("⚠️ EVEN flow failed softly: {}", e.message)
        }

    /**
     * Main integration flow that polls the integer source and routes messages.
     * Polls every 100ms and routes based on even/odd logic.
     */
    @Bean
    fun myFlow(integerSource: AtomicInteger): IntegrationFlow =
        integrationFlow("numberInputChannel") {
            wireTap("wireTapLoggingFlow.input")

            route { p: Int ->
                val channel =
                    if (p % 2 == 0) {
                        "evenChannel"
                    } else {
                        "oddChannel"
                    }
                logger.info("🔀 Router: {} → {}", p, channel)
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
            wireTap("wireTapLoggingFlow.input")

            enrichHeaders {
                header("processedAt", System.currentTimeMillis())
                header("messageId", UUID.randomUUID().toString())
                header("sourceFlow", "evenFlow")
                header("errorChannel", "deadLetterChannel")
            }

            filter({ p: Int ->
                val passes = p >= 0
                logger.info("  🔍 Even Filter: checking {} → {}", p, if (passes) "PASS" else "REJECT")
                passes
            }, { discardChannel("discardChannel") })

            transform { obj: Int ->
                if (obj != 0 && obj % 8 == 0) {
                    throw RuntimeException("❌ Simulated failure in EVEN flow with $obj")
                }
                logger.info("  ⚙️  Even Transformer: {} → 'Number {}'", obj, obj)
                "Number $obj"
            }

            handle { p ->
                logger.info("  ✅ Even Handler: payload=[{}], headers=[{}]", p.payload, p.headers)
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
            wireTap("wireTapLoggingFlow.input")

            enrichHeaders {
                header("processedAt", System.currentTimeMillis())
                header("messageId", UUID.randomUUID().toString())
                header("sourceFlow", "evenFlow")
                header("errorChannel", "deadLetterChannel")
            }

            filter({ p: Int ->
                val passes = p > 0
                logger.info("  🔍 Odd Filter: checking {} → {}", p, if (passes) "PASS" else "REJECT")
                passes
            }, { discardChannel("discardChannel") })

            transform { obj: Int ->
                logger.info("  ⚙️  Odd Transformer: {} → 'Number {}'", obj, obj)
                "Number $obj"
            }

            handle { p ->
                logger.info("  ✅ Odd Handler: payload=[{}], headers=[{}]", p.payload, p.headers)
            }
        }

    /**
     * Integration flow for handling discarded messages.
     */
    @Bean
    fun discarded(): IntegrationFlow =
        integrationFlow("discardChannel") {
            handle { p ->
                logger.info("  🗑️  Discard Handler: [{}]", p.payload)
            }
        }

    /**
     * Integration flow for handling failed messages.
     */
    @Bean
    fun deadLetterFlow(): IntegrationFlow =
        integrationFlow("deadLetterChannel") {
            handle { message ->
                val payload = message.payload
                val causeMessage =
                    when (payload) {
                        is Throwable -> payload.cause?.message ?: payload.message
                        else -> payload.toString()
                    }
                logger.error(
                    "💀 Dead Letter received: Cause=[{}], Payload=[{}], Headers=[{}]",
                    causeMessage,
                    payload,
                    message.headers,
                )
            }
        }

    @Bean
    fun wireTapLoggingFlow(): IntegrationFlow =
        integrationFlow("wireTapLoggingFlow.input") {
            handle { message ->
                logger.info("👀 Wire Tap intercepted message: {}", message.payload)
            }
        }

    /**
     * Scheduled task that periodically sends negative random numbers via the gateway.
     */
    @Scheduled(fixedRate = 1000)
    fun sendNegativeNumber() {
        val number = -Random.nextInt(100)
        logger.info("🚀 Gateway injecting: {}", number)
        sendNumber.sendNumber(number)
    }

    /**
     * Scheduled task that periodically sends positive random numbers via the gateway.
     */
    @Scheduled(fixedRate = 100)
    fun sendPositiveNumber() {
        val number = positiveCounter.getAndIncrement()
        logger.info("📥 Source generated number: {}", number)
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
        logger.info("  🔧 Service Activator: Received [{}] (type: {})", p, p.javaClass.simpleName)
    }

    @ServiceActivator(inputChannel = "evenChannel")
    fun handleEven(p: Any) {
        logger.info("  🔧 Service Activator: Received [{}] (type: {})", p, p.javaClass.simpleName)
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
