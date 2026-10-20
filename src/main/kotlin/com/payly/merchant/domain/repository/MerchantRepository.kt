package com.payly.merchant.domain.repository

import com.payly.merchant.domain.model.Merchant
import java.util.UUID

interface MerchantRepository {

    fun getById(id: UUID): Merchant

}