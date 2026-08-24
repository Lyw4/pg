package com.ex.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class QrCodeService {

    public String qrCodeDataUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "QR 코드로 변환할 값이 비어 있습니다.");
        }

        Map<EncodeHintType, Object> hints =
                new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    value.trim(), BarcodeFormat.QR_CODE, 1, 1, hints);
            StringBuilder modules = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        modules.append("<rect x=\"")
                                .append(x)
                                .append("\" y=\"")
                                .append(y)
                                .append("\" width=\"1\" height=\"1\"/>");
                    }
                }
            }
            String svg = """
                    <svg xmlns="http://www.w3.org/2000/svg"
                         width="%d" height="%d" viewBox="0 0 %d %d"
                         shape-rendering="crispEdges">
                      <rect width="100%%" height="100%%" fill="white"/>
                      <g fill="#111">%s</g>
                    </svg>
                    """.formatted(
                            matrix.getWidth(),
                            matrix.getHeight(),
                            matrix.getWidth(),
                            matrix.getHeight(),
                            modules);
            return "data:image/svg+xml;base64,"
                    + Base64.getEncoder().encodeToString(
                            svg.getBytes(StandardCharsets.UTF_8));
        } catch (WriterException exception) {
            throw new IllegalArgumentException(
                    "QR 코드를 생성할 수 없습니다.", exception);
        }
    }
}
