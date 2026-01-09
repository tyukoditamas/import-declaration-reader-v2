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
import java.util.List;

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
            // Header
            writer.writeNext(new String[]{
                    "nr.crt",
                    "CIF/CNP",
                    "den. client",
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

                String productNoteDocumentReference;

                if (dto.getReferintaDocument().contains("26ROBU1030"))
                    productNoteDocumentReference = "OTP - " + dto.getReferintaDocument() + " - " + dto.getNrContainer();
                else if (dto.getReferintaDocument().contains("26ROCT1900"))
                    productNoteDocumentReference = "CT - " + dto.getReferintaDocument() + " - " + dto.getNrContainer();
                else
                    productNoteDocumentReference = dto.getReferintaDocument() + " - " + dto.getNrContainer();

                String productNotePMrn;

                if (dto.getMrn().contains("26ROBU1030"))
                    productNotePMrn = "OTP - "  + dto.getMrn() + " - " + dto.getNrContainer();
                else if (dto.getReferintaDocument().contains("26ROCT1900"))
                    productNotePMrn = "CT - " + dto.getMrn() + " - " + dto.getNrContainer();
                else
                    productNotePMrn = dto.getMrn() + " - " + dto.getNrContainer();



                // --- 1) transit row ---
                String[] transit = {
                        String.valueOf(counter),      // nr.crt
                        "PL 5831014898",                   // CIF/CNP
                        "LPP S.A",               // den client
                        "EUR",                             // deviz
                        "TRANSIT",                      // produs
                        "",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "75",                           // Pret FTVA
                        "0",                           // cota TVA
                        productNoteDocumentReference,     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(transit);

                // --- 2) physical control
                String[] physicalControl = {
                        String.valueOf(counter),      // nr.crt
                        "",                             // CIF/CNP
                        "",                             // den client
                        "EUR",                             // deviz
                        "PHYSICAL CONTROL",                      // produs
                        "",                             // Serie produs
                        "4",                            // Cant
                        "BUC",                          // UM
                        "22",                           // Pret FTVA
                        "0",                           // cota TVA
                        productNoteDocumentReference,     // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(physicalControl);

                // --- 3) PRIMARY CUSTOMS DECLARATION row ---
                String[] primary = {
                        String.valueOf(counter),                    // nr.crt
                        "",                                        //CIF/CNP
                        "",                                         // den client
                        "EUR",                                      // deviz
                        "PRIMARY CUSTOMS DECLARATION",              // produs
                        "",                                         // Serie produs
                        "1",                                        // Cant
                        "BUC",                                      // UM
                        "50",                                       // Pret FTVA logic
                        "0",                                       // cota TVA
                        productNotePMrn,                  // nota produs
                        "",                                         // scutit TVA
                        ""                                          // motiv scutire TVA
                };
                writer.writeNext(primary);

                // --- 4) ADDITIONAL hs row ---
                if (Integer.parseInt(dto.getNrArticole()) > 1) {
                    String[] additionalHsCode = {
                            String.valueOf(counter),  // nr.crt
                            "",                             // CIF/CNP
                            "",                             // den client
                            "EUR",                         // deviz
                            "ADDITIONAL HS CODE",          // produs
                            "",                            // Serie produs
                            String.valueOf(Integer.parseInt(dto.getNrArticole()) - 1),                        // Cant
                            "BUC",                        // UM
                            "5",                           // Pret FTVA = 2.5% × A00
                            "0",                             // cota TVA
                            productNotePMrn,                 // nota produs
                            "",                            // scutit TVA
                            ""                             // motiv scutire TVA
                    };
                    writer.writeNext(additionalHsCode);
                }

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
                    "den. client",
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
                String productNoteDocumentReference;

                if (dto.getReferintaDocument().contains("26ROBU1030"))
                    productNoteDocumentReference = "OTP - " + dto.getReferintaDocument() + " - " + dto.getNrContainer();
                else if (dto.getReferintaDocument().contains("26ROCT1900"))
                    productNoteDocumentReference = "CT - " + dto.getReferintaDocument() + " - " + dto.getNrContainer();
                else
                    productNoteDocumentReference = dto.getReferintaDocument() + " - " + dto.getNrContainer();

                String productNotePMrn;

                if (dto.getMrn().contains("26ROBU1030"))
                    productNotePMrn = "OTP - "  + dto.getMrn() + " - " + dto.getNrContainer();
                else if (dto.getReferintaDocument().contains("26ROCT1900"))
                    productNotePMrn = "CT - " + dto.getMrn() + " - " + dto.getNrContainer();
                else
                    productNotePMrn = dto.getMrn() + " - " + dto.getNrContainer();


                // --- 1) transit row ---
                String[] transit = {
                        String.valueOf(counter),      // nr.crt
                        "PL 5831014898",                // CIF/CNP
                        "LPP S.A",                      // den client
                        "EUR",                             // deviz
                        "TRANSIT",                      // produs
                        "",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "75",                           // Pret FTVA
                        "0",                           // cota TVA
                        productNoteDocumentReference,             // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(transit);


                // --- 2) PRIMARY CUSTOMS DECLARATION ---
                String[] primary = {
                        String.valueOf(counter),                    // nr.crt
                        "",                                        // CIF/CNP
                        "",                                        // den client
                        "EUR",                                      // deviz
                        "PRIMARY CUSTOMS DECLARATION",              // produs
                        "",                               // Serie produs
                        "1",                                        // Cant
                        "BUC",                                      // UM
                        "50",                                       // Pret FTVA logic
                        "0",                                       // cota TVA
                        productNotePMrn,                         // nota produs
                        "",                                         // scutit TVA
                        ""                                          // motiv scutire TVA
                };
                writer.writeNext(primary);


                // --- 3) ADDITIONAL HS CODE ---
                if (Integer.parseInt(dto.getNrArticole()) > 1) {
                    String[] additionalHsCode = {
                            String.valueOf(counter),  // nr.crt
                            "",                         // CIF/CNP
                            "",                         // den client
                            "EUR",                         // deviz
                            "ADDITIONAL HS CODE",       // produs
                            "",                         // Serie produs
                            String.valueOf(Integer.parseInt(dto.getNrArticole()) - 1),                        // Cant
                            "BUC",                      // UM
                            "5",                 // Pret FTVA = 2.5% × A00
                            "0",                       // cota TVA
                            productNotePMrn,    // nota produs
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
