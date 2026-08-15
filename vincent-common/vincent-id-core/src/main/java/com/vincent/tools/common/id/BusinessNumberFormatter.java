package com.vincent.tools.common.id;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class BusinessNumberFormatter {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private BusinessNumberFormatter() {
    }

    public static String format(String template, long sequence) {
        return format(template, sequence, LocalDate.now(ZoneOffset.UTC));
    }

    public static String format(String template, long sequence, LocalDate date) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(date, "date");
        String formatted = template;
        formatted = formatted.replace("{date}", BASIC_DATE.format(date));
        formatted = formatted.replace("{seq}", String.valueOf(sequence));
        return formatted;
    }
}
