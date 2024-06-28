package com.vmware.tanzu.se.simple;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ClientCertHeaderFilter extends OncePerRequestFilter {

  // Old pre-jakarta header name
  // private static final String JAVAX_SERVLET_REQUEST_X509_CERTIFICATE =
  // "javax.servlet.request.X509Certificate";
  private static final String JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE = "jakarta.servlet.request.X509Certificate";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (StringUtils.hasText(request.getHeader(JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE))) {
      filterChain.doFilter(request, response);
    } else {
      response.sendError(HttpStatus.FORBIDDEN.value());
    }

  }

}
