package com.simxe.vireon.security.web;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.*;
import java.util.*;
class BrowserSecurityTests {
 final BrowserSecurityPolicy policy=new BrowserSecurityPolicy(Set.of("http://127.0.0.1:20010"),"VIREON_TEST_IAM_CSRF",true);
 MockHttpServletRequest request(String method){var r=new MockHttpServletRequest(method,"/api/query");r.setServerName("127.0.0.1");r.setServerPort(20010);r.setRemoteAddr("127.0.0.1");return r;}
 @Test void anonymousValidPreflightNeverRunsBusinessAndRejectsWrongRouteMethodOrHeaders()throws Exception{
  var f=new BrowserBoundaryFilter(policy,p->p.equals("/api/query")?Set.of("GET"):null);
  for(String method:List.of("GET","POST","DELETE")){
   var r=request("OPTIONS");r.addHeader("Origin","http://127.0.0.1:20010");r.addHeader("Access-Control-Request-Method",method);r.addHeader("Access-Control-Request-Headers","Content-Type, X-XSRF-TOKEN");var s=new MockHttpServletResponse();f.doFilter(r,s,(a,b)->{throw new AssertionError("No preflight business");});assertThat(s.getStatus()).isEqualTo(method.equals("GET")?204:403);
  }
  var r=request("OPTIONS");r.addHeader("Origin","http://127.0.0.1:20010");r.addHeader("Access-Control-Request-Method","GET");r.addHeader("Access-Control-Request-Headers","Authorization");var s=new MockHttpServletResponse();f.doFilter(r,s,(a,b)->{throw new AssertionError();});assertThat(s.getStatus()).isEqualTo(403);
 }
 @Test void exactOriginTransportAndDuplicateCsrfCookieAreEnforced()throws Exception{
  var f=new BrowserBoundaryFilter(policy,p->Set.of("GET","POST"));
  for(String origin:List.of("http://evil.invalid","null","http://127.0.0.1:20010.evil")){var r=request("GET");r.addHeader("Origin",origin);var s=new MockHttpServletResponse();f.doFilter(r,s,(a,b)->{throw new AssertionError();});assertThat(s.getStatus()).isEqualTo(403);}
  var r=request("POST");r.addHeader("Origin","http://127.0.0.1:20010");r.addHeader("Cookie","VIREON_TEST_IAM_CSRF=a; VIREON_TEST_IAM_CSRF=b");var s=new MockHttpServletResponse();f.doFilter(r,s,(a,b)->{throw new AssertionError();});assertThat(s.getStatus()).isEqualTo(403);
  r=request("GET");r.setRemoteAddr("192.0.2.1");s=new MockHttpServletResponse();f.doFilter(r,s,(a,b)->{throw new AssertionError();});assertThat(s.getStatus()).isEqualTo(403);
 }
 @Test void maskedHeaderIsRequiredAndRawOrParameterCannotSubstitute(){
  var repository=BrowserSecurityConfigurer.repository(policy);var handler=new HeaderCsrfTokenRequestHandler();var r=request("GET");var s=new MockHttpServletResponse();var raw=repository.generateToken(r);repository.saveToken(raw,r,s);handler.handle(r,s,()->raw);
  var exposed=(CsrfToken)r.getAttribute(CsrfToken.class.getName());String masked=exposed.getToken();assertThat(masked).isNotEqualTo(raw.getToken());
  var post=request("POST");post.addHeader("X-XSRF-TOKEN",masked);assertThat(handler.resolveCsrfTokenValue(post,raw)).isEqualTo(raw.getToken());
  post=request("POST");post.addHeader("X-XSRF-TOKEN",raw.getToken());assertThat(handler.resolveCsrfTokenValue(post,raw)).isNull();
  post=request("POST");post.setParameter("_csrf",masked);assertThat(handler.resolveCsrfTokenValue(post,raw)).isNull();
  post=request("POST");post.addHeader("X-XSRF-TOKEN",masked);post.addHeader("X-XSRF-TOKEN",masked);assertThat(handler.resolveCsrfTokenValue(post,raw)).isNull();
  assertThat(s.getCookie(policy.csrfCookieName()).getAttribute("SameSite")).isEqualTo("Strict");
  assertThat(s.getHeader("Set-Cookie")).contains("Path=/api","HttpOnly").doesNotContain("Domain=","Secure;");
 }
 @Test void unsafeProductionOrLoopbackConfigurationFails(){assertThatThrownBy(()->new BrowserSecurityPolicy(Set.of("http://example.test"),"CSRF",false)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new BrowserSecurityPolicy(Set.of("http://localhost:3000"),"CSRF",true)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new BrowserSecurityPolicy(Set.of("https://*.example.test"),"CSRF",false)).isInstanceOf(IllegalArgumentException.class);}
}
