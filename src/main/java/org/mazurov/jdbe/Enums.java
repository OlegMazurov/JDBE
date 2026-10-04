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

public class Enums {


//    enum ValueType { // Bitmask     (!) sync enum changes with AnMetric.java
    public static final int VAL_NA        = 0;  // nothing specified (use this enum instead of 0)
    public static final int VAL_TIMEVAL   = 1;
    public static final int VAL_VALUE     = 2;
    public static final int VAL_PERCENT   = 4;
    public static final int VAL_DELTA     = 8;
    public static final int VAL_RATIO     = 16;
    public static final int VAL_INTERNAL  = 32;
    public static final int VAL_HIDE_ALL  = 64;  // hide all, but allows settings to be remembered

    public static final int LMS_USER =        0;   /* running in user mode */
    public static final int LMS_SYSTEM =      1;   /* running in sys call or page fault */
    public static final int LMS_TRAP =        2;   /* running in other trap */
    public static final int LMS_TFAULT =      3;   /* asleep in user text page fault */
    public static final int LMS_DFAULT =      4;   /* asleep in user data page fault */
    public static final int LMS_KFAULT =      5;   /* asleep in kernel page fault */
    public static final int LMS_USER_LOCK =   6;   /* asleep waiting for user-mode lock */
    public static final int LMS_SLEEP =       7;   /* asleep for any other reason */
    public static final int LMS_WAIT_CPU =    8;   /* waiting for CPU (latency) */
    public static final int LMS_STOPPED =     9;   /* stopped (/proc, jobcontrol, or lwp_stop) */
    public static final int LMS_LINUX_CPU =   10;  /* LINUX timer_create(CLOCK_THREAD_CPUTIME_ID) */
    public static final int LMS_KERNEL_CPU =  11;  /* LINUX timer_create(CLOCK_THREAD_CPUTIME_ID) */
    public static final int LMS_NUM_STATES =  12;  /* total number of above states */
    public static final int LMS_NUM_SOLARIS_MSTATES =     10;  /* LMS microstates thru LMS_STOPPED */

    public static final int CUNIT_NULL    = -1;
    public static final int CUNIT_BYTES   = -2;
    public static final int CUNIT_TIME    = -3;


    // sync enum changes with both AnMetric.java and AnVariable.java
    public enum ValueTag {
        VT_SHORT,
        VT_INT,
        VT_LLONG,
        VT_FLOAT,
        VT_DOUBLE,
        VT_HRTIME,
        VT_LABEL,
        VT_ADDRESS,
        VT_OFFSET,
        VT_ULLONG;

        static final ValueTag[] values = {
                null, VT_SHORT, VT_INT, VT_LLONG, VT_FLOAT, VT_DOUBLE,
                VT_HRTIME, VT_LABEL, VT_ADDRESS, VT_OFFSET, VT_ULLONG
        };

        public static ValueTag valueOf(int value) {
            return values[value];
        }
    };


    public enum MetricType {
        MET_NORMAL,      // functions, lines, pcs; src & disasm (non-compare)
        MET_CALL,        // callers-callees
        MET_DATA,        // dataspace
        MET_INDX,        // index objects
        MET_CALL_AGR,    // call tree
        MET_COMMON,      // Analyzer uses for DSP_DISASM, DSP_SOURCE, ...
        MET_IO,          // IO activity
        MET_SRCDIS,      // src & disasm (non comparison mode)
        MET_HEAP;        // Heap leaked list


        static final MetricType[] values = {
                MET_NORMAL, MET_CALL, MET_DATA, MET_INDX, MET_CALL_AGR,
                MET_COMMON, MET_IO, MET_SRCDIS, MET_HEAP
        };

        public static MetricType valueOf(int value) {
            return values[value];
        }
    }

    public enum ProfData_type { // aka "data_id" (not the same as Pckt_type "kind")
        DATA_SAMPLE(0, "Process-wide Resource Utilization"),      // Traditional collect "Samples"
        DATA_GCEVENT(1, "Java Garbage Collection Events"),     // Java Garbage Collection events
        DATA_HEAPSZ(2, "Heap Size"),      // heap size tracking based on heap tracing data
        DATA_CLOCK(3, "Clock Profiling"),
        DATA_HWC(4, "HW Counter Profiling"),         // hardware counter profiling data
        DATA_SYNCH(5, "Synchronization Tracing"),       // synchronization tracing data
        DATA_HEAP(6, "Heap Tracing"),        // heap tracing data
        DATA_MPI(7, "Not implemented"),         // MPI tracing data
        DATA_RACE(8, "Not implemented"),        // data race detection data
        DATA_DLCK(9, "Not implemented"),        // deadlock detection data
        DATA_OMP(10, "Not implemented"),         // OpenMP profiling data (fork events)
        DATA_OMP2(11, "Not implemented"),        // OpenMP profiling data (enter thread events)
        DATA_OMP3(12, "Not implemented"),        // OpenMP profiling data (enter task events)
        DATA_OMP4(13, "Not implemented"),        // OpenMP profiling data (parreg descriptions)
        DATA_OMP5(14, "Not implemented"),        // OpenMP profiling data (task descriptions)
        DATA_IOTRACE(15, "IO Tracing"),     // IO tracing data
        DATA_LAST(16, "Not implemented");

        private int value;
        private String uname;

        ProfData_type(int value, String uname) {
            this.value = value;
            this.uname = uname;
        }

        int getValue() {
            return value;
        }

        String getUserName() {
            return uname;
        }
    }

    public static String get_prof_data_type_name(ProfData_type t) {
        return t.toString();
    }

    public static String get_prof_data_type_uname(ProfData_type t) {
        return t.getUserName();
    }

    enum FuncListDisp_type {
        DSP_FUNCTION(1),
        DSP_LINE(2),
        DSP_PC(3),
        DSP_SOURCE(4),
        DSP_DISASM  (5),
        DSP_SELF    (6), // not a tab; ID for Callers-Callees fragment data
        DSP_CALLER  (7),
        DSP_CALLEE  (8), // not a tab; ID for Callers-Callees callees data
        DSP_CALLTREE(9),
        DSP_TIMELINE(10),
        DSP_STATIS  (11),
        DSP_EXP     (12),
        DSP_LEAKLIST(13),
        DSP_MEMOBJ  (14), // requires a specific subtype to define a tab
        DSP_DATAOBJ (15),
        DSP_DLAYOUT (16),
        DSP_SRC_FILE(17), // not a tab; Details information (?)
        DSP_IFREQ   (18),
        DSP_RACES   (19),
        DSP_INDXOBJ (20), // requires a specific subtype to define a tab
        DSP_DUALSOURCE(21),
        DSP_SOURCE_DISASM(22),
        DSP_DEADLOCKS    (23),
        DSP_MPI_TL  (24),
        DSP_MPI_CHART    (25),
        //DSP_TIMELINE_CLASSIC_TBR  (26),
        DSP_SOURCE_V2    (27), // comparison
        DSP_DISASM_V2    (28), // comparison
        //DSP_THREADS_TL   (29;
        //DSP_THREADS_CHART(30;
        DSP_IOACTIVITY   (31),
        DSP_OVERVIEW(32),
        DSP_IOVFD   (33),
        DSP_IOCALLSTACK  (34),
        DSP_MINICALLER   (37),
        DSP_HEAPCALLSTACK(39),
        DSP_CALLFLAME    (40),
        DSP_SAMPLE(99);

        int value;

        FuncListDisp_type(int value) {
            this.value = value;
        }
    };

    enum LibExpand {
        LIBEX_SHOW(0),
        LIBEX_HIDE(1),
        LIBEX_API(2);

        int value;
        LibExpand(int value) {
            this.value = value;
        }
    };


}