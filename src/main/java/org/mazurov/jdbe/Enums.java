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

    // Magic value stored in experiments that identifies which LMS states are valid
    public static final int LMS_MAGIC_ID_LINUX =           1;  // Linux: LMS_LINUX_CPU
    public static final int LMS_MAGIC_ID_ERKERNEL_USER =   2;  // er_kernel user: LMS_USER, LMS_SYSTEM
    public static final int LMS_MAGIC_ID_ERKERNEL_KERNEL = 3;  // er_kernel kernel: LMS_KERNEL_CPU
    public static final int LMS_MAGIC_ID_SOLARIS =        10;  // Solaris: LMS_USER thru LMS_STOPPED

    public static final String[] LMS_STATE_STRINGS = {
        "USER", "SYSTEM", "TRAP", "TFAULT", "DFAULT", "KFAULT",
        "USER_LOCK", "SLEEP", "WAIT_CPU", "STOPPED", "LINUX_CPU", "KERNEL_CPU"
    };
    public static final String[] LMS_STATE_USTRINGS = {
        "User CPU", "System CPU", "Trap CPU", "Text Page Fault", "Data Page Fault",
        "Kernel Page Fault", "User Lock", "Sleep", "Wait CPU", "Stopped",
        "User+System CPU", "Kernel CPU"
    };

    // dbe_types.h: these ordinals are stored in archive files; do not reorder
    public enum Platform_t {
        Unknown, Sparc, Sparcv9, Intel, Sparcv8plus, Java, Amd64, Aarch64, RISCV
    }

    public enum WSize_t {
        Wnone, W32, W64
    }

    public static final long ZERO_TIME = 0L;
    public static final long MAX_TIME = Long.MAX_VALUE;
    public static final long NANOSEC = 1_000_000_000L;

    public static final String DYNFUNC_SEGMENT = "DYNAMIC_FUNCTIONS";
    public static final int COL_WARN_FSTYPE = 201; // Emsgnum.h: writing to a potentially-distorting file system

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
    }

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

        public static ProfData_type fromInt(int value) {
            for (ProfData_type type : ProfData_type.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
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
    }

    public enum CmpMode {
        CMP_DISABLE(0),
        CMP_ENABLE (1),
        CMP_RATIO(2),
        CMP_DELTA(4);

        final int value;
        CmpMode(int value) {
            this.value = value;
        }

        public static CmpMode fromInt(int value) {
            for (CmpMode mode : CmpMode.values()) {
                if (mode.value == value) {
                    return mode;
                }
            }
            return null;
        }
    }

    public enum LibExpand {
        LIBEX_SHOW(0),
        LIBEX_HIDE(1),
        LIBEX_API(2);

        final int value;
        LibExpand(int value) {
            this.value = value;
        }
    }

    public enum VMode {
        VMODE_MACHINE,
        VMODE_USER,
        VMODE_EXPERT
    }

    public enum PrintMode {
        PM_TEXT,
        PM_HTML,
        PM_DELIM_SEP_LIST
    }

    public enum Prop_type {
        PROP_NONE,
        // commonly used properties (libcollector modules, er_print)
        PROP_ATSTAMP,     // hrtime_t, Filter: system HRT timestamp;
        // "Absolute TSTAMP"
        PROP_ETSTAMP,     // hrtime_t, Filter: nanoseconds from subexperiment start;
        // "subExperiment TSTAMP"
        PROP_TSTAMP,      // hrtime_t, Packet: system HRT timestamp
        // Filter: nanoseconds from founder start
        PROP_THRID,       // mapped to uint32_t by readPacket
        PROP_LWPID,       // mapped to uint32_t by readPacket
        PROP_CPUID,       // mapped to uint32_t by readPacket
        PROP_FRINFO,      // uint64_t frinfo
        PROP_EVT_TIME,    // hrtime_t Filter: Time delta
        // If TSTAMP taken at end of event, EVT_TIME will be positive
        // If TSTAMP taken at start of event, EVT_TIME will be negative
        // Note: clock and hwc profile events set EVT_TIME=0
        //    except Solaris Microstate events where NTICK>1:
        //    These will use EVT_TIME=(NTICK-1)*<tick duration>

        // DATA_SAMPLE
        PROP_SAMPLE,      // uint64_t sample number
        PROP_SMPLOBJ,     // Sample*

        // DATA_GCEVENT
        PROP_GCEVENT,     // uint64_t event id
        PROP_GCEVENTOBJ,  // GCEvent*

        // DATA_CLOCK
        PROP_MSTATE,      // unsigned ProfilePacket::mstate
        PROP_NTICK,       // unsigned ProfilePacket::value
        PROP_OMPSTATE,    // int ProfilePacket::ompstate
        PROP_MPISTATE,    // int ProfilePacket::mpistate

        // DATA_SAMPLE     // see PrUsage class, see PROP_MSTATE - TBR?
        PROP_UCPU,
        PROP_SCPU,
        PROP_TRAP,
        PROP_TFLT,
        PROP_DFLT,
        PROP_KFLT,
        PROP_ULCK,
        PROP_TSLP,
        PROP_WCPU,
        PROP_TSTP,

        // DATA_SYNCH
        PROP_SRQST,       // hrtime_t SyncPacket::requested
        PROP_SOBJ,        // Vaddr SyncPacket::objp

        // DATA_HWC
        PROP_HWCTAG,      // uint32_t HWCntrPacket::tag;
        PROP_HWCINT,      // uint64_t HWCntrPacket::interval
        PROP_VADDR,       // Vaddr HWCntrPacket::dbeVA->eaddr
        PROP_PADDR,       // Vaddr HWCntrPacket::dbePA->eaddr
        PROP_HWCDOBJ,     // DataObject* HWCntrPacket::dobj
        PROP_VIRTPC,      // Vaddr HWCntrPacket::eventVPC
        PROP_PHYSPC,      // Vaddr HWCntrPacket::eventPPC
        PROP_EA_PAGESIZE, // uint32_t HWCntrPacket::ea_pagesize
        PROP_PC_PAGESIZE, // uint32_t HWCntrPacket::pc_pagesize
        PROP_EA_LGRP,     // uint32_t HWCntrPacket::ea_lgrp
        PROP_PC_LGRP,     // uint32_t HWCntrPacket::pc_lgrp
        PROP_LWP_LGRP_HOME, // uint32_t HWCntrPacket::lwp_lgrp_home
        PROP_PS_LGRP_HOME,  // uint32_t HWCntrPacket::ps_lgrp_home
        PROP_MEM_LAT,     // uint64_t HWCntrPacket::latency
        PROP_MEM_SRC,     // uint64_t HWCntrPacket::data_source

        // DATA_HEAP
        PROP_HTYPE,       // Heap_type HeapPacket::mtype
        PROP_HSIZE,       // Size HeapPacket::size (bytes alloc'd by this event)
        PROP_HVADDR,      // Vaddr HeapPacket::vaddr
        PROP_HOVADDR,     // Vaddr HeapPacket::ovaddr
        PROP_HLEAKED,     // Size HeapPacket::leaked (net bytes leaked)
        PROP_HMEM_USAGE,  // Size heap memory usage
        PROP_HFREED,      // Size (bytes freed by this event)
        PROP_HCUR_ALLOCS, // int64_t (net allocations running total.  Recomputed after each filter)
        PROP_HCUR_NET_ALLOC, // int64_t (net allocation for this packet.  Recomputed after each filter)
        PROP_HCUR_LEAKS,  // Size (net leaks running total.  Recomputed after each filter)

        // DATA_IOTRACE
        PROP_IOTYPE,      // IOTrace_type IOTracePacket::iotype
        PROP_IOFD,        // int32_t IOTracePacket::fd
        PROP_IONBYTE,     // Size_type IOTracePacket::nbyte
        PROP_IORQST,      // hrtime_t IOTracePacket::requested
        PROP_IOOFD,       // int32_t IOTracePacket::ofd
        PROP_IOFSTYPE,    // FileSystem_type IOTracePacket::fstype
        PROP_IOFNAME,     // char IOTracePacket::fname
        PROP_IOVFD,       // int32_t virtual file descriptor

        // DATA_MPI
        PROP_MPITYPE,     // MPI_type MPIPacket::mpitype
        PROP_MPISCOUNT,   // Size MPIPacket::scount
        PROP_MPISBYTES,   // Size MPIPacket::sbytes
        PROP_MPIRCOUNT,   // Size MPIPacket::rcount
        PROP_MPIRBYTES,   // Size MPIPacket::rbytes

        // DATA_OMP*
        PROP_CPRID,       // uint64_t (Note: not same as "PROP_CPRID" below)
        PROP_PPRID,       // uint64_t OMPPacket::omp_pprid
        PROP_TSKID,       // uint64_t (Note: not same as "PROP_CPRID" below)
        PROP_PTSKID,      // uint64_t OMPPacket::omp_ptskid
        PROP_PRPC,        // uint64_t OMPPacket::omp_prpc

        // DATA_RACE
        PROP_RTYPE,       // Race_type RacePacket::rtype
        PROP_RID,         // uint32_t RacePacket::id
        PROP_RVADDR,      // Vaddr RacePacket::vaddr
        PROP_RCNT,        // uint32_t RacePacket::count
        PROP_LEAFPC,      // Vaddr CommonPacket::leafpc

        // DATA_DLCK
        PROP_DID,         // uint32_t DeadlockPacket::id
        PROP_DTYPE,       // Deadlock_Lock_type DeadlockPacket::lock_type
        PROP_DLTYPE,      // Deadlock_type DeadlockPacket::dl_type
        PROP_DVADDR,      // Vaddr DeadlockPacket::lock_addr

        // Synthetic properties (queries only)
        PROP_STACKID,
        PROP_STACK,       // void* Generic; mapped to M, U, or XSTACK
        PROP_MSTACK,      // void* machine stack
        PROP_USTACK,      // void* user_stack
        PROP_XSTACK,      // void* expert_stack
        PROP_HSTACK,      // void* hide_stack
        //PROP_CPRID,       // void* (Note: not same as "PROP_CPRID" above)
        //PROP_TSKID,       // void* (Note: not same as "PROP_TSKID" above)
        PROP_JTHREAD,     // JThread* CommonPacket::jthread
        PROP_LEAF,        // uint64_t stack leaf function
        PROP_DOBJ,        // "DOBJ" DataObject*
        PROP_SAMPLE_MAP,  // Map events to SAMPLE using sample's time range
        PROP_GCEVENT_MAP, // Map events to GCEVENT using gcevent's time range
        PROP_PID,         // int unix getpid()
        PROP_EXPID,       // int Experiment->getUserExpId(), AKA process number, >=1.
        PROP_EXPID_CMP,   // int "Comparable PROP_EXPID".  In compare mode, if this
        //              process has been matched to another groups' process,
        //              returns PROP_EXPID of the matching process with the
        //              lowest PROP_EXPGRID value.  Otherwise returns PROP_EXPID.
        PROP_EXPGRID,     // int Comparison group number.  >=0, 0 is Baseline.
        PROP_PARREG,      // "PARREG" uint64_t (see 6436500) TBR?
        PROP_TSTAMP_LO,   // hrtime_t Filter: Event's low TSTAMP
        PROP_TSTAMP_HI,   // hrtime_t Filter: Event's high TSTAMP
        PROP_TSTAMP2,     // hrtime_t Filter: End TSTAMP (TSTAMP<=TSTAMP2)
        PROP_FREQ_MHZ,    // int frequency in MHZ (for converting HWC profiling cycles to time)
        PROP_NTICK_USEC,  // hrtime_t Clock profiling interval, microseconds (PROP_NTICK * Experiment->ptimer_usec)
        PROP_IOHEAPBYTES, // Size PROP_HSIZE or PROP_IONBYTE
        PROP_STACKL,      // void* Generic; mapped to M, U, or XSTACK for DbeLine
        PROP_MSTACKL,     // void* machine stack
        PROP_USTACKL,     // void* user_stack
        PROP_XSTACKL,     // void* expert_stack
        PROP_STACKI,      // void* Generic; mapped to M, U, or XSTACK for DbeInstr
        PROP_MSTACKI,     // void* machine stack
        PROP_USTACKI,     // void* user_stack
        PROP_XSTACKI,     // void* expert_stack
        PROP_DDSCR_LNK,   // long long index into DataDescriptor table for a related event
        PROP_VOIDP_OBJ,   // void* pointer to object containing metadata
        PROP_LAST
    }

    public enum VType_type {
        TYPE_NONE,
        TYPE_INT32,
        TYPE_UINT32,
        TYPE_INT64,
        TYPE_UINT64,
        TYPE_STRING,
        TYPE_DOUBLE,
        TYPE_OBJ,
        TYPE_DATE, // Used in FieldDescr only, mapped to TYPE_UINT64 in PropDescr
        TYPE_BOOL, // Used only to describe filter props
        TYPE_ENUM, // Used only to describe filter props

        TYPE_LAST
    }

    public enum Data_flag {
        DDFLAG_NOSHOW(0x01);

        final int value;
        Data_flag(int value) {
            this.value = value;
        }
    }

    public enum Prop_flag {
        PRFLAG_NOSHOW(0x40);

        final int value;
        Prop_flag(int value) {
            this.value = value;
        }
    }

    public enum Pckt_type {
        EMPTY_PCKT(0),
        PROF_PCKT(1),
        SYNC_PCKT(2),
        HW_PCKT(3),
        XHWC_PCKT(4),
        HEAP_PCKT(5),
        MPI_PCKT(6),
        MHWC_PCKT(7),
        OPROF_PCKT(8),
        OMP_PCKT(9),
        RACE_PCKT(10),
        FRAME_PCKT(11),
        OMP2_PCKT(12),
        DEADLOCK_PCKT(13),
        OMP3_PCKT(14),
        OMP4_PCKT(15),
        OMP5_PCKT(16),
        UID_PCKT(17),
        FRAME2_PCKT(18),
        IOTRACE_PCKT(19),
        LAST_PCKT(20),        /* last data packet type */
        CLOSED_PCKT(65535);   /*  -1, this packet closes a block */

        final int value;
        Pckt_type(int value) {
            this.value = value;
        }

        public static Pckt_type fromInt(int value) {
            for (Pckt_type type : Pckt_type.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum Heap_type {
        MALLOC_TRACE(0),
        FREE_TRACE(1),
        REALLOC_TRACE(2),
        MMAP_TRACE(3),
        MUNMAP_TRACE(4),
        HEAPTYPE_LAST(5);

        final int value;
        Heap_type(int value) {
            this.value = value;
        }
    }
}
