package dev.duras.cogvalidatorjava;

import dev.duras.cogvalidatorjava.service.CogValidationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CogValidationServiceTest {
    private CogValidationService validationService;

    @BeforeAll
    void init() {
        this.validationService = new CogValidationService();
    }

    @Test
    void testValidateGeoTIFF_ValidFile() throws IOException {

        String result = validationService.validateGeoTIFF("src/test/resources/london_jpeg75_cog.tif");

        assertTrue(result.contains("is a valid cloud optimized GeoTIFF"));
    }

    @Test
    void testValidateGeoTIFF_InvalidFile() throws IOException {

        String result = validationService.validateGeoTIFF("src/test/resources/f41078e1.tif");

        assertTrue(result.contains("is NOT a valid cloud optimized GeoTIFF"));
    }
}
