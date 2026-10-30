package com.simxe.vireon.security.web;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.*;
/** Masked header only: no raw-cookie shortcut and no request-parameter fallback. */
public final class HeaderCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
 private final XorCsrfTokenRequestAttributeHandler xor=new XorCsrfTokenRequestAttributeHandler();
 public void handle(HttpServletRequest r,HttpServletResponse s,Supplier<CsrfToken> token){xor.handle(r,s,token);}
 public String resolveCsrfTokenValue(HttpServletRequest r,CsrfToken token){
  var values=Collections.list(r.getHeaders(token.getHeaderName()));if(values.size()!=1||values.getFirst().isBlank())return null;
  return xor.resolveCsrfTokenValue(r,token);
 }
}
