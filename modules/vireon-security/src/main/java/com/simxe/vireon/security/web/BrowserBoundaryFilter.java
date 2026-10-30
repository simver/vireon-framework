package com.simxe.vireon.security.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.springframework.web.filter.OncePerRequestFilter;
/** Host-wide CORS and source/duplicate-CSRF checks before authentication and CSRF verification. */
public final class BrowserBoundaryFilter extends OncePerRequestFilter {
 private final BrowserSecurityPolicy policy;private final Function<String,Set<String>> routes;
 private static final Set<String> HEADERS=Set.of("content-type","x-xsrf-token");
 public BrowserBoundaryFilter(BrowserSecurityPolicy policy,Function<String,Set<String>> routes){this.policy=policy;this.routes=routes;}
 private static boolean single(HttpServletRequest r,String h){return Collections.list(r.getHeaders(h)).size()==1;}
 private static void reject(HttpServletRequest r,HttpServletResponse s,int status)throws IOException{s.setStatus(status);s.setContentType("application/json");s.setHeader("Cache-Control","no-store");s.getWriter().write("{\"code\":\"VIREON.HTTP.FORBIDDEN\",\"message\":\"FORBIDDEN\",\"data\":null,\"traceId\":"+traceJson(r)+"}");}
 private static String traceJson(HttpServletRequest r){Object value=r.getAttribute(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID);return value instanceof String text&&text.matches("[0-9a-f]{32}")?"\""+text+"\"":"null";}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  boolean options=r.getMethod().equals("OPTIONS");String origin=r.getHeader("Origin");
  if(!policy.transportAllowed(r)||origin!=null&&(!single(r,"Origin")||!policy.allowedOrigins().contains(origin))){reject(r,s,403);return;}
  if(options){
   if(origin==null||!single(r,"Access-Control-Request-Method")){reject(r,s,403);return;}
   String method=r.getHeader("Access-Control-Request-Method");var allowed=routes.apply(r.getRequestURI().substring(r.getContextPath().length()));
   if(!Set.of("GET","POST").contains(method)||allowed==null||!allowed.contains(method)){reject(r,s,403);return;}
   var requested=Collections.list(r.getHeaders("Access-Control-Request-Headers"));if(requested.size()>1){reject(r,s,403);return;}
   if(!requested.isEmpty())for(String header:requested.getFirst().split(",",-1))if(!HEADERS.contains(header.trim().toLowerCase(Locale.ROOT))){reject(r,s,403);return;}
   cors(s,origin);s.setHeader("Access-Control-Allow-Methods",method);s.setHeader("Access-Control-Allow-Headers","Content-Type, X-XSRF-TOKEN");s.setHeader("Vary","Origin, Access-Control-Request-Method, Access-Control-Request-Headers");s.setStatus(204);return;
  }
  if(r.getMethod().equals("POST")&&origin==null){reject(r,s,403);return;}
  if(origin!=null)cors(s,origin);
  if(!policy.allowedOrigins().contains(policy.effectiveOrigin(r))){reject(r,s,403);return;}
  try{CookieValues.unique(r,policy.csrfCookieName());}catch(IllegalArgumentException e){reject(r,s,403);return;}
  if(Collections.list(r.getHeaders("X-XSRF-TOKEN")).size()>1){reject(r,s,403);return;}
  chain.doFilter(r,s);
 }
 private static void cors(HttpServletResponse s,String origin){s.setHeader("Access-Control-Allow-Origin",origin);s.setHeader("Access-Control-Allow-Credentials","true");s.addHeader("Vary","Origin");}
}
