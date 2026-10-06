package com.appointmentplatform;

import org.springframework.boot.SpringApplication;

public class TestAppointmentPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(AppointmentPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
