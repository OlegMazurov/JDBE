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

import static org.mazurov.jdbe.Constants.*;

public class Emsg {

    public enum Cmsg_warn {
        CMSG_NONE(-1),
        CMSG_WARN(0),
        CMSG_ERROR(1),
        CMSG_FATAL(2),
        CMSG_COMMENT(3),
        CMSG_PARSER(4),
        CMSG_ARCHIVE(5);

        final int value;
        Cmsg_warn(int value) {
            this.value = value;
        }
    } ;

    Emsg next;       // next message in a queue
    Cmsg_warn warn;   // error/warning/...
    int flavor;       // the message flavor
    String par;        // the input parameter string
    String text;       // The I18N text of the message

    public Emsg(Cmsg_warn w, String i18n_text) {
        warn = w;
        flavor = 0;
        par = null;
        text = i18n_text;
        next = null;
    }
    
    public Emsg(Cmsg_warn w, int f, String param) {
        String type;
        warn = w;
        flavor = f;
        par = param != null ? param : "";
        next = null;

        // determine type
        type = switch (warn) {
            case CMSG_WARN -> "*** Collector Warning";
            case CMSG_ERROR -> "*** Collector Error";
            case CMSG_FATAL -> "*** Collector Fatal Error";
            case CMSG_COMMENT -> "Comment";
            case CMSG_PARSER -> "*** Log Error";
            case CMSG_ARCHIVE -> "*** Archive Error";
            default -> "*** Internal Error";
        };

        // now convert the message to its I18N'd string
        switch (flavor) {
            case COL_ERROR_NONE:
                text = String.format("%s: No error", type);
                break;
            case COL_ERROR_ARGS2BIG:
                text = String.format("%s: Data argument too long", type);
                break;
            case COL_ERROR_BADDIR:
                text = String.format("%s: Bad experiment directory name", type);
                break;
            case COL_ERROR_ARGS:
                text = String.format("%s: Data argument format error `%s'", type, par);
                break;
            case COL_ERROR_PROFARGS:
                text = String.format("%s: [UNUSED] Bad clock-profiling argument", type);
                break;
            case COL_ERROR_SYNCARGS:
                text = String.format("%s: [UNUSED] Bad synchronization tracing argument", type);
                break;
            case COL_ERROR_HWCARGS:
                text = String.format("%s: Bad hardware counter profiling argument", type);
                break;
            case COL_ERROR_DIRPERM:
                text = String.format("%s: Experiment directory is not writeable; check umask and permissions", type);
                break;
            case COL_ERROR_NOMSACCT:
                text = String.format("%s: Turning on microstate accounting failed", type);
                break;
            case COL_ERROR_PROFINIT:
                text = String.format("%s: Initializing clock-profiling failed", type);
                break;
            case COL_ERROR_SYNCINIT:
                text = String.format("%s: Initializing synchronization tracing failed", type);
                break;
            case COL_ERROR_HWCINIT:
                text = String.format("%s: Initializing hardware counter profiling failed -- %s", type, par);
                break;
            case COL_ERROR_HWCFAIL:
                text = String.format("%s: HW counter data collection failed; likely cause is that another process preempted the counters", type);
                break;
            case COL_ERROR_EXPOPEN:
                text = String.format("%s: Experiment initialization failed, %s", type, par);
                break;
            case COL_ERROR_SIZELIM:
                text = String.format("%s: Experiment size limit exceeded, writing %s", type, par);
                break;
            case COL_ERROR_SYSINFO:
                text = String.format("%s: system name can not be determined", type);
                break;
            case COL_ERROR_OVWOPEN:
                text = String.format("%s: Can't open overview %s", type, par);
                break;
            case COL_ERROR_OVWWRITE:
                text = String.format("%s: Can't write overview %s", type, par);
                break;
            case COL_ERROR_OVWREAD:
                text = String.format("%s: Can't read overview data for %s", type, par);
                break;
            case COL_ERROR_NOZMEM:
                text = String.format("%s: Open of /dev/zero failed: %s", type, par);
                break;
            case COL_ERROR_NOZMEMMAP:
                text = String.format("%s: Mmap of /dev/zero failed: %s", type, par);
                break;
            case COL_ERROR_NOHNDL:
                text = String.format("%s: Out of data handles for %s", type, par);
                break;
            case COL_ERROR_FILEOPN:
                text = String.format("%s: Open failed %s", type, par);
                break;
            case COL_ERROR_FILETRNC:
                text = String.format("%s: Truncate failed for file %s", type, par);
                break;
            case COL_ERROR_FILEMAP:
                text = String.format("%s: Mmap failed %s", type, par);
                break;
            case COL_ERROR_HEAPINIT:
                text = String.format("%s: Initializing heap tracing failed", type);
                break;
            case COL_ERROR_DISPINIT:
                text = String.format("%s: Initializing SIGPROF dispatcher failed", type);
                break;
            case COL_ERROR_ITMRINIT:
                text = String.format("%s: Initializing interval timer failed; %s", type, par);
                break;
            case COL_ERROR_SMPLINIT:
                text = String.format("%s: Initializing periodic sampling failed", type);
                break;
            case COL_ERROR_MPIINIT:
                text = String.format("%s: Initializing MPI tracing failed", type);
                break;
            case COL_ERROR_JAVAINIT:
                text = String.format("%s: Initializing Java profiling failed", type);
                break;
            case COL_ERROR_LINEINIT:
                text = String.format("%s: Initializing descendant process lineage failed", type);
                break;
            case COL_ERROR_NOSPACE:
                text = String.format("%s: Out of disk space writing `%s'", type, par);
                break;
            case COL_ERROR_ITMRRST:
                text = String.format("%s: Resetting interval timer failed: %s", type, par);
                break;
            case COL_ERROR_MKDIR:
                text = String.format("%s: Unable to create directory `%s'", type, par);
                break;
            case COL_ERROR_JVM2NEW:
                text = String.format("%s: JVM version with JVMTI requires more recent release of the performance tools; please upgrade", type);
                break;
            case COL_ERROR_JVMNOTSUPP:
                text = String.format("%s: JVM version does not support JVMTI; no java profiling is available", type);
                break;
            case COL_ERROR_JVMNOJSTACK:
                text = String.format("%s: JVM version does not support java callstacks; java mode data will not be recorded", type);
                break;
            case COL_ERROR_DYNOPEN:
                text = String.format("%s: Can't open dyntext file `%s'", type, par);
                break;
            case COL_ERROR_DYNWRITE:
                text = String.format("%s: Can't write dyntext file `%s'", type, par);
                break;
            case COL_ERROR_MAPOPEN:
                text = String.format("%s: Can't open map file `%s'", type, par);
                break;
            case COL_ERROR_MAPREAD:
                text = String.format("%s: Can't read map file `%s'", type, par);
                break;
            case COL_ERROR_MAPWRITE:
                text = String.format("%s: Can't write map file", type);
                break;
            case COL_ERROR_RESOLVE:
                text = String.format("%s: Can't resolve map file `%s'", type, par);
                break;
            case COL_ERROR_OMPINIT:
                text = String.format("%s: Initializing OpenMP tracing failed", type);
                break;
            case COL_ERROR_DURATION_INIT:
                text = String.format("%s: Initializing experiment-duration setting to `%s' failed", type, par);
                break;
            case COL_ERROR_RDTINIT:
                text = String.format("%s: Initializing RDT failed", type);
                break;
            case COL_ERROR_GENERAL:
                if (!par.isBlank())
                    text = String.format("%s: %s", type, par);
                else
                    text = String.format("%s: General error", type);
                break;
            case COL_ERROR_EXEC_FAIL:
                text = String.format("%s: Exec of process failed", type);
                break;
            case COL_ERROR_THR_MAX:
                text = String.format("%s: Thread count exceeds maximum (%s); set SP_COLLECTOR_NUMTHREADS for higher value", type, par);
                break;
            case COL_ERROR_IOINIT:
                text = String.format("%s: Initializing IO tracing failed", type);
                break;
            case COL_ERROR_NODATA:
                text = String.format("%s: No data was recorded in the experiment", type);
                break;
            case COL_ERROR_DTRACE_FATAL:
                text = String.format("%s: Fatal error reported from DTrace -- %s", type, par);
                break;
            case COL_ERROR_MAPSEEK:
                text = String.format("%s: Seek error on map file `%s'", type, par);
                break;
            case COL_ERROR_UNEXP_FOUNDER:
                text = String.format("%s: Unexpected value for founder `%s'", type, par);
                break;
            case COL_ERROR_LOG_OPEN:
                text = String.format("%s: Failure to open log file", type);
                break;
            case COL_ERROR_TSD_INIT:
                text = String.format("%s: TSD could not be initialized", type);
                break;
            case COL_ERROR_UTIL_INIT:
                text = String.format("%s: libcol_util.c initialization failed", type);
                break;
            case COL_ERROR_MAPCACHE:
                text = String.format("%s: Unable to cache mappings;  internal error (`%s')", type, par);
                break;
            case COL_WARN_NONE:
                text = String.format("%s: No warning", type);
                break;
            case COL_WARN_FSTYPE:
                text = String.format("%s: Experiment was written to a filesystem of type `%s'; data may be distorted", type, par);
                break;
            case COL_WARN_PROFRND:
                text = String.format("%s: Profiling interval was changed from requested %s (microsecs.) used", type, par);
                break;
            case COL_WARN_SIZELIM:
                text = String.format("%s: Experiment size limit exceeded", type);
                break;
            case COL_WARN_SIGPROF:
                text = String.format("%s: SIGPROF handler was changed (%s) during the run; profile data may be unreliable", type, par);
                break;
            case COL_WARN_SMPLADJ:
                text = String.format("%s: Periodic sampling rate adjusted %s microseconds", type, par);
                break;
            case COL_WARN_ITMROVR:
                text = String.format("%s: Application's attempt to set interval timer period to %s was ignored; its behavior may be changed", type, par);
                break;
            case COL_WARN_ITMRREP:
                text = String.format("%s: Collection interval timer period was changed (%s); profile data may be unreliable", type, par);
                break;
            case COL_WARN_SIGEMT:
                text = String.format("%s: SIGEMT handler was changed during the run; profile data may be unreliable", type);
                break;
            case COL_WARN_CPCBLK:
                text = String.format("%s: libcpc access blocked for hardware counter profiling", type);
                break;
            case COL_WARN_VFORK:
                text = String.format("%s: vfork(2) replaced by %s; execution may be affected", type, par);
                break;
            case COL_WARN_EXECENV:
                text = String.format("%s: exec environment augmented with %s missing collection variables", type, par);
                break;
            case COL_WARN_SAMPSIGUSED:
                text = String.format("%s: target installed handler for sample signal %s; samples may be lost", type, par);
                break;
            case COL_WARN_PAUSESIGUSED:
                text = String.format("%s: target installed handler for pause/resume signal %s; data may be lost or unexpected",
                        type, par);
                break;
            case COL_WARN_CPCNOTRESERVED:
                text = String.format("%s: unable to reserve HW counters; data may be distorted by other users of the counters", type);
                break;
            case COL_WARN_LIBTHREAD_T1: /* par contains the aslwpid... do we want to report it? */
                text = String.format("%s: application ran with a libthread version that may distort data; see collect(1) man page", type);
                break;
            case COL_WARN_SIGMASK:
                text = String.format("%s: Blocking %s ignored while in use for collection", type, par);
                break;
            case COL_WARN_NOFOLLOW:
                text = String.format("%s: Following disabled for uncollectable target (%s)", type, par);
                break;
            case COL_WARN_RISKYFOLLOW:
                text = String.format("%s: Following unqualified target may be unreliable (%s)", type, par);
                break;
            case COL_WARN_IDCHNG:
                text = String.format("%s: Imminent process ID change (%s) may result in an inconsistent experiment", type, par);
                break;
            case COL_WARN_OLDJAVA:
                text = String.format("%s: Java profiling requires JVM version 1.4.2_02 or later", type);
                break;
            case COL_WARN_ITMRPOVR:
                text = String.format("%s: Collector reset application's profile timer %s; application behavior may be changed", type, par);
                break;
            case COL_WARN_NO_JAVA_HEAP:
                text = String.format("%s: Java heap profiling is not supported by JVMTI; disabled ", type);
                break;
            case COL_WARN_RDT_PAUSE_NOMEM:
                text = String.format("%s: Data race detection paused at %s because of running out of internal memory", type, par);
                break;
            case COL_WARN_RDT_RESUME:
                text = String.format("%s: Data race detection resumed", type);
                break;
            case COL_WARN_RDT_THROVER:
                text = String.format("%s: Too many concurrent/created threads;  accesses with thread IDs above limit are not checked", type);
                break;
            case COL_WARN_THR_PAUSE_RESUME:
                text = String.format("%s: The collector_thread_pause/collector_thread_resume APIs are deprecated, and will be removed in a future release", type);
                break;
            case COL_WARN_NOPROF_DATA:
                text = String.format("%s: No profile data recorded in experiment", type);
                break;
            case COL_WARN_LONG_FSTAT:
                text = String.format("%s: Long fstat call -- %s", type, par);
                break;
            case COL_WARN_LONG_READ:
                text = String.format("%s: Long read call -- %s", type, par);
                break;
            case COL_WARN_LINUX_X86_APICID:
                text = String.format("%s: Linux libc sched_getcpu() not found; using x86 %s IDs rather than CPU IDs", type, par);
                break;

            case COL_COMMENT_NONE:
                text = String.format("%s", par);
                break;
            case COL_COMMENT_CWD:
                text = String.format("Initial execution directory `%s'", par);
                break;
            case COL_COMMENT_ARGV:
                text = String.format("Argument list `%s'", par);
                break;
            case COL_COMMENT_MAYASSNAP:
                text = String.format("Mayas snap file `%s'", par);
                break;

            case COL_COMMENT_LINEFORK:
                text = String.format("Target fork: %s", par);
                break;
            case COL_COMMENT_LINEEXEC:
                text = String.format("Target exec: %s", par);
                break;
            case COL_COMMENT_LINECOMBO:
                text = String.format("Target fork/exec: %s", par);
                break;
            case COL_COMMENT_FOXSNAP:
                text = String.format("Fox snap file `%s'", par);
                break;
            case COL_COMMENT_ROCKSNAP:
                text = String.format("Rock simulator snap file `%s'", par);
                break;
            case COL_COMMENT_BITINSTRDATA:
                text = String.format("Bit instrument data file `%s'", par);
                break;
            case COL_COMMENT_BITSNAP:
                text = String.format("Bit snap file `%s'", par);
                break;
            case COL_COMMENT_SIMDSPSNAP:
                text = String.format("Simulator dataspace profiling snap file `%s'", par);
                break;
            case COL_COMMENT_HWCADJ:
                text = String.format("%s: HWC overflow interval adjusted: %s", type, par);
                break;
            case COL_WARN_APP_NOT_READY:
                text = String.format("*** Collect: %s", par);
                break;
            case COL_WARN_RDT_DL_TERMINATE:
                text = String.format("%s: Actual deadlock detected; process terminated", type);
                break;
            case COL_WARN_RDT_DL_TERMINATE_CORE:
                text = String.format("%s: Actual deadlock detected; process terminated and core dumped", type);
                break;
            case COL_WARN_RDT_DL_CONTINUE:
                text = String.format("%s: Actual deadlock detected; process allowed to continue", type);
                break;
            default:
                text = String.format("%s: Number %d (\"%s\")", type, flavor, par);
                break;
        }
    }

    public String get_msg() {
        return text;
    };

    public Cmsg_warn get_warn() {
        return warn;
    };

    public static String pr_mesgs(Emsg msg, String null_str, String lead) {
        if (msg == null) return null_str;

        StringBuilder sb = new StringBuilder();
        for (Emsg m = msg; m != null; m = m.next) {
            sb.append(lead);
            sb.append(m.get_msg());
            sb.append("\n");
        }
        return sb.toString();
    }

}
