package com.erp.dto.security;

import com.erp.domain.security.AppModule;
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
        private boolean createOwn;
        private boolean createAll;
        private boolean editOwn;
        private boolean editAll;
        private boolean deleteOwn;
        private boolean deleteAll;
        private boolean approve;

        // Explicit getters to avoid Lombok boolean naming surprises.
        public boolean isViewOwn() { return viewOwn; }
        public boolean isViewAll() { return viewAll; }
        public boolean isCreateOwn() { return createOwn; }
        public boolean isCreateAll() { return createAll; }
        public boolean isEditOwn() { return editOwn; }
        public boolean isEditAll() { return editAll; }
        public boolean isDeleteOwn() { return deleteOwn; }
        public boolean isDeleteAll() { return deleteAll; }
        public boolean isApprove() { return approve; }
    }
}