package com.ex.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class BarcodeService {

    private static final Map<Character, String> CODE_39 = Map.ofEntries(
            Map.entry('0', "101001101101"),
            Map.entry('1', "110100101011"),
            Map.entry('2', "101100101011"),
            Map.entry('3', "110110010101"),
            Map.entry('4', "101001101011"),
            Map.entry('5', "110100110101"),
            Map.entry('6', "101100110101"),
            Map.entry('7', "101001011011"),
            Map.entry('8', "110100101101"),
            Map.entry('9', "101100101101"),
            Map.entry('A', "110101001011"),
            Map.entry('B', "101101001011"),
            Map.entry('C', "110110100101"),
            Map.entry('D', "101011001011"),
            Map.entry('E', "110101100101"),
            Map.entry('F', "101101100101"),
            Map.entry('G', "101010011011"),
            Map.entry('H', "110101001101"),
            Map.entry('I', "101101001101"),
            Map.entry('J', "101011001101"),
            Map.entry('K', "110101010011"),
            Map.entry('L', "101101010011"),
            Map.entry('M', "110110101001"),
            Map.entry('N', "101011010011"),
            Map.entry('O', "110101101001"),
            Map.entry('P', "101101101001"),
            Map.entry('Q', "101010110011"),
            Map.entry('R', "110101011001"),
            Map.entry('S', "101101011001"),
            Map.entry('T', "101011011001"),
            Map.entry('U', "110010101011"),
            Map.entry('V', "100110101011"),
            Map.entry('W', "110011010101"),
            Map.entry('X', "100101101011"),
            Map.entry('Y', "110010110101"),
            Map.entry('Z', "100110110101"),
            Map.entry('-', "100101011011"),
            Map.entry('.', "110010101101"),
            Map.entry(' ', "100110101101"),
            Map.entry('*', "100101101101"));

    public String code39DataUri(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.isBlank()
                || normalized.chars().mapToObj(valueChar -> (char) valueChar)
                        .anyMatch(character -> !CODE_39.containsKey(character)
                                || character == '*')) {
            throw new IllegalArgumentException("바코드로 변환할 수 없는 LOT 번호입니다.");
        }

        StringBuilder modules = new StringBuilder();
        String encodedValue = "*" + normalized + "*";
        for (int index = 0; index < encodedValue.length(); index++) {
            if (index > 0) {
                modules.append('0');
            }
            modules.append(CODE_39.get(encodedValue.charAt(index)));
        }

        int moduleWidth = 2;
        int margin = 12;
        int width = modules.length() * moduleWidth + margin * 2;
        StringBuilder bars = new StringBuilder();
        for (int index = 0; index < modules.length(); index++) {
            if (modules.charAt(index) == '1') {
                bars.append("<rect x=\"")
                        .append(margin + index * moduleWidth)
                        .append("\" y=\"8\" width=\"")
                        .append(moduleWidth)
                        .append("\" height=\"46\"/>");
            }
        }

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="78" viewBox="0 0 %d 78">
                  <rect width="100%%" height="100%%" fill="white"/>
                  <g fill="#111">%s</g>
                  <text x="50%%" y="70" text-anchor="middle"
                        font-family="Arial, sans-serif" font-size="11"
                        letter-spacing="1">%s</text>
                </svg>
                """.formatted(width, width, bars, normalized);

        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(
                        svg.getBytes(StandardCharsets.UTF_8));
    }
}
