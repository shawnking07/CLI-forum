package enumObj;

public enum Operation {
    AUTH("ATH"),
    CREATE_THREAD("CRT"),
    POST_MESSAGE("MSG"),
    DELETE_MESSAGE("DLT"),
    EDIT_MESSAGE("EDT"),
    LIST_THREADS("LST"),
    READ_THREADS("RDT"),
    UPLOAD_FILE("UPD"),
    DOWNLOAD_FILE("DWN"),
    REMOVE_THREAD("RMV"),
    EXIT("XIT"),
    SHUTDOWN("SHT");

    public final String label;

    Operation(String label) {
        this.label = label;
    }
}
