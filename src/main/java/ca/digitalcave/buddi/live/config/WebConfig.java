package ca.digitalcave.buddi.live.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(final ViewControllerRegistry registry) {
		registry.addRedirectViewController("/", "/index");
		registry.addRedirectViewController("/index.html", "/index");
		registry.addRedirectViewController("/buddilive", "/index");
		registry.addRedirectViewController("/favicon.ico", "/img/logo-title-small.png");
	}

	@Override
	public void addResourceHandlers(final ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/authentication/**")
			.addResourceLocations("classpath:ca/digitalcave/moss/auth/resource/ui/extjs/");
	}

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> jsonSuffixFilter() {
		final FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
											final FilterChain filterChain) throws ServletException, IOException {
				final String uri = request.getRequestURI();
				if (uri.endsWith(".json")) {
					final String stripped = uri.substring(0, uri.length() - 5);
					filterChain.doFilter(new HttpServletRequestWrapper(request) {
						@Override
						public String getRequestURI() { return stripped; }

						@Override
						public String getServletPath() { return stripped; }
					}, response);
				} else {
					filterChain.doFilter(request, response);
				}
			}
		});
		registration.setOrder(1);
		return registration;
	}
}
