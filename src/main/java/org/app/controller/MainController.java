package org.app.controller;

import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.app.model.ImportDeclaration;
import org.app.service.PdfFolderService;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

public class MainController {
    @FXML private Button browseButton;
    @FXML private TextArea logArea;
    @FXML private ProgressIndicator spinner;


    @FXML private void onBrowse() {
        File folder = new DirectoryChooser()
                .showDialog((Stage) browseButton.getScene().getWindow());
        if (folder == null) {
            log("Folder selection cancelled.");
            return;
        }
        log("Processing folder: " + folder);

        // 1) Create a Task that does the work off the FX thread
        Task<List<ImportDeclaration>> task = new Task<>() {
            @Override
            protected List<ImportDeclaration> call() throws Exception {
                PdfFolderService svc = new PdfFolderService(line ->
                        Platform.runLater(() -> log(line))
                );
                return svc.processFolder(folder);
            }
        };

        // 2) Wire its messageProperty to your logArea
        task.messageProperty().addListener((obs, old, msg) -> {
            if (msg != null && !msg.isBlank()) {
                log(msg);
            }
        });

        // Bind spinner visibility to the fact that the task is running
        spinner.visibleProperty().bind(task.runningProperty());

        // Disable your browse button while the task is running
        browseButton.disableProperty().bind(task.runningProperty());

        // 3) When it succeeds, write out the CSV on the FX thread
        task.setOnSucceeded(e -> {
            try {
                writeCsv(new File(folder, "output.csv"), task.getValue());
            } catch (Exception ex) {
                log("Error writing CSV: " + ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            log("Fatal: " + task.getException().getMessage());
        });

        // 4) Kick it off
        new Thread(task, "pdf-extractor").start();
    }

    private void writeCsv(File outFile, List<ImportDeclaration> list) throws Exception {
        // for rounding the 2.5% commission to 2 decimals
        DecimalFormat df = new DecimalFormat("#.##");

        try (CSVWriter writer = new CSVWriter(new FileWriter(outFile))) {
            // 1) Header
            writer.writeNext(new String[]{
                    "nr.crt",
                    "CIF/CNP",
                    "client",
                    "deviz",
                    "produs",
                    "Serie produs",
                    "Cant",
                    "UM",
                    "Pret FTVA",
                    "cota TVA",
                    "nota produs",
                    "scutit TVA (0/1)",
                    "motiv scutire TVA"
            });

            int counter = 1;
            for (ImportDeclaration dto : list) {
                String cif = dto.getNrDestinatar();
                double valoare = 0;
                try {
                    valoare = Double.parseDouble(dto.getValoareStatistica().replace(",", ""));
                } catch (Exception ignore) {}

                // --- 1) primary row ---
                String[] primary = {
                        String.valueOf(counter),      // nr.crt
                        cif,                            // CIF/CNP
                        "",
                        "RON",                          // deviz
                        "PREST VAMALE",                 // produs
                        "",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        // Pret FTVA logic
                        "RO25153581".equals(cif)
                                ? (valoare <= 50_000 ? "250"
                                : valoare <= 200_000 ? "315"
                                : "915")
                                : "25",
                        "19",                           // cota TVA
                        "MRN " + dto.getMrn(),          // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(primary);

                // --- 2) supplemental row ---
                String[] transit = {
                        String.valueOf(counter),      // nr.crt
                        "",                             // CIF/CNP
                        "",
                        "",                             // deviz
                        "INTOCMIRE TRANZIT UPS",        // produs
                        "",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "25",                           // Pret FTVA
                        "19",                           // cota TVA
                        "TM- " + dto.getReferintaDocument(),     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(transit);

                // --- only if there was an advance deposit: two more rows ---
                String depozit = dto.getDepozitPlataAnticipata();
                if (depozit != null && !depozit.isBlank()) {
                    // 3) commission row
                    double totalA00 = 0;
                    try {
                        totalA00 = Double.parseDouble(
                                Objects.toString(dto.getTotalPlataA00(), "0").replace(",", "")
                        );
                    } catch (Exception ignore) {}
                    String commission = df.format(totalA00 * 0.025);

                    String[] commissionRow = {
                            String.valueOf(counter),  // nr.crt
                            "",                         // CIF/CNP
                            "",
                            "RON",                         // deviz
                            "COMISION PLATI AVANSATE",  // produs
                            "",                         // Serie produs
                            "1",                        // Cant
                            "BUC",                      // UM
                            commission,                 // Pret FTVA = 2.5% × A00
                            "19",                       // cota TVA
                            "MRN " + dto.getMrn(),      // nota produs
                            "",                         // scutit TVA
                            ""                          // motiv scutire TVA
                    };
                    writer.writeNext(commissionRow);

                    // 4) advance‐payment row
                    counter++;
                    String[] advanceRow = {
                            String.valueOf(counter),  // nr.crt
                            "",                         // CIF/CNP
                            "",
                            "RON",                         // deviz
                            "PLATI AVANSATE",           // produs
                            "",                         // Serie produs
                            "1",                        // Cant
                            "BUC",                      // UM
                            Objects.toString(dto.getTotalPlataA00(), ""), // Pret FTVA = total
                            "0",                        // cota TVA
                            "MRN " + dto.getMrn(),      // nota produs
                            "",                         // scutit TVA
                            ""                          // motiv scutire TVA
                    };
                    writer.writeNext(advanceRow);
                }

                counter++;
            }
        }

        log("CSV written to: " + outFile.getAbsolutePath());
    }


    private void log(String message) {
        logArea.appendText(message + "\n");
    }
}
