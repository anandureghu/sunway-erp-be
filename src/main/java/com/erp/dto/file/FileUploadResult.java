package com.erp.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadResult {

    /**
     * Path inside the container (saved in DB)
     */
    private String blobPath;

    /**
     * Whether this file is public or private
     */
    private boolean isPublic;
}
