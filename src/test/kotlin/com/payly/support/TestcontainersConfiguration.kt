package com.payly.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Контейнеры инфраструктуры для тестов, поднимающих Spring-контекст.
 *
 * Сами контейнеры живут в companion object — один экземпляр на JVM. Интеграционные
 * и e2e тесты собирают разные Spring-контексты (MockMvc + @MockitoBean против
 * реального сервера на случайном порту), и без этого каждый контекст поднимал бы
 * свой Postgres/Redis/Kafka. Останавливает их Ryuk при завершении JVM.
 *
 * Версии Postgres и Redis совпадают с docker-compose.yml, Kafka — см. комментарий ниже.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// destroyMethod = "" — не даём Spring закрыть общий контейнер вместе с одним
	// из контекстов, им ещё пользуются другие. @ServiceConnection сам прописывает
	// адреса контейнеров в spring.datasource / spring.kafka / spring.data.redis
	@Bean(destroyMethod = "")
	@ServiceConnection
	fun kafkaContainer(): KafkaContainer = kafka

	@Bean(destroyMethod = "")
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer = postgres

	@Bean(destroyMethod = "")
	@ServiceConnection(name = "redis")
	fun redisContainer(): GenericContainer<*> = redis

	companion object {
		// Kafka 4.x: KafkaContainer этой версии Testcontainers не стартует с образами 3.x.
		// native-образ (GraalVM) поднимается за ~1 с против ~5 с у JVM-образа
		val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.0"))

		val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))

		val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)
	}

}
