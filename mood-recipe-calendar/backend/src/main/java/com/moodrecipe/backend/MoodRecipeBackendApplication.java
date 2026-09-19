package com.moodrecipe.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoodRecipeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoodRecipeBackendApplication.class, args);
	}

}
