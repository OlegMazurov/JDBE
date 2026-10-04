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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.mazurov.jdbe.Emsg.Cmsg_warn;

public class Experiment {

    private static final String SP_NOTES_FILE = "notes";
    private static final String SP_LOG_FILE = "log.xml";

    private static final long PROFILE_BUFFER_CHUNK = 16384;

    enum Exp_status {
        SUCCESS,
        INCOMPLETE,
        FAILURE
    };

    static class Collection_params { // Experiment collection parameters

        static final int MAX_HWCOUNT = 64;

        int profile_mode;     // if clock-profiling is on
        long ptimer_usec; // Clock profile timer interval (microseconds)
        int lms_magic_id;     // identifies which LMS_* states are live
        int sync_mode;        // if synctrace is on
        int sync_threshold;   // value of synctrace threshold, in microseconds
        int sync_scope;       // value of synctrace scope: Java and/or native

        int heap_mode;        // if heaptrace is on
        int io_mode;          // if iotrace is on
        int race_mode;        // if race-detection is on
        int race_stack;       // setting for stack data collection
        int deadlock_mode;    // if deadlock-detection is on
        int omp_mode;         // if omptrace is on

        int hw_mode;          // if hw-counter profiling is on
        int xhw_mode;    // if extended (true-PC) HW counter profiling for any counter

        String[] hw_aux_name = new String[MAX_HWCOUNT];
        String[] hw_username = new String[MAX_HWCOUNT];
        int[] hw_interval = new int[MAX_HWCOUNT];     // nominal interval for count
        int[] hw_tpc = new int[MAX_HWCOUNT];          // non-zero, if aggressive TPC/VA requested
        int[] hw_metric_tag = new int[MAX_HWCOUNT];   // tag as used for finding metrics
        int[] hw_cpu_ver = new int[MAX_HWCOUNT];      // Chip version number for this metric

        int sample_periodic;      // if periodic sampling is on
        int sample_timer;         // Sample timer (sec)
        int limit;                // experiment size limit
        String pause_sig;    // Pause/resume signal string
        String sample_sig;   // Sampling signal string
        String start_delay;  // Data collect start delay string
        String terminate;    // Data collection termination time string
        String linetrace;
    };


    protected String expt_name;      // name of experiment

    // message queues
    Emsgqueue commentq;  // comments for the experiment header
    Emsgqueue runlogq;   // used temporarily; after log file processing,
    // messages are appended to the commentq
    Emsgqueue errorq;    // error messages
    Emsgqueue warnq;     // warning messages
    Emsgqueue notesq;    // user-written notes messages
    Emsgqueue pprocq;    // postprocessing messages
    Emsgqueue ifreqq;    // Instruction frequency data, from count experiment

    List<BaseMetric> metrics = new ArrayList<>();
    boolean need_swap_endian;
    Collection_params coll_params = new Collection_params();

    private Exp_status status;        // Error status

    private boolean has_java = true;    // TODO
    protected String uarglist;       // argv[] array, as a string
    private String utargname;      // basename of argv[0] extracted from uarglist

    private int exp_maj_version;  // major version number of current experiment
    private int exp_min_version;  // minor version number of current experiment

    private long blksz = PROFILE_BUFFER_CHUNK; // binary data file block size

    private boolean obsolete;         // If pointer file experiment detected

    private boolean broken;
    private String cversion;       // collector version string

    public Experiment() {
        init();
    }

    private void init() {
        commentq = new Emsgqueue("commentq");
        runlogq = new Emsgqueue("runlogq");
        errorq = new Emsgqueue("errorq");
        warnq = new Emsgqueue("warnq");
        notesq = new Emsgqueue("notesq");
        pprocq = new Emsgqueue("pprocq");
        ifreqq = null;
    }

    public boolean isBroken() {
        return broken;
    }

    public boolean hasJava() {
        return has_java;
    }

    public String get_expt_name() {
        return expt_name;   // Return the pathname to the experiment
    }

    public Emsg fetch_warnings() {
        return warnq.fetch();
    }

    public Emsg fetch_errors() {
        return errorq.fetch();
    }

    // This function checks that the experiment directory
    // is of the proper form, and accessible
    Exp_status find_expdir(String path) {

        // Save the name
        expt_name = path;

        // Check that the name ends in .er
        if (path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }

        if (!path.endsWith(".er")) {
            Emsg m = new Emsg(Cmsg_warn.CMSG_FATAL, "*** Error: not a valid experiment name");
            errorq.append(m);
            status = Exp_status.FAILURE;
            return status;
        }

        // Check if new directory structure (i.e., no pointer file)
        if (!Files.exists(Path.of(path))) {
            Emsg m = new Emsg(Cmsg_warn.CMSG_FATAL, "*** Error: experiment not found");
            errorq.append(m);
            status = Exp_status.FAILURE;
            return status;
        }
        if (!Files.isDirectory(Path.of(path))) {
            // ignore pointer-file experiments
            Emsg m = new Emsg(Cmsg_warn.CMSG_FATAL,
                    "*** Error: experiment was recorded with an earlier version, and can not be read");
            errorq.append(m);
            obsolete = true;
            status = Exp_status.FAILURE;
            return status;
        }
        return Exp_status.SUCCESS;
    }

    void read_notes_file() {
        // Open log file:
        String fname = String.format("%s/%s", expt_name, SP_NOTES_FILE);
        File file = new File(fname);
        if (!file.canRead()) {
            return;
        }

        if (!DbeSession.getInstance().is_interactive()) {
            Emsg m = new Emsg (Cmsg_warn.CMSG_COMMENT, "Notes:");
            notesq.append(m);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (true) {
                String str = reader.readLine();
                if (str == null) {
                    break;
                }
                int i = str.lastIndexOf('\n');
                if (i >= 0) str = str.substring(0, i);
                notesq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, str));
            }
        } catch (IOException ioe) {
            // ignore
        }
        if (!DbeSession.getInstance().is_interactive()) {
            Emsg m = new Emsg(Cmsg_warn.CMSG_COMMENT,
                    "============================================================");
            notesq.append(m);
        }
    }

    void read_log_file() {
        File logFile = new File(String.format("%s/%s", expt_name, SP_LOG_FILE));
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler dh = new ExperimentHandler();
            saxParser.parse(logFile, dh);
        }
        catch (SAXException | ParserConfigurationException | IOException e) {
            // Fatal error in the parser
            StringBuilder sb = new StringBuilder();
            if (obsolete)
                sb.append(String.format("%s", e.getMessage()));
            else
                sb.append(String.format("%s: %s", SP_LOG_FILE, e.getMessage()));
            errorq.append(new Emsg(Cmsg_warn.CMSG_FATAL, sb.toString()));
            status = Exp_status.FAILURE;
        }

//        DbeSession.getInstance().register_metric("IPC", "Instructions Per Cycle",
//                "insts/cycles");
//        DbeSession.getInstance().register_metric("CPI", "Cycles Per Instruction",
//                "cycles/insts");
//        DbeSession.getInstance().register_metric("K_IPC",
//                "Kernel Instructions Per Cycle",
//                "K_insts/K_cycles");
//        DbeSession.getInstance().register_metric("K_CPI",
//                "Kernel Cycles Per Instruction",
//                "K_cycles/K_insts");
    }

    void process_arglist_cmd(String arglist) {
        uarglist = arglist;

        if ("(fork)".equals(uarglist))
            return; // leaving target name null

        utargname = uarglist.split("\\s+")[0];
    }

    void register_metric(Metric.Type type) {
        BaseMetric mtr = DbeSession.getInstance().register_metric(type);
        metrics.add(mtr);
    }

    protected Exp_status open_epilogue() {
// TODO
//        // set up mapping for tagObj(PROP_EXPID)
//        mapTagValue(PROP_EXPID, userExpId);
//
//        post_process();
//        if (last_event != ZERO_TIME) { // if last_event is known
//            hrtime_t ts = last_event - exp_start_time;
//            String msg = String.format("Experiment Ended: %d.%09d\nData Collection Duration: %d.%09d",
//                    (long) (ts / NANOSEC), (long) (ts % NANOSEC),
//                    (long) (non_paused_time / NANOSEC),
//                    (long) (non_paused_time % NANOSEC));
//            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
//        }
//
//        // Check for incomplete experiment, and inform the user
//        if (status == Exp_status.INCOMPLETE) {
//            if (exec_started == true)
//                // experiment ended with the exec, not abnormally
//                status = Exp_status.SUCCESS;
//            else {
//                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "*** Note: experiment was not closed"));
//                // runlogq.append(new Emsg(CMSG_COMMENT, cmnt));
//            }
//        }
//        // write a descriptive header for the experiment
//        write_header();
        return status;
    }

//    void register_metric(Hwcentry ctr, String aux, String uname) {
//        BaseMetric mtr = DbeSession.getInstance().register_metric(ctr, aux, uname);
//        metrics.append(mtr);
//        if (mtr.get_dependent_bm())
//            metrics->append(mtr.get_dependent_bm());
//    }


//    class ExperimentFile {
//
//        enum A {
//            EF_NOT_OPENED,
//            EF_OPENED,
//            EF_CLOSED,
//            EF_FAILURE
//        };
//
//        ExperimentFile(Experiment *_exp, String _fname) {
//            exp = _exp;
//            fh = null;
//            bufsz = 0;
//            buffer = null;
//            ef_status = EF_NOT_OPENED;
//            offset = 0;
//            fname = dbe_sprintf ( ("%s/%s"), expt_name, _fname);
//        }
//
//        boolean open (bool new_open = false);
//
//        String get_name() {
//            return fname;
//        }
//
//        int get_status() {
//            return ef_status;
//        }
//
//        char *fgets ();
//        void close ();
//
//        File fh;
//        Experiment exp;
//        String fname;
//        long offset;
//        int bufsz, ef_status;
//        char *buffer;
//    };

    class ExperimentHandler extends DefaultHandler {

        //Experiment exp;
        Element curElem;
        List<Element> stack;
        Module dynfuncModule;
//        DataDescriptor dDscr;
//        PacketDescriptor pDscr;
//        PropDescr propDscr;
        String text;
        Cmsg_warn mkind;
        int mnum;
        int mec;

        ExperimentHandler() {
            stack = new ArrayList<>();
            pushElem(Element.EL_NONE);
            dynfuncModule = null;
//            dDscr = null;
//            pDscr = null;
//            propDscr = null;
            text = null;
            mkind = Cmsg_warn.CMSG_NONE;
            mnum = -1;
            mec = -1;
        }

        @Override
        public void endDocument() {
            // SP_TAG_STATE should be used to describe states, but it isn't
            // let's do it here:
//            DataDescriptor dd = exp.getDataDescriptor(DATA_HEAP);
//            if (dd != null) {
//                PropDescr prop = dd.getProp(PROP_HTYPE);
//                if (prop != null) {
//                    char * stateNames [HEAPTYPE_LAST] = HEAPTYPE_STATE_STRINGS;
//                    char * stateUNames[HEAPTYPE_LAST] = HEAPTYPE_STATE_USTRINGS;
//                    for (int ii = 0; ii < HEAPTYPE_LAST; ii++)
//                        prop.addState(ii, stateNames[ii], stateUNames[ii]);
//                }
//            }
//            dd = exp.getDataDescriptor(DATA_IOTRACE);
//            if (dd != null) {
//                PropDescr prop = dd.getProp(PROP_IOTYPE);
//                if (prop != null) {
//                    char * stateNames [IOTRACETYPE_LAST] = IOTRACETYPE_STATE_STRINGS;
//                    char * stateUNames[IOTRACETYPE_LAST] = IOTRACETYPE_STATE_USTRINGS;
//                    for (int ii = 0; ii < IOTRACETYPE_LAST; ii++)
//                        prop->addState (ii, stateNames[ii], stateUNames[ii]);
//                }
//            }
        }

        private static final String SP_TAG_COLLECTOR =       "collector";
        private static final String SP_TAG_CPU =             "cpu";
        private static final String SP_TAG_DATAPTR =         "dataptr";
        private static final String SP_TAG_EVENT =           "event";
        private static final String SP_TAG_EXPERIMENT =      "experiment";
        private static final String SP_TAG_FIELD =           "field";
        private static final String SP_TAG_PROCESS =         "process";
        private static final String SP_TAG_PROFILE =         "profile";
        private static final String SP_TAG_PROFDATA =        "profdata";
        private static final String SP_TAG_PROFPCKT =        "profpckt";
        private static final String SP_TAG_SETTING =         "setting";
        private static final String SP_TAG_STATE =           "state";
        private static final String SP_TAG_SYSTEM =          "system";
        private static final String SP_TAG_POWERM =          "powerm";
        private static final String SP_TAG_FREQUENCY =       "frequency";
        private static final String SP_TAG_DTRACEFATAL =     "dtracefatal";

        static final String SP_JCMD_ARCH =           "architecture";
        static final String SP_JCMD_ARCHIVE =        "archive_run";
        static final String SP_JCMD_ARGLIST =        "arglist";
        static final String SP_JCMD_BLKSZ =          "blksz";
        static final String SP_JCMD_CERROR =         "cerror";
        static final String SP_JCMD_CLASS_LOAD =     "class_load";
        static final String SP_JCMD_CLASS_UNLOAD =   "class_unload";
        static final String SP_JCMD_COLLENV =        "collenv";
        static final String SP_JCMD_COMMENT =        "comment";
        static final String SP_JCMD_CPUID =          "cpuid";
        static final String SP_JCMD_CWARN =          "cwarn";
        static final String SP_JCMD_CWD =            "cwd";
        static final String SP_JCMD_CVERSION =       "cversion";
        static final String SP_JCMD_DATARACE =       "datarace";
        static final String SP_JCMD_DEADLOCK =       "deadlock";
        static final String SP_JCMD_DELAYSTART =     "delay_start";
        static final String SP_JCMD_DESC_START =     "desc_start";
        static final String SP_JCMD_DESC_STARTED =   "desc_started";
        static final String SP_JCMD_DVERSION =       "dversion";
        static final String SP_JCMD_EXEC_START =     "exec_start";
        static final String SP_JCMD_EXEC_ERROR =     "exec_error";
        static final String SP_JCMD_EXIT =           "exit";
        static final String SP_JCMD_EXPT_DURATION =  "exp_duration";
        static final String SP_JCMD_FAKETIME =       "faketime";
        static final String SP_JCMD_FN_LOAD =        "fn_load";
        static final String SP_JCMD_FN_UNLOAD =      "fn_unload";
        static final String SP_JCMD_FUN_MAP =        "fun_map";
        static final String SP_JCMD_FUN_UNMAP =      "fun_unmap";
        static final String SP_JCMD_HEAPTRACE =      "heaptrace";
        static final String SP_JCMD_HOSTNAME =       "hostname";
        static final String SP_JCMD_HWC_DEFAULT =    "hwc_default";
        static final String SP_JCMD_HW_COUNTER =     "hwcounter";
        static final String SP_JCMD_HW_SIM_CTR =     "hwsimctr";
        static final String SP_JCMD_IOTRACE =        "iotrace";
        static final String SP_JCMD_JCM_LOAD =       "jcm_load";
        static final String SP_JCMD_JCM_UNLOAD =     "jcm_unload";
        static final String SP_JCMD_JCM_MAP =        "jcm_map";
        static final String SP_JCMD_JCM_UNMAP =      "jcm_unmap";
        static final String SP_JCMD_JTHREND =        "jthread_end";
        static final String SP_JCMD_JTHRSTART =      "jthread_start";
        static final String SP_JCMD_GCEND =          "gc_end";
        static final String SP_JCMD_GCSTART =        "gc_start";
        static final String SP_JCMD_JVERSION =       "jversion";
//static final String SP_JCMD_KPROFILE        "kprofile"    /* TBR */
        static final String SP_JCMD_LIMIT =          "limit";
        static final String SP_JCMD_LINETRACE =      "linetrace";
        static final String SP_JCMD_LO_OPEN =        "lo_open";
        static final String SP_JCMD_LO_CLOSE =       "lo_close";
        static final String SP_JCMD_MOD_OPEN =       "mod_open";
        static final String SP_JCMD_MPIEXP =         "MPIexperiment";
        static final String SP_JCMD_MPI_NO_TRACE =   "MPI_no_trace";
        static final String SP_JCMD_MPIOMPVER =      "mpi_openmpi_version";
        static final String SP_JCMD_MPITRACEVER =    "mpi_trace_version";
        static final String SP_JCMD_MPIPP =          "mpipp";
        static final String SP_JCMD_MPIPPERR =       "mpipp_err";
        static final String SP_JCMD_MPIPPWARN =      "mpipp_warn";
        static final String SP_JCMD_MPISTATE =       "mpistate";
        static final String SP_JCMD_MPITRACE =       "mpitrace"; /* backwards compat only */
        static final String SP_JCMD_MPVIEW =         "mpview";
        static final String SP_JCMD_MSGTRACE =       "msgtrace";
        static final String SP_JCMD_NOIDLE =         "noidle";
        static final String SP_JCMD_OMPTRACE =       "omptrace";
        static final String SP_JCMD_OS =             "os";
        static final String SP_JCMD_PAGESIZE =       "pagesize";
        static final String SP_JCMD_PAUSE =          "pause";
        static final String SP_JCMD_PAUSE_SIG =      "pause_signal";
        static final String SP_JCMD_PROFILE =        "profile";
        static final String SP_JCMD_RESUME =         "resume";
        static final String SP_JCMD_RUN =            "run";
        static final String SP_JCMD_SAMPLE =         "sample";
        static final String SP_JCMD_SAMPLE_PERIOD =  "sample_period";
        static final String SP_JCMD_SAMPLE_SIG =     "sample_signal";
        static final String SP_JCMD_SEGMENT_MAP =    "seg_map";
        static final String SP_JCMD_SEGMENT_UNMAP =  "seg_unmap";
        static final String SP_JCMD_SRCHPATH =       "search_path";
        static final String SP_JCMD_STACKBASE =      "stackbase";
        static final String SP_JCMD_SUNPERF =        "sunperf";
        static final String SP_JCMD_SYNCTRACE =      "synctrace";
        static final String SP_JCMD_TERMINATE =      "terminate";
        static final String SP_JCMD_THREAD_PAUSE =   "thread_pause";
        static final String SP_JCMD_THREAD_RESUME =  "thread_resume";
        static final String SP_JCMD_USERNAME =       "username";
        static final String SP_JCMD_VERSION =        "version";
        static final String SP_JCMD_WSIZE =          "wsize";
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
            switch (qName) {
            case SP_TAG_EXPERIMENT -> {
                pushElem(Element.EL_EXPERIMENT);
                String str = attrs.getValue("version");
                if (str != null) {
                    String[] parts = str.split("\\.");
                    int major = Integer.parseInt(parts[0]);
                    int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    exp_maj_version = major;
                    exp_min_version = minor;
                }
            }
            case SP_TAG_COLLECTOR -> {
                pushElem(Element.EL_COLLECTOR);
            }
            case SP_TAG_SETTING -> {
                int found = 0;
                pushElem(Element.EL_SETTING);
                String str = attrs.getValue(SP_JCMD_LIMIT);
                if (str != null) {
                    found = 1;
                    coll_params.limit = Integer.parseInt(str);
                }
                str = attrs.getValue(SP_JCMD_BLKSZ);
                if (str != null) {
                    found = 1;
                    blksz = Integer.parseInt(str);
                }
                str = attrs.getValue(SP_JCMD_STACKBASE);
                if (str != null) {
                    found = 1;
                    stack_base = strtoull (str, null, 0);
                }
                str = attrs.getValue(SP_JCMD_HWC_DEFAULT);
                if (str != null)
                {
                    found = 1;
                    hwc_default = true;
                }
                str = attrs.getValue(SP_JCMD_NOIDLE);
                if (str != null) {
                    found = 1;
                    commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                             "*** Note: experiment does not have events from idle CPUs"));
                }
                str = attrs.getValue(SP_JCMD_FAKETIME);
                if (str != null) {
                    found = 1;
                    timelineavail = false;
                    commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                             "*** Note: experiment does not have timestamps; timeline unavailable"));
                }
                str = attrs.getValue(SP_JCMD_DELAYSTART);
                if (str != null) {
                    found = 1;
                    coll_params.start_delay = str;
                }
                str = attrs.getValue(SP_JCMD_TERMINATE);
                if (str != null) {
                    found = 1;
                    coll_params.terminate = str;
                }
                str = attrs.getValue(SP_JCMD_PAUSE_SIG);
                if (str != null)
                {
                    found = 1;
                    coll_params.pause_sig = str;
                }
                str = attrs.getValue(SP_JCMD_SAMPLE_PERIOD);
                if (str != null)
                {
                    found = 1;
                    coll_params.sample_periodic = 1;
                    coll_params.sample_timer = Integer.parseInt(str);
                }
                str = attrs.getValue(SP_JCMD_SAMPLE_SIG);
                if (str != null)
                {
                    found = 1;
                    coll_params.sample_sig = str;
                }
                str = attrs.getValue(SP_JCMD_SRCHPATH);
                if (str != null) {
                    found = 1;
                    String msg = String.format("Search path: %s", str);
                    runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                    DbeSession.getInstance().add_classpath(str);
                }
                str = attrs.getValue(SP_JCMD_LINETRACE);
                if (str != null) {
                    found = 1;
                    coll_params.linetrace = str;
                }

                str = attrs.getValue(SP_JCMD_COLLENV);
                if (str != null) {
                    found = 1;
                    String msg = String.format("  Data collection environment variable: %s", str);
                    runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                }
                if (found == 0) {
                    int nattr = attrs.getLength();
                    if (nattr != 0) {
                        StringBuilder sb = new StringBuilder("Unexpected attributes: ");
                        for (int k = 0; k < nattr; k++) {
                            String qn = attrs.getQName(k);
                            String vl = attrs.getValue(k);
                            sb.append(" ").append(qn).append("=").append(vl);
                        }
                        throw new SAXException(sb.toString());
                    }
                }
                // END OF CODE FOR "setting"
            }
            case SP_TAG_SYSTEM -> {
                pushElem(Element.EL_SYSTEM);
                String str = attrs.getValue("hostname");
                if (str != null) {
                    hostname = str;
                }
                str = attrs.getValue("os");
                if (str != null) {
                    os_version = str;
                    /* For Linux experiments expect sparse thread ID's */
                    if ("SunOS".equals(str)) {
                        sparse_threads = true;
                    }
                }
                str = attrs.getValue("arch");
                if (str != null) {
                    if ("i86pc".equals(str) || "i686".equals(str) || "x86_64".equals(str)) {
                        platform = Intel;
                    } else if ("aarch64".equals(str)) {
                        platform = Aarch64;
                    } else {
                        platform = Sparc;
                    }
                    need_swap_endian = (DbeSession::platform == Sparc) ?
                            (platform != Sparc) : (platform == Sparc);
                    architecture = str;
                }
                str = attrs.getValue("pagesz");
                if (str != null) {
                    page_size = Integer.parseInt(str);
                }
                str = attrs.getValue("npages");
                if (str != null) {
                    npages = Integer.parseInt(str);
                }
            }
            case SP_TAG_POWERM -> {
                pushElem(Element.EL_POWERM);
            }
            case SP_TAG_FREQUENCY -> {
                pushElem(Element.EL_FREQUENCY);
                String str = attrs.getValue("clk");
                if (str != null) {
                    set_clock(Integer.parseInt(str));
                }
                // check for frequency_scaling or turbo_mode recorded from libcollector under dbx
                str = attrs.getValue("frequency_scaling");
                String str2 = attrs.getValue("turbo_mode");
                if (str != null || str2 != null) {
                    varclock = 1;
                }
            }
            case SP_TAG_CPU -> {
                pushElem(Element.EL_CPU);
                ncpus++;
                String str = attrs.getValue( ("clk"));
                if (str != null) {
                    int clk = Integer.parseInt(str);
                    if (maxclock == 0) {
                        minclock = clk;
                        maxclock = clk;
                    } else {
                        if (clk < minclock)
                            minclock = clk;
                        if (clk > maxclock)
                            maxclock = clk;
                    }
                    clock = clk;
                }
                // check for frequency_scaling or turbo_mode
                str = attrs.getValue("frequency_scaling");
                String str2 = attrs.getValue("turbo_mode");
                if (str != null || str2 != null) {
                    varclock = 1;
                }
            }
            case SP_TAG_PROCESS -> {
                pushElem(Element.EL_PROCESS);
                String str = attrs.getValue("wsize");
                if (str != null) {
                    int wsz = Integer.parseInt(str);
                    if (wsz == 32) {
                        wsize = W32;
                    } else if (wsz == 64) {
                        wsize = W64;
                    }
                }
                str = attrs.getValue("pid");
                if (str != null) {
                    pid = Integer.parseInt(str);
                }
                str = attrs.getValue("ppid");
                if (str != null) {
                    ppid = Integer.parseInt(str);
                }
                str = attrs.getValue("pgrp");
                if (str != null) {
                    pgrp = Integer.parseInt(str);
                }
                str = attrs.getValue("sid");
                if (str != null) {
                    sid = Integer.parseInt(str);
                }
                str = attrs.getValue("cwd");
                if (str != null) {
                    ucwd = str;
                }
                str = attrs.getValue( ("pagesz"));
                if (str != null) {
                    page_size = Integer.parseInt(str);
                }
            }
            case SP_TAG_EVENT -> { // Start code for event
                pushElem(Element.EL_EVENT);
                hrtime_t ts = (hrtime_t) 0;
                String str = attrs.getValue("tstamp");
                if (str != null) {
                    ts = parseTStamp(str);
                }
                str = attrs.getValue("kind");
                if (str != null) {
                    switch (str) {
                        case SP_JCMD_RUN -> {
                            broken = false;
                            exp_start_time = ts;
                            str = attrs.getValue(("time"));
                            if (str != null)
                                start_sec = Long.parseLong(str);
                            str = attrs.getValue(("pid"));
                            if (str != null)
                                pid = Integer.parseInt(str);
                            str = attrs.getValue(("ppid"));
                            if (str != null)
                                ppid = Integer.parseInt(str);
                            str = attrs.getValue(("pgrp"));
                            if (str != null)
                                pgrp = Integer.parseInt(str);
                            str = attrs.getValue(("sid"));
                            if (str != null)
                                sid = Integer.parseInt(str);
                            status = Exp_status.INCOMPLETE;
                        }
                        case SP_JCMD_ARCHIVE -> {
                            pprocq.append(new Emsg(Cmsg_warn.CMSG_WARN, "er_archive run: XXXXXXX"));
                        }
                        case SP_JCMD_SAMPLE -> {
                            update_last_event(exp_start_time + ts); // ts is 0-based
                            str = attrs.getValue(("id"));
                            int id = str ? Integer.parseInt(str) : -1;
                            String label = attrs.getValue("label");
                            process_sample_cmd(null, ts, id, label);
                        }
                        case SP_JCMD_EXIT -> {
                            // don't treat EXIT as an event w.r.t. last_event and non_paused_time
                            status = Exp_status.SUCCESS;
                        }
                        case SP_JCMD_CERROR -> {
                            mkind = Cmsg_warn.CMSG_ERROR;
                            str = attrs.getValue(("id"));
                            if (str != null) {
                                mnum = Integer.parseInt(str);
                            }
                            str = attrs.getValue(("ec"));
                            if (str != null) {
                                mec = Integer.parseInt(str);
                            }
                        }
                        case SP_JCMD_CWARN -> {
                            mkind = Cmsg_warn.CMSG_WARN;
                            str = attrs.getValue(("id"));
                            if (str != null)
                                mnum = Integer.parseInt(str);
                        }
                        case SP_JCMD_COMMENT -> {
                            mkind = Cmsg_warn.CMSG_COMMENT;
                            str = attrs.getValue(("id"));
                            if (str != null)
                                mnum = Integer.parseInt(str);
                            str = attrs.getValue(("text"));
                            if (str != null) {
                                StringBuilder sb;
                                sb.sprintf(("*** Note: %s"), str);
                                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, sb));
                            }
                        }
                        case SP_JCMD_DESC_START -> {
                            String variant = toStr(attrs, ("variant"));
                            String lineage = toStr(attrs, ("lineage"));
                            int follow = toInt(attrs, ("follow"));
                            String msg = toStr(attrs, ("msg"));
                            process_desc_start_cmd(null, ts, variant, lineage, follow, msg);
                            break;
                        }
                        case SP_JCMD_DESC_STARTED -> {
                            String variant = toStr(attrs, ("variant"));
                            String lineage = toStr(attrs, ("lineage"));
                            int follow = toInt(attrs, ("follow"));
                            String msg = toStr(attrs, ("msg"));
                            process_desc_started_cmd(null, ts, variant, lineage, follow, msg);
                            break;
                        }
                        case SP_JCMD_EXEC_START -> {
                            // if successful, acts like experiment termination - no "exit" entry will follow
                            update_last_event(exp_start_time + ts); // ts is 0-based

                            String variant = toStr(attrs, ("variant"));
                            String lineage = toStr(attrs, ("lineage"));
                            int follow = toInt(attrs, ("follow"));
                            String msg = toStr(attrs, ("msg"));
                            process_desc_start_cmd(null, ts, variant, lineage, follow, msg);
                            exec_started = true;
                            break;
                        }
                        case SP_JCMD_EXEC_ERROR -> {
                            update_last_event(exp_start_time + ts); // ts is 0-based

                            String variant = toStr(attrs, ("variant"));
                            String lineage = toStr(attrs, ("lineage"));
                            int follow = toInt(attrs, ("follow"));
                            String msg = toStr(attrs, ("msg"));
                            process_desc_started_cmd(null, ts, variant, lineage, follow, msg);
                            exec_started = false;
                            break;
                        }
                        case SP_JCMD_JTHRSTART -> {
                            String name = attrs.getValue("name");
                            String grpname = attrs.getValue("grpname");
                            String prntname = attrs.getValue("prntname");
                            str = attrs.getValue(("tid"));
                            uint64_t tid = str ? strtoull(str, null, 0) : 0;
                            str = attrs.getValue(("jthr"));
                            Vaddr jthr = str ? strtoull(str, null, 0) : 0;
                            str = attrs.getValue(("jenv"));
                            Vaddr jenv = str ? strtoull(str, null, 0) : 0;
                            process_jthr_start_cmd(null, name, grpname, prntname, tid, jthr, jenv, ts);
                            break;
                        }
                        case SP_JCMD_JTHREND -> {
                            str = attrs.getValue(("tid"));
                            uint64_t tid = str != null ? strtoull(str, null, 0) : 0;
                            str = attrs.getValue(("jthr"));
                            Vaddr jthr = str != null ? strtoull(str, null, 0) : 0;
                            str = attrs.getValue(("jenv"));
                            Vaddr jenv = str != null ? strtoull(str, null, 0) : 0;
                            process_jthr_end_cmd(null, tid, jthr, jenv, ts);
                            break;
                        }
                        case SP_JCMD_GCEND -> {
                            if (getDataDescriptor(DATA_GCEVENT) == null)
                                newDataDescriptor(DATA_GCEVENT);
                            process_gc_end_cmd(ts);
                        }
                        case SP_JCMD_GCSTART -> {
                            if (getDataDescriptor(DATA_GCEVENT) == null)
                                newDataDescriptor(DATA_GCEVENT);
                            process_gc_start_cmd(ts);
                        }
                        case SP_JCMD_PAUSE -> {
                            if (resume_ts != MAX_TIME) {
                                // data collection was active
                                hrtime_t delta = ts - resume_ts;
                                non_paused_time += delta;
                                resume_ts = MAX_TIME; // collection is paused
                            }
                            StringBuilder sb;
                            str = attrs.getValue(("name"));
                            if (str == null)
                                sb.sprintf(("Pause: %d.%09d"), (long) (ts / NANOSEC),
                                        (long) (ts % NANOSEC));
                            else
                                sb.sprintf(("Pause (%s): %ld.%09ld"), str,
                                        (long) (ts / NANOSEC), (long) (ts % NANOSEC));
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, sb));
                            break;
                        }
                        case SP_JCMD_RESUME -> {
                            if (resume_ts == MAX_TIME)
                                // data collection was paused
                                resume_ts = ts; // remember start time
                            StringBuilder sb;
                            sb.sprintf(("Resume: %ld.%09ld"), (long) (ts / NANOSEC), (long) (ts % NANOSEC));
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, sb));
                            if (exp_start_time == ZERO_TIME)
                                exp_start_time = ts;
                            break;
                        }
                        case SP_JCMD_THREAD_PAUSE -> {
                            str = attrs.getValue(("tid"));
                            long tid = str != null ? Long.parseLong(str) : 0;
                            String msg = String.format("Thread %d pause: %d.%09d", tid,
                                    (long) (ts / NANOSEC), (long) (ts % NANOSEC));
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                            break;
                        }
                        case SP_JCMD_THREAD_RESUME -> {
                            str = attrs.getValue(("tid"));
                            long tid = str != null ? Long.parseLong(str) : 0;
                            String msg = String.format("Thread %d resume: %d.%09d", tid,
                                    (long) (ts / NANOSEC), (long) (ts % NANOSEC));
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                            break;
                        }
                        case "map" -> {
                            ts += exp_start_time;
                            str = attrs.getValue(("vaddr"));
                            Vaddr vaddr = str != null ? strtoull(str, null, 0) : 0;
                            str = attrs.getValue(("size"));
                            int msize = str != null ? Integer.parseInt(str) : 0;
                            str = attrs.getValue(("foffset"));
                            int64_t offset = str != null ? strtoll(str, null, 0) : 0;
                            str = attrs.getValue(("modes"));
                            int64_t modes = str != null ? strtoll(str, null, 0) : 0;
                            str = attrs.getValue(("chksum"));
                            int64_t chksum = 0;
                            if (str != null)
                                chksum = Elf::normalize_checksum (strtoll(str, null, 0));
                            String name = (String) attrs.getValue(("name"));
                            str = attrs.getValue(("object"));
                            if ("segment".equals(str)) {
                                if (strcmp(name, ("LinuxKernel")) == 0)
                                    process_Linux_kernel_cmd(ts);
                                else
                                    process_seg_map_cmd(null, ts, vaddr, msize, 0,
                                            offset, modes, chksum, name);
                            } else if ("function".equals(str)) {
                                process_fn_load_cmd(dynfuncModule, name, vaddr, msize, ts);
                                dynfuncModule = null;
                            } else if ("dynfunc".equals(str)) {
                                if (dynfuncModule == null) {
                                    dynfuncModule = dbeSession -> createModule(get_dynfunc_lo(DYNFUNC_SEGMENT), name);
                                    dynfuncModule.flags |= MOD_FLAG_UNKNOWN;
                                    dynfuncModule.set_file_name(dynfuncModule.getMainSrc().get_name());
                                }
                                create_dynfunc(dynfuncModule,
                                        ( char*)attrs.getValue(("funcname")), vaddr, msize);
                            } else if ("jcm".equals(str)) {
                                str = attrs.getValue(("methodId"));
                                Vaddr mid = str != null ? strtoull(str, null, 0) : 0;
                                process_jcm_load_cmd(null, mid, vaddr, msize, ts);
                            }
                            break;
                        }
                        case "unmap" -> {
                            ts += exp_start_time;
                            str = attrs.getValue(("vaddr"));
                            Vaddr vaddr = str != null ? strtoull(str, null, 0) : 0;
                            process_seg_unmap_cmd(null, ts, vaddr);
                            break;
                        }
                    }
                }
                // end of code for event
            }
            case SP_TAG_PROFILE -> {
                pushElem(Element.EL_PROFILE);
                String str = attrs.getValue("name");
                if (str == null) {
                    return;
                }
                if ("profile".equals(str)) {
                    coll_params.profile_mode = 1;
                    str = attrs.getValue( ("numstates"));
                    if (str != null)
                        coll_params.lms_magic_id = Integer.parseInt(str);
                    str = attrs.getValue( ("ptimer"));
                    if (str != null)
                        coll_params.ptimer_usec = Integer.parseInt(str); // microseconds

                    PropDescr mstate_prop = null;
                    String  stateNames [/*LMS_NUM_STATES*/] = LMS_STATE_STRINGS;
                    String  stateUNames[/*LMS_NUM_STATES*/] = LMS_STATE_USTRINGS;
                    {
                        dDscr = newDataDescriptor(DATA_CLOCK);
                        PropDescr prop = new PropDescr(PROP_MSTATE, "MSTATE");
                        prop.uname = "Thread state");
                        prop.vtype = TYPE_UINT32;
                        // (states added below)
                        dDscr->addProperty (prop);
                        mstate_prop = prop;

                        prop = new PropDescr (PROP_NTICK,  ("NTICK"));
                        prop.uname = "Number of Profiling Ticks");
                        prop.vtype = TYPE_UINT32;
                        dDscr.addProperty(prop);
                    }

                    switch (coll_params.lms_magic_id) {
                        case LMS_MAGIC_ID_SOLARIS:
                            register_metric (Metric.Type.CP_TOTAL);
                            register_metric (Metric.Type.CP_TOTAL_CPU);
                            register_metric (Metric.Type.CP_LMS_USER);
                            register_metric (Metric.Type.CP_LMS_SYSTEM);
                            register_metric (Metric.Type.CP_LMS_TRAP);
                            register_metric (Metric.Type.CP_LMS_DFAULT);
                            register_metric (Metric.Type.CP_LMS_TFAULT);
                            register_metric (Metric.Type.CP_LMS_KFAULT);
                            register_metric (Metric.Type.CP_LMS_STOPPED);
                            register_metric (Metric.Type.CP_LMS_WAIT_CPU);
                            register_metric (Metric.Type.CP_LMS_SLEEP);
                            register_metric (Metric.Type.CP_LMS_USER_LOCK);
                            for (int ii = 0; ii < LMS_NUM_SOLARIS_MSTATES; ii++)
                                mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                            break;
                        case LMS_MAGIC_ID_ERKERNEL_KERNEL:
                            register_metric (Metric.Type.CP_KERNEL_CPU);
                        {
                            int ii = LMS_KERNEL_CPU;
                            mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                        }
                        break;
                        case LMS_MAGIC_ID_ERKERNEL_USER:
                            register_metric (Metric.Type.CP_TOTAL_CPU);
                            register_metric (Metric.Type.CP_LMS_USER);
                            register_metric (Metric.Type.CP_LMS_SYSTEM);
                        {
                            int ii = LMS_KERNEL_CPU;
                            mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                            ii = LMS_USER;
                            mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                            ii = LMS_SYSTEM;
                            mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                        }
                        break;
                        case LMS_MAGIC_ID_LINUX:
                            register_metric (Metric.Type.CP_TOTAL_CPU);
                        {
                            int ii = LMS_LINUX_CPU;
                            mstate_prop->addState (ii, stateNames[ii], stateUNames[ii]);
                        }
                        break;
                        default:
                            // odd
                            break;
                    }
                }
                else if ("heaptrace".equals(str)) {
                    coll_params.heap_mode = 1;
                    leaklistavail = true;
                    heapdataavail = true;
                    register_metric (Metric.Type.HEAP_ALLOC_BYTES);
                    register_metric (Metric.Type.HEAP_ALLOC_CNT);
                    register_metric (Metric.Type.HEAP_LEAK_BYTES);
                    register_metric (Metric.Type.HEAP_LEAK_CNT);
                    dDscr = newDataDescriptor (DATA_HEAP);
                }
                else if ("iotrace".equals(str)) {
                    coll_params.io_mode = 1;
                    iodataavail = true;
                    register_metric (Metric.Type.IO_READ_TIME);
                    register_metric (Metric.Type.IO_READ_BYTES);
                    register_metric (Metric.Type.IO_READ_CNT);
                    register_metric (Metric.Type.IO_WRITE_TIME);
                    register_metric (Metric.Type.IO_WRITE_BYTES);
                    register_metric (Metric.Type.IO_WRITE_CNT);
                    register_metric (Metric.Type.IO_OTHER_TIME);
                    register_metric (Metric.Type.IO_OTHER_CNT);
                    register_metric (Metric.Type.IO_ERROR_TIME);
                    register_metric (Metric.Type.IO_ERROR_CNT);
                    dDscr = newDataDescriptor (DATA_IOTRACE);
                } else if ("synctrace".equals(str)) {
                    coll_params.sync_mode = 1;
                    str = attrs.getValue( ("threshold"));
                    if (str != null)
                        coll_params.sync_threshold = Integer.parseInt(str);
                    str = attrs.getValue( ("scope"));
                    if (str != null)
                        coll_params.sync_scope = Integer.parseInt(str);
                    else  // Should only happen with old experiments; use the old default
                        coll_params.sync_scope = SYNCSCOPE_NATIVE | SYNCSCOPE_JAVA;
                    register_metric (Metric.Type.SYNC_WAIT_TIME);
                    register_metric (Metric.Type.SYNC_WAIT_COUNT);
                    dDscr = newDataDescriptor (DATA_SYNCH);
                } else if ("omptrace".equals(str)) {
                    coll_params.omp_mode = 1;
                    dDscr = newDataDescriptor (DATA_OMP, DDFLAG_NOSHOW);
                } else if ("hwcounter".equals(str)) {
                    str = attrs.getValue("cpuver");
                    int cpuver = str != null ? Integer.parseInt(str) : 0;
                    String counter = attrs.getValue("hwcname");
                    String int_name = attrs.getValue("int_name")); // may not be present
                    str = attrs.getValue( ("interval"));
                    int interval = str != null ? Integer.parseInt(str) : 0;
                    str = attrs.getValue( ("tag"));
                    int tag = str != null ? Integer.parseInt(str) : 0;
                    str = attrs.getValue( ("memop"));
                    int i_tpc = str != null ? Integer.parseInt(str) : 0;
                    String modstr = attrs.getValue("modstr");
                    process_hwcounter_cmd (null, cpuver, counter, int_name, interval, tag, i_tpc, modstr);
                    dDscr = newDataDescriptor (DATA_HWC);
                } else if ("hwsimctr".equals(str)) {
                    int cpuver = toInt (attrs,  ("cpuver"));
                    String hwcname = attrs.getValue("hwcname");
                    String int_name = attrs.getValue("int_name");
                    String metric = attrs.getValue("metric");
                    int reg = toInt (attrs,  ("reg_num"));
                    int interval = toInt (attrs,  ("interval"));
                    int timecvt = toInt (attrs,  ("timecvt"));
                    int i_tpc = toInt (attrs,  ("memop"));
                    int tag = toInt (attrs,  ("tag"));
                    process_hwsimctr_cmd (null, cpuver, hwcname, int_name, metric, reg,
                            interval, timecvt, i_tpc, tag);
                    dDscr = newDataDescriptor (DATA_HWC);
                }
                else if ("dversion".equals(str))
                    dversion = attrs.getValue("version");
                else if ("jprofile".equals(str)) {
                    has_java = true;
                    str = attrs.getValue( ("jversion"));
                    if (str != null)
                        jversion = str;
                } else if (strcmp (str, "datarace") == 0) {
                    coll_params.race_mode = 1;
                    racelistavail = true;
                    str = attrs.getValue( ("scheme"));
                    coll_params.race_stack = str != null ? Integer.parseInt(str) : 0;
                    register_metric (Metric.Type.RACCESS);
                    dDscr = newDataDescriptor (DATA_RACE);
                } else if ("deadlock".equals(str)) {
                    coll_params.deadlock_mode = 1;
                    deadlocklistavail = true;
                    register_metric (Metric.Type.DEADLOCKS);
                    dDscr = newDataDescriptor (DATA_DLCK);
                }
            }
            /* XXX -- obsolete tag, but is still written to experiments */
            case SP_TAG_DATAPTR -> {
                pushElem(Element.EL_DATAPTR);
            }
            case SP_TAG_PROFDATA -> {
                pushElem(Element.EL_PROFDATA);
                // SS12 HWC experiments are not well structured
                String fname = attrs.getValue( ("fname"));
                if (fname != null && strcmp (fname, SP_HWCNTR_FILE) == 0)
                    dDscr = newDataDescriptor (DATA_HWC);
            }
            case SP_TAG_PROFPCKT -> {
                pushElem(Element.EL_PROFPCKT);
                String str = attrs.getValue( ("kind")); // see Pckt_type
                int kind = str != null ? Integer.parseInt(str) : -1;
                if (kind < 0)
                    return;
                if (coll_params.omp_mode == 1)
                {
                    if (kind == OMP_PCKT)
                        dDscr = newDataDescriptor (DATA_OMP, DDFLAG_NOSHOW);
                    else if (kind == OMP2_PCKT)
                        dDscr = newDataDescriptor (DATA_OMP2, DDFLAG_NOSHOW);
                    else if (kind == OMP3_PCKT)
                        dDscr = newDataDescriptor (DATA_OMP3, DDFLAG_NOSHOW);
                    else if (kind == OMP4_PCKT)
                        dDscr = newDataDescriptor (DATA_OMP4, DDFLAG_NOSHOW);
                    else if (kind == OMP5_PCKT)
                        dDscr = newDataDescriptor (DATA_OMP5, DDFLAG_NOSHOW);
                }
                pDscr = newPacketDescriptor (kind, dDscr);
                return;
            }
            case SP_TAG_FIELD -> {
                pushElem(Element.EL_FIELD);
                if (pDscr != null) {
	                String name = attrs.getValue( ("name"));
                    if (name == null)
                        return;
                    int propID = dbeSession->registerPropertyName (name);
                    propDscr = new PropDescr(propID, name);
                    FieldDescr fldDscr = new FieldDescr(propID, name);

	                String str = attrs.getValue( ("type"));
                    if (str != null) {
                        if ("INT32".equals(str))
                            fldDscr->vtype = TYPE_INT32;
                        else if ("UINT32".equals(str))
                            fldDscr->vtype = TYPE_UINT32;
                        else if ("INT64".equals(str))
                            fldDscr->vtype = TYPE_INT64;
                        else if ("UINT64".equals(str))
                            fldDscr->vtype = TYPE_UINT64;
                        else if ("STRING".equals(str))
                            fldDscr->vtype = TYPE_STRING;
                        else if ("DOUBLE".equals(str))
                            fldDscr->vtype = TYPE_DOUBLE;
                        else if ("DATE".equals(str)) {
                            fldDscr->vtype = TYPE_DATE;
		                    String fmt = attrs.getValue( ("format"));
                            fldDscr->format = fmt != null ? fmt : "";
                        }
                    }
                    propDscr->vtype = fldDscr->vtype;

                    // TYPE_DATE is converted to TYPE_UINT64 in propDscr
                    if (fldDscr->vtype == TYPE_DATE)
                        propDscr->vtype = TYPE_UINT64;

                    // Fix some types until they are fixed in libcollector
                    if (propID == PROP_VIRTPC || propID == PROP_PHYSPC)
                    {
                        if (fldDscr->vtype == TYPE_INT32)
                            propDscr->vtype = TYPE_UINT32;
                        else if (fldDscr->vtype == TYPE_INT64)
                            propDscr->vtype = TYPE_UINT64;
                    }

                    // The following props get mapped to 32-bit values in readPacket
                    if (propID == PROP_CPUID || propID == PROP_THRID
                            || propID == PROP_LWPID)
                        propDscr->vtype = TYPE_UINT32; // override experiment property

                    str = attrs.getValue( ("uname"));
                    if (str != null) {
                        propDscr.uname = str;
                    }
                    str = attrs.getValue("noshow");
                    if (str != null && Integer.parseInt(str) != 0)
                        propDscr->flags |= PRFLAG_NOSHOW;

                    if (dDscr == null) {
                        StringBuilder sb;
                        sb.sprintf ( ("*** Error: data parsing failed. Log file is corrupted."));
                        warnq.append(new Emsg (Cmsg_warn.CMSG_ERROR, sb));
                        throw new SAXException (sb.toString ());
                    }

                    dDscr->addProperty (propDscr);
                    str = attrs.getValue( ("offset"));
                    if (str)
                        fldDscr->offset = Integer.parseInt(str);
                    pDscr->addField (fldDscr);
                }
            }
            case SP_TAG_STATE -> {
                pushElem(Element.EL_STATE);
                if (propDscr != null) {
	                String str = attrs.getValue( ("value"));
                    int value = str != null ? Integer.parseInt(str) : -1;
                    str = attrs.getValue( ("name"));
	                String ustr = attrs.getValue( ("uname"));
                    propDscr.addState(value, str, ustr);
                }
            }
            case SP_TAG_DTRACEFATAL -> {
                pushElem(Element.EL_DTRACEFATAL);
            }
            default -> {
                StringBuilder sb;
                sb.sprintf ( ("*** Warning: unrecognized element %s"), qName);
                warnq.append(new Emsg (Cmsg_warn.CMSG_WARN, sb));
                pushElem(Element.EL_NONE);
            }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (curElem == Element.EL_EVENT && mkind.value >= 0 && mnum >= 0) {
                String str;
                if (mec > 0)
                    str = String.format("%s -- %s", text != null ? text : "", strerror (mec));
                else
                    str = String.format("%s", text != null ? text : "");
                Emsg msg = new Emsg (mkind, mnum, str);
                if (mkind == Cmsg_warn.CMSG_WARN) {
                    if (mnum != COL_WARN_FSTYPE
                            || dbeSession->check_ignore_fs_warn() == false)
                        warnq.append(msg);
                    else
                        commentq.append(msg);
                }
                else if (mkind == Cmsg_warn.CMSG_ERROR || mkind == Cmsg_warn.CMSG_FATAL)
                    errorq.append(msg);
                else if (mkind == Cmsg_warn.CMSG_COMMENT) {
                    commentq.append(msg);
                }
                mkind = Cmsg_warn.CMSG_NONE;
                mnum = -1;
                mec = -1;
            }
            else if (curElem == Element.EL_PROFILE)
                dDscr = null;
            else if (curElem == Element.EL_PROFPCKT)
                pDscr = null;
            else if (curElem == Element.EL_FIELD)
                propDscr = null;
            text = null;
            popElem ();
        }

        @Override
        public void characters(char ch[], int start, int length) {
            switch (curElem) {
                case EL_COLLECTOR -> cversion = new String(ch, start, length);
                case EL_PROCESS -> process_arglist_cmd(new String(ch, start, length));
                case EL_EVENT -> text = new String(ch, start, length);
            }
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            String msg = String.format("%s at line %d, column %d",
                    e.getMessage(), e.getLineNumber(), e.getColumnNumber());
            throw new SAXException(msg);
        }

        enum Element
        {
            EL_NONE,
            EL_EXPERIMENT,
            EL_COLLECTOR,
            EL_SETTING,
            EL_PROCESS,
            EL_SYSTEM,
            EL_EVENT,
            EL_PROFILE,
            EL_DATAPTR,
            EL_PROFDATA,
            EL_PROFPCKT,
            EL_FIELD,
            EL_CPU,
            EL_STATE,
            EL_FREQUENCY,
            EL_POWERM,
            EL_DTRACEFATAL
        };

        static int toInt (Attributes attrs, String atr) {
            String str = attrs.getValue(atr);
            if (str == null) return 0;
            return Integer.parseInt(str);
        }

        static String toStr(Attributes attrs, String atr) {
            String str = attrs.getValue(atr);
            return str != null ? str : "";

        }

        void pushElem(Element element) {
            curElem = element;
            stack.add(curElem);
        }

        void popElem() {
            stack.remove(stack.size() - 1);
            curElem = stack.get(stack.size() - 1);
        }
    }

}
