package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.csv.CsvReader;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidImportFileException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidSpecException;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.ImportReport;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.RowMessage;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Imports fitments from a catalog/supplier spreadsheet (CSV). Each valid row creates the spec if
 * needed and an IMPORTED fitment, which customers only see after an administrator validates it.
 * Invalid rows are reported by line and skipped; a dry run validates without saving anything.
 *
 * <p>Columns (header names are case/accent-insensitive; '_' or space): sku, marca, modelo,
 * versao, motor, combustivel, ano_inicio, ano_fim, capacidade_oleo_litros, fonte, observacao.
 * Required: sku, marca, modelo, ano_inicio, ano_fim, fonte.
 */
@Service
@RequiredArgsConstructor
public class FitmentImportService {

    static final int MAX_DATA_ROWS = 20_000;
    private static final List<String> REQUIRED = List.of("SKU", "MARCA", "MODELO", "ANO INICIO", "ANO FIM", "FONTE");

    private final ProductRepository productRepository;
    private final VehicleSpecRepository specRepository;
    private final ProductFitmentRepository fitmentRepository;
    private final FitmentAdminService fitmentAdminService;

    @Transactional
    public ImportReport importCsv(InputStream input, boolean dryRun) {
        List<CsvReader.Row> rows;
        try {
            rows = CsvReader.read(input, MAX_DATA_ROWS + 1);
        } catch (IOException exception) {
            throw new InvalidImportFileException("Could not read the CSV file: " + exception.getMessage());
        }
        if (rows.isEmpty()) {
            throw new InvalidImportFileException("The CSV file is empty");
        }
        Map<String, Integer> columns = columnIndex(rows.get(0));

        Counters counters = new Counters();
        Map<SpecDraft, VehicleSpec> specCache = new HashMap<>();
        Map<String, Optional<Product>> productCache = new HashMap<>();
        for (CsvReader.Row row : rows.subList(1, rows.size())) {
            counters.total++;
            try {
                importRow(row, columns, counters, specCache, productCache);
            } catch (RowException | InvalidSpecException exception) {
                counters.errors.add(new RowMessage(row.line(), exception.getMessage()));
            }
        }

        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new ImportReport(dryRun, counters.total, counters.fitmentsCreated, counters.fitmentsExisting,
                counters.specsCreated, counters.errors, counters.warnings);
    }

    private void importRow(CsvReader.Row row, Map<String, Integer> columns, Counters counters,
            Map<SpecDraft, VehicleSpec> specCache, Map<String, Optional<Product>> productCache) {
        String sku = required(row, columns, "SKU").trim().toUpperCase(Locale.ROOT);
        Product product = productCache.computeIfAbsent(sku, productRepository::findBySku)
                .orElseThrow(() -> new RowException("No product with SKU '" + sku + "'"));
        String source = required(row, columns, "FONTE").trim();
        if (source.length() > 300) {
            throw new RowException("'fonte' is longer than 300 characters");
        }
        String notes = optional(row, columns, "OBSERVACAO");
        if (notes != null && notes.length() > 500) {
            throw new RowException("'observacao' is longer than 500 characters");
        }

        SpecDraft draft = SpecDraft.of(
                required(row, columns, "MARCA"),
                required(row, columns, "MODELO"),
                optional(row, columns, "VERSAO"),
                optional(row, columns, "MOTOR"),
                optional(row, columns, "COMBUSTIVEL"),
                integer(row, columns, "ANO INICIO"),
                integer(row, columns, "ANO FIM"),
                decimal(row, columns, "CAPACIDADE OLEO LITROS"));
        // The cache key ignores oil capacity: it is an attribute of the spec, not part of its identity.
        SpecDraft identity = new SpecDraft(draft.brand(), draft.model(), draft.version(), draft.engine(),
                draft.fuel(), draft.yearStart(), draft.yearEnd(), null);
        VehicleSpec spec = specCache.get(identity);
        if (spec == null) {
            spec = fitmentAdminService.findIdentical(draft).orElse(null);
            if (spec == null) {
                spec = specRepository.save(draft.toEntity());
                counters.specsCreated++;
            }
            specCache.put(identity, spec);
        }
        mergeOilCapacity(spec, draft.oilCapacityLiters(), row.line(), counters);

        Optional<ProductFitment> existing = spec.getId() == null
                ? Optional.empty()
                : fitmentRepository.findByProductIdAndSpecId(product.getId(), spec.getId());
        if (existing.isPresent()) {
            counters.fitmentsExisting++;
            if (existing.get().getStatus() == FitmentStatus.REJECTED) {
                counters.warnings.add(new RowMessage(row.line(),
                        "Fitment was rejected before; kept as rejected"));
            }
            return;
        }
        ProductFitment fitment = new ProductFitment();
        fitment.setProduct(product);
        fitment.setSpec(spec);
        fitment.setStatus(FitmentStatus.IMPORTED);
        fitment.setSource(source);
        fitment.setNotes(notes);
        fitmentRepository.save(fitment);
        counters.fitmentsCreated++;
    }

    private static void mergeOilCapacity(VehicleSpec spec, BigDecimal oil, int line, Counters counters) {
        if (oil == null) {
            return;
        }
        if (spec.getOilCapacityLiters() == null) {
            spec.setOilCapacityLiters(oil);
        } else if (spec.getOilCapacityLiters().compareTo(oil) != 0) {
            counters.warnings.add(new RowMessage(line, "Oil capacity " + oil.toPlainString()
                    + " L differs from the spec's " + spec.getOilCapacityLiters().toPlainString() + " L; kept the existing value"));
        }
    }

    private static Map<String, Integer> columnIndex(CsvReader.Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.values().size(); i++) {
            String name = VehicleNames.text(header.values().get(i));
            if (name != null) {
                columns.putIfAbsent(name, i);
            }
        }
        List<String> missing = REQUIRED.stream().filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidImportFileException("Missing required columns: "
                    + String.join(", ", missing.stream().map(n -> n.toLowerCase(Locale.ROOT).replace(' ', '_')).toList()));
        }
        return columns;
    }

    private static String required(CsvReader.Row row, Map<String, Integer> columns, String column) {
        String value = optional(row, columns, column);
        if (value == null) {
            throw new RowException("'" + column.toLowerCase(Locale.ROOT).replace(' ', '_') + "' is required");
        }
        return value;
    }

    private static String optional(CsvReader.Row row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        String value = index == null ? null : row.get(index);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer integer(CsvReader.Row row, Map<String, Integer> columns, String column) {
        String value = required(row, columns, column);
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new RowException("'" + column.toLowerCase(Locale.ROOT).replace(' ', '_') + "' is not a year: " + value);
        }
    }

    private static BigDecimal decimal(CsvReader.Row row, Map<String, Integer> columns, String column) {
        String value = optional(row, columns, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new RowException("'" + column.toLowerCase(Locale.ROOT).replace(' ', '_') + "' is not a number: " + value);
        }
    }

    private static final class Counters {
        int total;
        int fitmentsCreated;
        int fitmentsExisting;
        int specsCreated;
        final List<RowMessage> errors = new ArrayList<>();
        final List<RowMessage> warnings = new ArrayList<>();
    }

    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
