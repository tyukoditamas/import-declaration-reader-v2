package org.app.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.app.helper.NativeExtractor;
import org.app.model.ImportDeclaration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PdfFolderService {
    private static final Log log = LogFactory.getLog(PdfFolderService.class);
    private final ObjectMapper mapper;
    private final Consumer<String> logger;

    public PdfFolderService(Consumer<String> logger) {
        this.logger = logger;
        this.mapper = new ObjectMapper()
                // ignore the “file” or “error” fields when binding to ImportDeclaration
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public List<ImportDeclaration> processFolder(File folder) throws Exception {
        // 1) run the extractor
        Path extractor = NativeExtractor.unpackExtractor();
        ProcessBuilder pb = new ProcessBuilder(
                extractor.toAbsolutePath().toString(),
                folder.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // 2) collect its stdout
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        int exit = proc.waitFor();
        String json = out.toString(StandardCharsets.UTF_8);
        if (exit != 0) {
            throw new RuntimeException("Extractor failed:\n" + json);
        }

        // 3) parse into a JSON treee
        JsonNode root = mapper.readTree(json);
        if (! (root instanceof ArrayNode) ) {
            throw new RuntimeException("Unexpected extractor output (not a JSON array):\n" + json);
        }

        List<String> expected = List.of(
                "nrDestinatar","mrn","nrArticole",
                "referintaDocument","nrContainer"
        );

        List<Map.Entry<String, ImportDeclaration>> rows = new ArrayList<>();

        ArrayNode arr = (ArrayNode) root;
        for (JsonNode n : arr) {
            String fileName = n.path("file").asText("<unknown>");

            if (n.has("error")) {
                logger.accept("❌ Failed to parse: "
                        + fileName
                        + " → " + n.get("error").asText());
                continue;
            }

            // check if *any* expected field is non-blank
            boolean hasData = expected.stream()
                    .anyMatch(field ->
                            n.hasNonNull(field)
                                    && !n.get(field).asText().isBlank()
                    );

            if (!hasData) {
                logger.accept("❌ Wrong structure: " + fileName);
                continue;
            }

            // otherwise bind and record it
            ImportDeclaration dto = mapper.treeToValue(n, ImportDeclaration.class);
            rows.add(new AbstractMap.SimpleEntry<>(fileName, dto));
        }

        rows.sort((a, b) -> naturalCompareIgnoreCase(a.getKey(), b.getKey()));

        List<ImportDeclaration> good = new ArrayList<>(rows.size());
        for (var e : rows) {
            logger.accept("✅ Parsed successfully: " + e.getKey());
            good.add(e.getValue());
        }
        logger.accept("Total PDFs parsed: " + good.size());
        return good;
    }

    private static int naturalCompareIgnoreCase(String a, String b) {
        int ia = 0, ib = 0, na = a.length(), nb = b.length();
        while (ia < na && ib < nb) {
            char ca = Character.toLowerCase(a.charAt(ia));
            char cb = Character.toLowerCase(b.charAt(ib));

            // if both chunks are digits, compare as integers
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ja = ia; while (ja < na && Character.isDigit(a.charAt(ja))) ja++;
                int jb = ib; while (jb < nb && Character.isDigit(b.charAt(jb))) jb++;
                String da = a.substring(ia, ja);
                String db = b.substring(ib, jb);

                // strip leading zeros for fair numeric compare
                String da2 = da.replaceFirst("^0+(?!$)", "");
                String db2 = db.replaceFirst("^0+(?!$)", "");
                int cmp = Integer.compare(da2.length(), db2.length());
                if (cmp == 0) cmp = da2.compareTo(db2);
                if (cmp != 0) return cmp;

                ia = ja; ib = jb; // numbers equal → move on
                continue;
            }

            // otherwise compare chars (case-insensitive)
            if (ca != cb) return ca - cb;
            ia++; ib++;
        }
        return (na - ia) - (nb - ib);
    }
}
