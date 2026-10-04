/* Copyright (C) 2026 Oleg Mazurov

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses>. */

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
