package com.payly.share.infrastructure.metrics

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Выполняет [action] после коммита текущей транзакции, а без транзакции — сразу.
 *
 * Нужно бизнес-метрикам: если инкрементить счётчик прямо в @Transactional-методе,
 * а транзакция потом откатится (конфликт optimistic lock, ошибка outbox), метрика
 * покажет событие, которого в БД нет.
 */
fun afterCommit(action: () -> Unit) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        action()
        return
    }

    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() = action()
    })
}
