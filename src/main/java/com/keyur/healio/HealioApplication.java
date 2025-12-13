package com.keyur.healio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealioApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealioApplication.class, args);
	}

}
