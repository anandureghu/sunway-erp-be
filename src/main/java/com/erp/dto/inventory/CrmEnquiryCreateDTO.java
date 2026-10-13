package com.erp.dto.inventory;

import com.erp.domain.inventory.CrmPipelineStatus;
import com.erp.domain.inventory.CrmProspectType;
import com.erp.domain.inventory.CrmRelationshipStage;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmEnquiryCreateDTO {
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
}
