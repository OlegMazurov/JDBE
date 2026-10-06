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

public class Constants {
    public static final int COL_ERROR_NONE		= 0;	/* OK */
    public static final int COL_ERROR_ARGS2BIG		= 1;	/* data descriptor too long */
    public static final int COL_ERROR_BADDIR		= 2;	/* experiment directory error */
    public static final int COL_ERROR_ARGS		= 3;	/* data descriptor format error */
    public static final int COL_ERROR_PROFARGS		= 4;	/* clock profile parameter error */
    public static final int COL_ERROR_SYNCARGS		= 5;	/* synctrace parameter error */
    public static final int COL_ERROR_HWCARGS		= 6;	/* HWC profile parameter error */
    public static final int COL_ERROR_DIRPERM		= 7;	/* experiment directory not writable */
    public static final int COL_ERROR_NOMSACCT		= 8;	/* failed to turn on microstate accounting */
    public static final int COL_ERROR_PROFINIT		= 9;	/* failed to initialize profiling */
    public static final int COL_ERROR_SYNCINIT		= 10;	/* failed to initialize synchronization tracing */
    public static final int COL_ERROR_HWCINIT		= 11;	/* failed to initialize HWC profiling */
    public static final int COL_ERROR_HWCFAIL		= 12;	/* HWC profiling failed during run */
    public static final int COL_ERROR_EXPOPEN		= 13;	/* Experiment initialization failed */
    public static final int COL_ERROR_SIZELIM		= 14;	/* Experiment exceeded size limit */
    public static final int COL_ERROR_SYSINFO		= 15;	/* uname call failed */
    public static final int COL_ERROR_OVWOPEN		= 16;	/* Opening the overview file failed */
    public static final int COL_ERROR_OVWWRITE		= 17;	/* Writing the overview file failed */
    public static final int COL_ERROR_OVWREAD		= 18;	/* Reading the overview data failed */
    public static final int COL_ERROR_NOZMEM		= 19;	/* Unable to open /dev/zero */
    public static final int COL_ERROR_NOZMEMMAP		= 20;	/* Unable to map /dev/zero */
    public static final int COL_ERROR_NOHNDL		= 21;	/* No more handles available for data */
    public static final int COL_ERROR_FILEOPN		= 22;	/* Unable to open file */
    public static final int COL_ERROR_FILETRNC		= 23;	/* Unable to truncate file */
    public static final int COL_ERROR_FILEMAP		= 24;	/* Unable to mmap file */
    public static final int COL_ERROR_HEAPINIT		= 25;	/* Unable to install heap tracing */
    public static final int COL_ERROR_DISPINIT          = 26;   /* Failed to install dispatcher */
    public static final int COL_ERROR_ITMRINIT          = 27;   /* Failed to install interval timer */
    public static final int COL_ERROR_SMPLINIT          = 28;   /* Failed to initialize periodic sampling */
    public static final int COL_ERROR_MPIINIT               = 29;      /* Failed to initialize MPI tracing */
    public static final int COL_ERROR_JAVAINIT              = 30;      /* Failed to initialize Java profiling */
    public static final int COL_ERROR_LINEINIT              = 31;      /* Failed to initialize lineage tracing */
    public static final int COL_ERROR_NOSPACE               = 32;      /* Ran out of disk space writing file */
    public static final int COL_ERROR_ITMRRST               = 33;      /* Failed to reset interval timer */
    public static final int COL_ERROR_MKDIR                 = 34;      /* Failed to create (sub)directory */
    public static final int COL_ERROR_JVM2NEW               = 35;      /* JVM is too new for us to cope (JVMTI interface) */
    public static final int COL_ERROR_JVMNOTSUPP            = 36;      /* JVM does not support profiling (no JVMTI interface) */
    public static final int COL_ERROR_JVMNOJSTACK           = 37;      /* JVM does not support java stack unwind */
    public static final int COL_ERROR_DYNOPEN               = 38;      /* Unable to open dyntext file */
    public static final int COL_ERROR_DYNWRITE              = 39;      /* Unable to write dyntext file */
    public static final int COL_ERROR_MAPOPEN               = 40;      /* Unable to open map file */
    public static final int COL_ERROR_MAPREAD               = 41;      /* Unable to read map file */
    public static final int COL_ERROR_MAPWRITE              = 42;      /* Unable to write map file */
    public static final int COL_ERROR_RESOLVE               = 43;      /* Unable to resolve map file */
    public static final int COL_ERROR_OMPINIT               = 44;      /* Failure to initialize OpenMP tracing */
    public static final int COL_ERROR_DURATION_INIT         = 45;      /* Failure to initialize -t (duration) processing */
    public static final int COL_ERROR_RDTINIT		= 46;	/* Unable to install RDT */
    public static final int COL_ERROR_GENERAL		= 47;	/* General error */
    public static final int COL_ERROR_EXEC_FAIL		= 48;	/* Can't exec the process */
    public static final int COL_ERROR_THR_MAX		= 49;	/* More threads than are supported */
    public static final int COL_ERROR_IOINIT		= 50;	/* failed to initialize IO tracing */
    public static final int COL_ERROR_NODATA		= 51;	/* No data recorded in experiment */
    public static final int COL_ERROR_DTRACE_FATAL 	= 52;	/* Fatal error from er_kernel DTrace code */
    public static final int COL_ERROR_MAPSEEK 		= 53;	/* Error on seek of map file */
    public static final int COL_ERROR_UNEXP_FOUNDER 	= 54;	/* Unexpected value for SP_COLLECTOR_FOUNDER */
    public static final int COL_ERROR_LOG_OPEN	    	= 55;	/* Failure to open log.xml file */
    public static final int COL_ERROR_TSD_INIT	    	= 56;	/* TSD could not be initialized */
    public static final int COL_ERROR_UTIL_INIT	    	= 57;	/* libcol_util.c could not be initialized */
    public static final int COL_ERROR_MAPCACHE		= 58;	/* Unable to cache mappings */

    public static final int COL_WARN_NONE		= 200;	/* just a note, not a real warning */
    public static final int COL_WARN_FSTYPE		= 201;	/* Writing to a potentially-distorting file system */
    public static final int COL_WARN_PROFRND		= 202;	/* Profile interval rounded */
    public static final int COL_WARN_SIZELIM		= 203;	/* Size limit specified */
    public static final int COL_WARN_SIGPROF		= 204;	/* SIGPROF handler replaced */
    public static final int COL_WARN_SMPLADJ                = 205;     /* Periodic sampling rate adjusted */
    public static final int COL_WARN_ITMROVR                = 206;     /* Application interval timer resetting prevented */
    public static final int COL_WARN_ITMRREP                = 207;     /* Collection interval timer found to have been overridden */
    public static final int COL_WARN_SIGEMT                 = 208;	/* SIGEMT handler replaced */
    public static final int COL_WARN_CPCBLK                 = 209;     /* libcpc access blocked */
    public static final int COL_WARN_VFORK                  = 210;     /* vfork(2) switched to fork1(2) */
    public static final int COL_WARN_EXECENV                = 211;     /* incomplete exec environment */
    public static final int COL_WARN_SAMPSIGUSED            = 212;     /* target installed handler for sample signal */
    public static final int COL_WARN_PAUSESIGUSED           = 213;     /* target installed handler for pause signal */
    public static final int COL_WARN_CPCNOTRESERVED         = 214;     /* unable to reserve HW counters for kernel profiling */
    public static final int COL_WARN_LIBTHREAD_T1           = 215;     /* collection with classic libthread */
    public static final int COL_WARN_SIGMASK                = 216;     /* profiling signal masking overridden */
    public static final int COL_WARN_NOFOLLOW               = 217;     /* descendant following disabled */
    public static final int COL_WARN_RISKYFOLLOW            = 218;     /* descendant following unqualified */
    public static final int COL_WARN_IDCHNG                 = 219;     /* process ID change requested */
    public static final int COL_WARN_OLDJAVA		= 220;	/* Java profiling requires JVM version 1.4.2_02 or later */
    public static final int COL_WARN_ITMRPOVR		= 221;	/* Overriding app-set interval timer */
    public static final int COL_WARN_NO_JAVA_HEAP	= 222;	/* Java heap tracing not supported (JVM 1.5) */
    public static final int COL_WARN_RDT_PAUSE_NOMEM        = 223;     /* RDT paused because of running out of memory */
    public static final int COL_WARN_RDT_RESUME             = 224;     /* RDT resumed */
    public static final int COL_WARN_RDT_THROVER            = 225;     /* RDT: too many threads */
    public static final int COL_WARN_THR_PAUSE_RESUME       = 226;     /* use of thread pause/resume API is deprecateds */
    public static final int COL_WARN_APP_NOT_READY          = 227;     /* Application is not instrumented for RDT */
    public static final int COL_WARN_RDT_DL_TERMINATE       = 228;     /* RDT: terminate execution on actual deadlock */
    public static final int COL_WARN_RDT_DL_TERMINATE_CORE  = 229;     /* RDT: dump core and terminate execution on actual deadlock */
    public static final int COL_WARN_RDT_DL_CONTINUE        = 230;     /* RDT: continue execution on actual deadlock */
    public static final int COL_WARN_NOPROF_DATA	= 231;	/* No profile data recorded in experiment */
    public static final int COL_WARN_LONG_FSTAT 	= 232;	/* fstat call on /proc/self/map took > 200 ms. */
    public static final int COL_WARN_LONG_READ 		= 233;	/* read call on /proc/self/map took > 200 ms. */
    public static final int COL_WARN_LINUX_X86_APICID	= 234;	/* using x86 APIC IDs rather than Linux sched_getcpu() */

    public static final int COL_COMMENT_NONE            = 400;     /* no comment */
    public static final int COL_COMMENT_CWD		= 401;     /* initial execution directory */
    public static final int COL_COMMENT_ARGV		= 402;     /* arguments */
    public static final int COL_COMMENT_MAYASSNAP	= 403;     /* Mayas snap file name */
    public static final int COL_COMMENT_LINEFORK        = 404;     /* process fork'd */
    public static final int COL_COMMENT_LINEEXEC        = 405;     /* process exec'd */
    public static final int COL_COMMENT_LINECOMBO       = 406;     /* process combo fork/exec */
    public static final int COL_COMMENT_FOXSNAP		= 407;     /* Fox snap file name */
    public static final int COL_COMMENT_ROCKSNAP	= 408;     /* Rock simulator snap file name */
    public static final int COL_COMMENT_BITINSTRDATA	= 409;     /* Bit instrdata file name */
    public static final int COL_COMMENT_BITSNAP		= 410;     /* Bit snap file name */
    public static final int COL_COMMENT_SIMDSPSNAP	= 411;     /* Simulator dataspace profiling snap file name */
    public static final int COL_COMMENT_HWCADJ		= 412;     /* HWC overflow interval adjusted */
}
