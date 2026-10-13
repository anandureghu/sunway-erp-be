package com.erp.dto.inventory;

import com.erp.domain.inventory.CrmPipelineStatus;
import com.erp.domain.inventory.CrmProspectType;
import com.erp.domain.inventory.CrmRelationshipStage;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmEnquiryResponseDTO {
    private Long id;
    private String name;
    private String organizationName;
    private CrmProspectType prospectType;
    private String email;
    private String phone;
    private String secondaryContact;
    private String address;
    private String source;
    private String interest;
    private String summary;
    private CrmPipelineStatus pipelineStatus;
    private CrmRelationshipStage relationshipStage;
    private Long customerId;
    private Long vendorId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<CrmEnquiryNoteDTO> notes;
}
