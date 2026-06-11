package com.erp.dto.security;

import com.erp.domain.security.AppModule;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
public class ModulePermissionDTO {

    private AppModule module;

    private PermissionDTO permission;

    @Getter
    @Setter
    public static class PermissionDTO {

        private boolean viewOwn;
        private boolean viewAll;

        // ✅ Accept both "create" and "createPermission" from frontend
        @JsonProperty("create")
        private boolean create;

        // ✅ Accept both "edit" and "editPermission" from frontend
        @JsonProperty("edit")
        private boolean edit;

        private boolean deletePermission;
        private boolean approve;

        // ✅ Explicit getters to avoid Lombok bool naming issues
        public boolean isCreate() { return create; }
        public boolean isEdit() { return edit; }
        public boolean isViewOwn() { return viewOwn; }
        public boolean isViewAll() { return viewAll; }
        public boolean isDeletePermission() { return deletePermission; }
        public boolean isApprove() { return approve; }
    }
}