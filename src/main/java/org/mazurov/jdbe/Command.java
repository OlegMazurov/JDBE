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

public class Command {

    private static final String fhdr = "\nCommands controlling the function list:";
    private static final String cchdr = "\nCommands controlling the callers-callees and calltree lists:";
    private static final String lahdr = "\nCommands controlling the leak and allocation lists:";
    private static final String iohdr = "\nCommand controlling the I/O activity report:";
    private static final String rahdr = "\nCommands controlling the race events lists:";
    private static final String ddhdr = "\nCommands controlling the deadlock events lists:";
    private static final String typehdr = "equivalent to \"memobj type\", or \"indxobj type\"";
    private static final String typehdr2 = "  where type is a memory object or index object type";
    private static final String sdhdr = "\nCommands controlling the source and disassembly listings:";
    private static final String lsthdr = "\nCommands listing experiments, samples and threads:";
    private static final String lohdr = "\nCommands controlling load object selection:";
    private static final String obj_allhdr = "  the special object name `all' refers to all load objects";
    private static final String methdr = "\nCommands that list metrics:";
    private static final String othdr = "\nCommands that print other displays:";
    private static final String outhdr = "\nCommands that control output:";
    private static final String mischdr = "\nMiscellaneous commands:";
    private static final String exphdr = "\nCommands for experiments (scripts and interactive mode only):";
    private static final String deflthdr = "\nDefault-setting commands:";
    private static final String selhdr = "\nCommands controlling old-style filters/selection:";
    private static final String filthdr = "\nCommands controlling filters:";
    private static final String indxobjhdr = "\nCommands controlling the index objects:";
    private static final String unsuphdr = "\nUnsupported commands:";
    private static final String helphdr = "\nHelp command:";

    enum CmdType
    {
        // Pathtree-related commands
        FUNCS,
        HOTPCS,
        HOTLINES,
        FDETAIL,
        OBJECTS,
        LDETAIL,
        PDETAIL,
        SOURCE,
        DISASM,
        METRIC_LIST,
        METRICS,
        SORT,
        GPROF,
        GMETRIC_LIST,
        FSINGLE,
        CSINGLE,
        CPREPEND,
        CAPPEND,
        CRMFIRST,
        CRMLAST,
        CALLTREE,
        CALLFLAME,

        // Source/disassembly control commands
        SCOMPCOM,
        STHRESH,
        DCOMPCOM,
        COMPCOM,
        DTHRESH,

        // Heap trace-related commands
        LEAKS,
        ALLOCS,
        HEAP,
        HEAPSTAT,

        // I/O trace-related commands
        IOACTIVITY,
        IOVFD,
        IOCALLSTACK,
        IOSTAT,

        // Race detection related commands
        RACE_EVNTS,
        RACE_SUM,

        // Deadlock detection commands
        DEADLOCK_EVNTS,
        DEADLOCK_SUM,

        // DataSpace commands
        DOBJECTS,
        DO_SINGLE,
        DO_LAYOUT,
        DO_METRIC_LIST,

        // MemorySpace commands
        MEMOBJ,
        MEMOBJLIST,
        MEMOBJDEF,
        MEMOBJDROP,
        MACHINEMODEL,

        // Custom tab commands
        INDXOBJDEF,
        INDXOBJLIST,
        INDXOBJ,
        INDX_METRIC_LIST,

        // Old-style filtering commands
        OBJECT_LIST,
        OBJECT_SELECT,
        SAMPLE_LIST,
        SAMPLE_SELECT,
        THREAD_LIST,
        THREAD_SELECT,
        LWP_LIST,
        LWP_SELECT,
        CPU_LIST,
        CPU_SELECT,

        // Shared Object display commands
        OBJECT_SHOW,
        OBJECT_HIDE,
        OBJECT_API,
        OBJECTS_DEFAULT,

        // the new filtering commands
        FILTERS,

        // Miscellaneous commands
        COMPARE,
        PRINTMODE,
        HEADER,
        OVERVIEW_NEW,
        SAMPLE_DETAIL,
        STATISTICS,
        EXP_LIST,
        DESCRIBE,
        OUTFILE,
        APPENDFILE,
        LIMIT,
        NAMEFMT,
        VIEWMODE,
        EN_DESC,
        SETPATH,
        ADDPATH,
        PATHMAP,
        LIBDIRS,
        SCRIPT,
        VERSION_cmd,
        QUIT,
        PROCSTATS,

        // Experiments handling commands
        ADD_EXP,
        DROP_EXP,
        OPEN_EXP,

        // .rc-only Commands
        DMETRICS,
        DSORT,
        TLMODE,
        TLDATA,
        TABS,
        TIMELINE,
        MPI_TIMELINE,
        MPI_CHART,
        TIMELINE_CLASSIC_TBR,
        SOURCE_V2,
        DISASM_V2,
        RTABS,
        DUALSOURCE,
        SOURCEDISAM,

        HELP,             // this is the last of the commands listed with "help"
        IFREQ,
        DUMPNODES,
        DUMPSTACKS,
        DUMPUNK,
        DUMPFUNC,
        DUMPDOBJS,
        DUMPMAP,
        DUMPENTITIES,
        DUMP_PROFILE,
        DUMP_SYNC,
        DUMP_HWC,
        DUMP_HEAP,
        DUMP_IOTRACE,
        RACE_ACCS,
        DMPI_FUNCS,
        DMPI_MSGS,
        DMPI_EVENTS,
        DMEM,
        DUMP_GC,
        DKILL,
        IGNORE_NO_XHWCPROF,
        IGNORE_FS_WARN,
        QQUIT,
        HHELP,            // this is the last command listed with "xhelp"
        NO_CMD,           // Dummy command, used for headers in help
        DUMMY_CMD,        // Dummy command, used for help

        // unused commands
        LOADOBJECT,
        LOADOBJECT_LIST,
        LOADOBJECT_SELECT,

        // Internal-only Commands
        LAST_CMD,         // No more commands for which a help line is possible
        STDIN,
        COMMENT,
        WHOAMI,

        // Error return "commands"
        AMBIGUOUS_CMD,
        UNKNOWN_CMD
    };

    static class Cmdtable {
        CmdType token;      // command key
        String str;          // command string
        String alt;          // alternate command string
        String arg;          // argument string for help
        int arg_count;      // no. of arguments
        String desc;        // description for help
        Cmdtable(CmdType token, String str, String alt, String arg, int count, String desc) {
            this.token = token;
            this.str = str;
            this.alt = alt;
            this.arg = arg;
            this.arg_count = count;
            this.desc = desc;
        }
    }

    static Cmdtable[] cmd_lst = {   // list of commands
            // User Commands
        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, fhdr),
        new Cmdtable(CmdType.FUNCS, "functions", null, null, 0, "display functions with current metrics"),
        new Cmdtable(CmdType.METRICS, "metrics", null, "metric_spec", 1, "display hot PC's with current metrics"),
        new Cmdtable(CmdType.SORT, "sort", null, "metric_spec", 1, "sort tables by the specified metric"),
        new Cmdtable(CmdType.FDETAIL, "fsummary", null, null, 0, "display summary metrics for each function"),
        new Cmdtable(CmdType.FSINGLE, "fsingle", null, "function_name #", 2, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, cchdr),
        new Cmdtable(CmdType.GPROF, "callers-callees", "gprof", null, 0, ""),
        new Cmdtable(CmdType.CSINGLE, "csingle", null, "function_name #", 2, ""),
        new Cmdtable(CmdType.CPREPEND, "cprepend", null, "function_name #", 2, ""),
        new Cmdtable(CmdType.CAPPEND, "cappend", null, "function_name #", 2, ""),
        new Cmdtable(CmdType.CRMFIRST, "crmfirst", null, null, 0, ""),
        new Cmdtable(CmdType.CRMLAST, "crmlast", null, null, 0, ""),
        new Cmdtable(CmdType.CALLTREE, "calltree", "ctree", null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, lahdr),
        new Cmdtable(CmdType.LEAKS, "leaks", null, null, 0, ""),
        new Cmdtable(CmdType.ALLOCS, "allocs", null, null, 0, ""),
        new Cmdtable(CmdType.HEAP, "heap", null, null, 0, ""),
        new Cmdtable(CmdType.HEAPSTAT, "heapstat", null, null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, iohdr),
        new Cmdtable(CmdType.IOACTIVITY, "ioactivity", null, null, 0, ""),
        new Cmdtable(CmdType.IOVFD, "iodetail", null, null, 0, ""),
        new Cmdtable(CmdType.IOCALLSTACK, "iocallstack", null, null, 0, ""),
        new Cmdtable(CmdType.IOSTAT, "iostat", null, null, 0, ""),

        // PC, line, source and dissassembly commands
        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, sdhdr),
        new Cmdtable(CmdType.HOTPCS, "pcs", null, null, 0, ""),
        new Cmdtable(CmdType.PDETAIL, "psummary", null, null, 0, ""),
        new Cmdtable(CmdType.HOTLINES, "lines", null, null, 0, "display hot lines with current metrics"),
        new Cmdtable(CmdType.LDETAIL, "lsummary", null, null, 0, ""),
        new Cmdtable(CmdType.SOURCE, "source", null, "func/file #", 2, ""),
        new Cmdtable(CmdType.DISASM, "disasm", null, "func/file #", 2, ""),
        new Cmdtable(CmdType.SCOMPCOM, "scc", null, "com_spec", 1, ""),
        new Cmdtable(CmdType.STHRESH, "sthresh", null, "value", 1, ""),
        new Cmdtable(CmdType.DCOMPCOM, "dcc", null, "com_spec", 1, ""),
        new Cmdtable(CmdType.COMPCOM, "cc", null, "com_spec", 1, ""),
        new Cmdtable(CmdType.DTHRESH, "dthresh", null, "value", 1, ""),
        new Cmdtable(CmdType.SETPATH, "setpath", null, "path_list", 1, ""),
        new Cmdtable(CmdType.ADDPATH, "addpath", null, "path_list", 1, ""),
        new Cmdtable(CmdType.PATHMAP, "pathmap", null, "old_prefix new_prefix", 2, ""),
        new Cmdtable(CmdType.LIBDIRS, "preload_libdirs", null, null, 1, ""),

        // Index Object commands
        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, indxobjhdr),
        new Cmdtable(CmdType.INDXOBJ, "indxobj", null, "type", 1, ""),
        new Cmdtable(CmdType.INDXOBJLIST, "indxobj_list", null, null, 0, ""),
        new Cmdtable(CmdType.INDXOBJDEF, "indxobj_define", null, "type \"index-expr\"", 2, ""),

        // Deadlock detection commands
        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, ddhdr),
        new Cmdtable(CmdType.DEADLOCK_EVNTS, "deadlocks", null, null, 0, ""),
        new Cmdtable(CmdType.DEADLOCK_SUM, "dsummary", null, "{deadlock_id|all)", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, lsthdr),
        new Cmdtable(CmdType.EXP_LIST, "experiment_list", "exp_list", null, 0, ""),
        new Cmdtable(CmdType.SAMPLE_LIST, "sample_list", null, null, 0, ""),
        new Cmdtable(CmdType.LWP_LIST, "lwp_list", null, null, 0, ""),
        new Cmdtable(CmdType.THREAD_LIST, "thread_list", null, null, 0, ""),
        new Cmdtable(CmdType.CPU_LIST, "cpu_list", null, null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, filthdr),
        new Cmdtable(CmdType.FILTERS, "filters", null, "filter-specification", 1, ""),
        new Cmdtable(CmdType.DESCRIBE, "describe", null, null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, selhdr),
        new Cmdtable(CmdType.SAMPLE_SELECT, "sample_select", null, "sample_spec", 1, ""),
        new Cmdtable(CmdType.LWP_SELECT, "lwp_select", null, "lwp_spec", 1, ""),
        new Cmdtable(CmdType.THREAD_SELECT, "thread_select", null, "thread_spec", 1, ""),
        new Cmdtable(CmdType.CPU_SELECT, "cpu_select", null, "cpu_spec", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, lohdr),
        new Cmdtable(CmdType.OBJECT_LIST, "object_list", null, null, 0, ""),
        new Cmdtable(CmdType.OBJECT_SHOW, "object_show", null, "obj1,...", 1, ""),
        new Cmdtable(CmdType.OBJECT_HIDE, "object_hide", null, "obj1,...", 1, ""),
        new Cmdtable(CmdType.OBJECT_API, "object_api", null, "obj1,...", 1, ""),
        new Cmdtable(CmdType.DUMMY_CMD, " ", null, null, 0, obj_allhdr),
        new Cmdtable(CmdType.OBJECTS_DEFAULT, "objects_default", null, null, 1, ""),

        new Cmdtable(CmdType.OBJECT_SELECT, "object_select", null, "obj1,...", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, methdr),
        new Cmdtable(CmdType.METRIC_LIST, "metric_list", null, null, 0, ""),
        new Cmdtable(CmdType.GMETRIC_LIST, "cmetric_list", "gmetric_list", null, 0, ""),
        new Cmdtable(CmdType.INDX_METRIC_LIST, "indx_metric_list", null, null, 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, outhdr),
        new Cmdtable(CmdType.OUTFILE, "outfile", null, "filename", 1, ""),
        new Cmdtable(CmdType.APPENDFILE, "appendfile", null, "filename", 1, ""),
        new Cmdtable(CmdType.LIMIT, "limit", null, "n", 1, ""),
        new Cmdtable(CmdType.NAMEFMT, "name", null, "{long|short|mangled)[:{soname|nosoname)]", 1, ""),
        new Cmdtable(CmdType.VIEWMODE, "viewmode", null, "{user|expert|machine)", 1, ""),
        new Cmdtable(CmdType.COMPARE, "compare", null, "{on|off|delta|ratio)", 1, ""),
        new Cmdtable(CmdType.PRINTMODE, "printmode", null, "string", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, othdr),
        new Cmdtable(CmdType.HEADER, "header", null, "exp_id", 1, ""),
        new Cmdtable(CmdType.OBJECTS, "objects", null, null, 0, "display object list with errors or warnings"),
        new Cmdtable(CmdType.OVERVIEW_NEW, "overview", null, null, 0, ""),
        new Cmdtable(CmdType.SAMPLE_DETAIL, "sample_detail", null, "exp_id", 1, ""),
        new Cmdtable(CmdType.STATISTICS, "statistics", null, "exp_id", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, exphdr),
        new Cmdtable(CmdType.OPEN_EXP, "open_exp", null, "experiment", 1, ""),
        new Cmdtable(CmdType.ADD_EXP, "add_exp", null, "experiment", 1, ""),
        new Cmdtable(CmdType.DROP_EXP, "drop_exp", null, "experiment", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, deflthdr),
        new Cmdtable(CmdType.DMETRICS, "dmetrics", null, "metric_spec", 1, ""),
        new Cmdtable(CmdType.DSORT, "dsort", null, "metric_spec", 1, ""),
        new Cmdtable(CmdType.EN_DESC, "en_desc", null, "{on|off|=<regex>)", 1, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, mischdr),
        new Cmdtable(CmdType.DUMMY_CMD, "<type>", null, null, 0, typehdr),
        new Cmdtable(CmdType.DUMMY_CMD, " ", null, null, 0, typehdr2),

        new Cmdtable(CmdType.IFREQ, "ifreq", null, null, 0, ""),
        new Cmdtable(CmdType.PROCSTATS, "procstats", null, null, 0, ""),
        new Cmdtable(CmdType.SCRIPT, "script", null, "file", 1, ""),
        new Cmdtable(CmdType.VERSION_cmd, "version", null, null, 0, ""),
        new Cmdtable(CmdType.QUIT, "quit", "exit", null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, helphdr),
        new Cmdtable(CmdType.HELP, "help", null, null, 0, ""),

        new Cmdtable(CmdType.NO_CMD, "", null, null, 0, unsuphdr),
        new Cmdtable(CmdType.HELP, "-help", null, null, 0, ""),
        new Cmdtable(CmdType.DUMPFUNC, "dfuncs", null, "string", 1, ""),
        new Cmdtable(CmdType.DUMPDOBJS, "ddobjs", null, "string", 1, ""),
        new Cmdtable(CmdType.DUMPNODES, "dnodes", null, null, 0, ""),
        new Cmdtable(CmdType.DUMPSTACKS, "dstacks", null, null, 0, ""),
        new Cmdtable(CmdType.DUMPUNK, "dunkpc", null, null, 0, ""),
        new Cmdtable(CmdType.DUMPMAP, "dmap", null, null, 0, ""),
        new Cmdtable(CmdType.DUMPENTITIES, "dentities", null, null, 0, ""),
        new Cmdtable(CmdType.IGNORE_NO_XHWCPROF, "ignore_no_xhwcprof", null, null, 0, ""),
        new Cmdtable(CmdType.IGNORE_FS_WARN, "ignore_fs_warn", null, null, 0, ""),

        new Cmdtable(CmdType.DUMP_PROFILE, "dprofile", null, null, 0, ""),
        new Cmdtable(CmdType.DUMP_SYNC, "dsync", null, null, 0, ""),
        new Cmdtable(CmdType.DUMP_IOTRACE, "diotrace", null, null, 0, ""),
        new Cmdtable(CmdType.DUMP_HWC, "dhwc", null, null, 0, ""),
        new Cmdtable(CmdType.DUMP_HEAP, "dheap", null, null, 0, ""),
        new Cmdtable(CmdType.RACE_ACCS, "r_accs", null, null, 0, ""),

        new Cmdtable(CmdType.DMPI_FUNCS, "dmpi_funcs", null, null, 0, ""),
        new Cmdtable(CmdType.DMPI_MSGS, "dmpi_msgs", null, null, 0, ""),
        new Cmdtable(CmdType.DMPI_EVENTS, "dmpi_events", null, null, 0, ""),

        new Cmdtable(CmdType.DMEM, "dmem", null, null, 1, ""),
        new Cmdtable(CmdType.DUMP_GC, "dumpgc", null, null, 0, ""),
        new Cmdtable(CmdType.DKILL, "dkill", null, null, 2, ""),

        new Cmdtable(CmdType.QQUIT, "xquit", null, null, 0, ""),
        // use xquit for memory leak detection in dbe; it's
        // like quit, but deletes all data loaded

        new Cmdtable(CmdType.HHELP, "xhelp", null, null, 0, ""),
        new Cmdtable(CmdType.WHOAMI, "whoami", null, null, 0, null),

        // these are not recognized at this point
        new Cmdtable(CmdType.LOADOBJECT, "segments", "pmap", null, 0, ""),
        new Cmdtable(CmdType.LOADOBJECT_LIST, "segment_list", null, null, 0, ""),
        new Cmdtable(CmdType.LOADOBJECT_SELECT, "segment_select", null, "seg1,...", 1, ""),

        new Cmdtable(CmdType.LAST_CMD, "xxxx", null, null, 0, null)
    };

    public static String get_cmd_str(CmdType type) {
        for (int i = 0;; i++) {
            if (cmd_lst[i].token == CmdType.LAST_CMD)
                break;
            if (type == cmd_lst[i].token)
                return cmd_lst[i].str;
        }
        return "xxxx";
    }
}
