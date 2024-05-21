package dev.duras.cogvalidatorjava.service;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Vector;

@Service
public class CogValidationService {

    public String validateGeoTIFF(String filePath) throws IOException {
        gdal.AllRegister();
        Dataset ds = gdal.Open(filePath);
        if (ds == null) {
            throw new IOException("Invalid file: " + gdal.GetLastErrorMsg());
        }

        if (!ds.GetDriver().getShortName().equals("GTiff")) {
            throw new IOException("The file is not a GeoTIFF");
        }

        int[] errors = new int[1];
        int[] warnings = new int[1];

        validate(ds, errors, warnings, filePath);

        StringBuilder result = new StringBuilder();
        if (warnings[0] > 0) {
            result.append("The following warnings were found:\n");
            for (int i = 0; i < warnings[0]; i++) {
                result.append(" - Warning ").append(i).append("\n");
            }
            result.append("\n");
        }
        if (errors[0] > 0) {
            result.append(filePath).append(" is NOT a valid cloud optimized GeoTIFF.\n");
            result.append("The following errors were found:\n");
            for (int i = 0; i < errors[0]; i++) {
                result.append(" - Error ").append(i).append("\n");
            }
            result.append("\n");
        } else {
            result.append(filePath).append(" is a valid cloud optimized GeoTIFF\n");
        }
        return result.toString();
    }

    private void validate(Dataset ds, int[] errors, int[] warnings, String filePath) throws IOException {
        Band mainBand = ds.GetRasterBand(1);
        int ovrCount = mainBand.GetOverviewCount();
        Vector<String> filelist = ds.GetFileList();
        if (filelist != null && !filelist.isEmpty()) {
            for (String file : filelist) {
                if (file.endsWith(".ovr")) {
                    errors[0]++;
                }
            }
        }

        if (mainBand.getXSize() > 512 || mainBand.getYSize() > 512) {
            int[] blockSize = new int[2];
            mainBand.GetBlockSize(blockSize, new int[1]);
            if (blockSize[0] == mainBand.getXSize() && blockSize[0] > 1024) {
                errors[0]++;
            }
            if (ovrCount == 0) {
                warnings[0]++;
            }
        }

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            FileChannel channel = file.getChannel();
            fullCheckBand(channel, "Main resolution image", mainBand, errors);
            if (mainBand.GetMaskFlags() == gdalconst.GMF_PER_DATASET) {
                fullCheckBand(channel, "Mask band of main resolution image", mainBand.GetMaskBand(), errors);
            }
            for (int i = 0; i < ovrCount; i++) {
                Band ovrBand = ds.GetRasterBand(1).GetOverview(i);
                fullCheckBand(channel, "Overview " + i, ovrBand, errors);
                if (ovrBand.GetMaskFlags() == gdalconst.GMF_PER_DATASET) {
                    fullCheckBand(channel, "Mask band of overview " + i, ovrBand.GetMaskBand(), errors);
                }
            }
        }
    }

    private void fullCheckBand(FileChannel channel, String bandName, Band band, int[] errors) throws IOException {
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
                        errors[0]++;
                    }

                    // Check leader size
                    if (byteCount > 4) {
                        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        channel.read(buffer, offset - 4);
                        buffer.flip();
                        int leaderSize = buffer.getInt();
                        if (leaderSize != byteCount) {
                            errors[0]++;
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
                            errors[0]++;
                        }
                    }
                }
                lastOffset = offset;
            }
        }
    }
}
