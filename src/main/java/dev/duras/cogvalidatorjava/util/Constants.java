package dev.duras.cogvalidatorjava.util;

public class Constants {

    public static final String ERROR_OVERVIEWS_EXTERNAL = "Overviews found in external .ovr file. They should be internal.";
    public static final String ERROR_TILE_SIZE = "Tile size exceeds the image width.";
    public static final String WARNING_NO_OVERVIEWS = "No overviews found for large image.";
    public static final String ERROR_NOT_GEOTIFF = "The file is not a GeoTIFF";
    public static final String ERROR_INVALID_FILE = "Invalid file: ";
    public static final String VALID_COG = "is a valid cloud optimized GeoTIFF";
    public static final String INVALID_COG = "is NOT a valid cloud optimized GeoTIFF.";
    public static final String WARNING_FOUND = "The following warnings were found:\n";
    public static final String ERROR_FOUND = "The following errors were found:\n";

    private Constants() {
    }
}
