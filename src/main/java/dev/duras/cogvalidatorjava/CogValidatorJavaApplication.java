package dev.duras.cogvalidatorjava;

import dev.duras.cogvalidatorjava.service.CogValidationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class CogValidatorJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CogValidatorJavaApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(CogValidationService validationService) {
        return args -> {
            if (args.length < 1) {
                System.out.println("Usage: java -jar cog-validator-java-0.0.1.jar <file_path>");
                System.exit(1);
            }

            String filePath = args[0];

            try {
                String result = validationService.validateGeoTIFF(filePath);
                System.out.println(result);
                System.exit(1);
            } catch (IOException e) {
                System.err.println("Error validating Cloud Optimized GeoTIFF: " + e.getMessage());
                System.exit(0);
            }
        };
    }

}
