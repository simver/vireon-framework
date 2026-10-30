package com.simxe.vireon.web.security;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
/** Explicit host adapter: Tomcat rejects CONNECT before a servlet context exists.
 * Normalize only unsupported methods at the engine boundary; servlet filters still own
 * route methods, overrides and OPTIONS. Does not enable TRACE or CONNECT on the connector. */
public final class TomcatHttpMethodPolicyValve extends ValveBase {
 public TomcatHttpMethodPolicyValve(){super(true);}
 @Override public void invoke(Request request,Response response)throws IOException,ServletException{
  if(HttpMethodPolicyFilter.supportedMethod(request.getMethod())){getNext().invoke(request,response);return;}
  if(response.isError()){response.reset();response.setSuspended(false);}
  HttpMethodPolicyFilter.rejectUnsupportedMethod(request,response);
 }
}
