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

import java.sql.Time;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mazurov.jdbe.Enums.*;

public class BaseMetric {

    static private final int NSUBTYPES = 2;   // STATIC/EXCLUSIVE, INCLUSIVE

    /** METRIC_*_PRECISION determine the least threshold value
     * for time measured metrics. Any event that counts for less
     * than 1sec/METRIC_PRECISION is discarded.
     */
    static private final long METRIC_SIG_PRECISION = TimeUnit.SECONDS.toMicros(1);
    static private final long METRIC_HR_PRECISION = TimeUnit.SECONDS.toMicros(1);

    static AtomicInteger last_id = new AtomicInteger(0);

    private Expression cond;                // determines which packets to evaluate
    private String cond_spec;               // used to generate "cond"
    private Expression val;                 // determines the numeric value for packet
    private String val_spec;                // used to generate "val"

    private Expression expr;    // for comparison: an additional expression to determine
                                // which packets to eval. Should be null otherwise.
    private String expr_spec;   // used to generate "expr"
    int id;                     // unique id (assigned to last_id @ "new")
    Type type;                  // e.g. HWCNTR
    private String aux;                        // for HWCs only: Hwcentry ctr->name
    private String cmd;                        // the .rc metric command, e.g. "total"
    private String username;                   // e.g. "GTXT("Total Wait Time")"
    int flavors;                      // bitmask of SubType capabilities
    int value_styles;                 // bitmask of ValueType capabilities
    int[] default_visbits = new int[NSUBTYPES];   // ValueType, e.g. VAL_VALUE|VAL_TIMEVAL
    ValueTag valtype;                 // e.g. VT_LLONG
    long precision;              // e.g. METRIC_SIG_PRECISION, 1, etc.
//    Hwcentry hw_ctr;                 // HWC definition
    ProfData_type packet_type;        // e.g. DATA_HWC, or -1 for N/A
    boolean zeroThreshold;               // deadlock stuff

    int clock_unit;

    String legend;                 // for comparison: add'l column text


    enum Type {
        // Subtype==STATIC metrics:
        ONAME(1),  //ONAME must be 1
        SIZES(2),
        ADDRESS(3),
        // Clock Profiling, Derived Metrics:
        CP_TOTAL(4),
        CP_TOTAL_CPU(5),
        // Clock profiling, Solaris Microstates (LMS_* defines)
        CP_LMS_USER(6),
        CP_LMS_SYSTEM(7),
        CP_LMS_TRAP(8),
        CP_LMS_TFAULT(9),
        CP_LMS_DFAULT(10),
        CP_LMS_KFAULT(11),
        CP_LMS_USER_LOCK(12),
        CP_LMS_SLEEP(13),
        CP_LMS_WAIT_CPU(14),
        CP_LMS_STOPPED(15),
        // Kernel clock profiling
        CP_KERNEL_CPU(16),
        // Sync Tracing
        SYNC_WAIT_TIME(17),
        SYNC_WAIT_COUNT(18),
        // HWC
        HWCNTR(19),
        // Heap Tracing:
        HEAP_ALLOC_CNT(20),
        HEAP_ALLOC_BYTES(21),
        HEAP_LEAK_CNT(22),
        HEAP_LEAK_BYTES(23),
        // I/O Tracing:
        IO_READ_BYTES(24),
        IO_READ_CNT(25),
        IO_READ_TIME(26),
        IO_WRITE_BYTES(27),
        IO_WRITE_CNT(28),
        IO_WRITE_TIME(29),
        IO_OTHER_CNT(30),
        IO_OTHER_TIME(31),
        IO_ERROR_CNT(32),
        IO_ERROR_TIME(33),
        // MPI Tracing:
        MPI_TIME(34),
        MPI_SEND(35),
        MPI_BYTES_SENT(36),
        MPI_RCV(37),
        MPI_BYTES_RCVD(38),
        MPI_OTHER(39),
        // OMP states:
        OMP_NONE(40),
        OMP_OVHD(41),
        OMP_WORK(42),
        OMP_IBAR(43),
        OMP_EBAR(44),
        OMP_WAIT(45),
        OMP_SERL(46),
        OMP_RDUC(47),
        OMP_LKWT(48),
        OMP_CTWT(49),
        OMP_ODWT(50),
        OMP_MSTR(51),
        OMP_SNGL(52),
        OMP_ORDD(53),
        OMP_MASTER_THREAD(54),
        // MPI states:
        MPI_WORK(55),
        MPI_WAIT(56),
        // Races and Deadlocks
        RACCESS(57),
        DEADLOCKS(58),
        // Derived Metrics
        DERIVED(59);

        static final Type[] values = {
            null, ONAME, SIZES, ADDRESS, CP_TOTAL, CP_TOTAL_CPU, CP_LMS_USER, CP_LMS_SYSTEM, CP_LMS_TRAP,
            CP_LMS_TFAULT, CP_LMS_DFAULT, CP_LMS_KFAULT, CP_LMS_USER_LOCK, CP_LMS_SLEEP, CP_LMS_WAIT_CPU,
            CP_LMS_STOPPED, CP_KERNEL_CPU, SYNC_WAIT_TIME, SYNC_WAIT_COUNT, HWCNTR, HEAP_ALLOC_CNT,
            HEAP_ALLOC_BYTES, HEAP_LEAK_CNT, HEAP_LEAK_BYTES, IO_READ_BYTES, IO_READ_CNT, IO_READ_TIME,
            IO_WRITE_BYTES, IO_WRITE_CNT, IO_WRITE_TIME, IO_OTHER_CNT, IO_OTHER_TIME, IO_ERROR_CNT,
            IO_ERROR_TIME, MPI_TIME, MPI_SEND, MPI_BYTES_SENT, MPI_RCV, MPI_BYTES_RCVD, MPI_OTHER,
            OMP_NONE, OMP_OVHD, OMP_WORK, OMP_IBAR, OMP_EBAR, OMP_WAIT, OMP_SERL, OMP_RDUC, OMP_LKWT,
            OMP_CTWT, OMP_ODWT, OMP_MSTR, OMP_SNGL, OMP_ORDD, OMP_MASTER_THREAD,
            MPI_WORK, MPI_WAIT, RACCESS, DEADLOCKS, DERIVED
        };
        final int value;

        Type(int value) {
            this.value = value;
        }

        static Type valueOf(int i) {
            return values[i];
        }
    };

    // enum SubType
    // sync enum changes with AnMetric.java
    public static final int STATIC = 1; // Type==SIZES, ADDRESS, ONAME
    public static final int EXCLUSIVE = 2;
    public static final int INCLUSIVE = 4;
    public static final int ATTRIBUTED = 8;
    public static final int DATASPACE = 16; // Can be accessed in dataspace views


    private void init(Type t) {
        id = last_id.getAndIncrement();
        type = t;
        aux = null;
        cmd = null;
        username = null;
//        hw_ctr = null;
        cond = null;
        val = null;
        expr = null;
        cond_spec = null;
        val_spec = null;
        expr_spec = null;
        legend = null;
//        definition = null;
//        dependent_bm = null;
        zeroThreshold = false;
//        clock_unit = (Presentation_clock_unit) 0;
        Arrays.fill(default_visbits, VAL_NA);
        valtype = ValueTag.VT_DOUBLE;
        precision = METRIC_HR_PRECISION;
        flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
        value_styles = VAL_TIMEVAL | VAL_PERCENT;
    }

    public BaseMetric(Type t) {
        init(t);
        switch (t) {
            case CP_LMS_USER:
            case CP_LMS_SYSTEM:
            case CP_LMS_WAIT_CPU:
            case CP_LMS_USER_LOCK:
            case CP_LMS_TFAULT:
            case CP_LMS_DFAULT:
            case OMP_MASTER_THREAD:
            case CP_TOTAL:
            case CP_TOTAL_CPU:
            case CP_LMS_TRAP:
            case CP_LMS_KFAULT:
            case CP_LMS_SLEEP:
            case CP_LMS_STOPPED:
            case OMP_NONE:
            case OMP_OVHD:
            case OMP_WORK:
            case OMP_IBAR:
            case OMP_EBAR:
            case OMP_WAIT:
            case OMP_SERL:
            case OMP_RDUC:
            case OMP_LKWT:
            case OMP_CTWT:
            case OMP_ODWT:
            case OMP_MSTR:
            case OMP_SNGL:
            case OMP_ORDD:
            case CP_KERNEL_CPU:
                // all of these are floating point, precision = clock profile tick
                valtype = ValueTag.VT_DOUBLE;
                precision = METRIC_SIG_PRECISION;
                flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
                value_styles = VAL_TIMEVAL | VAL_PERCENT;
                break;
            case SYNC_WAIT_TIME:
            case IO_READ_TIME:
            case IO_WRITE_TIME:
            case IO_OTHER_TIME:
            case IO_ERROR_TIME:
                // all of these are floating point, precision = hrtime tick
                valtype = ValueTag.VT_DOUBLE;
                precision = METRIC_HR_PRECISION;
                flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
                value_styles = VAL_TIMEVAL | VAL_PERCENT;
                break;
            case SYNC_WAIT_COUNT:
            case HEAP_ALLOC_CNT:
            case HEAP_LEAK_CNT:
            case IO_READ_CNT:
            case IO_WRITE_CNT:
            case IO_OTHER_CNT:
            case IO_ERROR_CNT:
                valtype = ValueTag.VT_LLONG;
                precision = 1;
                flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
                value_styles = VAL_VALUE | VAL_PERCENT;
                break;
            case RACCESS:
            case DEADLOCKS:
                // all of these are integer
                valtype = ValueTag.VT_LLONG;
                precision = 1;
                flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
                value_styles = VAL_VALUE | VAL_PERCENT;
                zeroThreshold = true;
                break;
            case HEAP_ALLOC_BYTES:
            case HEAP_LEAK_BYTES:
            case IO_READ_BYTES:
            case IO_WRITE_BYTES:
                // all of these are longlong
                valtype = ValueTag.VT_ULLONG;
                precision = 1;
                flavors = EXCLUSIVE | INCLUSIVE | ATTRIBUTED;
                value_styles = VAL_VALUE | VAL_PERCENT;
                break;
            case SIZES:
                valtype = ValueTag.VT_LLONG;
                precision = 1;
                flavors = STATIC;
                value_styles = VAL_VALUE;
                break;
            case ADDRESS:
                valtype = ValueTag.VT_ADDRESS;
                precision = 1;
                flavors = STATIC;
                value_styles = VAL_VALUE;
                break;
            case ONAME:
                valtype = ValueTag.VT_LABEL;
                precision = 0;
                flavors = STATIC;
                value_styles = VAL_VALUE;
                break;
            case HWCNTR: // We should call the other constructor for hwc metric
            default:
                throw new IllegalArgumentException();
        }
        specify();
    }

    public BaseMetric(BaseMetric m) {
        id = m.id;
        type = m.type;
        aux = m.aux;
        cmd = m.cmd;
        username = m.username;
        flavors = m.flavors;
        value_styles = m.value_styles;
        valtype = m.valtype;
        precision = m.precision;
//        hw_ctr = m.hw_ctr;
        packet_type = m.packet_type;
        zeroThreshold = m.zeroThreshold;
//        clock_unit = m.clock_unit;
        System.arraycopy(m.default_visbits, 0, default_visbits, 0, NSUBTYPES);
        if (m.cond_spec != null) {
            cond_spec = m.cond_spec;
            cond = m.cond.copy();
        } else {
            cond = null;
            cond_spec = null;
        }
        if (m.val_spec != null) {
            val_spec = m.val_spec;
            val = m.val.copy();
        } else {
            val = null;
            val_spec = null;
        }
        if (m.expr_spec != null) {
            expr_spec = m.expr_spec;
            expr = m.expr.copy();
        } else {
            expr = null;
            expr_spec = null;
        }
        legend = m.legend;
//        definition = null;
//        if (m.definition != null)
//            definition = Definition::add_definition (m.definition.def);
//        dependent_bm = m.dependent_bm;
    }

    public boolean is_internal() {
        return (get_value_styles () & VAL_INTERNAL) != 0;
    }

    public int get_id() {
        return id;
    }

    Type get_type() {
        return type;
    }

//    Hwcentry get_hw_ctr() {
//        return hw_ctr;
//    }

    public String get_aux() {
        return aux;
    }
    public String get_username() {
        return username;
    }

    public String get_cmd() {
        return cmd;
    }

    public int get_flavors() {
        return flavors;
    }

    public boolean hasFlavor(int subType) {
        return (flavors & subType) != 0;
    }

    public int get_value_styles() {
        return value_styles;
    }

    public ProfData_type get_packet_type() {
        return packet_type;
    }

    public String get_expr_spec() {
        return expr_spec;
    }

    public void set_expr_spec(String expr_spec) {
        id = last_id.getAndIncrement();
        if (this.expr_spec != null) {
            this.expr_spec = null;
            this.expr = null;
        }
        if (expr_spec != null) {
            this.expr = DbeSession.getInstance().ql_parse(expr_spec);
            if (this.expr == null) {
                System.err.printf("Invalid expression in metric specification `%s'\n", expr_spec);
                return;
            }
            this.expr_spec = expr_spec;
        }
    }

    private void set_cond_spec(String _cond_spec) {
        if (cond_spec != null) {
            cond_spec = null;
            cond = null;
        }
        if (_cond_spec != null) {
            cond = DbeSession.getInstance().ql_parse(_cond_spec);
            if (cond == null) {
                throw new IllegalArgumentException(
                        String.format("Invalid expression in metric specification `%s'", _cond_spec));
            }
            cond_spec = _cond_spec;
        }
    }

    private void set_val_spec(String _val_spec) {
        if (val_spec != null) {
            val_spec = null;
            val = null;
        }
        if (_val_spec != null) {
            val = DbeSession.getInstance().ql_parse(_val_spec);
            if (val == null) {
                throw new IllegalArgumentException(
                        String.format("Invalid expression in metric specification `%s'", _val_spec));
            }
            val_spec = _val_spec;
        }
    }

    private void specify_mstate_metric(int st) {
        specify_prof_metric (String.format("MSTATE==%d", st));
    }

    private void specify_ompstate_metric(int st) {
        specify_prof_metric(String.format("OMPSTATE==%d", st));
    }

    private void specify_prof_metric(String _cond_spec) {
        packet_type = ProfData_type.DATA_CLOCK;
        specify_metric(_cond_spec, "NTICK_USEC"); // microseconds
    }

    private void specify_metric(String _cond_spec, String _val_spec) {
        set_cond_spec(_cond_spec);
        set_val_spec(_val_spec);
    }

    private void specify() {
//        enum
//        {
//            IDLE_STATE_BITS =
//                    (1 << OMP_IDLE_STATE) | (1 << OMP_IBAR_STATE) | (1 << OMP_EBAR_STATE) |
//                            (1 << OMP_LKWT_STATE) | (1 << OMP_CTWT_STATE) | (1 << OMP_ODWT_STATE) |
//                            (1 << OMP_ATWT_STATE) | (1 << OMP_TSKWT_STATE),
//                    LMS_USER_BITS =
//                            (1 << OMP_NO_STATE) | (1 << OMP_WORK_STATE) | (1 << OMP_SERL_STATE) |
//                                    (1 << OMP_RDUC_STATE)
//        };

        clock_unit = CUNIT_TIME;
        switch (type) {
            case SIZES:
                username = "Size";
                clock_unit = CUNIT_BYTES;
                cmd = "size";
                break;
            case ADDRESS:
                username = "PC Address";
                cmd = "address";
                break;
            case ONAME:
                username = "Name";
                cmd = "name";
                break;
            case CP_LMS_SYSTEM:
                username = "System CPU Time";
                specify_mstate_metric(LMS_SYSTEM);
                cmd = "system";
                break;
            case CP_TOTAL_CPU:
                username = "Total CPU Time";
                specify_prof_metric(
                        String.format("(MSTATE==%d)||(MSTATE==%d)||(MSTATE==%d)||(MSTATE==%d)",
                        LMS_USER, LMS_SYSTEM, LMS_TRAP, LMS_LINUX_CPU));
                cmd = "totalcpu";
                break;
            case CP_TOTAL:
                username = "Total Thread Time";
                specify_prof_metric(
                        String.format("(MSTATE!=%d)&&(MSTATE!=%d)", LMS_KERNEL_CPU, LMS_LINUX_CPU));
                cmd = "total";
                break;
            case CP_KERNEL_CPU:
                username = "Kernel CPU Time";
                specify_mstate_metric (LMS_KERNEL_CPU);
                cmd = "kcpu";
                break;
            case CP_LMS_USER:
                username = "User CPU Time";
                specify_mstate_metric (LMS_USER);
                cmd = "user";
                break;
            case CP_LMS_WAIT_CPU:
                username = "Wait CPU Time";
                specify_mstate_metric (LMS_WAIT_CPU);
                cmd = "wait";
                break;
            case CP_LMS_USER_LOCK:
                username = "User Lock Time";
                specify_mstate_metric (LMS_USER_LOCK);
                cmd = "lock";
                break;
            case CP_LMS_TFAULT:
                username = "Text Page Fault Time";
                specify_mstate_metric (LMS_TFAULT);
                cmd = "textpfault";
                break;
            case CP_LMS_DFAULT:
                username = "Data Page Fault Time";
                specify_mstate_metric (LMS_DFAULT);
                cmd = "datapfault";
                break;
            case CP_LMS_TRAP:
                username = "Trap CPU Time";
                specify_mstate_metric (LMS_TRAP);
                cmd = "trap";
                break;
            case CP_LMS_KFAULT:
                username = "Kernel Page Fault Time";
                specify_mstate_metric (LMS_KFAULT);
                cmd = "kernelpfault";
                break;
            case CP_LMS_SLEEP:
                username = "Sleep Time";
                specify_mstate_metric (LMS_SLEEP);
                cmd = "sleep";
                break;
            case CP_LMS_STOPPED:
                username = "Stopped Time";
                specify_mstate_metric (LMS_STOPPED);
                cmd = "stop";
                break;
//            case OMP_MASTER_THREAD:
//                username = "Master Thread Time";
//                specify_prof_metric ("LWPID==1");
//                cmd = "masterthread";
//                break;
//            case OMP_OVHD:
//                username = "OpenMP Overhead Time";
//                specify_ompstate_metric (OMP_OVHD_STATE);
//                cmd = "ompovhd";
//                break;
//            case OMP_WORK:
//                username = "OpenMP Work Time";
//                snprintf (buf, sizeof (buf),
//                        "(OMPSTATE>=0) && (MSTATE==%d) && ((1<<OMPSTATE) & %d)"),
//                        LMS_USER, LMS_USER_BITS);
//                specify_prof_metric (buf);
//                cmd = "ompwork"));
//                break;
//            case OMP_WAIT:
//                username = "OpenMP Wait Time";
//                snprintf (buf, sizeof (buf),
//                        "OMPSTATE>=0 && ((1<<OMPSTATE) & ((MSTATE!=%d) ? %d : %d))",
//                        LMS_USER, (LMS_USER_BITS | IDLE_STATE_BITS), IDLE_STATE_BITS);
//                specify_prof_metric (buf);
//                cmd = "ompwait";
//                break;
//            case OMP_IBAR:
//                username = "OpenMP Implicit Barrier Time";
//                specify_ompstate_metric (OMP_IBAR_STATE);
//                cmd = "ompibar";
//                break;
//            case OMP_EBAR:
//                username = "OpenMP Explicit Barrier Time";
//                specify_ompstate_metric (OMP_EBAR_STATE);
//                cmd = "ompebar";
//                break;
//            case OMP_SERL:
//                username = "OpenMP Serial Time";
//                specify_ompstate_metric (OMP_SERL_STATE);
//                cmd = "ompserl";
//                break;
//            case OMP_RDUC:
//                username = "OpenMP Reduction Time";
//                specify_ompstate_metric (OMP_RDUC_STATE);
//                cmd = "omprduc";
//                break;
//            case OMP_LKWT:
//                username = "OpenMP Lock Wait Time"));
//                specify_ompstate_metric (OMP_LKWT_STATE);
//                cmd = "omplkwt"));
//                break;
//            case OMP_CTWT:
//                username = "OpenMP Critical Section Wait Time"));
//                specify_ompstate_metric (OMP_CTWT_STATE);
//                cmd = "ompctwt"));
//                break;
//            case OMP_ODWT:
//                username = "OpenMP Ordered Section Wait Time"));
//                specify_ompstate_metric (OMP_ODWT_STATE);
//                cmd = "ompodwt"));
//                break;
//            case SYNC_WAIT_TIME:
//                packet_type = ProfData_type.DATA_SYNCH;
//                username = "Sync Wait Time"));
//                snprintf (buf, sizeof (buf), "(EVT_TIME)/%lld"),
//                        (long long) (NANOSEC / METRIC_HR_PRECISION));
//                specify_metric (null, buf);
//                cmd = "sync"));
//                break;
//            case SYNC_WAIT_COUNT:
//                packet_type = ProfData_type.DATA_SYNCH;
//                username = "Sync Wait Count"));
//                specify_metric (null, "1"));
//                cmd = "syncn"));
//                break;
//            case HEAP_ALLOC_CNT:
//                packet_type = ProfData_type.DATA_HEAP;
//                username = "Allocations"));
//                snprintf (buf, sizeof (buf), "(HTYPE!=%d)&&(HTYPE!=%d)&&HVADDR"),
//                        FREE_TRACE, MUNMAP_TRACE);
//                specify_metric (buf, "1"));
//                cmd = "heapalloccnt"));
//                break;
//            case HEAP_ALLOC_BYTES:
//                packet_type = ProfData_type.DATA_HEAP;
//                username = "Bytes Allocated"));
//                snprintf (buf, sizeof (buf), "(HTYPE!=%d)&&(HTYPE!=%d)&&HVADDR"),
//                        FREE_TRACE, MUNMAP_TRACE);
//                specify_metric (buf, "HSIZE"));
//                cmd = "heapallocbytes"));
//                break;
//            case HEAP_LEAK_CNT:
//                packet_type = ProfData_type.DATA_HEAP;
//                username = "Leaks"));
//                snprintf (buf, sizeof (buf), "(HTYPE!=%d)&&(HTYPE!=%d)&&HVADDR&&HLEAKED",
//                        FREE_TRACE, MUNMAP_TRACE);
//                specify_metric (buf, "1"));
//                cmd = "heapleakcnt"));
//                break;
//            case HEAP_LEAK_BYTES:
//                packet_type = ProfData_type.DATA_HEAP;
//                username = "Bytes Leaked"));
//                snprintf (buf, sizeof (buf), "(HTYPE!=%d)&&(HTYPE!=%d)&&HVADDR"),
//                        FREE_TRACE, MUNMAP_TRACE);
//                specify_metric (buf, "HLEAKED"));
//                cmd = "heapleakbytes"));
//                break;
//
//            case IO_READ_CNT:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Read Count"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)", READ_TRACE);
//                specify_metric (buf, "1"));
//                cmd = "ioreadcnt"));
//                break;
//            case IO_WRITE_CNT:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Write Count"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)", WRITE_TRACE);
//                specify_metric (buf, "1"));
//                cmd = "iowritecnt"));
//                break;
//            case IO_OTHER_CNT:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Other I/O Count"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)",
//                        OPEN_TRACE, CLOSE_TRACE, OTHERIO_TRACE);
//                specify_metric (buf, "1"));
//                cmd = "ioothercnt"));
//                break;
//            case IO_ERROR_CNT:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "I/O Error Count"));
//                snprintf (buf, sizeof (buf),
//                        "(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)",
//                        READ_TRACE_ERROR, WRITE_TRACE_ERROR, OPEN_TRACE_ERROR,
//                        CLOSE_TRACE_ERROR, OTHERIO_TRACE_ERROR);
//                specify_metric (buf, "1"));
//                cmd = "ioerrorcnt"));
//                break;
//            case IO_READ_BYTES:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Read Bytes"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)&&IONBYTE"),
//                        READ_TRACE);
//                specify_metric (buf, "IONBYTE"));
//                cmd = "ioreadbytes"));
//                break;
//            case IO_WRITE_BYTES:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Write Bytes"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)&&IONBYTE", WRITE_TRACE);
//                specify_metric (buf, "IONBYTE"));
//                cmd = "iowritebytes"));
//                break;
//            case IO_READ_TIME:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Read Time"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)&&EVT_TIME", READ_TRACE);
//                snprintf (buf2, sizeof (buf2), "(EVT_TIME)/%lld"),
//                        (long long) (NANOSEC / METRIC_HR_PRECISION));
//                specify_metric (buf, buf2);
//                cmd = "ioreadtime"));
//                break;
//            case IO_WRITE_TIME:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Write Time"));
//                snprintf (buf, sizeof (buf), "(IOTYPE==%d)&&EVT_TIME"),
//                        WRITE_TRACE);
//                snprintf (buf2, sizeof (buf2), "(EVT_TIME)/%lld"),
//                        (long long) (NANOSEC / METRIC_HR_PRECISION));
//                specify_metric (buf, buf2);
//                cmd = "iowritetime"));
//                break;
//            case IO_OTHER_TIME:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "Other I/O Time"));
//                snprintf (buf, sizeof (buf),
//                        "(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)&&EVT_TIME",
//                        OPEN_TRACE, CLOSE_TRACE, OTHERIO_TRACE);
//                snprintf (buf2, sizeof (buf2), "(EVT_TIME)/%lld"),
//                        (long long) (NANOSEC / METRIC_HR_PRECISION));
//                specify_metric (buf, buf2);
//                cmd = "ioothertime"));
//                break;
//            case IO_ERROR_TIME:
//                packet_type = ProfData_type.DATA_IOTRACE;
//                username = "I/O Error Time"));
//                snprintf (buf, sizeof (buf),
//                        "(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)||(IOTYPE==%d)&&EVT_TIME",
//                        READ_TRACE_ERROR, WRITE_TRACE_ERROR, OPEN_TRACE_ERROR,
//                        CLOSE_TRACE_ERROR, OTHERIO_TRACE_ERROR);
//                snprintf (buf2, sizeof (buf2), "(EVT_TIME)/%lld"),
//                        (long long) (NANOSEC / METRIC_HR_PRECISION));
//                specify_metric (buf, buf2);
//                cmd = "ioerrortime"));
//                break;
//            case RACCESS:
//                packet_type = ProfData_type.DATA_RACE;
//                username = "Race Accesses";
//                specify_metric(null, "RCNT");
//                cmd = "raccess";
//                break;
//            case DEADLOCKS:
//                packet_type = ProfData_type.DATA_DLCK;
//                username = "Deadlocks";
//                specify_metric (null, "1");
//                cmd = "deadlocks";
//                break;
//            case HWCNTR:
//                packet_type = ProfData_type.DATA_HWC;
//                // username, cmd, and aux set by hwc constructor
//                if (valtype == ValueTag.VT_DOUBLE) {
//                    if (hw_ctr->timecvt > 0)  // CPU cycles
//                        specify_metric (null, "((HWCINT*1000000)/FREQ_MHZ)"));
//                    else if (hw_ctr->timecvt < 0)
//                    { // reference clock (frequency is -timecvt MHz)
//                        snprintf (buf, sizeof (buf), "((HWCINT*1000000)/%d)"), -hw_ctr->timecvt);
//                        specify_metric (null, buf);
//                    }
//                    else  // shouldn't happen
//                        specify_metric (null, "0"));
//                    // resulting unit: seconds * 1e12
//                    precision = 1000000L * 1000000L; // Seconds * 1e12
//                }
//                else {
//                    specify_metric(null, "HWCINT");
//                    precision = 1;
//                }
//                break;
            case OMP_MSTR:
            case OMP_SNGL:
            case OMP_ORDD:
            case OMP_NONE:
            default:
                username = "****";
                throw new IllegalArgumentException(
                        String.format("BaseMetric.init Undefined type %s", type.toString()));
        }
    }

}
