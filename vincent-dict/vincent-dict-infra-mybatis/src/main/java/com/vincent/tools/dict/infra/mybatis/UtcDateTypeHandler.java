package com.vincent.tools.dict.infra.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class UtcDateTypeHandler extends BaseTypeHandler<Date> {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, utcFormat().format(parameter));
    }

    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseUtc(rs.getString(columnName));
    }

    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseUtc(rs.getString(columnIndex));
    }

    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseUtc(cs.getString(columnIndex));
    }

    private static SimpleDateFormat utcFormat() {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN);
        format.setTimeZone(UTC);
        format.setLenient(false);
        return format;
    }

    private static Date parseUtc(String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return utcFormat().parse(normalizeFractionalSeconds(value));
        } catch (ParseException exception) {
            throw new SQLException("invalid DATETIME value: " + value, exception);
        }
    }

    private static String normalizeFractionalSeconds(String value) {
        int dot = value.indexOf('.');
        if (dot < 0) {
            return value + ".000";
        }
        int end = dot + 4;
        if (value.length() >= end) {
            return value.substring(0, end);
        }
        StringBuilder normalized = new StringBuilder(value);
        while (normalized.length() < end) {
            normalized.append('0');
        }
        return normalized.toString();
    }
}
