package com.vmware.tanzu.se.simple;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    //Allow access to health endpoint
    http.authorizeHttpRequests((requests) -> requests.requestMatchers("/actuator/health").permitAll())
      .authorizeHttpRequests((requests) -> requests.requestMatchers("/**").authenticated())
      .x509(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public UserDetailsService simpleUserDetailsService() {
    return new SimpleUserDetailsService();
  }
}

