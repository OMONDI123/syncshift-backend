package co.ke.shiftsync.common;

/** What kind of thing an audit log entry is about. */
public enum AuditEntityType {
    SHIFT,
    SWAP,
    AVAILABILITY,
    USER,
    SECURITY,
    SETTINGS,
    LOCATION
}
