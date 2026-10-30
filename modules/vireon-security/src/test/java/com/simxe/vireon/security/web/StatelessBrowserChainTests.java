package com.simxe.vireon.security.web;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.SessionManagementFilter;
class StatelessBrowserChainTests {
 @Configuration(proxyBeanMethods=false) @EnableWebSecurity static class Host {
  @Bean SecurityFilterChain chain(HttpSecurity h)throws Exception{
   var policy=new BrowserSecurityPolicy(Set.of("https://host.test"),"__Secure-VIREON_IAM_CSRF",false);
   BrowserSecurityConfigurer.configure(h,BrowserSecurityConfigurer.repository(policy));h.authorizeHttpRequests(a->a.anyRequest().denyAll());return h.build();
  }
 }
 @Test void statelessCookieIdentityDoesNotTriggerPerRequestSessionAuthenticationCsrfRotation(){new WebApplicationContextRunner().withUserConfiguration(Host.class).run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(SecurityFilterChain.class).getFilters()).noneMatch(SessionManagementFilter.class::isInstance);});}
}
