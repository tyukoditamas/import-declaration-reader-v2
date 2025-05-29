package org.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportDeclaration {
    private String nrDestinatar;
    private String mrn;
    private String nrArticole;
    private String nrContainer;
    private String referintaDocument;
}
