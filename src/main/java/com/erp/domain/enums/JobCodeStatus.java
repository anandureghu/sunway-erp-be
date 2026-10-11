package com.erp.domain.enums;

/**
 * Position state of a job code.
 * <ul>
 *   <li>VACANT — free to assign (the state of a newly created code).</li>
 *   <li>OCCUPIED — held by a still-employed person. Set automatically when the code is
 *       assigned, and cleared back to VACANT when the holder leaves or becomes inactive.</li>
 *   <li>ON_HOLD — set by HR on a vacant code (pending department approval, or held for a
 *       particular candidate). Cannot be assigned until HR sets it back to VACANT.</li>
 * </ul>
 */
public enum JobCodeStatus {
    VACANT,
    OCCUPIED,
    ON_HOLD
}
