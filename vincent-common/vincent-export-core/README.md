# vincent-export-core

纯 Java 库：基于 EasyExcel 的 Excel 流式读写（Java 8 / Boot 2.2 兼容）。

## API

```java
VincentExcelExporter.write(outputStream, OrderRow.class, rows);
VincentExcelExporter.read(inputStream, OrderRow.class, row -> process(row));
```

DTO 使用 EasyExcel `@ExcelProperty` 标注列名。

## 验收

```bash
mvn -P '!jdk-17' test -pl vincent-common/vincent-export-core
```
