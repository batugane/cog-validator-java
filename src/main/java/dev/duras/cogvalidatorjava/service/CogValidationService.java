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

import static dev.duras.cogvalidatorjava.util.Constants.*;

@Service
public class CogValidationService {

    // Check if a file is a (Geo)TIFF with cloud optimized compatible structure.
    public String validateGeoTIFF(String filePath) throws IOException {
        gdal.AllRegister();
        Dataset ds = gdal.Open(filePath);
        if (ds == null) {
            throw new IOException(ERROR_INVALID_FILE + gdal.GetLastErrorMsg());
        }

        if (!ds.GetDriver().getShortName().equals("GTiff")) {
            throw new IOException(ERROR_NOT_GEOTIFF);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validate(ds, errors, warnings, filePath);

        return buildResult(filePath, errors, warnings);
    }

    private void validate(Dataset ds, List<String> errors, List<String> warnings, String filePath) throws IOException {
        Band mainBand = ds.GetRasterBand(1);
        int ovrCount = mainBand.GetOverviewCount();
        ArrayList<String> fileList = new ArrayList<>(ds.GetFileList());

        checkExternalOverviews(fileList, errors);
        checkMainBand(mainBand, errors, warnings, ovrCount);

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            FileChannel channel = file.getChannel();
            validateBand(channel, "Main resolution image", mainBand, errors);
            validateMaskBand(channel, "Main resolution image", mainBand, errors);
            validateOverviews(channel, mainBand, ovrCount, errors);
        }
    }

    private void checkExternalOverviews(ArrayList<String> fileList, List<String> errors) {
        if (!fileList.isEmpty()) {
            for (String file : fileList) {
                if (file.endsWith(".ovr")) {
                    errors.add(ERROR_OVERVIEWS_EXTERNAL);
                }
            }
        }
    }

    private void checkMainBand(Band mainBand, List<String> errors, List<String> warnings, int ovrCount) {
        if (mainBand.getXSize() > 512 || mainBand.getYSize() > 512) {
            int[] blockSize = new int[2];
            mainBand.GetBlockSize(blockSize, new int[1]);
            if (blockSize[0] == mainBand.getXSize() && blockSize[0] > 1024) {
                errors.add(ERROR_TILE_SIZE);
            }
            if (ovrCount == 0) {
                warnings.add(WARNING_NO_OVERVIEWS);
            }
        }
    }

    private void validateBand(FileChannel channel, String bandName, Band band, List<String> errors) throws IOException {
        int[] blockXSize = new int[1];
        int[] blockYSize = new int[1];
        band.GetBlockSize(blockXSize, blockYSize);
        int yblocks = (band.getYSize() + blockYSize[0] - 1) / blockYSize[0];
        int xblocks = (band.getXSize() + blockXSize[0] - 1) / blockXSize[0];
        long lastOffset = 0;

        for (int y = 0; y < yblocks; y++) {
            for (int x = 0; x < xblocks; x++) {
                validateBlock(channel, bandName, band, errors, x, y, lastOffset);
            }
        }
    }

    private void validateBlock(FileChannel channel, String bandName, Band band, List<String> errors, int x, int y, long lastOffset) throws IOException {
        String offsetStr = band.GetMetadataItem("BLOCK_OFFSET_" + x + "_" + y, "TIFF");
        long offset = offsetStr != null ? Long.parseLong(offsetStr) : 0;
        String byteCountStr = band.GetMetadataItem("BLOCK_SIZE_" + x + "_" + y, "TIFF");
        long byteCount = byteCountStr != null ? Long.parseLong(byteCountStr) : 0;

        if (offset > 0) {
            if (offset < lastOffset) {
                errors.add(String.format("%s block (%d, %d) offset is less than previous block.", bandName, x, y));
            }

            checkLeaderSize(channel, bandName, errors, x, y, offset, byteCount);
            checkTrailerBytes(channel, bandName, errors, x, y, offset, byteCount);
        }
    }

    private void checkLeaderSize(FileChannel channel, String bandName, List<String> errors, int x, int y, long offset, long byteCount) throws IOException {
        if (byteCount > 4) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer, offset - 4);
            buffer.flip();
            int leaderSize = buffer.getInt();
            if (leaderSize != byteCount) {
                errors.add(String.format("%s block (%d, %d) leader size (%d) does not match byte count (%d).", bandName, x, y, leaderSize, byteCount));
            }
        }
    }

    private void checkTrailerBytes(FileChannel channel, String bandName, List<String> errors, int x, int y, long offset, long byteCount) throws IOException {
        if (byteCount >= 4) {
            ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer, offset + byteCount - 4);
            buffer.flip();
            int trailerBytes1 = buffer.getInt();
            int trailerBytes2 = buffer.getInt();
            if (trailerBytes1 != trailerBytes2) {
                errors.add(String.format("%s block (%d, %d) trailer bytes do not match.", bandName, x, y));
            }
        }
    }

    private void validateMaskBand(FileChannel channel, String bandName, Band mainBand, List<String> errors) throws IOException {
        if (mainBand.GetMaskFlags() == gdalconstConstants.GMF_PER_DATASET) {
            Band maskBand = mainBand.GetMaskBand();
            validateBand(channel, "Mask band of " + bandName, maskBand, errors);
        }
    }

    private void validateOverviews(FileChannel channel, Band mainBand, int ovrCount, List<String> errors) throws IOException {
        for (int i = 0; i < ovrCount; i++) {
            Band ovrBand = mainBand.GetOverview(i);
            validateBand(channel, "Overview " + i, ovrBand, errors);
            validateMaskBand(channel, "Overview " + i, ovrBand, errors);
        }
    }

    private String buildResult(String filePath, List<String> errors, List<String> warnings) {
        StringBuilder result = new StringBuilder();
        if (!warnings.isEmpty()) {
            result.append(WARNING_FOUND);
            for (String warning : warnings) {
                result.append(" - ").append(warning).append("\n");
            }
            result.append("\n");
        }
        if (!errors.isEmpty()) {
            result.append(filePath).append(" ").append(INVALID_COG).append("\n");
            result.append(ERROR_FOUND);
            for (String error : errors) {
                result.append(" - ").append(error).append("\n");
            }
            result.append("\n");
        } else {
            result.append(filePath).append(" ").append(VALID_COG).append("\n");
        }
        return result.toString();
    }
}
