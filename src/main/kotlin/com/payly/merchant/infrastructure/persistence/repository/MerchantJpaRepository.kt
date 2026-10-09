package com.payly.merchant.infrastructure.persistence.repository

import com.payly.merchant.infrastructure.persistence.entity.MerchantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantJpaRepository: JpaRepository<MerchantJpaEntity, UUID> {
}