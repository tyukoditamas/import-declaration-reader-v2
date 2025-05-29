package org.app.controller;

import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.app.model.ImportDeclaration;
import org.app.service.PdfFolderService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

public class MainController {
    @FXML
    private Button browseButton;
    @FXML
    private TextArea logArea;
    @FXML
    private ProgressIndicator spinner;
    @FXML
    private RadioButton cuFizicRadio;
    @FXML
    private RadioButton faraFizicRadio;
    private final ToggleGroup fizicToggle = new ToggleGroup();


    @FXML
    public void initialize() {
        cuFizicRadio.setToggleGroup(fizicToggle);
        faraFizicRadio.setToggleGroup(fizicToggle);
        cuFizicRadio.setSelected(true); // Optionally set a default
    }

    @FXML
    private void onBrowse() {
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
                // Use the selected radio button to decide which method to call
                if (cuFizicRadio.isSelected()) {
                    writeCsvCuFizic(new File(folder, "output.csv"), task.getValue());
                } else if (faraFizicRadio.isSelected()) {
                    writeCsvFaraFizic(new File(folder, "output.csv"), task.getValue());
                } else {
                    log("Please select CU FIZIC or FARA FIZIC before proceeding.");
                }
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

    private void writeCsvCuFizic(File outFile, List<ImportDeclaration> list) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outFile))) {
            // 1) Header
            writer.writeNext(new String[]{
                    "nr.crt",
                    "CIF/CNP",
                    "deviz",
                    "Produs",
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

                // --- 1) primary row ---
                String[] primary = {
                        String.valueOf(counter),                    // nr.crt
                        cif,                                        // CIF/CNP
                        "eur",                                      // deviz
                        "PRIMARY CUSTOMS DECLARATION",              // produs
                        "nu e cazul",                               // Serie produs
                        "1",                                        // Cant
                        "BUC",                                      // UM
                        "50",                                       // Pret FTVA logic
                        "0",                                       // cota TVA
                        "MRN " + dto.getMrn()
                                + " - CONTAINER" + dto.getNrContainer(),    // nota produs
                        "",                                         // scutit TVA
                        ""                                          // motiv scutire TVA
                };
                writer.writeNext(primary);

                // --- 2) supplemental row ---
                String[] transit = {
                        String.valueOf(counter),      // nr.crt
                        "",                             // CIF/CNP
                        "eur",                             // deviz
                        "TRANSIT",                      // produs
                        "nu e cazul",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "75",                           // Pret FTVA
                        "0",                           // cota TVA
                        dto.getReferintaDocument(),     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(transit);

                if (Integer.parseInt(dto.getNrArticole()) > 1) {
                    String[] additionalHsCode = {
                            String.valueOf(counter),  // nr.crt
                            "",                         // CIF/CNP
                            "eur",                         // deviz
                            "ADDITIONAL HS CODE",       // produs
                            "nu e cazul",                         // Serie produs
                            String.valueOf(Integer.parseInt(dto.getNrArticole()) - 1),                        // Cant
                            "BUC",                      // UM
                            "5",                 // Pret FTVA = 2.5% × A00
                            "0",                       // cota TVA
                            "MRN " + dto.getMrn()
                                    + " - CONTAINER" + dto.getNrContainer(),    // nota produs
                            "",                         // scutit TVA
                            ""                          // motiv scutire TVA
                    };
                    writer.writeNext(additionalHsCode);
                }

                // --- 3) physical control
                String[] physicalControl = {
                        String.valueOf(counter),      // nr.crt
                        "",                             // CIF/CNP
                        "eur",                             // deviz
                        "PHYSICAL CONTROL",                      // produs
                        "nu e cazul",                             // Serie produs
                        "0",                            // Cant
                        "BUC",                          // UM
                        "22",                           // Pret FTVA
                        "0",                           // cota TVA
                        "CT - " + dto.getReferintaDocument(),     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(physicalControl);

                counter++;
            }
        }

        log("CSV written to: " + outFile.getAbsolutePath());
    }

    private void writeCsvFaraFizic(File outFile, List<ImportDeclaration> list) throws Exception {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outFile))) {
            // 1) Header
            writer.writeNext(new String[]{
                    "nr.crt",
                    "CIF/CNP",
                    "deviz",
                    "Produs",
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

                // --- 1) primary row ---
                String[] primary = {
                        String.valueOf(counter),                    // nr.crt
                        cif,                                        // CIF/CNP
                        "eur",                                      // deviz
                        "PRIMARY CUSTOMS DECLARATION",              // produs
                        "nu e cazul",                               // Serie produs
                        "1",                                        // Cant
                        "BUC",                                      // UM
                        "50",                                       // Pret FTVA logic
                        "0",                                       // cota TVA
                        "MRN " + dto.getMrn()
                                + " - CONTAINER" + dto.getNrContainer(),    // nota produs
                        "",                                         // scutit TVA
                        ""                                          // motiv scutire TVA
                };
                writer.writeNext(primary);

                // --- 2) supplemental row ---
                String[] transit = {
                        String.valueOf(counter),      // nr.crt
                        "",                             // CIF/CNP
                        "",
                        "eur",                             // deviz
                        "TRANSIT",                      // produs
                        "nu e cazul",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "75",                           // Pret FTVA
                        "0",                           // cota TVA
                        dto.getReferintaDocument(),     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(transit);

                if (Integer.parseInt(dto.getNrArticole()) > 1) {
                    String[] additionalHsCode = {
                            String.valueOf(counter),  // nr.crt
                            "",                         // CIF/CNP
                            "",
                            "eur",                         // deviz
                            "ADDITIONAL HS CODE",       // produs
                            "nu e cazul",                         // Serie produs
                            String.valueOf(Integer.parseInt(dto.getNrArticole()) - 1),                        // Cant
                            "BUC",                      // UM
                            "5",                 // Pret FTVA = 2.5% × A00
                            "0",                       // cota TVA
                            "MRN " + dto.getMrn()
                                    + " - CONTAINER" + dto.getNrContainer(),    // nota produs
                            "",                         // scutit TVA
                            ""                          // motiv scutire TVA
                    };
                    writer.writeNext(additionalHsCode);
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
