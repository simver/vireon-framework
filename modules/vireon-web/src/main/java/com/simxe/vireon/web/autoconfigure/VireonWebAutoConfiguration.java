package com.simxe.vireon.web.autoconfigure;

import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseBodyAdvice.class)
public class VireonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    VireonResponseProtocolAdvice vireonResponseProtocolAdvice() {
        return new VireonResponseProtocolAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    VireonGlobalExceptionHandler vireonGlobalExceptionHandler() {
        return new VireonGlobalExceptionHandler();
    }
}
