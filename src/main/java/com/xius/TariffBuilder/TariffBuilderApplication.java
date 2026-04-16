package com.xius.TariffBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TariffBuilderApplication {

	private static final Logger logger =
			LoggerFactory.getLogger(TariffBuilderApplication.class);

	public static void main(String[] args) {

		logger.info("Starting TariffBuilderApplication...");

		SpringApplication.run(
				TariffBuilderApplication.class,
				args);

		logger.info("TariffBuilderApplication started successfully");
	}
}