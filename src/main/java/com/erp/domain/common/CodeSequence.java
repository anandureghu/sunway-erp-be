package com.erp.domain.common;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSequence {

    @Id
    @Column(name = "code_key")
    private String codeKey;  // e.g., CONTRACT_2026

    @Column(nullable = false)
    private Long lastNumber;
}