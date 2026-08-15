package com.vincent.tools.common.export;

import com.alibaba.excel.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VincentExcelExporterTest {
    @Test
    void writes_and_reads_rows() {
        List<SampleRow> rows = new ArrayList<SampleRow>();
        rows.add(new SampleRow("A001", "Alpha"));
        rows.add(new SampleRow("A002", "Beta"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VincentExcelExporter.write(output, SampleRow.class, rows);

        List<SampleRow> read = new ArrayList<SampleRow>();
        VincentExcelExporter.read(new ByteArrayInputStream(output.toByteArray()), SampleRow.class, read::add);

        assertThat(read).hasSize(2);
        assertThat(read.get(0).getCode()).isEqualTo("A001");
        assertThat(read.get(1).getName()).isEqualTo("Beta");
    }

    public static final class SampleRow {
        @ExcelProperty("code")
        private String code;

        @ExcelProperty("name")
        private String name;

        public SampleRow() {
        }

        public SampleRow(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
