package com.simxe.vireon.security.web;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.NullSecurityContextRepository;
/** Standard Spring Security installation; the host owns all paths, chain matching and fallback. */
public final class BrowserSecurityConfigurer {
 private BrowserSecurityConfigurer(){}
 public static CookieCsrfTokenRepository repository(BrowserSecurityPolicy policy){
  var repository=new CookieCsrfTokenRepository();repository.setCookieName(policy.csrfCookieName());repository.setHeaderName("X-XSRF-TOKEN");repository.setCookiePath("/api");
  repository.setCookieCustomizer(cookie->cookie.httpOnly(true).secure(!policy.loopbackTest()).sameSite("Strict"));return repository;
 }
 public static void configure(HttpSecurity http,CookieCsrfTokenRepository repository)throws Exception{
  http.csrf(c->c.csrfTokenRepository(repository).csrfTokenRequestHandler(new HeaderCsrfTokenRequestHandler()));
  // Credential adapters explicitly own login/logout/password-change lifecycle. A stateless
  // request must not look like a fresh servlet-session login and clear CSRF on every call.
  http.cors(c->c.disable()).sessionManagement(s->s.disable());
  http.securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()));
  http.requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable());
 }
}
