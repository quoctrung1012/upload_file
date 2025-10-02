package com.upload_file.service;

import com.upload_file.entity.FileDB;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PoiOfficeService {
  private static String excelTemplateCache = null;
  private static final Logger logger = LoggerFactory.getLogger(PoiOfficeService.class);

  @Autowired
  private FileStorageService fileStorageService;

  /**
   * Chuyển đổi file Office thành HTML để preview
   */
  public String convertOfficeToHtml(String fileId) throws Exception {
    FileDB fileDB = fileStorageService.getFile(fileId);

    byte[] fileData = getFileData(fileDB);
    String contentType = fileDB.getType();
    String fileName = fileDB.getName();

    logger.info("Converting Office file to HTML: {} ({})", fileName, contentType);

    try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
      return switch (contentType) {
        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> convertXlsxToHtml(inputStream, fileName);
        case "application/vnd.ms-excel" -> convertXlsToHtml(inputStream, fileName);
        default -> throw new UnsupportedOperationException("Unsupported file type: " + contentType);
      };
    }
  }
  // ============= UTILITY METHODS =============

  private byte[] getFileData(FileDB fileDB) throws Exception {
    if (fileDB.getPath() != null && !fileDB.getPath().equals("(db)")) {
      // File stored on filesystem
      Path filePath = Paths.get(fileDB.getPath());
      if (!Files.exists(filePath)) {
        throw new FileNotFoundException("File not found: " + fileDB.getPath());
      }
      return Files.readAllBytes(filePath);
    } else {
      // File stored in database
      byte[] data = fileDB.getData();
      if (data == null) {
        throw new IllegalStateException("File data is null");
      }
      return data;
    }
  }

  /**
   * Kiểm tra xem file có phải là Office document không
   */
  public boolean isOfficeDocument(String contentType) {
    return contentType != null && (
            contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
            contentType.equals("application/vnd.ms-excel")
    );
  }

  /**
   * Load HTML template từ file
   */
  private String loadExcelTemplate() {
    if (excelTemplateCache == null) {
      try {
        ClassPathResource resource = new ClassPathResource("templates/excel-template.html");
        excelTemplateCache = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        logger.info("Excel template loaded successfully");
      } catch (IOException e) {
        logger.error("Failed to load Excel template", e);
        excelTemplateCache = createBasicTemplate();
      }
    }
    return excelTemplateCache;
  }

  /**
   * Tạo template cơ bản nếu không load được file
   */
  private String createBasicTemplate() {
    return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>{{TITLE}}</title>
            <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap-icons/1.10.0/font/bootstrap-icons.min.css" rel="stylesheet">
            <style>
                body { background-color: #f8f9fa; }
                .main-header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 2rem 0; }
                .sheet-tabs { background: white; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                .table-container { margin: 1.5rem; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            </style>
        </head>
        <body>
            <div class="main-header">
                <div class="container text-center">
                    <h1><i class="bi bi-file-earmark-spreadsheet me-3"></i>{{TITLE}}</h1>
                    <p class="lead">Excel Document Preview</p>
                </div>
            </div>
            <div class="container-fluid">
                <div class="sheet-tabs">
                    {{TAB_NAVIGATION}}
                    {{TAB_CONTENT}}
                </div>
            </div>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/js/bootstrap.bundle.min.js"></script>
        </body>
        </html>
        """;
  }

  // ============= EXCEL CONVERSION =============

  public String convertXlsxToHtml(InputStream inputStream, String fileName) throws Exception {
    try (Workbook workbook = new XSSFWorkbook(inputStream)) {
      return convertExcelToHtml(workbook, fileName);
    }
  }

  public String convertXlsToHtml(InputStream inputStream, String fileName) throws Exception {
    try (Workbook workbook = new HSSFWorkbook(inputStream)) {
      return convertExcelToHtml(workbook, fileName);
    }
  }

  private String convertExcelToHtml(Workbook workbook, String fileName) throws Exception {
    String template = loadExcelTemplate();

    // Tạo navigation tabs
    StringBuilder tabNavigation = new StringBuilder();
    tabNavigation.append("<ul class='nav nav-tabs' id='excelTabs' role='tablist'>");

    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
      Sheet sheet = workbook.getSheetAt(i);
      String sheetName = sheet.getSheetName();
      String activeClass = (i == 0) ? " active" : "";

      tabNavigation.append("<li class='nav-item' role='presentation'>")
          .append("<button class='nav-link").append(activeClass).append("' ")
          .append("id='tab-").append(i).append("' ")
          .append("data-bs-toggle='tab' data-bs-target='#content-").append(i).append("' ")
          .append("type='button' role='tab'>")
          .append("<i class='bi bi-table me-2'></i>")
          .append(escapeHtml(sheetName))
          .append("</button></li>");
    }
    tabNavigation.append("</ul>");

    // Tạo tab content
    StringBuilder tabContent = new StringBuilder();
    tabContent.append("<div class='tab-content' id='excelTabContent'>");

    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
      Sheet sheet = workbook.getSheetAt(i);
      String activeClass = (i == 0) ? " show active" : "";

      tabContent.append("<div class='tab-pane fade").append(activeClass).append("' ")
          .append("id='content-").append(i).append("' role='tabpanel'>");

      // Sheet info
      tabContent.append(generateSheetInfo(sheet));

      // Table data
      tabContent.append(generateSheetTable(sheet));

      tabContent.append("</div>");
    }
    tabContent.append("</div>");

    // Replace placeholders trong template
    return template
        .replace("{{TITLE}}", escapeHtml(fileName))
        .replace("{{TAB_NAVIGATION}}", tabNavigation.toString())
        .replace("{{TAB_CONTENT}}", tabContent.toString());
  }

  /**
   * Tạo thông tin sheet
   */
  private String generateSheetInfo(Sheet sheet) {
    int totalRows = sheet.getLastRowNum() + 1;
    int totalCols = 0;
    for (Row row : sheet) {
      if (row.getLastCellNum() > totalCols) {
        totalCols = row.getLastCellNum();
      }
    }

    return String.format("""
        <div class='sheet-info'>
            <div class='row'>
                <div class='col-md-8'>
                    <div class='info-item'>
                        <i class='bi bi-file-text'></i>
                        <strong>Sheet:</strong> %s
                    </div>
                </div>
                <div class='col-md-2'>
                    <div class='info-item'>
                        <i class='bi bi-list-ol'></i>
                        <strong>Rows:</strong> %d
                    </div>
                </div>
                <div class='col-md-2'>
                    <div class='info-item'>
                        <i class='bi bi-grid-3x3'></i>
                        <strong>Columns:</strong> %d
                    </div>
                </div>
            </div>
        </div>
        """, escapeHtml(sheet.getSheetName()), totalRows, totalCols);
  }

  /**
   * Tạo bảng dữ liệu cho sheet
   */
  private String generateSheetTable(Sheet sheet) {
    StringBuilder tableHtml = new StringBuilder();

    // Find data range
    int lastRowNum = sheet.getLastRowNum();
    int maxCellNum = 0;
    for (Row row : sheet) {
      if (row.getLastCellNum() > maxCellNum) {
        maxCellNum = row.getLastCellNum();
      }
    }

    tableHtml.append("<div class='table-container'>")
        .append("<div class='table-responsive'>")
        .append("<table class='table table-hover mb-0'>");

    // Header
    tableHtml.append("<thead><tr><th class='row-number'>#</th>");
    for (int cellNum = 0; cellNum < maxCellNum; cellNum++) {
      String columnLetter = getColumnLetter(cellNum);
      tableHtml.append("<th>").append(columnLetter).append("</th>");
    }
    tableHtml.append("</tr></thead>");

    // Body
    tableHtml.append("<tbody>");
    for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
      Row row = sheet.getRow(rowNum);
      tableHtml.append("<tr>");

      // Row number
      tableHtml.append("<td class='row-number'>").append(rowNum + 1).append("</td>");

      // Cells
      for (int cellNum = 0; cellNum < maxCellNum; cellNum++) {
        Cell cell = (row != null) ? row.getCell(cellNum) : null;
        String cellValue = getCellValueAsString(cell);
        String cssClass = getCellCssClass(cell, cellValue);

        tableHtml.append("<td class='").append(cssClass).append("' title='")
            .append(escapeHtml(cellValue)).append("'>")
            .append(escapeHtml(cellValue))
            .append("</td>");
      }
      tableHtml.append("</tr>");
    }
    tableHtml.append("</tbody></table></div></div>");

    return tableHtml.toString();
  }

  /**
   * Xác định CSS class cho cell
   */
  private String getCellCssClass(Cell cell, String cellValue) {
    if (cell == null || cellValue.trim().isEmpty()) {
      return "empty-cell";
    } else if (cell.getCellType() == CellType.NUMERIC) {
      return "numeric";
    } else {
      return "text-cell";
    }
  }

  /**
   * Chuyển đổi column index sang Excel column letter
   */
  private String getColumnLetter(int columnIndex) {
    StringBuilder result = new StringBuilder();
    while (columnIndex >= 0) {
      result.insert(0, (char) ('A' + columnIndex % 26));
      columnIndex = columnIndex / 26 - 1;
    }
    return result.toString();
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) return "";

    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue().toString();
        } else {
          double numericValue = cell.getNumericCellValue();
          return (numericValue == Math.floor(numericValue)) ?
              String.valueOf((long) numericValue) : String.valueOf(numericValue);
        }
      case BOOLEAN:
        return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        try {
          return String.valueOf(cell.getNumericCellValue());
        } catch (Exception e) {
          return cell.getStringCellValue();
        }
      default:
        return "";
    }
  }

  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;");
  }
}
