package ca.digitalcave.buddi.live.config;

import ca.digitalcave.buddi.live.security.CookieAuthenticationFilter;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public CookieAuthenticationFilter cookieAuthenticationFilter(final AuthenticationHelper authenticationHelper) {
		return new CookieAuthenticationFilter(authenticationHelper);
	}

	@Bean
	public SecurityFilterChain filterChain(final HttpSecurity http, final CookieAuthenticationFilter cookieAuthFilter) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.addFilterBefore(cookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests(auth -> auth
				.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC).permitAll()
				.requestMatchers("/", "/index", "/index.html",
					"/authentication/**",
					"/stores/**",
					"/donation-completed",
					"/css/**", "/img/**", "/lib/**", "/doc/**", "/buddilive/**").permitAll()
				.requestMatchers("/data/**").authenticated()
				.anyRequest().permitAll()
			)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(401);
					response.setContentType("application/json");
					response.getWriter().write("{\"success\": false}");
				})
			);

		return http.build();
	}
}
