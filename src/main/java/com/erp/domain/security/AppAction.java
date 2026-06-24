package com.erp.domain.security;

public enum AppAction {
    VIEW_OWN,
    VIEW_ALL,
    // Coarse "can do this at all" — true when either the OWN or ALL grant is set.
    // Kept for existing @RequiresPermission / @PreAuthorize gates.
    CREATE,
    EDIT,
    DELETE,
    // Scoped variants — OWN = only the holder's own records, ALL = everyone's.
    CREATE_OWN,
    CREATE_ALL,
    EDIT_OWN,
    EDIT_ALL,
    DELETE_OWN,
    DELETE_ALL,
    APPROVE
}
