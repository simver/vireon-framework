package com.simxe.vireon.security.web;
import jakarta.servlet.http.*;
import java.util.*;
public final class CookieValues {
 private CookieValues(){}
 public static Optional<String> unique(HttpServletRequest request,String name){
  List<String> values=new ArrayList<>();var headers=request.getHeaders("Cookie");boolean raw=false;
  while(headers.hasMoreElements()){raw=true;for(String item:headers.nextElement().split(";")){String[] pair=item.trim().split("=",2);if(pair[0].equals(name))values.add(pair.length==2?pair[1]:"");}}
  if(!raw&&request.getCookies()!=null)for(Cookie c:request.getCookies())if(c.getName().equals(name))values.add(c.getValue());
  if(values.size()>1)throw new IllegalArgumentException("DUPLICATE_COOKIE");
  return values.isEmpty()?Optional.empty():Optional.of(values.getFirst());
 }
}
