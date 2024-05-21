package dev.duras.cogvalidatorjava.service;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Service
public class CogValidationService {

    //Check if a file is a (Geo)TIFF with cloud optimized compatible structure.
    public String validateGeoTIFF(String filePath) throws IOException {
        gdal.AllRegister();
        Dataset ds = gdal.Open(filePath);
        if (ds == null) {
            throw new IOException("Invalid file: " + gdal.GetLastErrorMsg());
        }

        if (!ds.GetDriver().getShortName().equals("GTiff")) {
            throw new IOException("The file is not a GeoTIFF");
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validate(ds, errors, warnings, filePath);

        StringBuilder result = new StringBuilder();
        if (!warnings.isEmpty()) {
            result.append("The following warnings were found:\n");
            for (String warning : warnings) {
                result.append(" - ").append(warning).append("\n");
            }
            result.append("\n");
        }
        if (!errors.isEmpty()) {
            result.append(filePath).append(" is NOT a valid cloud optimized GeoTIFF.\n");
            result.append("The following errors were found:\n");
            for (String error : errors) {
                result.append(" - ").append(error).append("\n");
            }
            result.append("\n");
        } else {
            result.append(filePath).append(" is a valid cloud optimized GeoTIFF\n");
        }
        return result.toString();
    }

    private void validate(Dataset ds, List<String> errors, List<String> warnings, String filePath) throws IOException {
        Band mainBand = ds.GetRasterBand(1);
        int ovrCount = mainBand.GetOverviewCount();
        ArrayList<String> fileList = new ArrayList<>(ds.GetFileList());
        if (fileList != null && !fileList.isEmpty()) {
            for (String file : fileList) {
                if (file.endsWith(".ovr")) {
                    errors.add("Overviews found in external .ovr file. They should be internal.");
                }
            }
        }

        if (mainBand.getXSize() > 512 || mainBand.getYSize() > 512) {
            int[] blockSize = new int[2];
            mainBand.GetBlockSize(blockSize, new int[1]);
            if (blockSize[0] == mainBand.getXSize() && blockSize[0] > 1024) {
                errors.add("Tile size exceeds the image width.");
            }
            if (ovrCount == 0) {
                warnings.add("No overviews found for large image.");
            }
        }

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            FileChannel channel = file.getChannel();
            fullCheckBand(channel, "Main resolution image", mainBand, errors);
            if (mainBand.GetMaskFlags() == gdalconstConstants.GMF_PER_DATASET) {
                fullCheckBand(channel, "Mask band of main resolution image", mainBand.GetMaskBand(), errors);
            }
            for (int i = 0; i < ovrCount; i++) {
                Band ovrBand = ds.GetRasterBand(1).GetOverview(i);
                fullCheckBand(channel, "Overview " + i, ovrBand, errors);
                if (ovrBand.GetMaskFlags() == gdalconstConstants.GMF_PER_DATASET) {
                    fullCheckBand(channel, "Mask band of overview " + i, ovrBand.GetMaskBand(), errors);
                }
            }
        }
    }

    private void fullCheckBand(FileChannel channel, String bandName, Band band, List<String> errors) throws IOException {
        int[] blockXSize = new int[1];
        int[] blockYSize = new int[1];
        band.GetBlockSize(blockXSize, blockYSize);
        int yblocks = (band.getYSize() + blockYSize[0] - 1) / blockYSize[0];
        int xblocks = (band.getXSize() + blockXSize[0] - 1) / blockXSize[0];
        long lastOffset = 0;

        for (int y = 0; y < yblocks; y++) {
            for (int x = 0; x < xblocks; x++) {
                String offsetStr = band.GetMetadataItem("BLOCK_OFFSET_" + x + "_" + y, "TIFF");
                long offset = offsetStr != null ? Long.parseLong(offsetStr) : 0;
                String byteCountStr = band.GetMetadataItem("BLOCK_SIZE_" + x + "_" + y, "TIFF");
                long byteCount = byteCountStr != null ? Long.parseLong(byteCountStr) : 0;

                if (offset > 0) {
                    if (offset < lastOffset) {
                        errors.add(bandName + " block (" + x + ", " + y + ") offset is less than previous block.");
                    }

                    // Check leader size
                    if (byteCount > 4) {
                        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        channel.read(buffer, offset - 4);
                        buffer.flip();
                        int leaderSize = buffer.getInt();
                        if (leaderSize != byteCount) {
                            errors.add(bandName + " block (" + x + ", " + y + ") leader size (" + leaderSize + ") does not match byte count (" + byteCount + ").");
                        }
                    }

                    // Check trailer bytes
                    if (byteCount >= 4) {
                        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                        channel.read(buffer, offset + byteCount - 4);
                        buffer.flip();
                        int trailerBytes1 = buffer.getInt();
                        int trailerBytes2 = buffer.getInt();
                        if (trailerBytes1 != trailerBytes2) {
                            errors.add(bandName + " block (" + x + ", " + y + ") trailer bytes do not match.");
                        }
                    }
                }
                lastOffset = offset;
            }
        }
    }
}
