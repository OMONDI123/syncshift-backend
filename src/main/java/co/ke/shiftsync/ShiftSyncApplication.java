package co.ke.shiftsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ShiftSync — multi-location staff scheduling platform for Coastal Eats.
 * See /docs (Swagger UI) once running, and README.md for role logins,
 * assumptions and known limitations.
 */
@SpringBootApplication
@EnableScheduling
public class ShiftSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShiftSyncApplication.class, args);
	}

}
