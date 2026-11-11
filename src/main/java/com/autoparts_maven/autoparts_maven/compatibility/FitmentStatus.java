package com.autoparts_maven.autoparts_maven.compatibility;

public enum FitmentStatus {
    /** Came from an import; not shown to customers until reviewed. */
    IMPORTED,
    /** Confirmed by an administrator against its source; shown to customers. */
    VALIDATED,
    /** Checked and found wrong; kept so a re-import does not bring it back unnoticed. */
    REJECTED
}
