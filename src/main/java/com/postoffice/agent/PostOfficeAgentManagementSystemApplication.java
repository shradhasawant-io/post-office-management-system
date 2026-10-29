package com.postoffice.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PostOfficeAgentManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(PostOfficeAgentManagementSystemApplication.class, args);
		System.out.println("Hello");
	}

}
