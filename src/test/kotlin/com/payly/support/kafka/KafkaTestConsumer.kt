package com.payly.support.kafka

import com.payly.support.TestcontainersConfiguration
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility.await
import java.time.Duration
import java.util.UUID

/**
 * Читает топик с начала отдельной consumer group, чтобы проверить,
 * что событие действительно дошло до Kafka. Использовать в `use { }`.
 */
class KafkaTestConsumer(topic: String) : AutoCloseable {

    // Обычный KafkaConsumer, а не @KafkaListener: тесту нужно читать топик
    // по запросу и в своём потоке, не встраиваясь в Spring-контекст приложения
    private val consumer = KafkaConsumer<String, String>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to TestcontainersConfiguration.kafka.bootstrapServers,
            // Уникальная группа на каждый consumer — офсеты не делятся между тестами
            ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
            // Новая группа читает с начала топика, поэтому событие не теряется,
            // даже если оно было опубликовано до первого poll()
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
    ).apply { subscribe(listOf(topic)) }

    // Всё прочитанное копится здесь: в топике есть события и из прошлых тестов,
    // поэтому нужные записи потом отбираются предикатом
    private val received = mutableListOf<ConsumerRecord<String, String>>()

    /**
     * Опрашивает топик, пока не появится хотя бы одна запись под [predicate]
     * (или не выйдет [timeout] — тогда Awaitility уронит тест), и возвращает все такие записи.
     */
    fun awaitRecords(
        timeout: Duration = Duration.ofSeconds(15),
        predicate: (ConsumerRecord<String, String>) -> Boolean,
    ): List<ConsumerRecord<String, String>> {
        await().atMost(timeout).until {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add)
            received.any(predicate)
        }

        return received.filter(predicate)
    }

    override fun close() = consumer.close()

}
