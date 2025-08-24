package org.mazurov.jdbe;

public class IndexObject {

    // for use in index object definitions
    public static final int INDXOBJ_EXPGRID_SHIFT   = 60;
    public static final int INDXOBJ_EXPID_SHIFT     = 32;
    public static final int INDXOBJ_PAYLOAD_SHIFT   = 0;
    public static final long INDXOBJ_EXPGRID_MASK    =
            ((1L << (64 - INDXOBJ_EXPGRID_SHIFT)) - 1);
    public static final long INDXOBJ_EXPID_MASK      =
            ((1L << (INDXOBJ_EXPGRID_SHIFT - INDXOBJ_EXPID_SHIFT)) - 1);
    public static final long INDXOBJ_PAYLOAD_MASK    =
            ((1L << (INDXOBJ_EXPID_SHIFT - INDXOBJ_PAYLOAD_SHIFT)) - 1);

    enum IndexObjTypes_t {
        INDEX_THREADS,
        INDEX_CPUS,
        INDEX_SAMPLES,
        INDEX_GCEVENTS,
        INDEX_SECONDS,
        INDEX_PROCESSES,
        INDEX_EXPERIMENTS,
        INDEX_BYTES,
        INDEX_DURATION,
        INDEX_LAST    // never used; marks the count of precompiled items
    }

    static class IndexObjType_t {
        int type;
        String name;           // used as input
        String i18n_name;      // used for output
        String index_expr_str;
        Expression index_expr;
        char mnemonic;
        String short_description;
        String long_description;
//        MemObjType_t memObj;

    }
}
