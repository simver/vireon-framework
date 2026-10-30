package com.simxe.vireon.web.security;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
/** Lazy access avoids premature MVC/security bean initialization and uses the host's actual mappings. */
public final class MvcHttpRouteCatalog implements HttpRouteCatalog {
 private final Supplier<Collection<RequestMappingHandlerMapping>> mappings;
 public MvcHttpRouteCatalog(Supplier<Collection<RequestMappingHandlerMapping>> mappings){this.mappings=mappings;}
 public Set<String> methods(String path){
  Set<String> methods=new HashSet<>();boolean found=false;
  for(var mapping:mappings.get())for(var info:mapping.getHandlerMethods().keySet()){
   Set<String> patterns=info.getPatternValues();
   if(patterns.stream().anyMatch(p->PathPatternParser.defaultInstance.parse(p).matches(PathContainer.parsePath(path)))){
    found=true;var declared=info.getMethodsCondition().getMethods();
    if(declared.isEmpty())methods.addAll(Set.of("GET","POST"));else declared.forEach(m->methods.add(m.name()));
   }
  }
  return found?Set.copyOf(methods):null;
 }
}
