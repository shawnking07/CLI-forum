package model;

import java.util.Arrays;
import java.util.Optional;

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

    public static Operation fromString(String label) {
        Optional<Operation> any = Arrays.stream(Operation.values())
                .filter(v -> v.label.equals(label))
                .findAny();
        if (any.isPresent()) {
            return any.get();
        } else {
            throw new IllegalArgumentException("Not such operation");
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
