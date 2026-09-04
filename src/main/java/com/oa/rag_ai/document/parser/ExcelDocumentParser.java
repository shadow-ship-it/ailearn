package com.oa.rag_ai.document.parser;

import com.oa.rag_ai.document.DocumentStorageException;
import com.oa.rag_ai.document.DocumentType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Excel 解析器：每个工作表作为一个二级标题，行数据整体输出为表格块。
 *
 * <p>行数过多时按 {@link #MAX_ROWS_PER_BLOCK} 拆分为多个表格块，并复用首行作为表头，
 * 避免单个块过大影响后续检索与向量化。
 */
@Component
public class ExcelDocumentParser implements DocumentParser {

    /** 单个工作表最多读取的行数 */
    private static final int MAX_ROWS_PER_SHEET = 5000;

    /** 单个表格块的最大行数，超出后拆块 */
    private static final int MAX_ROWS_PER_BLOCK = 200;

    @Override
    public Set<DocumentType> supports() {
        return EnumSet.of(DocumentType.XLS, DocumentType.XLSX);
    }

    @Override
    public DocumentStructure parse(byte[] bytes, String filename, DocumentType type) {
        List<DocumentBlock> blocks = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                collectSheet(workbook.getSheetAt(index), formatter, evaluator, blocks);
            }
        } catch (IOException | RuntimeException e) {
            throw new DocumentStorageException("解析 Excel 文档失败：" + filename + "，原因：" + e.getMessage(), e);
        }
        return new DocumentStructure("excel", blocks);
    }

    private void collectSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator,
                              List<DocumentBlock> blocks) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (Row row : sheet) {
            if (rows.size() >= MAX_ROWS_PER_SHEET) {
                break;
            }
            List<String> cells = readRow(row, formatter, evaluator);
            if (cells.isEmpty()) {
                continue;
            }
            columns = Math.max(columns, cells.size());
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            return;
        }

        String sheetName = sheet.getSheetName();
        String location = "sheet:" + sheetName;
        blocks.add(DocumentBlock.heading(sheetName, 2, location));

        if (columns == 1) {
            // 单列工作表按列表处理，语义上更接近枚举项
            for (List<String> row : rows) {
                blocks.add(DocumentBlock.listItem(row.get(0), location));
            }
            return;
        }

        List<List<String>> padded = padColumns(rows, columns);
        if (padded.size() <= MAX_ROWS_PER_BLOCK) {
            blocks.add(DocumentBlock.table(padded, location));
            return;
        }
        List<String> header = padded.get(0);
        for (int start = 0; start < padded.size(); start += MAX_ROWS_PER_BLOCK) {
            List<List<String>> part = new ArrayList<>();
            if (start > 0) {
                part.add(header);
            }
            part.addAll(padded.subList(start, Math.min(start + MAX_ROWS_PER_BLOCK, padded.size())));
            blocks.add(DocumentBlock.table(part, location));
        }
    }

    private List<String> readRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> cells = new ArrayList<>();
        short lastCell = row.getLastCellNum();
        for (int index = 0; index < lastCell; index++) {
            Cell cell = row.getCell(index);
            cells.add(cell == null ? "" : ParserUtils.clean(formatter.formatCellValue(cell, evaluator)));
        }
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    private static List<List<String>> padColumns(List<List<String>> rows, int columns) {
        List<List<String>> padded = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++) {
                cells.add(column < row.size() ? row.get(column) : "");
            }
            padded.add(cells);
        }
        return padded;
    }
}
