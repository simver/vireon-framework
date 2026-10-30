package com.simxe.vireon.web.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

/** Installed before security; no IAM fields, path prefix policy or state-changing GET. */
public final class HttpMethodPolicyFilter extends OncePerRequestFilter {
 private final HttpRouteCatalog routes;
 public HttpMethodPolicyFilter(HttpRouteCatalog routes){this.routes=Objects.requireNonNull(routes);}
 public static boolean supportedMethod(String method){return Set.of("GET","POST","OPTIONS").contains(method);}
 public static String path(HttpServletRequest r){return r.getRequestURI().substring(r.getContextPath().length());}
 public static void error(HttpServletRequest request,HttpServletResponse response,int status,String code)throws IOException{
  writeError(response,status,code,traceValue(request));
 }
 /** Safe even before a servlet context exists: never fires request attribute listeners. */
 public static void rejectUnsupportedMethod(HttpServletRequest request,HttpServletResponse response)throws IOException{
  String trace=traceValue(request);if(trace==null||trace.equals("00000000000000000000000000000000"))trace=UUID.randomUUID().toString().replace("-","");
  response.setHeader(com.simxe.vireon.core.http.VireonHeaders.TRACE_ID,trace);writeError(response,405,"METHOD_NOT_ALLOWED",trace);
 }
 private static void writeError(HttpServletResponse response,int status,String code,String trace)throws IOException{
  response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
  response.getWriter().write("{\"code\":\"VIREON.HTTP."+code+"\",\"message\":\""+code+"\",\"data\":null,\"traceId\":"+(trace==null?"null":"\""+trace+"\"")+"}");
 }
 private static String traceValue(HttpServletRequest r){Object value=r.getAttribute(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID);return value instanceof String text&&text.matches("[0-9a-f]{32}")?text:null;}
 @Override protected boolean shouldNotFilterErrorDispatch(){return false;}
 @Override protected void doFilterNestedErrorDispatch(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{doFilterInternal(r,s,chain);}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  Object trace=r.getAttribute(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID);
  if(!(trace instanceof String value)||!value.matches("[0-9a-f]{32}")||value.equals("00000000000000000000000000000000"))r.setAttribute(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID,UUID.randomUUID().toString().replace("-",""));
  s.setHeader(com.simxe.vireon.core.http.VireonHeaders.TRACE_ID,(String)r.getAttribute(com.simxe.vireon.core.http.VireonRequestAttributes.TRACE_ID));
  String method=r.getMethod();
  boolean override=List.of("X-HTTP-Method-Override","X-Method-Override","X-HTTP-Method").stream().anyMatch(h->r.getHeader(h)!=null);
  String query=r.getQueryString();
  try {if(query!=null)for(String part:query.split("&"))if("_method".equals(UriUtils.decode(part.split("=",2)[0],StandardCharsets.UTF_8)))override=true;}
  catch(IllegalArgumentException failure){error(r,s,400,"INVALID_INPUT");return;}
  if(override || !supportedMethod(method)){rejectUnsupportedMethod(r,s);return;}
  if("OPTIONS".equals(method)){
   if(r.getHeader("Origin")==null||r.getHeader("Access-Control-Request-Method")==null)error(r,s,405,"METHOD_NOT_ALLOWED");
   else chain.doFilter(r,s);return;
  }
  var allowed=routes.methods(path(r));
  if(allowed!=null&&!allowed.contains(method)){error(r,s,405,"METHOD_NOT_ALLOWED");return;}
  if("GET".equals(method)&&(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null)){error(r,s,400,"INVALID_INPUT");return;}
  chain.doFilter(r,s);
 }
}
