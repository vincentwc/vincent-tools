package com.vincent.tools.common.export;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class VincentExcelExporter {
    private VincentExcelExporter() {
    }

    public static <T> void write(OutputStream output, Class<T> headClass, Iterable<T> rows) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(headClass, "headClass");
        Objects.requireNonNull(rows, "rows");
        EasyExcel.write(output, headClass).sheet().doWrite(toList(rows));
    }

    public static <T> void read(InputStream input, Class<T> headClass, Consumer<T> rowConsumer) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(headClass, "headClass");
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        EasyExcel.read(input, headClass, new RowConsumerListener<T>(rowConsumer)).sheet().doRead();
    }

    private static <T> List<T> toList(Iterable<T> rows) {
        if (rows instanceof List) {
            return (List<T>) rows;
        }
        List<T> list = new ArrayList<T>();
        Iterator<T> iterator = rows.iterator();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    private static final class RowConsumerListener<T> implements ReadListener<T> {
        private final Consumer<T> rowConsumer;

        RowConsumerListener(Consumer<T> rowConsumer) {
            this.rowConsumer = rowConsumer;
        }

        @Override
        public void invoke(T data, AnalysisContext context) {
            rowConsumer.accept(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }
    }
}
