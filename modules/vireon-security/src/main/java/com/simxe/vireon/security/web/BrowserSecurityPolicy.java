package com.simxe.vireon.security.web;
import java.net.URI;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
/** Immutable explicit host policy, independent of identity/account models. */
public record BrowserSecurityPolicy(Set<String> allowedOrigins,String csrfCookieName,boolean loopbackTest){
 public BrowserSecurityPolicy {
  allowedOrigins=Set.copyOf(Objects.requireNonNull(allowedOrigins));
  if(allowedOrigins.isEmpty()||csrfCookieName==null||!csrfCookieName.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("BROWSER_SECURITY_CONFIGURATION_REQUIRED");
  for(String value:allowedOrigins){
   URI u;try{u=URI.create(value);}catch(IllegalArgumentException e){throw new IllegalArgumentException("INVALID_BROWSER_ORIGIN");}
   if(u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!u.getRawPath().isEmpty()||value.contains("*"))throw new IllegalArgumentException("INVALID_BROWSER_ORIGIN");
   if(loopbackTest){if(!"http".equals(u.getScheme())||!loopback(u.getHost()))throw new IllegalArgumentException("LOOPBACK_ORIGIN_REQUIRED");}
   else if(!"https".equals(u.getScheme()))throw new IllegalArgumentException("HTTPS_ORIGIN_REQUIRED");
  }
 }
 public static boolean loopback(String host){return Set.of("127.0.0.1","[::1]","::1").contains(host);}
 public boolean transportAllowed(HttpServletRequest r){return loopbackTest?loopback(r.getRemoteAddr())&&loopback(r.getServerName()):r.isSecure();}
 public String effectiveOrigin(HttpServletRequest r){
  String origin=r.getHeader("Origin");if(origin!=null)return origin;
  int port=r.getServerPort();return r.getScheme()+"://"+r.getServerName()+((port==80&&r.getScheme().equals("http"))||(port==443&&r.getScheme().equals("https"))?"":":"+port);
 }
}
