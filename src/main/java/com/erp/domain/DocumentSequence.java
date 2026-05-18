package com.erp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSequence {
    @Id
    private String documentType;
    
    private Long nextValue;
    
    @Version
    private Long version;
    
    public DocumentSequence(String documentType, Long nextValue) {
        this.documentType = documentType;
        this.nextValue = nextValue;
    }
}
