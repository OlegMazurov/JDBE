package org.mazurov.jdbe;

public class LoadObject {
    enum seg_type {
        SEG_TEXT,
        SEG_DATA,
        SEG_BSS,
        SEG_HEAP,
        SEG_STACK,
        SEG_DEVICE,
        SEG_UNKNOWN
    };

    seg_type type = seg_type.SEG_UNKNOWN;
    int seg_idx;                  // for compatibility (ADDRESS)
    String pathname;               // User name of object file

    public String get_pathname() {
        return pathname;
    }

    public String get_name() {
        return "Not implemented";
    }
}
