package com.simxe.vireon.web.security;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
class HttpMethodPolicyTests {
 final HttpRouteCatalog routes=p->p.equals("/api/query")?java.util.Set.of("GET"):p.equals("/api/write")?java.util.Set.of("POST"):null;
 int invoke(String method,String path,String query,String origin,String proposed,String override)throws Exception{
  var r=new MockHttpServletRequest(method,path);r.setQueryString(query);if(origin!=null)r.addHeader("Origin",origin);if(proposed!=null)r.addHeader("Access-Control-Request-Method",proposed);if(override!=null)r.addHeader("X-HTTP-Method-Override",override);
  var s=new MockHttpServletResponse();new HttpMethodPolicyFilter(routes).doFilter(r,s,(a,b)->((jakarta.servlet.http.HttpServletResponse)b).setStatus(218));return s.getStatus();
 }
 @Test void disabledMethodsAlwaysPrecedeAuthentication()throws Exception{for(String method:java.util.List.of("HEAD","PUT","PATCH","DELETE","TRACE","CONNECT","BREW"))assertThat(invoke(method,"/api/write",null,null,null,null)).isEqualTo(405);}
 @Test void onlyActualSupportedGetPostPass()throws Exception{assertThat(invoke("POST","/api/write",null,null,null,null)).isEqualTo(218);assertThat(invoke("GET","/api/write",null,null,null,null)).isEqualTo(405);assertThat(invoke("GET","/api/query",null,null,null,null)).isEqualTo(218);}
 @Test void optionsMustBeAPreflightAndOverridesAreAlwaysRejected()throws Exception{assertThat(invoke("OPTIONS","/api/query",null,null,null,null)).isEqualTo(405);assertThat(invoke("OPTIONS","/api/query",null,"https://trusted.test","GET",null)).isEqualTo(218);assertThat(invoke("POST","/api/write","%5Fmethod=DELETE",null,null,null)).isEqualTo(405);assertThat(invoke("POST","/api/write",null,null,null,"GET")).isEqualTo(405);}
 @Test void unrelatedBusinessParametersAndEncodedValuesAreNotIamRules()throws Exception{assertThat(invoke("GET","/api/query","domain=custom&q=100%25",null,null,null)).isEqualTo(218);}
 @Test void containerErrorAndNestedErrorDispatchCannotBypassMethodPolicy()throws Exception{
  for(String method:java.util.List.of("TRACE","CONNECT"))for(boolean nested:new boolean[]{false,true}){
   var r=new MockHttpServletRequest(method,"/error");r.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);r.setAttribute("jakarta.servlet.error.request_uri","/api/write");if(nested)r.setAttribute(HttpMethodPolicyFilter.class.getName()+".FILTERED",true);
   var s=new MockHttpServletResponse();new HttpMethodPolicyFilter(routes).doFilter(r,s,(a,b)->{throw new AssertionError("Forbidden method cannot reach error security/business dispatch");});assertThat(s.getStatus()).isEqualTo(405);
  }
 }
 @Test void preContextRejectionNeverWritesServletRequestAttributes()throws Exception{
  var request=new jakarta.servlet.http.HttpServletRequestWrapper(new MockHttpServletRequest("CONNECT","/api/write")){
   @Override public void setAttribute(String name,Object value){throw new AssertionError("No Servlet Context for attribute listeners");}
   @Override public void removeAttribute(String name){throw new AssertionError("No Servlet Context for attribute listeners");}
  };
  var response=new MockHttpServletResponse();
  HttpMethodPolicyFilter.class.getMethod("rejectUnsupportedMethod",jakarta.servlet.http.HttpServletRequest.class,jakarta.servlet.http.HttpServletResponse.class).invoke(null,request,response);
  assertThat(response.getStatus()).isEqualTo(405);assertThat(response.getHeader("X-Trace-Id")).matches("[0-9a-f]{32}");assertThat(response.getContentAsString()).contains(response.getHeader("X-Trace-Id"));
 }
}
