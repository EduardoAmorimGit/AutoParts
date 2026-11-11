package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.common.csv.CsvReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Creates or updates products from a spreadsheet (CSV), matching by SKU. Rows without a price are
 * skipped with a warning, so a full supplier list can be imported with only the items to be sold
 * priced. Blank cells keep the current value of an existing product. A dry run saves nothing.
 *
 * <p>Columns (case/accent-insensitive): sku, nome (or nome_sugerido), descricao (or
 * descricao_tecnica), marca, tipo, categoria, preco, estoque, ativo, viscosidade, especificacao,
 * volume_litros, imagens (URLs separated by '|'). Required columns: sku, nome, preco.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    static final int MAX_DATA_ROWS = 20_000;

    private static final Map<String, List<String>> COLUMNS = Map.ofEntries(
            Map.entry("sku", List.of("SKU", "CODIGO")),
            Map.entry("name", List.of("NOME", "NOME SUGERIDO")),
            Map.entry("description", List.of("DESCRICAO", "DESCRICAO TECNICA")),
            Map.entry("brand", List.of("MARCA")),
            Map.entry("type", List.of("TIPO")),
            Map.entry("category", List.of("CATEGORIA")),
            Map.entry("price", List.of("PRECO")),
            Map.entry("stock", List.of("ESTOQUE")),
            Map.entry("active", List.of("ATIVO")),
            Map.entry("viscosity", List.of("VISCOSIDADE")),
            Map.entry("specification", List.of("ESPECIFICACAO")),
            Map.entry("volume", List.of("VOLUME LITROS", "VOLUME")),
            Map.entry("images", List.of("IMAGENS", "IMAGEM URL", "IMAGEM")));

    public record ImportReport(boolean dryRun, int totalRows, int created, int updated, int skippedWithoutPrice,
            List<RowMessage> errors, List<RowMessage> warnings) {
    }

    public record RowMessage(int line, String message) {
    }

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    @Transactional
    public ImportReport importCsv(InputStream input, boolean dryRun) {
        List<CsvReader.Row> rows;
        try {
            rows = CsvReader.read(input, MAX_DATA_ROWS + 1);
        } catch (IOException exception) {
            throw new ProductImportException("Could not read the CSV file: " + exception.getMessage());
        }
        if (rows.isEmpty()) {
            throw new ProductImportException("The CSV file is empty");
        }
        Map<String, Integer> columns = columnIndex(rows.get(0));

        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<RowMessage> errors = new ArrayList<>();
        List<RowMessage> warnings = new ArrayList<>();
        Map<String, Integer> seenSkus = new HashMap<>();
        for (CsvReader.Row row : rows.subList(1, rows.size())) {
            total++;
            Cells cells = new Cells(row, columns);
            try {
                String sku = ProductService.normalizeSku(cells.required("sku"));
                Integer firstLine = seenSkus.putIfAbsent(sku, row.line());
                if (firstLine != null) {
                    throw new RowException("SKU '" + sku + "' repeats line " + firstLine);
                }
                if (cells.get("price") == null) {
                    skipped++;
                    warnings.add(new RowMessage(row.line(), "No price; product '" + sku + "' was not imported"));
                    continue;
                }
                Optional<Product> existing = productRepository.findBySku(sku);
                RowValues values = parse(cells, existing.isEmpty());
                Product product = existing.orElseGet(Product::new);
                apply(product, sku, values, existing.isEmpty());
                productRepository.save(product);
                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            } catch (RowException | InvalidCategoryNameException exception) {
                errors.add(new RowMessage(row.line(), exception.getMessage()));
            }
        }
        productRepository.flush();

        if (dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new ImportReport(dryRun, total, created, updated, skipped, errors, warnings);
    }

    /** Every value of a row, validated before anything touches the (possibly managed) product. */
    private record RowValues(String name, String description, String brand, BigDecimal price, Integer stock,
            Boolean active, ProductType type, String category, String viscosity, String specification,
            BigDecimal volume, List<String> images) {
    }

    private static RowValues parse(Cells cells, boolean isNew) {
        String name = isNew ? cells.required("name") : cells.get("name");
        String type = cells.get("type");
        String category = cells.get("category");
        if (category != null) {
            CategoryService.slugOf(maxLength("categoria", category, 80));
        }
        String viscosity = cells.get("viscosity");
        String volumeRaw = cells.get("volume");
        BigDecimal volume = null;
        if (volumeRaw != null) {
            volume = decimal("volume_litros", volumeRaw);
            if (volume.signum() <= 0 || volume.compareTo(new BigDecimal("999.99")) > 0 || volume.scale() > 2) {
                throw new RowException("'volume_litros' must be between 0.01 and 999.99");
            }
        }
        String imagesRaw = cells.get("images");
        List<String> images = null;
        if (imagesRaw != null) {
            images = Arrays.stream(imagesRaw.split("\\|")).map(String::trim).filter(u -> !u.isEmpty()).toList();
            for (String url : images) {
                if (!url.matches("^https://\\S+$") || url.length() > 500) {
                    throw new RowException("Image URL must start with https:// and have at most 500 characters: " + url);
                }
            }
            if (images.size() > 10) {
                throw new RowException("At most 10 images per product");
            }
        }
        String stock = cells.get("stock");
        String active = cells.get("active");
        String description = cells.get("description");
        String brand = cells.get("brand");
        String specification = cells.get("specification");
        return new RowValues(
                name == null ? null : maxLength("nome", name, 150),
                description == null ? null : maxLength("descricao", description, 2000),
                brand == null ? null : maxLength("marca", brand, 100),
                price(cells.required("price")),
                stock == null ? null : nonNegativeInt("estoque", stock),
                active == null ? null : yesNo(active),
                type == null ? null : ProductType.parse(type).orElseThrow(() -> new RowException(
                        "Unknown type '" + type + "' (use e.g. 'Filtro de oleo', 'Filtro de ar', 'Oleo do motor')")),
                category,
                viscosity == null ? null : maxLength("viscosidade", ProductService.normalizeViscosity(viscosity), 20),
                specification == null ? null : maxLength("especificacao", specification, 100),
                volume,
                images);
    }

    // Blank cells keep the current value of an existing product; new products get defaults.
    private void apply(Product product, String sku, RowValues values, boolean isNew) {
        product.setSku(sku);
        if (values.name() != null) {
            product.setName(values.name());
        }
        if (values.description() != null) {
            product.setDescription(values.description());
        }
        if (values.brand() != null) {
            product.setBrand(values.brand());
        }
        product.setPrice(values.price());
        if (values.stock() != null || isNew) {
            product.setStockQuantity(values.stock() == null ? 0 : values.stock());
        }
        if (values.active() != null || isNew) {
            product.setActive(values.active() == null || values.active());
        }
        if (values.type() != null || isNew) {
            product.setType(values.type() == null ? ProductType.OTHER : values.type());
        }
        if (values.category() != null) {
            product.setCategory(categoryService.findOrCreate(values.category()));
        }
        if (values.viscosity() != null) {
            product.setViscosity(values.viscosity());
        }
        if (values.specification() != null) {
            product.setOilSpecification(values.specification());
        }
        if (values.volume() != null) {
            product.setPackageVolumeLiters(values.volume());
        }
        if (values.images() != null) {
            product.getImageUrls().clear();
            product.getImageUrls().addAll(values.images());
        }
    }

    private static Map<String, Integer> columnIndex(CsvReader.Row header) {
        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < header.values().size(); i++) {
            byName.putIfAbsent(CsvReader.normalizeHeader(header.values().get(i)), i);
        }
        Map<String, Integer> columns = new HashMap<>();
        COLUMNS.forEach((key, names) -> names.stream().filter(byName::containsKey).findFirst()
                .ifPresent(name -> columns.put(key, byName.get(name))));
        List<String> missing = List.of("sku", "name", "price").stream().filter(k -> !columns.containsKey(k)).toList();
        if (!missing.isEmpty()) {
            throw new ProductImportException("Missing required columns: "
                    + String.join(", ", missing.stream().map(k -> switch (k) {
                        case "name" -> "nome";
                        case "price" -> "preco";
                        default -> k;
                    }).toList()));
        }
        return columns;
    }

    // "29,90", "1.234,56" and "29.90" are all accepted.
    private static BigDecimal price(String raw) {
        BigDecimal value = decimal("preco", raw);
        if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 10) {
            throw new RowException("'preco' must be zero or positive with at most two decimals: " + raw);
        }
        return value;
    }

    private static BigDecimal decimal(String column, String raw) {
        String value = raw.replace("R$", "").replace(" ", "");
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        }
        try {
            // "29.900" is 29.9; "30" stays a whole number.
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.scale() > 2) {
                parsed = parsed.stripTrailingZeros();
            }
            return parsed.scale() < 0 ? parsed.setScale(0) : parsed;
        } catch (NumberFormatException exception) {
            throw new RowException("'" + column + "' is not a number: " + raw);
        }
    }

    private static int nonNegativeInt(String column, String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new RowException("'" + column + "' must be a whole number >= 0: " + raw);
        }
    }

    private static boolean yesNo(String raw) {
        String value = CsvReader.normalizeHeader(raw);
        return switch (value) {
            case "SIM", "S", "TRUE", "1", "ATIVO" -> true;
            case "NAO", "N", "FALSE", "0", "INATIVO" -> false;
            default -> throw new RowException("'ativo' must be sim or nao: " + raw);
        };
    }

    private static String maxLength(String column, String value, int max) {
        if (value.length() > max) {
            throw new RowException("'" + column + "' is longer than " + max + " characters");
        }
        return value;
    }

    private record Cells(CsvReader.Row row, Map<String, Integer> columns) {

        String get(String key) {
            Integer index = columns.get(key);
            String value = index == null ? null : row.get(index);
            return value == null || value.isBlank() ? null : value.trim();
        }

        String required(String key) {
            String value = get(key);
            if (value == null) {
                throw new RowException("'" + key.toLowerCase(Locale.ROOT) + "' is required");
            }
            return value;
        }
    }

    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
