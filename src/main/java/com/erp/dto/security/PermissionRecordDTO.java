package com.erp.dto.security;

import com.erp.domain.security.HrModule;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PermissionRecordDTO {
    Long id;
    HrModule module;
    boolean viewOwn;
    boolean viewAll;
    boolean createPermission;
    boolean editPermission;
    boolean deletePermission;
    boolean approve;
}
