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
import java.util.ArrayList;
import java.util.List;
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

        List<ImportDeclaration> good = new ArrayList<>();
        List<String> expected = List.of(
                "nrDestinatar","mrn","nrArticole",
                "referintaDocument","nrContainer"
        );

        System.out.println(root);

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
            logger.accept("✅ Parsed successfully: " + fileName);
            good.add(dto);
        }

        logger.accept("Total PDFs parsed: " + good.size());
        return good;
    }
}
