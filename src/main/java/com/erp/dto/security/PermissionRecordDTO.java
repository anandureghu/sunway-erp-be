package com.erp.dto.security;

import com.erp.domain.security.AppModule;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PermissionRecordDTO {
    Long id;
    AppModule module;
    boolean viewOwn;
    boolean viewAll;
    boolean createPermission;
    boolean editPermission;
    boolean deletePermission;
    boolean approve;
}
