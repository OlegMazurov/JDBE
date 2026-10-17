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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mazurov.jdbe.Emsg.Cmsg_warn;
import static org.mazurov.jdbe.Enums.*;
import static org.mazurov.jdbe.Enums.ProfData_type.*;
import static org.mazurov.jdbe.Enums.Platform_t.*;
import static org.mazurov.jdbe.Enums.WSize_t.*;

public class Experiment {

    private static final String SP_NOTES_FILE = "notes";
    private static final String SP_LOG_FILE = "log.xml";
    private static final String SP_MAP_FILE = "map.xml";
    private static final String SP_MAP_UNRESOLVABLE = "Unresolvable";
    private static final long PROT_READ_EXEC = 0x5; // PROT_READ | PROT_EXEC
    private static final String SP_FRINFO_FILE = "data.frameinfo";
    private static final String SP_PROFILE_FILE = "profile";

    private static final int FRAME_PCKT = 11;
    private static final int UID_PCKT = 17;
    private static final int STACK_INFO = 1;
    private static final int JAVA_INFO = 2;
    private static final int OMP_INFO = 3;
    private static final int OMP2_INFO = 5;
    private static final long COMPRESSED_INFO = 0x80000000L;

    private static final long SP_LEAF_CHECK_MARKER = -1L;
    private static final long SP_TRUNC_STACK_MARKER = -2L;
    private static final long SP_FAILED_UNWIND_MARKER = -3L;

    private static final long PROFILE_BUFFER_CHUNK = 16384;

    enum Exp_status {
        SUCCESS,
        INCOMPLETE,
        FAILURE
    };

    static class GCEvent {
        int id;
        long start;
        long end;
    }

    static class JThread {
        String name;
        String group_name;
        String parent_name;
        long tid;
        long jthr;
        long jenv;
        long jthr_id; 
        long start;
        long end;

        boolean is_system() {
            return "system".equals(group_name);
        }
    }

    static class PrUsage {
        long pr_tstamp, pr_create, pr_term, pr_rtime;
        long pr_utime, pr_stime, pr_ttime, pr_tftime, pr_dftime, pr_kftime,
                pr_ltime, pr_slptime, pr_wtime, pr_stoptime;
        long pr_minf, pr_majf, pr_nswap, pr_inblk, pr_oublk,
                pr_msnd, pr_mrcv, pr_sigs, pr_vctx, pr_ictx, pr_sysc, pr_ioch;

        static PrUsage bind64(ByteBuffer buf, long off) {
            PrUsage pu = new PrUsage();
            pu.pr_tstamp = timestruc64(buf, off + 8);
            pu.pr_create = timestruc64(buf, off + 24);
            pu.pr_term = timestruc64(buf, off + 40);
            pu.pr_rtime = timestruc64(buf, off + 56);
            pu.pr_utime = timestruc64(buf, off + 72);
            pu.pr_stime = timestruc64(buf, off + 88);
            pu.pr_ttime = timestruc64(buf, off + 104);
            pu.pr_tftime = timestruc64(buf, off + 120);
            pu.pr_dftime = timestruc64(buf, off + 136);
            pu.pr_kftime = timestruc64(buf, off + 152);
            pu.pr_ltime = timestruc64(buf, off + 168);
            pu.pr_slptime = timestruc64(buf, off + 184);
            pu.pr_wtime = timestruc64(buf, off + 200);
            pu.pr_stoptime = timestruc64(buf, off + 216);
            // offset 232: filltime[6], 96 bytes -> next real field at 232+96=328
            pu.pr_minf = buf.getLong((int) (off + 328));
            pu.pr_majf = buf.getLong((int) (off + 336));
            pu.pr_nswap = buf.getLong((int) (off + 344));
            pu.pr_inblk = buf.getLong((int) (off + 352));
            pu.pr_oublk = buf.getLong((int) (off + 360));
            pu.pr_msnd = buf.getLong((int) (off + 368));
            pu.pr_mrcv = buf.getLong((int) (off + 376));
            pu.pr_sigs = buf.getLong((int) (off + 384));
            pu.pr_vctx = buf.getLong((int) (off + 392));
            pu.pr_ictx = buf.getLong((int) (off + 400));
            pu.pr_sysc = buf.getLong((int) (off + 408));
            pu.pr_ioch = buf.getLong((int) (off + 416));
            return pu;
        }

        private static long timestruc64(ByteBuffer buf, long off) {
            long sec = buf.getLong((int) off);
            long nsec = buf.getLong((int) (off + 8));
            return sec * NANOSEC + nsec;
        }

        long[] getMstateValues() {
            return new long[] {
                    pr_utime, pr_stime, pr_ttime, pr_tftime, pr_dftime,
                    pr_kftime, pr_ltime, pr_slptime, pr_wtime, pr_stoptime
            };
        }
    }

    static class Sample {
        final int number;
        String start_label;
        String end_label;
        long start_time;
        long end_time;
        PrUsage prusage;

        Sample(int number) {
            this.number = number;
        }
    }

    List<Sample> samples = new ArrayList<>();
    String first_sample_label;
    private Sample sample_last_used;
    private GCEvent gcevent_last_used;

    Sample mapEventToSample(long ts) {
        if (sample_last_used != null && ts >= sample_last_used.start_time && ts <= sample_last_used.end_time)
            return sample_last_used;
        for (Sample sample : samples) {
            if (ts >= sample.start_time && ts <= sample.end_time) {
                sample_last_used = sample;
                return sample;
            }
        }
        return null;
    }

    GCEvent mapEventToGCEvent(long ts) {
        if (gcevent_last_used != null && ts >= gcevent_last_used.start && ts <= gcevent_last_used.end)
            return gcevent_last_used;
        for (GCEvent gcevent : gcevents) {
            if (ts >= gcevent.start && ts <= gcevent.end) {
                gcevent_last_used = gcevent;
                return gcevent;
            }
        }
        return null;
    }

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
    }

    static class PacketDescriptor {
        DataDescriptor ddscr;
        List<ExperimentHandler.FieldDescr> fields;

        PacketDescriptor (DataDescriptor ddscr) {
            this.ddscr = ddscr;
            fields = new ArrayList<>();
        }

        DataDescriptor getDataDescriptor() {
            return ddscr;
        }

        List<ExperimentHandler.FieldDescr> getFields() {
            return fields;
        }

        void addField (ExperimentHandler.FieldDescr fldDscr) {
            if (fldDscr != null) {
                fields.add(fldDscr);
            }
        }
    }

    protected String expt_name;      // name of experiment
    private int expIdx;               // this experiment's index in the DbeSession
    private int userExpId;            // 1-based id shown to the user
    int pid;                         // pid of the target process
    int ppid;                        // parent pid
    int pgrp;                        // process group id
    int sid;                         // session id
    String ucwd;                     // working directory

    boolean leaklistavail;           // heap-leak data was recorded
    boolean heapdataavail;           // heap-allocation data was recorded
    boolean iodataavail;             // I/O trace data was recorded
    boolean ifreqavail;              // instruction-frequency (count) data was recorded
    boolean racelistavail;           // true if there are race events in the experiment
    boolean deadlocklistavail;       // true if there are deadlock events in the experiment
    boolean timelineavail = true;    // true if there are valid timestamps in the experiment
    boolean hwc_default;             // true if HW counters were enabled by default
    boolean exec_started;            // true if exec was called, and exec error not yet seen
    boolean sparse_threads;

    String hostname;                 // Hostname (e.g. mymachine)
    String architecture;              // Architecture name ("sun4")
    String os_version;                // Operating system name
    String dversion;                  // driver version string (er_kernel)
    String jversion;                  // Java version string (java profiling)
    String username;                  
    String machinemodel;               // machine model name, if known
    Platform_t platform = Platform_t.Unknown; // Sparc, Sparcv9, Intel, ...
    WSize_t wsize = WSize_t.Wnone;    // word size: may be W32 or W64
    int clock;                        // CPU clock frequency, Mhz
    int varclock;                     // set if CPU clock frequency can change: turbo-mode
    int maxclock;                     // max. CPU clock frequency on MP machine
    int minclock;                     // min. CPU clock frequency on MP machine
    int ncpus;                        // count of CPUs where expt was recorded
    int page_size = 4096;             // page size (bytes)
    int npages;                       // number of page size
    long stack_base = 0xf0000000L;    // stack base

    long exp_start_time = ZERO_TIME;  // wall-clock hrtime at exp start (not zero based)
    long start_sec;                   // starting timeval secs.
    long non_paused_time;             // sum of periods where data collection is active
    long resume_ts;                   // tracks log.xml start/resume times
    long last_event = ZERO_TIME;      // hrtime of the last known event

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
    List<GCEvent> gcevents = new ArrayList<>();
    List<JThread> jthreads = new ArrayList<>();

    private final Map<Long, Integer> thrTagMap = new HashMap<>();

    int mapThrid(long rawTid) {
        return thrTagMap.computeIfAbsent(rawTid, k -> thrTagMap.size() + 1);
    }

    static class SegMem {
        long base;
        long size;
        long load_time;
        long unload_time = MAX_TIME;
        Histable obj; // a LoadObject (segment) or a Function (standalone/dynamic function)
    }

    List<SegMem> segItems = new ArrayList<>();     // master list of all segment loads/unloads
    List<SegMem> activeSegs = new ArrayList<>();   
                                                    

    static class MapRecord {
        enum Kind { LOAD, UNLOAD }
        Kind kind;
        Histable obj;
        long base;
        long size;
        long ts;
    }

    List<MapRecord> mrecs = new ArrayList<>();

    private void mrecInsert(MapRecord mrec) {
        int sz = mrecs.size();
        if (sz == 0 || mrecs.get(sz - 1).ts <= mrec.ts) {
            mrecs.add(mrec);
            return;
        }
        int lo = 0, hi = sz - 1;
        while (lo <= hi) {
            int md = (lo + hi) / 2;
            if (mrecs.get(md).ts < mrec.ts)
                lo = md + 1;
            else
                hi = md - 1;
        }
        mrecs.add(lo, mrec);
    }

    static class UIDnode {
        long uid;
        long val;
        UIDnode next;

        UIDnode(long uid, long val) {
            this.uid = uid;
            this.val = val;
        }
    }

    static class RawFramePacket {
        long uid;
        UIDnode uidn;       // native stack head
        UIDnode uidj;       // java stack head
        UIDnode omp_uid;
        int omp_state;
    }

    Map<Long, UIDnode> uidHTable = new HashMap<>();
    List<UIDnode> uidnodes = new ArrayList<>();
    Map<Long, RawFramePacket> frmpckts = new HashMap<>();
    int invalid_packet;

    private UIDnode get_uid_node(long uid, long val) {
        if (uid != 0) {
            UIDnode node = uidHTable.get(uid);
            if (node != null)
                return node;
        }
        UIDnode node = new UIDnode(uid, val);
        if (uid != 0) {
            uidHTable.put(uid, node);
            uidnodes.add(node);
        }
        return node;
    }

    private UIDnode get_uid_node(long uid) {
        if (uid == 0)
            return null;
        UIDnode node = uidHTable.get(uid);
        if (node != null)
            return node;
        node = new UIDnode(uid, 0);
        node.next = node; 
        return node;
    }

    private static long funcAddr32(long val32) {
        if (val32 == (SP_LEAF_CHECK_MARKER & 0xFFFFFFFFL))
            return SP_LEAF_CHECK_MARKER;
        if (val32 == (SP_TRUNC_STACK_MARKER & 0xFFFFFFFFL))
            return SP_TRUNC_STACK_MARKER;
        if (val32 == (SP_FAILED_UNWIND_MARKER & 0xFFFFFFFFL))
            return SP_FAILED_UNWIND_MARKER;
        return val32;
    }

    @FunctionalInterface
    private interface ElemReader {
        long read(ByteBuffer buf, long off);
    }

    private UIDnode addUid(ByteBuffer buf, long uid, long arrOff, long stackSize, long linkUid) {
        boolean is32 = (wsize == W32);
        ElemReader reader = is32 ? (b, off) -> funcAddr32(u32(b, off)) : Experiment::u64;
        return addUid(buf, uid, arrOff, stackSize, linkUid, is32 ? 4 : 8, reader);
    }

    private UIDnode addJavaInfoUid(ByteBuffer buf, long uid, long arrOff, long stackSize, long linkUid) {
        ElemReader reader = (b, off) -> ((off - arrOff) / 8) % 2 == 0 ? b.getInt((int) off) : b.getLong((int) off);
        return addUid(buf, uid, arrOff, stackSize, linkUid, 8, reader);
    }

    private UIDnode addUid(ByteBuffer buf, long uid, long arrOff, long stackSize, long linkUid,
                            int elemSize, ElemReader reader) {
        if (uid == 0)
            return null;
        int count = (int) (stackSize / elemSize);
        long v0 = reader.read(buf, arrOff);
        UIDnode node = null;
        UIDnode res = get_uid_node(uid, v0);
        UIDnode next = res;
        for (int i = 0; i < count; i++) {
            long off = arrOff + (long) i * elemSize;
            long v = reader.read(buf, off);
            if (next == null) {
                next = get_uid_node(0, v);
                if (node != null)
                    node.next = next;
            }
            node = next;
            next = node.next;
            if (node.val == 0)
                node.val = v;
            else if (node.val != v) // Algorithmic error (should never happen)
                node.val = SP_LEAF_CHECK_MARKER;
        }
        if (next == null && linkUid != 0 && node != null)
            node.next = get_uid_node(linkUid);
        return res;
    }

    private static int u16(ByteBuffer buf, long pos) {
        return buf.getShort((int) pos) & 0xFFFF;
    }

    private static long u32(ByteBuffer buf, long pos) {
        return buf.getInt((int) pos) & 0xFFFFFFFFL;
    }

    private static long u64(ByteBuffer buf, long pos) {
        return buf.getLong((int) pos);
    }

    void read_frameinfo_file() {
        read_data_file(SP_FRINFO_FILE, String.format("Loading CallStack Data: %s", new File(expt_name).getName()));
    }

    void read_profile_file() {
        DataDescriptor dDscr = getDataDescriptor(DATA_CLOCK);
        if (dDscr == null)
            return; // no clock-profiling data collected in this experiment
        read_data_file(SP_PROFILE_FILE, String.format("Loading Clock Profiling Data: %s", new File(expt_name).getName()));
        updateLastEventFromDataDescriptor(dDscr);
    }

    private void updateLastEventFromDataDescriptor(DataDescriptor dDscr) {
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        long n = dDscr.getSize();
        for (long i = 0; i < n; i++)
            update_last_event(dDscr.getLongValue(tstampOrd, i));
    }

    private static final String SP_HEAPTRACE_FILE = "heaptrace";

    void read_heaptrace_file() {
        DataDescriptor dDscr = getDataDescriptor(DATA_HEAP);
        if (dDscr == null)
            return; // no heap tracing in this experiment
        read_data_file(SP_HEAPTRACE_FILE, String.format("Loading Heap Tracing Data: %s", new File(expt_name).getName()));
        updateLastEventFromDataDescriptor(dDscr);
        compute_heap_leaked(dDscr);
    }

    private static final String SP_OVERVIEW_FILE = "overview";
    private static final int PRUSAGE64_SIZE = 8 + 20 * 16 + 22 * 8;

    void read_overview_file() {
        File f = new File(String.format("%s/%s", expt_name, SP_OVERVIEW_FILE));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; 
        }
        if (wsize != W64 || data.length < PRUSAGE64_SIZE)
            return; 
        newDataDescriptor(DATA_SAMPLE);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long nrecords = data.length / PRUSAGE64_SIZE;

        PrUsage prev = null;
        int sampleNumber = 1;
        for (long rec = 0; rec < nrecords; rec++) {
            long off = rec * (long) PRUSAGE64_SIZE;
            PrUsage cur = PrUsage.bind64(buf, off);
            if (prev == null) {
                prev = cur;
                continue; // skip the first record, matches native
            }
            Sample sample;
            if (sampleNumber > samples.size()) {
                sample = new Sample(sampleNumber);
                sample.start_label = "<unknown>";
                sample.end_label = "<unknown>";
                samples.add(sample);
            } else {
                sample = samples.get(sampleNumber - 1);
            }
            sampleNumber++;
            sample.start_time = prev.pr_tstamp + 1;
            sample.end_time = cur.pr_tstamp;
            prev.pr_rtime = cur.pr_rtime - prev.pr_rtime;
            prev.pr_utime = cur.pr_utime - prev.pr_utime;
            prev.pr_stime = cur.pr_stime - prev.pr_stime;
            prev.pr_ttime = cur.pr_ttime - prev.pr_ttime;
            prev.pr_tftime = cur.pr_tftime - prev.pr_tftime;
            prev.pr_dftime = cur.pr_dftime - prev.pr_dftime;
            prev.pr_kftime = cur.pr_kftime - prev.pr_kftime;
            prev.pr_ltime = cur.pr_ltime - prev.pr_ltime;
            prev.pr_slptime = cur.pr_slptime - prev.pr_slptime;
            prev.pr_wtime = cur.pr_wtime - prev.pr_wtime;
            prev.pr_stoptime = cur.pr_stoptime - prev.pr_stoptime;
            prev.pr_minf = cur.pr_minf - prev.pr_minf;
            prev.pr_majf = cur.pr_majf - prev.pr_majf;
            prev.pr_nswap = cur.pr_nswap - prev.pr_nswap;
            prev.pr_inblk = cur.pr_inblk - prev.pr_inblk;
            prev.pr_oublk = cur.pr_oublk - prev.pr_oublk;
            prev.pr_msnd = cur.pr_msnd - prev.pr_msnd;
            prev.pr_mrcv = cur.pr_mrcv - prev.pr_mrcv;
            prev.pr_sigs = cur.pr_sigs - prev.pr_sigs;
            prev.pr_vctx = cur.pr_vctx - prev.pr_vctx;
            prev.pr_ictx = cur.pr_ictx - prev.pr_ictx;
            prev.pr_sysc = cur.pr_sysc - prev.pr_sysc;
            prev.pr_ioch = cur.pr_ioch - prev.pr_ioch;
            sample.prusage = prev;
            prev = cur;
        }

        while (samples.size() >= sampleNumber)
            samples.remove(samples.size() - 1);

        if (prev != null)
            update_last_event(prev.pr_tstamp);
    }

    private void build_sample_events() {
        DataDescriptor dDscr = getDataDescriptor(DATA_SAMPLE);
        if (dDscr == null)
            return;

        PropDescr prop = new PropDescr(Prop_type.PROP_SMPLOBJ, "SMPLOBJ");
        prop.vtype = VType_type.TYPE_OBJ;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_TSTAMP, "TSTAMP");
        prop.uname = "High resolution timestamp";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_SAMPLE, "SAMPLE");
        prop.uname = "Sample number";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_EVT_TIME, "EVT_TIME");
        prop.uname = "Event duration";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        for (Sample sample : samples) {
            long recn = dDscr.addRecord();
            dDscr.setObjValue(Prop_type.PROP_SMPLOBJ.ordinal(), recn, sample);
            dDscr.setValue(Prop_type.PROP_SAMPLE.ordinal(), recn, sample.number);
            dDscr.setValue(Prop_type.PROP_TSTAMP.ordinal(), recn, sample.end_time);
            dDscr.setValue(Prop_type.PROP_EVT_TIME.ordinal(), recn, sample.end_time - sample.start_time);
        }
    }

    private void build_gc_events() {
        DataDescriptor dDscr = getDataDescriptor(DATA_GCEVENT);
        if (dDscr == null)
            return;

        PropDescr prop = new PropDescr(Prop_type.PROP_GCEVENTOBJ, "GCEVENTOBJ");
        prop.vtype = VType_type.TYPE_OBJ;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_TSTAMP, "TSTAMP");
        prop.uname = "High resolution timestamp";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_GCEVENT, "GCEVENT");
        prop.uname = "GCEvent number";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        prop = new PropDescr(Prop_type.PROP_EVT_TIME, "EVT_TIME");
        prop.uname = "Event duration";
        prop.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(prop);

        for (GCEvent gcevent : gcevents) {
            long recn = dDscr.addRecord();
            dDscr.setObjValue(Prop_type.PROP_GCEVENTOBJ.ordinal(), recn, gcevent);
            dDscr.setValue(Prop_type.PROP_GCEVENT.ordinal(), recn, gcevent.id);
            dDscr.setValue(Prop_type.PROP_TSTAMP.ordinal(), recn, gcevent.end);
            dDscr.setValue(Prop_type.PROP_EVT_TIME.ordinal(), recn, gcevent.end - gcevent.start);
        }
    }

    private void compute_heap_leaked(DataDescriptor dDscr) {
        PropDescr hleakedProp = new PropDescr(Prop_type.PROP_HLEAKED, "HLEAKED");
        hleakedProp.uname = "Bytes Leaked";
        hleakedProp.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(hleakedProp);

        PropDescr ddscrLnkProp = new PropDescr(Prop_type.PROP_DDSCR_LNK, "DDSCR_LNK");
        ddscrLnkProp.vtype = VType_type.TYPE_INT64;
        dDscr.addProperty(ddscrLnkProp);

        PropDescr hcurAllocsProp = new PropDescr(Prop_type.PROP_HCUR_ALLOCS, "HCUR_ALLOCS");
        hcurAllocsProp.uname = "Net Bytes Allocated";
        hcurAllocsProp.vtype = VType_type.TYPE_INT64;
        dDscr.addProperty(hcurAllocsProp);

        PropDescr hcurLeaksProp = new PropDescr(Prop_type.PROP_HCUR_LEAKS, "HCUR_LEAKS");
        hcurLeaksProp.uname = "Net Bytes Leaked";
        hcurLeaksProp.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(hcurLeaksProp);

        PropDescr hcurNetAllocProp = new PropDescr(Prop_type.PROP_HCUR_NET_ALLOC, "HCUR_NET_ALLOC");
        hcurNetAllocProp.vtype = VType_type.TYPE_INT64;
        //TODO: hcurNetAllocProp.flags = DDFLAG_NOSHOW;
        dDscr.addProperty(hcurNetAllocProp);

        int htypeOrd = Prop_type.PROP_HTYPE.ordinal();
        int hsizeOrd = Prop_type.PROP_HSIZE.ordinal();
        int hvaddrOrd = Prop_type.PROP_HVADDR.ordinal();
        int hovaddrOrd = Prop_type.PROP_HOVADDR.ordinal();
        int hleakedOrd = Prop_type.PROP_HLEAKED.ordinal();
        int ddscrLnkOrd = Prop_type.PROP_DDSCR_LNK.ordinal();
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();

        int size = (int) dDscr.getSize();
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++)
            order[i] = i;
        Arrays.sort(order, java.util.Comparator.comparingLong((Integer i) -> dDscr.getLongValue(tstampOrd, i)));

        Map<Long, Integer> addrToAllocIdx = new HashMap<>();
        for (int i : order) {
            int htype = dDscr.getIntValue(htypeOrd, i);
            long vaddr = dDscr.getLongValue(hvaddrOrd, i);
            long hsize = dDscr.getLongValue(hsizeOrd, i);
            if (htype == Heap_type.MALLOC_TRACE.value || htype == Heap_type.REALLOC_TRACE.value
                    || htype == Heap_type.MMAP_TRACE.value) {
                dDscr.setValue(hleakedOrd, i, hsize); // tentatively fully leaked
                if (vaddr != 0)
                    addrToAllocIdx.put(vaddr, i);
                if (htype == Heap_type.REALLOC_TRACE.value) {
                    long ovaddr = dDscr.getLongValue(hovaddrOrd, i);
                    Integer oldIdx = ovaddr != 0 ? addrToAllocIdx.remove(ovaddr) : null;
                    if (oldIdx != null) {
                        dDscr.setValue(hleakedOrd, oldIdx, 0);
                        dDscr.setValue(ddscrLnkOrd, oldIdx, i + 1);
                    }
                }
            } else if (htype == Heap_type.FREE_TRACE.value || htype == Heap_type.MUNMAP_TRACE.value) {
                Integer allocIdx = vaddr != 0 ? addrToAllocIdx.remove(vaddr) : null;
                if (allocIdx != null) {
                    dDscr.setValue(hleakedOrd, allocIdx, 0);
                    dDscr.setValue(ddscrLnkOrd, allocIdx, i + 1);
                }
            }
        }
    }

    private void read_data_file(String fname, String msg) {
        File f = new File(String.format("%s/%s", expt_name, fname));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; 
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long offset = 0;
        long remaining = data.length;
        invalid_packet = 0;
        while (true) {
            long pcktsz = readPacket(buf, offset, remaining);
            if (pcktsz == 0)
                break;
            remaining -= pcktsz;
            offset += pcktsz;
        }
        if (invalid_packet > 0)
            warnq.append(new Emsg(Cmsg_warn.CMSG_WARN, String.format(
                    "WARNING: There are %d invalid packet(s) in the %s file", invalid_packet, fname)));
    }

    private long readPacket(ByteBuffer buf, long offset, long remaining) {
        if (remaining < 4)
            return 0;
        int tsize = u16(buf, offset);
        if (tsize == 0) {
            return PROFILE_BUFFER_CHUNK - offset % PROFILE_BUFFER_CHUNK;
        }
        if (offset + tsize > buf.capacity())
            return 0; // truncated trailing packet
        int type = u16(buf, offset + 2);
        switch (type) {
            case FRAME_PCKT -> readFramePacket(buf, offset, tsize);
            case UID_PCKT -> readUidPacket(buf, offset, tsize);
            case 0 -> { } // EMPTY_PCKT: valid no-op packet
            default -> readGenericFieldPacket(buf, offset, type, tsize);
        }
        return tsize;
    }

    private void readGenericFieldPacket(ByteBuffer buf, long offset, int type, long pktsz) {
        PacketDescriptor pDscr = getPacketDescriptor(type);
        if (pDscr == null)
            return;
        DataDescriptor dDscr = pDscr.getDataDescriptor();
        if (dDscr == null)
            return;
        long recn = dDscr.addRecord();
        for (ExperimentHandler.FieldDescr field : pDscr.getFields()) {
            long fieldOff = offset + field.offset();
            if (field.propID() == Prop_type.PROP_THRID || field.propID() == Prop_type.PROP_LWPID
                    || field.propID() == Prop_type.PROP_CPUID) {
                long raw = switch (field.vtype()) {
                    case TYPE_INT32, TYPE_UINT32 -> u32(buf, fieldOff);
                    case TYPE_INT64, TYPE_UINT64 -> u64(buf, fieldOff);
                    default -> 0;
                };
                long val = field.propID() == Prop_type.PROP_THRID ? mapThrid(raw) : raw;
                dDscr.setValue(field.propID().ordinal(), recn, val);
                continue;
            }
            switch (field.vtype()) {
                case TYPE_INT32, TYPE_UINT32 -> dDscr.setValue(field.propID().ordinal(), recn, u32(buf, fieldOff));
                case TYPE_INT64, TYPE_UINT64 -> dDscr.setValue(field.propID().ordinal(), recn, u64(buf, fieldOff));
                case TYPE_STRING -> {
                    int len = (int) (pktsz - field.offset());
                    if (len > 0 && buf.get((int) fieldOff) != 0)
                        dDscr.setObjValue(field.propID().ordinal(), recn, readCString(buf, fieldOff, len));
                }
                default -> { } 
                                
            }
        }
    }

    private static String readCString(ByteBuffer buf, long off, int maxLen) {
        int start = (int) off;
        int end = start;
        int limit = start + maxLen;
        while (end < limit && buf.get(end) != 0)
            end++;
        byte[] bytes = new byte[end - start];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = buf.get(start + i);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private void readFramePacket(ByteBuffer buf, long offset, int tsize) {
        RawFramePacket fp = new RawFramePacket();
        fp.uid = u64(buf, offset + 8);       
        long hsize = u32(buf, offset + 4);   
        long ptr = offset + hsize;
        long end = offset + tsize;
        while (ptr < end) {
            long subHsize = u32(buf, ptr);           
            if (subHsize == 0 || ptr + subHsize > end)
                break;
            long kindRaw = u32(buf, ptr + 4);        
            boolean compressed = (kindRaw & COMPRESSED_INFO) != 0;
            int kind = (int) (kindRaw & ~COMPRESSED_INFO);
            switch (kind) {
                case STACK_INFO -> {
                    long stackOff = ptr + 16;         
                    long stackSize = subHsize - 16;
                    long uidn = u64(buf, ptr + 8);    
                    if (stackSize <= 0) {
                        fp.uidn = get_uid_node(uidn);
                    } else {
                        long linkUid = 0;
                        if (compressed) {
                            stackSize -= 8;
                            linkUid = u64(buf, stackOff + stackSize);
                        }
                        fp.uidn = addUid(buf, uidn, stackOff, stackSize, linkUid);
                    }
                }
                case JAVA_INFO -> {
                    long stackOff = ptr + 16;         
                    long stackSize = subHsize - 16;
                    long uidj = u64(buf, ptr + 8);    
                    if (stackSize <= 0) {
                        fp.uidj = get_uid_node(uidj);
                    } else {
                        long linkUid = 0;
                        if (compressed) {
                            stackSize -= 8;
                            linkUid = u64(buf, stackOff + stackSize);
                        }
                        fp.uidj = wsize == W64
                                ? addJavaInfoUid(buf, uidj, stackOff, stackSize, linkUid)
                                : addUid(buf, uidj, stackOff, stackSize, linkUid);
                    }
                }
                case OMP_INFO -> fp.omp_state = (int) u32(buf, ptr + 8); 
                case OMP2_INFO -> {
                    long ompUid = u64(buf, ptr + 16); 
                    fp.omp_uid = get_uid_node(ompUid);
                    fp.omp_state = (int) u32(buf, ptr + 8); 
                }
                default -> { }
            }
            ptr += subHsize;
        }
        frmpckts.put(fp.uid, fp);
    }

    private void readUidPacket(ByteBuffer buf, long offset, int tsize) {
        long flags = u32(buf, offset + 4);   
        long uid = u64(buf, offset + 8);     
        long arrOff = offset + 16;           
        long arrLength = tsize - 16;
        if (arrLength <= 0)
            return;
        long linkUid = 0;
        if ((flags & COMPRESSED_INFO) != 0) {
            arrLength -= 8;
            linkUid = u64(buf, arrOff + arrLength);
        }
        addUid(buf, uid, arrOff, arrLength, linkUid);
    }

    long gc_duration;

    private Exp_status status;        // Error status

    private boolean has_java = true;    
    protected String uarglist;       // argv[] array, as a string
    private String utargname;      // basename of argv[0] extracted from uarglist

    private int exp_maj_version;  // major version number of current experiment
    private int exp_min_version;  // minor version number of current experiment

    private long blksz = PROFILE_BUFFER_CHUNK; // binary data file block size

    private boolean obsolete;         // If pointer file experiment detected

    private boolean broken;
    private String cversion;       // collector version string

    private final DataDescriptor[] dataDscrs = new DataDescriptor[DATA_LAST.getValue()];
    private final PacketDescriptor[] pcktDscrs = new PacketDescriptor[Pckt_type.LAST_PCKT.value];

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

    public boolean isObsolete() {
        return obsolete;
    }

    public boolean hasJava() {
        return has_java;
    }

    public String get_expt_name() {
        return expt_name;   // Return the pathname to the experiment
    }

    public int getPID() {
        return pid;
    }

    public String getUtargname() {
        return utargname;
    }

    public long getStartTime() {
        return exp_start_time;
    }

    // Mirrors native's Experiment::getRelativeStartTime (Experiment.cc:2989-2999):
    // delta between this experiment's start and its founder's start, for fork/exec
    // descendant-process chains. This port has no founder/descendant tracking (every
    // experiment is its own founder), so the delta is always 0.
    public long getRelativeStartTime() {
        return 0;
    }

    public long getWallStartSec() {
        return start_sec;
    }

    public long getLastEvent() {
        return last_event != ZERO_TIME ? last_event : exp_start_time;
    }

    // Mirrors native's Experiment::getGCDuration (Experiment.h:216-221).
    public long getGCDuration() {
        return gc_duration;
    }

    public String getHostname() {
        return hostname;
    }

    public int getExpIdx() {
        return expIdx;
    }

    void setExpIdx(int idx) {
        expIdx = idx;
    }

    public int getUserExpId() {
        return userExpId;
    }

    void setUserExpId(int id) {
        userExpId = id;
    }

    public Exp_status get_status() {
        return status;
    }

    void set_clock(int clk) {
        if (clk > 0) {
            if (maxclock < clk) {
                maxclock = clk;
                clock = maxclock;
            }
            if (minclock == 0 || minclock > clk)
                minclock = clk;
        }
    }

    void update_last_event(long ts /* wall_ts */) {
        if (last_event == ZERO_TIME)
            last_event = ts; // not yet initialized
        // compare deltas to avoid hrtime_t wrap
        if (last_event - exp_start_time < ts - exp_start_time)
            last_event = ts;
    }

    private static long parseTStamp(String s) {
        int len = s.length();
        int i = 0;
        boolean neg = false;
        if (i < len && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        long secs = 0;
        while (i < len && Character.isDigit(s.charAt(i))) {
            secs = secs * 10 + (s.charAt(i) - '0');
            i++;
        }
        long ts = (neg ? -secs : secs) * NANOSEC;
        int dot = s.indexOf('.');
        if (dot >= 0) {
            long nanos = 0;
            int j = dot + 1;
            while (j < len && Character.isDigit(s.charAt(j))) {
                nanos = nanos * 10 + (s.charAt(j) - '0');
                j++;
            }
            ts += nanos;
        }
        return ts;
    }

    LoadObject get_dynfunc_lo(String loName) {
        LoadObject lo = createLoadObject(loName);
        if (lo.noname == null) {
            lo.type = LoadObject.seg_type.SEG_TEXT;
            lo.noname = DbeSession.getInstance().createModule(lo, "<Unknown>");
            lo.noname.flags |= Module.MOD_FLAG_UNKNOWN;
        }
        return lo;
    }

    Function create_dynfunc(Module mod, String fname, long vaddr, long fsize) {
        Function f = DbeSession.getInstance().createFunction();
        f.set_name(fname);
        f.flags |= Function.FUNC_FLAG_DYNAMIC;
        f.size = fsize;
        f.img_offset = vaddr;
        f.module = mod;
        mod.functions.add(f);
        mod.loadobject.functions.add(f);
        return f;
    }

    void process_sample_cmd(String cmd, long ts, int sample_number, String label) {
        if (sample_number == 0) {
            first_sample_label = label;
            return;
        }
        Sample prev_sample = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        String start_label = prev_sample != null ? prev_sample.end_label : first_sample_label;
        Sample sample = new Sample(sample_number);
        sample.start_label = start_label;
        sample.end_label = label;
        samples.add(sample);
    }

    void process_desc_start_cmd(String cmd, long ts, String variant, String lineage, int follow, String msg) {
        throw new RuntimeException("Experiment.process_desc_start_cmd not implemented");
    }

    void process_desc_started_cmd(String cmd, long ts, String variant, String lineage, int follow, String msg) {
        throw new RuntimeException("Experiment.process_desc_started_cmd not implemented");
    }

    void process_jthr_start_cmd(String cmd, String name, String grpname, String prntname,
                                 long tid, long jthr, long jenv, long ts) {
        JThread jthread = new JThread();
        jthread.name = name;
        jthread.group_name = grpname;
        jthread.parent_name = prntname;
        jthread.tid = mapThrid(tid); 
        jthread.jthr = jthr;
        jthread.jenv = jenv;
        jthread.jthr_id = jthreads.size(); 
        jthread.start = ts;
        jthread.end = MAX_TIME;
        jthreads.add(jthread);
    }

    void process_jthr_end_cmd(String cmd, long tid, long jthr, long jenv, long ts) {
        long ttid = mapThrid(tid); 
        JThread jthread = null;
        for (JThread jt : jthreads) {
            if (jt.tid == ttid) {
                jthread = jt;
                break;
            }
        }
        if (jthread == null) {
            jthread = new JThread();
            jthread.tid = ttid; 
            jthread.jthr = jthr;
            jthread.jenv = jenv;
            jthread.jthr_id = jthreads.size(); 
            jthread.start = ZERO_TIME;
            jthreads.add(jthread);
        }
        jthread.end = ts;
    }

    void process_gc_start_cmd(long ts) {
        if (!gcevents.isEmpty()) {
            GCEvent gcevent = gcevents.get(gcevents.size() - 1);
            // Weird: gc_start followed by another gc_start
            if (gcevent.end == MAX_TIME)
                return; // ignore nested gc_starts
        }
        GCEvent gcevent = new GCEvent();
        gcevent.start = ts;
        gcevent.end = MAX_TIME;
        gcevent.id = gcevents.size() + 1;
        gcevents.add(gcevent);
    }

    void process_gc_end_cmd(long ts) {
        if (gcevents.isEmpty()) {
            GCEvent gcevent = new GCEvent();
            gcevent.start = ZERO_TIME;
            gcevent.end = ts;
            gcevent.id = gcevents.size() + 1;
            gcevents.add(gcevent);
            return;
        }
        // extend the previous event (also covers gc_end followed by another gc_end)
        gcevents.get(gcevents.size() - 1).end = ts;
    }

    void process_Linux_kernel_cmd(long ts) {
        throw new RuntimeException("Experiment.process_Linux_kernel_cmd not implemented");
    }

    LoadObject createLoadObject(String path) {
        return DbeSession.getInstance().createLoadObject(path);
    }

    void process_seg_map_cmd(String cmd, long ts, long vaddr, int mapsize, int pagesize,
                              long offset, long modeflags, long chk, String nm) {
        if (nm == null || (nm.length() > 1 && nm.regionMatches(1, SP_MAP_UNRESOLVABLE, 0, SP_MAP_UNRESOLVABLE.length())))
            return;
        if (modeflags != PROT_READ_EXEC)
            return;
        LoadObject lo = createLoadObject(nm);
        if (lo.size == 0)
            lo.size = mapsize;

        MapRecord mrec = new MapRecord();
        mrec.kind = MapRecord.Kind.LOAD;
        mrec.obj = lo;
        mrec.base = vaddr;
        mrec.size = mapsize;
        mrec.ts = ts;
        mrecInsert(mrec);
    }

    void process_seg_unmap_cmd(String cmd, long ts, long vaddr) {
        MapRecord mrec = new MapRecord();
        mrec.kind = MapRecord.Kind.UNLOAD;
        mrec.base = vaddr;
        mrec.ts = ts;
        mrecInsert(mrec);
    }

    void process_fn_load_cmd(Module mod, String fname, long vaddr, int fsize, long ts) {
        if (mod != null) {
            for (Function f : mod.functions) {
                MapRecord mrec = new MapRecord();
                mrec.kind = MapRecord.Kind.LOAD;
                mrec.obj = f;
                mrec.base = f.img_offset;
                mrec.size = f.size;
                mrec.ts = ts;
                mrecInsert(mrec);
            }
            return;
        }
        LoadObject ds = get_dynfunc_lo(DYNFUNC_SEGMENT);
        Function dfunc = create_dynfunc(ds.noname, fname, vaddr, fsize);
        MapRecord mrec = new MapRecord();
        mrec.kind = MapRecord.Kind.LOAD;
        mrec.obj = dfunc;
        mrec.base = vaddr;
        mrec.size = fsize;
        mrec.ts = ts;
        mrecInsert(mrec);
    }

    private void processMapRecords() {
        for (MapRecord mrec : mrecs) {
            switch (mrec.kind) {
                case LOAD -> {
                    SegMem smem = new SegMem();
                    smem.base = mrec.base;
                    smem.size = mrec.size;
                    smem.load_time = mrec.ts;
                    smem.obj = mrec.obj;
                    if (checkSegOverlap(smem)) {
                        segItems.add(smem);
                        activeSegs.add(smem);
                    }
                }
                case UNLOAD -> {
                    for (int i = activeSegs.size() - 1; i >= 0; i--) {
                        SegMem smem = activeSegs.get(i);
                        if (smem.base == mrec.base) {
                            smem.unload_time = mrec.ts;
                            activeSegs.remove(i);
                            break;
                        }
                    }
                }
            }
        }
        mrecs.clear();
    }

    private List<SegMem> sortedSegItems;
    private long[] segEndMax;
    private int segIndexSize = -1; // segItems.size() as of the last index build

    private void ensureSegIndex() {
        if (segIndexSize == segItems.size())
            return;
        sortedSegItems = new ArrayList<>(segItems);
        sortedSegItems.sort((a, b) -> Long.compare(a.base, b.base));
        segEndMax = new long[sortedSegItems.size()];
        long m = Long.MIN_VALUE;
        for (int i = 0; i < sortedSegItems.size(); i++) {
            m = Math.max(m, sortedSegItems.get(i).base + sortedSegItems.get(i).size);
            segEndMax[i] = m;
        }
        segIndexSize = segItems.size();
    }

    // Index of the last entry with base <= addr, or -1 if none.
    private int segUpperBound(long addr) {
        int lo = 0, hi = sortedSegItems.size() - 1, res = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (sortedSegItems.get(mid).base <= addr) {
                res = mid;
                lo = mid + 1;
            } else
                hi = mid - 1;
        }
        return res;
    }

    Function map_Vaddr_to_PC(long addr, long ts) {
        ensureSegIndex();
        SegMem best = null;
        for (int i = segUpperBound(addr); i >= 0 && segEndMax[i] > addr; i--) {
            SegMem s = sortedSegItems.get(i);
            if (addr < s.base + s.size && ts >= s.load_time && ts < s.unload_time) {
                if (best == null || s.load_time > best.load_time)
                    best = s;
            }
        }
        if (best == null)
            return DbeSession.getInstance().getUnknownFunction();
        if (best.obj instanceof Function f)
            return f;
        LoadObject lo = (LoadObject) best.obj;
        if (lo.placeholderFunc == null) {
            lo.placeholderFunc = DbeSession.getInstance().createFunction();
            lo.placeholderFunc.set_name(lo.get_name());
            lo.placeholderFunc.flags |= Function.FUNC_FLAG_SIMULATED;
        }
        return lo.placeholderFunc;
    }

    Function map_jmid_to_PC(long mid, long ts) {
        if (mid == 0)
            return DbeSession.getInstance().getJUnknownFunction();
        Object obj = jmapsLocate(mid, ts);
        if (!(obj instanceof JMethod jm))
            return DbeSession.getInstance().getJUnknownFunction();
        return jm;
    }

    static final JThread JTHREAD_NONE = new JThread();

    private JThread map_pckt_to_Jthread(long tid, long ts) {
        if (!has_java)
            return null; 
        for (JThread jt : jthreads)
            if (jt.tid == tid && ts >= jt.start && ts < jt.end)
                return jt;
        return JTHREAD_NONE;
    }

    JThread lookupJThread(long tid, long ts) {
        return map_pckt_to_Jthread(tid, ts);
    }

    JThread get_jthread(long tid) {
        if (!has_java)
            return null; 
        JThread best = null;
        for (JThread jt : jthreads) {
            if (jt.tid != tid)
                continue;
            if (best == null)
                best = jt;
            else if (!jt.is_system() && jt.jthr_id < best.jthr_id)
                best = jt;
        }
        return best == null ? JTHREAD_NONE : best;
    }

    record ThreadName(String name, boolean isFinal) {}

    ThreadName describeThread(long thrid, long ts) {
        if (has_java) {
            JThread jthread = map_pckt_to_Jthread(thrid, ts);
            if (jthread != null && jthread != JTHREAD_NONE) {
                return new ThreadName(String.format(
                        "Process %d, Thread %d, JThread %d '%s', Group '%s', Parent '%s'",
                        getUserExpId(), thrid, jthread.jthr_id,
                        jthread.name == null ? "" : jthread.name,
                        jthread.group_name == null ? "" : jthread.group_name,
                        jthread.parent_name == null ? "" : jthread.parent_name),
                        true);
            }
        }
        return new ThreadName(String.format("Process %d, Thread %d", getUserExpId(), thrid), !has_java);
    }

    List<Function> resolveStack(long frinfo, long thrid, long ts) {
        RawFramePacket fp = frmpckts.get(frinfo);
        Function unknown = DbeSession.getInstance().getUnknownFunction();
        if (fp == null)
            return List.of(unknown);

        List<Function> javaStack = null;
        if (fp.uidj != null) {
            javaStack = new ArrayList<>();
            for (UIDnode node = fp.uidj; node != null; ) {
                UIDnode midNode = node.next;
                if (midNode == null)
                    break;
                javaStack.add(map_jmid_to_PC(midNode.val, ts));
                node = midNode.next;
            }
        }
        boolean javaResolvedReal = javaStack != null && !javaStack.isEmpty()
                && javaStack.get(0) != DbeSession.getInstance().getJUnknownFunction();

        JThread jthread = map_pckt_to_Jthread(thrid, ts);
        if (jthread == JTHREAD_NONE && javaResolvedReal)
            jthread = null; 
        boolean isSystemThread = jthread != null && jthread != JTHREAD_NONE
                && "system".equals(jthread.group_name);
        if (jthread == JTHREAD_NONE || isSystemThread)
            return List.of(DbeSession.getInstance().getJvmSystemFunction());

        if (javaStack != null && !javaStack.isEmpty())
            return javaStack;

        List<Function> nativeStack = new ArrayList<>();
        for (UIDnode node = fp.uidn; node != null; node = node.next)
            nativeStack.add(map_Vaddr_to_PC(node.val, ts));
        if (!nativeStack.isEmpty())
            return nativeStack;

        return List.of(unknown);
    }

    private Map<Long, long[]> frinfoFirstSeen;

    private void ensureFrinfoFirstSeen() {
        if (frinfoFirstSeen != null)
            return;
        frinfoFirstSeen = new HashMap<>();
        int frinfoOrd = Prop_type.PROP_FRINFO.ordinal();
        int thridOrd = Prop_type.PROP_THRID.ordinal();
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        for (ProfData_type dt : new ProfData_type[] {DATA_CLOCK, DATA_HEAP}) {
            DataDescriptor d = getDataDescriptor(dt);
            if (d == null)
                continue;
            long n = d.getSize();
            for (long i = 0; i < n; i++) {
                long frinfo = d.getLongValue(frinfoOrd, i);
                frinfoFirstSeen.putIfAbsent(frinfo,
                        new long[] {d.getIntValue(thridOrd, i), d.getLongValue(tstampOrd, i)});
            }
        }
    }

    List<Function> resolveStackById(long frinfo) {
        ensureFrinfoFirstSeen();
        long[] ctx = frinfoFirstSeen.get(frinfo);
        return ctx == null ? null : resolveStack(frinfo, ctx[0], ctx[1]);
    }

    private boolean checkSegOverlap(SegMem smem) {
        SegMem lo = null;
        for (SegMem other : activeSegs) {
            if (other.base <= smem.base && (lo == null || other.base > lo.base))
                lo = other;
        }
        if (lo != null && lo.base + lo.size > smem.base) {
            if (smem.base == lo.base && smem.size == lo.size
                    && (lo.obj.get_name().contains(smem.obj.get_name())
                        || smem.obj.get_name().contains(lo.obj.get_name())))
                return false; // duplicate load record
            warnSegOverlap(smem, lo);
        }
        List<SegMem> hiOverlaps = new ArrayList<>();
        for (SegMem other : activeSegs) {
            if (other.base > smem.base && other.base < smem.base + smem.size)
                hiOverlaps.add(other);
        }
        hiOverlaps.sort((a, b) -> Long.compare(a.base, b.base));
        for (SegMem other : hiOverlaps)
            warnSegOverlap(smem, other);
        return true;
    }

    private void warnSegOverlap(SegMem smem, SegMem other) {
        String msg = String.format(
                "*** Warning: Segment %s [0x%x-0x%x] overlaps %s [0x%x-0x%x], which has been implicitly unloaded",
                smem.obj.get_name(), smem.base, smem.base + smem.size,
                other.obj.get_name(), other.base, other.base + other.size);
        warnq.append(new Emsg(Cmsg_warn.CMSG_WARN, msg));
    }

    void process_jcm_load_cmd(String cmd, long mid, long vaddr, int msize, long ts) {
        Object obj = jmapsLocate(mid, ts);
        if (!(obj instanceof JMethod jfunc))
            return; 
        MapRecord mrec = new MapRecord();
        mrec.kind = MapRecord.Kind.LOAD;
        mrec.obj = jfunc;
        mrec.base = vaddr;
        mrec.size = msize;
        mrec.ts = ts;
        mrecInsert(mrec);
    }

    private final Map<Long, TreeMap<Long, Object>> jmaps = new HashMap<>();

    private void jmapsInsert(long id, long ts, Object obj) {
        jmaps.computeIfAbsent(id, k -> new TreeMap<>()).put(ts, obj);
    }

    private Object jmapsLocate(long id, long ts) {
        TreeMap<Long, Object> versions = jmaps.get(id);
        if (versions == null)
            return null;
        Map.Entry<Long, Object> e = versions.floorEntry(ts);
        return e != null ? e.getValue() : null;
    }

    private static final int ARCH_JCLASS = (10 << 8) | 3;
    private static final int ARCH_JMETHOD = (11 << 8) | 3;
    private static final int ARCH_JCLASS_LOCATION = (14 << 8) | 3;
    private static final String SP_JCLASSES_FILE = "jclasses";

    private LoadObject javaClassesLo;
    private final Map<String, Module> javaModulesByClassName = new HashMap<>();

    void read_java_classes_file() {
        File f = new File(String.format("%s/%s", expt_name, SP_JCLASSES_FILE));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; 
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        Module curMod = null;
        long curLoaded = ZERO_TIME;
        long offset = 0;
        while (offset + 4 <= data.length) {
            int tsize = u16(buf, offset);
            if (tsize == 0 || offset + tsize > data.length)
                break;
            int type = u16(buf, offset + 2);
            if (type == ARCH_JCLASS) {
                long classId = u64(buf, offset + 8);
                long tstamp = u64(buf, offset + 16);
                long strOff = offset + 24;
                long strLimit = offset + tsize;
                ArchStr className = readArchString(buf, strOff, strLimit);
                ArchStr fileName = readArchString(buf, strOff + className.paddedLen, strLimit);
                if (className.value.startsWith("L")) { // skip legacy "["-prefixed array-class records
                    curMod = getOrCreateJavaModule(className.value, fileName.value);
                    curLoaded = tstamp;
                    jmapsInsert(classId, tstamp, curMod);
                } else {
                    curMod = null;
                }
            } else if (type == ARCH_JMETHOD && curMod != null) {
                long methodId = u64(buf, offset + 16);
                long strOff = offset + 24;
                long strLimit = offset + tsize;
                ArchStr sName = readArchString(buf, strOff, strLimit);
                ArchStr signature = readArchString(buf, strOff + sName.paddedLen, strLimit);
                String fullname = String.format("%s.%s", curMod.get_name(), sName.value);
                JMethod jm = curMod.find_jmethod(fullname, signature.value);
                if (jm == null) {
                    jm = DbeSession.getInstance().createJMethod();
                    jm.size = -1;
                    jm.module = curMod;
                    jm.signature = signature.value;
                    jm.set_name(fullname);
                    curMod.functions.add(jm);
                    curMod.loadobject.functions.add(jm);
                }
                jmapsInsert(methodId, curLoaded, jm);
            }
            offset += tsize;
        }
    }

    private Module getOrCreateJavaModule(String className, String fileName) {
        String dotted = className;
        if (dotted.startsWith("L") && dotted.endsWith(";"))
            dotted = dotted.substring(1, dotted.length() - 1);
        dotted = dotted.replace('/', '.');
        Module mod = javaModulesByClassName.get(dotted);
        if (mod != null)
            return mod;
        if (javaClassesLo == null)
            javaClassesLo = createLoadObject("JAVA_CLASSES");
        mod = DbeSession.getInstance().createModule(javaClassesLo, dotted);
        mod.set_file_name(fileName);
        javaModulesByClassName.put(dotted, mod);
        return mod;
    }

    private static class ArchStr {
        final String value;
        final int paddedLen; // bytes consumed including the padded-to-4-byte terminator

        ArchStr(String value, int paddedLen) {
            this.value = value;
            this.paddedLen = paddedLen;
        }
    }

    private static ArchStr readArchString(ByteBuffer buf, long off, long limit) {
        int start = (int) off;
        int end = start;
        int lim = (int) limit;
        while (end < lim && buf.get(end) != 0)
            end++;
        byte[] bytes = new byte[end - start];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = buf.get(start + i);
        String s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        int padded = (bytes.length + 4) & ~3;
        return new ArchStr(s, padded);
    }

    void process_hwcounter_cmd(String cmd, int cpuver, String counter, String int_name,
                                int interval, int tag, int i_tpc, String modstr) {
        throw new RuntimeException("Experiment.process_hwcounter_cmd not implemented");
    }

    void process_hwsimctr_cmd(String cmd, int cpuver, String hwcname, String int_name, String metric,
                               int reg, int interval, int timecvt, int i_tpc, int tag) {
        throw new RuntimeException("Experiment.process_hwsimctr_cmd not implemented");
    }

    public void DBG_memuse(String sname) {
        throw new RuntimeException("Experiment.DBG_memuse not implemented");
    }

    public Emsg fetch_warnings() {
        return warnq.fetch();
    }

    public Emsg fetch_errors() {
        return errorq.fetch();
    }

    public Emsg fetch_comments() {
        return commentq.fetch();
    }

    public Emsg fetch_notes() {
        return notesq.fetch();
    }

    public Emsg fetch_pprocq() {
        return pprocq.fetch();
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
        try (InputStream body = new FileInputStream(logFile);
             InputStream in = new SequenceInputStream(java.util.Collections.enumeration(List.of(
                     new ByteArrayInputStream(("<" + ExperimentHandler.SP_TAG_LOG_WRAPPER + ">").getBytes()),
                     body,
                     new ByteArrayInputStream(("</" + ExperimentHandler.SP_TAG_LOG_WRAPPER + ">").getBytes()))))) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler dh = new ExperimentHandler();
            saxParser.parse(in, dh);
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
        post_process();
        build_gc_events();
        if (last_event != ZERO_TIME) { // if last_event is known
            long ts = last_event - exp_start_time;
            String msg = String.format("Experiment Ended: %d.%09d%nData Collection Duration: %d.%09d",
                    ts / NANOSEC, ts % NANOSEC,
                    non_paused_time / NANOSEC, non_paused_time % NANOSEC);
            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
        }

        // Check for incomplete experiment, and inform the user
        if (status == Exp_status.INCOMPLETE) {
            if (exec_started)
                // experiment ended with the exec, not abnormally
                status = Exp_status.SUCCESS;
            else
                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "*** Note: experiment was not closed"));
        }
        // write a descriptive header for the experiment
        write_header();
        return status;
    }

    private void post_process() {
        // update non_paused_time after final update to "last_event"
        if (resume_ts != MAX_TIME && last_event != ZERO_TIME) {
            long ts = last_event - exp_start_time;
            non_paused_time += ts - resume_ts;
            resume_ts = MAX_TIME; // collection is paused
        }

        // GC: prune events outside of experiment duration, calculate GC duration,
        // renumber ids
        gc_duration = ZERO_TIME;
        gcevents.removeIf(gcevent -> gcevent.end - exp_start_time < 0 || last_event - gcevent.start < 0);
        for (int index = 0; index < gcevents.size(); index++) {
            GCEvent gcevent = gcevents.get(index);
            gcevent.id = index + 1; // renumber to account for any deleted events
            if (gcevent.start - exp_start_time < 0 || gcevent.start == ZERO_TIME)
                gcevent.start = exp_start_time; // truncate events that start before experiment start
            if (last_event - gcevent.end < 0)
                gcevent.end = last_event; // truncate events that end after experiment end
            gc_duration += gcevent.end - gcevent.start;
        }
    }

    Exp_status open(String path) {
        if (find_expdir(path) != Exp_status.SUCCESS)
            return status;

        read_log_file();
        if (status == Exp_status.FAILURE) {
            if (fetch_errors() == null)
                errorq.append(new Emsg(Cmsg_warn.CMSG_FATAL, "*** Error: log file in experiment could not be parsed"));
            return status;
        }

        read_notes_file();
        read_java_classes_file();
        read_map_file();
        read_overview_file();
        build_sample_events();
        read_frameinfo_file();
        read_profile_file();
        read_heaptrace_file();
        return status;
    }

    void read_map_file() {
        File mapFile = new File(String.format("%s/%s", expt_name, SP_MAP_FILE));
        if (!mapFile.exists())
            return;
        try (InputStream body = new FileInputStream(mapFile);
             InputStream in = new SequenceInputStream(java.util.Collections.enumeration(List.of(
                     new ByteArrayInputStream(("<" + ExperimentHandler.SP_TAG_LOG_WRAPPER + ">").getBytes()),
                     body,
                     new ByteArrayInputStream(("</" + ExperimentHandler.SP_TAG_LOG_WRAPPER + ">").getBytes()))))) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler dh = new ExperimentHandler();
            saxParser.parse(in, dh);
        }
        catch (SAXException | ParserConfigurationException | IOException e) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s: %s", SP_MAP_FILE, e.getMessage()));
            errorq.append(new Emsg(Cmsg_warn.CMSG_FATAL, sb.toString()));
            status = Exp_status.FAILURE;
        }

        processMapRecords();

        for (LoadObject lo : DbeSession.getInstance().get_LoadObjects()) {
            Emsg m = lo.fetch_warnings();
            if (m != null)
                warnq.append(m);
        }
    }

    private void write_header() {
        // write message with target arglist
        if (uarglist != null) {
            String msg = String.format("%nTarget command (%s): '%s'", wsize == W64 ? "64-bit" : "32-bit", uarglist);
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
        }

        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                String.format("Process pid %d, ppid %d, pgrp %d, sid %d", pid, ppid, pgrp, sid)));

        if (username != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("User: `%s'", username)));

        if (ucwd != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("Current working directory: %s", ucwd)));

        if (cversion != null) {
            String wstring = switch (wsize) {
                case Wnone -> "?";
                case W32 -> "32-bit";
                case W64 -> "64-bit";
            };
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format(
                    "Collector version: `%s'; experiment version %d.%d (%s)",
                    cversion, exp_maj_version, exp_min_version, wstring)));
        }

        if (dversion != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("Kernel driver version: `%s'", dversion)));

        if (jversion != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("JVM version: `%s'", jversion)));

        // add comment for hostname, parameters
        if (hostname == null)
            hostname = "unknown";
        if (os_version == null)
            os_version = "unknown";
        if (architecture == null)
            architecture = "unknown";
        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format(
                "Host `%s', OS `%s', page size %d, architecture `%s'", hostname, os_version, page_size, architecture)));

        String cpuMsg;
        if (maxclock != minclock) {
            clock = maxclock;
            cpuMsg = String.format(
                    "  %d CPUs, with clocks ranging from %d to %d MHz.; max of %d MHz. assumed",
                    ncpus, minclock, maxclock, clock);
        } else {
            cpuMsg = String.format("  %d CPU%s, clock speed %d MHz.", ncpus, ncpus == 1 ? "" : "s", clock);
        }
        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, cpuMsg));

        if (page_size > 0 && npages > 0) {
            long memsize = ((long) npages * page_size) / (1024 * 1024);
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Memory: %d pages @  %d = %d MB.", npages, page_size, memsize)));
        }

        if (machinemodel != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("  Machine model: %s", machinemodel)));

        String startMsg;
        if (start_sec != 0) {
            java.time.ZonedDateTime zdt = java.time.Instant.ofEpochSecond(start_sec).atZone(java.time.ZoneOffset.UTC);
            String formatted = zdt.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", java.util.Locale.US));
            startMsg = String.format("Experiment started %s%n", formatted);
        } else {
            startMsg = "\nExperiment start not recorded";
        }
        write_coll_params();
        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, startMsg));
        commentq.appendqueue(runlogq);
        runlogq.mark_clear();
    }

    private void write_coll_params() {
        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "Data collection parameters:"));
        if (coll_params.profile_mode == 1)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Clock-profiling, interval = %d microsecs.", (int) coll_params.ptimer_usec)));

        if (coll_params.sync_mode == 1) {
            String scope_str = switch (coll_params.sync_scope) {
                case ExperimentHandler.SYNCSCOPE_JAVA -> "JAVA-APIs";
                case ExperimentHandler.SYNCSCOPE_NATIVE -> "Native-APIs";
                default -> "Native- and Java-APIs"; 
            };
            if (coll_params.sync_threshold < 0)
                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format(
                        "  Synchronization tracing, threshold = %d microsecs. (calibrated); %s",
                        -coll_params.sync_threshold, scope_str)));
            else
                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format(
                        "  Synchronization tracing, threshold = %d microsecs.; %s",
                        coll_params.sync_threshold, scope_str)));
        }

        if (coll_params.heap_mode == 1)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "  Heap tracing"));

        if (coll_params.io_mode == 1)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "  IO tracing"));

        if (coll_params.race_mode == 1) {
            String race_stack_name = switch (coll_params.race_stack) {
                case 0 -> "dual-stack";
                case 1 -> "single-stack";
                case 2 -> "leaf";
                default -> throw new IllegalStateException("bad race_stack: " + coll_params.race_stack);
            };
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("  Datarace detection, %s", race_stack_name)));
        }

        if (coll_params.deadlock_mode == 1)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, "  Deadlock detection"));

        if (coll_params.hw_mode == 1) {
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    hwc_default ? "  HW counter-profiling (default); counters:" : "  HW counter-profiling; counters:"));
            for (int i = 0; i < Collection_params.MAX_HWCOUNT; i++) {
                if (coll_params.hw_aux_name[i] == null)
                    continue;
                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format(
                        "    %s, tag %d, interval %d, memop %d",
                        coll_params.hw_aux_name[i], i, coll_params.hw_interval[i], coll_params.hw_tpc[i])));
            }
        }

        if (coll_params.sample_periodic == 1)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Periodic sampling, %d secs.", coll_params.sample_timer)));

        if (coll_params.limit != 0)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("  Experiment size limit, %d", coll_params.limit)));

        if (coll_params.linetrace != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Follow descendant processes from: %s", coll_params.linetrace)));

        if (coll_params.pause_sig != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("  Pause signal %s", coll_params.pause_sig)));

        if (coll_params.sample_sig != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("  Sample signal %s", coll_params.sample_sig)));

        if (coll_params.start_delay != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Data collection delay start %s seconds", coll_params.start_delay)));

        if (coll_params.terminate != null)
            commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT,
                    String.format("  Data collection termination after %s seconds", coll_params.terminate)));

        // add a blank line after data description
        commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, ""));
    }

//    void register_metric(Hwcentry ctr, String aux, String uname) {
//        BaseMetric mtr = DbeSession.getInstance().register_metric(ctr, aux, uname);
//        metrics.append(mtr);
//        if (mtr.get_dependent_bm())
//            metrics.append(mtr.get_dependent_bm());
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

        static final int SYNCSCOPE_NATIVE = 0x1;
        static final int SYNCSCOPE_JAVA = 0x2;
        static final String SP_HWCNTR_FILE = "hwcounters";

        record FieldDescr(Prop_type propID, String name, int offset, VType_type vtype, String format) {}

        Experiment exp;
        Element curElem;
        List<Element> stack;
        Module dynfuncModule;
        DataDescriptor dDscr;
        PacketDescriptor pDscr;
        PropDescr propDscr;
        String text;
        Cmsg_warn mkind;
        int mnum;
        int mec;

        ExperimentHandler() {
            exp = Experiment.this;
            stack = new ArrayList<>();
            pushElem(Element.EL_NONE);
            dynfuncModule = null;
            dDscr = null;
            pDscr = null;
            propDscr = null;
            text = null;
            mkind = Cmsg_warn.CMSG_NONE;
            mnum = -1;
            mec = -1;
        }

        private static final String[] HEAPTYPE_STATE_STRINGS =
                {"MALLOC", "FREE", "REALLOC", "MMAP", "MUNMAP"};
        private static final String[] HEAPTYPE_STATE_USTRINGS =
                {"malloc", "free", "realloc", "mmap", "munmap"};

        private static final String[] IOTRACETYPE_STATE_STRINGS = {
                "READ", "WRITE", "OPEN", "CLOSE", "OTHERIO",
                "READERROR", "WRITEERROR", "OPENERROR", "CLOSEERROR", "OTHERIOERROR"};
        private static final String[] IOTRACETYPE_STATE_USTRINGS = {
                "Read", "Write", "Open", "Close", "Other I/O",
                "Read Error", "Write Error", "Open Error", "Close Error", "Other I/O Error"};

        @Override
        public void endDocument() {
            DataDescriptor dd = exp.getDataDescriptor(DATA_HEAP);
            if (dd != null) {
                PropDescr prop = dd.getProp(Prop_type.PROP_HTYPE);
                if (prop != null) {
                    for (int ii = 0; ii < HEAPTYPE_STATE_STRINGS.length; ii++)
                        prop.addState(ii, HEAPTYPE_STATE_STRINGS[ii], HEAPTYPE_STATE_USTRINGS[ii]);
                }
            }
            dd = exp.getDataDescriptor(DATA_IOTRACE);
            if (dd != null) {
                PropDescr prop = dd.getProp(Prop_type.PROP_IOTYPE);
                if (prop != null) {
                    for (int ii = 0; ii < IOTRACETYPE_STATE_STRINGS.length; ii++)
                        prop.addState(ii, IOTRACETYPE_STATE_STRINGS[ii], IOTRACETYPE_STATE_USTRINGS[ii]);
                }
            }
        }

        private static final String SP_TAG_COLLECTOR =       "collector";
        private static final String SP_TAG_CPU =             "cpu";
        private static final String SP_TAG_DATAPTR =         "dataptr";
        private static final String SP_TAG_EVENT =           "event";
        static final String SP_TAG_LOG_WRAPPER = "gprofng-jdbe-log";

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
            case SP_TAG_LOG_WRAPPER ->
                pushElem(Element.EL_NONE);
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
                    stack_base = Util.strtoull(str);
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
                    need_swap_endian = (DbeSession.getInstance().platform == Sparc) ?
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
                long ts = (long) 0;
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
                            int id = str != null ? Integer.parseInt(str) : -1;
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
                                commentq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, String.format("*** Note: %s", str)));
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
                            long tid = str != null ? Util.strtoull(str) : 0;
                            str = attrs.getValue(("jthr"));
                            long jthr = str != null ? Util.strtoull(str) : 0;
                            str = attrs.getValue(("jenv"));
                            long jenv = str != null ? Util.strtoull(str) : 0;
                            process_jthr_start_cmd(null, name, grpname, prntname, tid, jthr, jenv, ts);
                            break;
                        }
                        case SP_JCMD_JTHREND -> {
                            str = attrs.getValue(("tid"));
                            long tid = str != null ? Util.strtoull(str) : 0;
                            str = attrs.getValue(("jthr"));
                            long jthr = str != null ? Util.strtoull(str) : 0;
                            str = attrs.getValue(("jenv"));
                            long jenv = str != null ? Util.strtoull(str) : 0;
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
                                long delta = ts - resume_ts;
                                non_paused_time += delta;
                                resume_ts = MAX_TIME; // collection is paused
                            }
                            String msg;
                            str = attrs.getValue(("name"));
                            if (str == null) {
                                msg = String.format("Pause: %d.%09d", ts / NANOSEC, ts % NANOSEC);
                            } else {
                                msg = String.format("Pause (%s): %d.%09d", str, ts / NANOSEC, ts % NANOSEC);
                            }
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                        }
                        case SP_JCMD_RESUME -> {
                            if (resume_ts == MAX_TIME) {
                                // data collection was paused
                                resume_ts = ts; // remember start time
                            }
                            String msg = String.format("Resume: %d.%09d", ts / NANOSEC, ts % NANOSEC);
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                            if (exp_start_time == ZERO_TIME)
                                exp_start_time = ts;
                        }
                        case SP_JCMD_THREAD_PAUSE -> {
                            str = attrs.getValue(("tid"));
                            long tid = str != null ? Long.parseLong(str) : 0;
                            String msg = String.format("Thread %d pause: %d.%09d", tid, ts / NANOSEC, ts % NANOSEC);
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                        }
                        case SP_JCMD_THREAD_RESUME -> {
                            str = attrs.getValue(("tid"));
                            long tid = str != null ? Long.parseLong(str) : 0;
                            String msg = String.format("Thread %d resume: %d.%09d", tid, ts / NANOSEC, ts % NANOSEC);
                            runlogq.append(new Emsg(Cmsg_warn.CMSG_COMMENT, msg));
                        }
                        case "map" -> {
                            ts += exp_start_time;
                            str = attrs.getValue(("vaddr"));
                            long vaddr = str != null ? Util.strtoull(str) : 0;
                            str = attrs.getValue(("size"));
                            int msize = str != null ? Integer.parseInt(str) : 0;
                            str = attrs.getValue(("foffset"));
                            long offset = str != null ? Util.strtoll(str) : 0;
                            str = attrs.getValue(("modes"));
                            long modes = str != null ? Util.strtoll(str) : 0;
                            str = attrs.getValue(("chksum"));
                            long chksum = 0;
//                            if (str != null)
//                                chksum = Elf::normalize_checksum (Util.strtoll(str));
                            String name = attrs.getValue(("name"));
                            str = attrs.getValue(("object"));
                            if ("segment".equals(str)) {
                                if ("LinuxKernel".equals(name))
                                    process_Linux_kernel_cmd(ts);
                                else
                                    process_seg_map_cmd(null, ts, vaddr, msize, 0,
                                            offset, modes, chksum, name);
                            } else if ("function".equals(str)) {
                                process_fn_load_cmd(dynfuncModule, name, vaddr, msize, ts);
                                dynfuncModule = null;
                            } else if ("dynfunc".equals(str)) {
                                if (dynfuncModule == null) {
                                    dynfuncModule = DbeSession.getInstance().createModule(get_dynfunc_lo(DYNFUNC_SEGMENT), name);
                                    dynfuncModule.flags |= Module.MOD_FLAG_UNKNOWN;
                                    dynfuncModule.set_file_name(dynfuncModule.getMainSrc().get_name());
                                }
                                create_dynfunc(dynfuncModule, attrs.getValue(("funcname")), vaddr, msize);
                            } else if ("jcm".equals(str)) {
                                str = attrs.getValue(("methodId"));
                                long mid = str != null ? Util.strtoull(str) : 0;
                                process_jcm_load_cmd(null, mid, vaddr, msize, ts);
                            }
                            break;
                        }
                        case "unmap" -> {
                            ts += exp_start_time;
                            str = attrs.getValue(("vaddr"));
                            long vaddr = str != null ? Util.strtoull(str) : 0;
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
                    {
                        dDscr = newDataDescriptor(DATA_CLOCK);
                        PropDescr prop = new PropDescr(Prop_type.PROP_MSTATE, "MSTATE");
                        prop.uname = "Thread state";
                        prop.vtype = VType_type.TYPE_UINT32;
                        // (states added below)
                        dDscr.addProperty (prop);
                        mstate_prop = prop;

                        prop = new PropDescr(Prop_type.PROP_NTICK, "NTICK");
                        prop.uname = "Number of Profiling Ticks";
                        prop.vtype = VType_type.TYPE_UINT32;
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
                                mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
                            break;
                        case LMS_MAGIC_ID_ERKERNEL_KERNEL:
                            register_metric (Metric.Type.CP_KERNEL_CPU);
                        {
                            int ii = LMS_KERNEL_CPU;
                            mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
                        }
                        break;
                        case LMS_MAGIC_ID_ERKERNEL_USER:
                            register_metric (Metric.Type.CP_TOTAL_CPU);
                            register_metric (Metric.Type.CP_LMS_USER);
                            register_metric (Metric.Type.CP_LMS_SYSTEM);
                        {
                            int ii = LMS_KERNEL_CPU;
                            mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
                            ii = LMS_USER;
                            mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
                            ii = LMS_SYSTEM;
                            mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
                        }
                        break;
                        case LMS_MAGIC_ID_LINUX:
                            register_metric (Metric.Type.CP_TOTAL_CPU);
                        {
                            int ii = LMS_LINUX_CPU;
                            mstate_prop.addState(ii, LMS_STATE_STRINGS[ii], LMS_STATE_USTRINGS[ii]);
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
                    dDscr = newDataDescriptor(DATA_OMP, Data_flag.DDFLAG_NOSHOW);
                } else if ("hwcounter".equals(str)) {
                    str = attrs.getValue("cpuver");
                    int cpuver = str != null ? Integer.parseInt(str) : 0;
                    String counter = attrs.getValue("hwcname");
                    String int_name = attrs.getValue("int_name"); // may not be present
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
                } else if ("datarace".equals(str)) {
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
                // SS12 HWC experiments are not well-structured
                String fname = attrs.getValue( ("fname"));
                if (SP_HWCNTR_FILE.equals(fname)) {
                    dDscr = newDataDescriptor(DATA_HWC);
                }
            }
            case SP_TAG_PROFPCKT -> {
                pushElem(Element.EL_PROFPCKT);
                String str = attrs.getValue( ("kind")); // see Pckt_type
                int kind = str != null ? Integer.parseInt(str) : -1;
                Pckt_type type = Pckt_type.fromInt(kind);
                if (type == null) {
                    throw new IllegalArgumentException(SP_TAG_PROFPCKT);
                }
                if (coll_params.omp_mode == 1) {
                    if (type == Pckt_type.OMP_PCKT)
                        dDscr = newDataDescriptor(DATA_OMP, Data_flag.DDFLAG_NOSHOW);
                    else if (type == Pckt_type.OMP2_PCKT)
                        dDscr = newDataDescriptor(DATA_OMP2, Data_flag.DDFLAG_NOSHOW);
                    else if (type == Pckt_type.OMP3_PCKT)
                        dDscr = newDataDescriptor(DATA_OMP3, Data_flag.DDFLAG_NOSHOW);
                    else if (type == Pckt_type.OMP4_PCKT)
                        dDscr = newDataDescriptor(DATA_OMP4, Data_flag.DDFLAG_NOSHOW);
                    else if (type == Pckt_type.OMP5_PCKT)
                        dDscr = newDataDescriptor(DATA_OMP5, Data_flag.DDFLAG_NOSHOW);
                }
                pDscr = newPacketDescriptor(type, dDscr);
            }
            case SP_TAG_FIELD -> {
                pushElem(Element.EL_FIELD);
                if (pDscr != null) {
	                String name = attrs.getValue( ("name"));
                    if (name == null)
                        return;
                    Prop_type propID = DbeSession.getInstance().registerPropertyName(name);
                    propDscr = new PropDescr(propID, name);

                    VType_type type = VType_type.TYPE_NONE;
                    String format = attrs.getValue("format");
                    String str = attrs.getValue( ("type"));
                    if (str != null) {
                        switch (str) {
                            case "INT32" -> type = VType_type.TYPE_INT32;
                            case "UINT32" -> type = VType_type.TYPE_UINT32;
                            case "INT64" -> type = VType_type.TYPE_INT64;
                            case "UINT64" -> type = VType_type.TYPE_UINT64;
                            case "STRING" -> type = VType_type.TYPE_STRING;
                            case "DOUBLE" -> type = VType_type.TYPE_DOUBLE;
                            case "DATE" ->  type = VType_type.TYPE_DATE;
                        }
                    }
                    propDscr.vtype = type;

                    // TYPE_DATE is converted to TYPE_UINT64 in propDscr
                    if (type == VType_type.TYPE_DATE)
                        propDscr.vtype = VType_type.TYPE_UINT64;

                    // Fix some types until they are fixed in libcollector
                    if (propID == Prop_type.PROP_VIRTPC || propID == Prop_type.PROP_PHYSPC)
                    {
                        if (type == VType_type.TYPE_INT32)
                            propDscr.vtype = VType_type.TYPE_UINT32;
                        else if (type == VType_type.TYPE_INT64)
                            propDscr.vtype = VType_type.TYPE_UINT64;
                    }

                    // The following props get mapped to 32-bit values in readPacket
                    if (propID == Prop_type.PROP_CPUID || propID == Prop_type.PROP_THRID
                            || propID == Prop_type.PROP_LWPID)
                        propDscr.vtype = VType_type.TYPE_UINT32; // override experiment property

                    str = attrs.getValue( ("uname"));
                    if (str != null) {
                        propDscr.uname = str;
                    }
                    str = attrs.getValue("noshow");
                    if (str != null && Integer.parseInt(str) != 0) {
                        propDscr.flags |= Prop_flag.PRFLAG_NOSHOW.value;
                    }

                    if (dDscr == null) {
                        String msg = "*** Error: data parsing failed. Log file is corrupted.";
                        warnq.append(new Emsg(Cmsg_warn.CMSG_ERROR, msg));
                        throw new SAXException(msg);
                    }

                    dDscr.addProperty (propDscr);

                    str = attrs.getValue( ("offset"));
                    int offset = str != null ? Integer.parseInt(str) : 0;
                    FieldDescr fldDscr = new FieldDescr(propID, name, offset, type, format);
                    pDscr.addField(fldDscr);
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
            case SP_TAG_DTRACEFATAL ->
                pushElem(Element.EL_DTRACEFATAL);
            default -> {
                warnq.append(new Emsg (Cmsg_warn.CMSG_WARN, String.format("*** Warning: unrecognized element %s", qName)));
                pushElem(Element.EL_NONE);
            }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (curElem == Element.EL_EVENT && mkind.value >= 0 && mnum >= 0) {
                String str;
                if (mec > 0) {
                    str = String.format("%s -- errno %d", text != null ? text : "", mec);
                } else {
                    str = String.format("%s", text != null ? text : "");
                }
                Emsg msg = new Emsg(mkind, mnum, str);
                if (mkind == Cmsg_warn.CMSG_WARN) {
                    if (mnum != COL_WARN_FSTYPE
                            || DbeSession.getInstance().check_ignore_fs_warn() == false)
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
        public void characters(char[] ch, int start, int length) {
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
            stack.removeLast();
            curElem = stack.getLast();
        }
    }

    DataDescriptor getDataDescriptor(ProfData_type type) {
        int idx = type.getValue();
        if (idx < 0 || idx >= dataDscrs.length) {
            throw new IllegalArgumentException("type: " + type);
        }
        return dataDscrs[idx];
    }

    public List<DataDescriptor> getDataDescriptors() {
        List<DataDescriptor> list = new ArrayList<>();
        for (int i = 0; i < dataDscrs.length; i++) {
            DataDescriptor d = get_raw_events(i);
            if (d != null)
                list.add(d);
        }
        return list;
    }

    public DataDescriptor get_raw_events(int data_id) {
        if (data_id == DATA_HEAPSZ.getValue())
            return get_heapsz_events();
        return data_id >= 0 && data_id < dataDscrs.length ? dataDscrs[data_id] : null;
    }

    public int base_data_id(int data_id) {
        if (data_id == DATA_HEAPSZ.getValue()) {
            return DATA_HEAP.getValue(); // DATA_HEAPSZ DataView is based on DATA_HEAP's DataView
        }
        return data_id;
    }

    DataView create_derived_data_view (int data_id, DataView dview) {
        // dview contains filtered packets
        if (data_id == DATA_HEAPSZ.getValue()) {
            return create_heapsz_data_view(dview);
        }
        return null;
    }

    DataDescriptor get_heapsz_events() {
        DataDescriptor dDscr = getDataDescriptor(DATA_HEAPSZ);
        if (dDscr != null)
            return dDscr;
        DataDescriptor heapDDscr = getDataDescriptor(DATA_HEAP);
        if (heapDDscr == null)
            return null;
        return newDataDescriptor(DATA_HEAPSZ, 0, heapDDscr);
    }

    private void update_heapsz_packet(Set<Long> touched, DataView dview, long alloc_pkt_id,
                                       long net_alloc, long leaks) {
        boolean newToSet = touched.add(alloc_pkt_id);
        if (!newToSet) {
            net_alloc += dview.getDataDescriptorValue(Prop_type.PROP_HCUR_NET_ALLOC.ordinal(), alloc_pkt_id);
            if (leaks != 0) {
                long old = dview.getDataDescriptorValue(Prop_type.PROP_HCUR_LEAKS.ordinal(), alloc_pkt_id);
                if (old != 0)
                    leaks = old;
            }
        }
        dview.setDataDescriptorValue(Prop_type.PROP_HCUR_NET_ALLOC.ordinal(), alloc_pkt_id, net_alloc);
        dview.setDataDescriptorValue(Prop_type.PROP_HCUR_LEAKS.ordinal(), alloc_pkt_id, leaks);
    }

    DataView create_heapsz_data_view(DataView heap_dview) {
        DataDescriptor dDscr = get_heapsz_events();
        if (dDscr == null)
            return null;
        Set<Long> touched = new LinkedHashSet<>();
        long sz = heap_dview.getSize();
        for (long i = 0; i < sz; i++) {
            long hsize = heap_dview.getULongValue(Prop_type.PROP_HSIZE.ordinal(), i);
            long leaks = heap_dview.getULongValue(Prop_type.PROP_HLEAKED.ordinal(), i);
            long alloc_pkt_id = heap_dview.getIdByIdx(i);
            update_heapsz_packet(touched, heap_dview, alloc_pkt_id, hsize, leaks);

            long free_pkt_id = heap_dview.getLongValue(Prop_type.PROP_DDSCR_LNK.ordinal(), i) - 1;
            if (free_pkt_id >= 0)
                update_heapsz_packet(touched, heap_dview, free_pkt_id, -hsize, 0);
        }

        DataView heapsz_dview = dDscr.createExtManagedView();
        for (long ddscr_pkt_id : touched)
            heapsz_dview.appendDataDescriptorId(ddscr_pkt_id);
        compute_heapsz_data_view(heapsz_dview);
        return heapsz_dview;
    }

    private void compute_heapsz_data_view(DataView heapsz_dview) {
        heapsz_dview.sort(new int[] {Prop_type.PROP_TSTAMP.ordinal()}, 1);
        long currentAllocs = 0, currentLeaks = 0;
        long sz = heapsz_dview.getSize();
        for (long i = 0; i < sz; i++) {
            long netAlloc = heapsz_dview.getLongValue(Prop_type.PROP_HCUR_NET_ALLOC.ordinal(), i);
            currentAllocs += netAlloc;
            heapsz_dview.setDataDescriptorValue(Prop_type.PROP_HCUR_ALLOCS.ordinal(),
                    heapsz_dview.getIdByIdx(i), currentAllocs);

            long leaks = heapsz_dview.getULongValue(Prop_type.PROP_HCUR_LEAKS.ordinal(), i);
            currentLeaks += leaks;
            heapsz_dview.setDataDescriptorValue(Prop_type.PROP_HCUR_LEAKS.ordinal(),
                    heapsz_dview.getIdByIdx(i), currentLeaks);
        }
    }

    DataDescriptor newDataDescriptor(ProfData_type type) {
        return newDataDescriptor(type, 0, null);
    }

    DataDescriptor newDataDescriptor(ProfData_type type, Data_flag flags) {
        return newDataDescriptor(type, flags.value, null);
    }

    DataDescriptor newDataDescriptor(ProfData_type type, int flags, DataDescriptor parent) {
        DataDescriptor dataDscr = getDataDescriptor(type);
        if (dataDscr != null) {
            return dataDscr;
        }

        if (parent != null) {
            dataDscr = new DataDescriptor(type, parent);
        } else {
            dataDscr = new DataDescriptor(type, flags);
        }
        dataDscrs[type.getValue()] = dataDscr;
        return dataDscr;
    }

    PacketDescriptor newPacketDescriptor(Pckt_type type, DataDescriptor dDscr) {
        PacketDescriptor pDscr = new PacketDescriptor(dDscr);
        pcktDscrs[type.value] = pDscr;
        return pDscr;
    }

    PacketDescriptor getPacketDescriptor(int type) {
        if (type < 0 || type >= pcktDscrs.length)
            return null;
        return pcktDscrs[type];
    }
}
