package com.payly.share.infrastructure.web

import com.payly.share.infrastructure.security.resolver.CurrentMerchantIdArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val currentMerchantIdArgumentResolver: CurrentMerchantIdArgumentResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentMerchantIdArgumentResolver)
    }
}
