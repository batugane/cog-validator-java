# Cloud Optimized GeoTIFF Validator

Cloud Optimized GeoTIFF Validator is a Spring Boot application designed to validate Cloud Optimized GeoTIFF (COG) files.
It ensures that the files are structured correctly and meet the COG specifications.

## Building the Project

Clone the repository and build the project using Maven.

```sh
git clone https://github.com/batugane/cog-validator-java.git
cd cog-validator-java
mvn install
```

## Running the Application

After building the project, you can run the application with the following command:

```sh
java -jar target/cog-validator-java-0.0.1.jar /path/to/your/file.tif
```

Replace `/path/to/your/file.tif` with the path to the GeoTIFF file you want to validate.

## Example Usage

```sh
java -jar cog-validator-java-0.0.1.jar example.tif
```

## Output

The application will output whether the file is a valid Cloud Optimized GeoTIFF and list any errors or warnings found
during validation.

## Acknowledgements

This project was inspired by [cog_validator](https://github.com/rouault/cog_validator) by Even Rouault.
