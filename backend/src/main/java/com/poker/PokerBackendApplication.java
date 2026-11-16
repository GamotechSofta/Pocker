package com.poker;

import com.poker.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PokerBackendApplication {

	public static void main(String[] args) {
		DotEnvLoader.load();
		SpringApplication.run(PokerBackendApplication.class, args);
	}

}
