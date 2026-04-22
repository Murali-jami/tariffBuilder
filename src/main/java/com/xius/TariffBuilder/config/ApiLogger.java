package com.xius.TariffBuilder.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class ApiLogger extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ApiLogger.class);

	/* ignore UI resources */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {

		String uri = request.getRequestURI();

		return uri.contains(".css") || uri.contains(".js") || uri.contains(".png") || uri.contains(".jpg")
				|| uri.contains(".jpeg") || uri.contains(".gif") || uri.contains(".ico") || uri.contains("/images/")
				|| uri.contains("/css/") || uri.contains("/js/") || uri.contains("/builder")
				|| uri.contains("/loginform");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String traceId = UUID.randomUUID().toString();

		MDC.put("traceId", traceId);

		long startTime = System.currentTimeMillis();

		ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, 1024 * 1024);

		ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

		try {

			log.info("------------ handleRequest ------------");

			log.info(req.getMethod());

			log.info(req.getRequestURI());

			log.info(request.getRemoteAddr());

			filterChain.doFilter(req, res);

		} finally {

			long duration = System.currentTimeMillis() - startTime;

			String requestBody = new String(req.getContentAsByteArray(), StandardCharsets.UTF_8);

			String responseBody = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);

			if (!requestBody.isBlank()) {

				log.info("Request Payload {}", requestBody);
			}

			log.info(String.valueOf(res.getStatus()));

			String contentType = res.getContentType();

			if (!responseBody.isBlank()) {

			    if (contentType != null && contentType.contains("application/json")) {
			        log.info("Response Payload {}", responseBody);

			    } else if (contentType != null && contentType.contains("text/html")) {
			        log.info("HTML response returned (status={})", res.getStatus());

			    } else {
			        log.info("Response Status {}", res.getStatus());
			    }
			}

			log.info("Execution time {} ms", duration);

			log.info("------------ handleResponse ------------");

			res.copyBodyToResponse();

			MDC.clear();
		}
	}
}