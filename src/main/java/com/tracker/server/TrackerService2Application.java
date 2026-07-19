package com.tracker.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrackerService2Application {

	public static void main(String[] args) {
		SpringApplication.run(TrackerService2Application.class, args);
	}

}
