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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    // Pckt_type (data_pckts.h) values relevant to data.frameinfo
    private static final int FRAME_PCKT = 11;
    private static final int UID_PCKT = 17;
    // Info_type (data_pckts.h) values for Frame_packet sub-records
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

    // minimal: real JThread tracking also maintains a tid-sorted index
    // (jthreads_idx) for stack-sample thread lookup; not needed for header display,
    // so not ported yet.
    static class JThread {
        String name;
        String group_name;
        String parent_name;
        long tid;
        long jthr;
        long jenv;
        long start;
        long end;
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
    String username;                  // user name (not currently set from log.xml)
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

    static class SegMem {
        long base;
        long size;
        long load_time;
        long unload_time = MAX_TIME;
        Histable obj; // a LoadObject (segment) or a Function (standalone/dynamic function)
    }

    List<SegMem> segItems = new ArrayList<>();     // master list of all segment loads/unloads
    List<SegMem> activeSegs = new ArrayList<>();   // currently-mapped segments (simplified
                                                    // stand-in for native's interval-tree "maps")

    static class MapRecord {
        enum Kind { LOAD, UNLOAD }
        Kind kind;
        Histable obj;
        long base;
        long size;
        long ts;
    }

    // Raw records collected during the SAX pass, always kept sorted by ts (mirrors
    // native's mrec_insert): map.xml records aren't necessarily in ts order (e.g. the
    // vdso "function" entries carry tstamp="0.0" but physically appear in the file
    // after the segment entries), so overlap detection has to run in a second pass,
    // over records in timestamp order, not file order.
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

    // Frame/uid graph read from data.frameinfo: each stack is a chain of UIDnodes (one
    // PC per node), letting stacks that share a common tail (the overwhelmingly common
    // case) share nodes instead of repeating them. RawFramePacket is the entry point:
    // one per "frinfo" uid referenced from a profile record, pointing at the head node
    // of its native/Java/OMP stack chains.
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

    // Simplified relative to native, which caches in a fixed-size single-slot-per-bucket
    // hash table (uidHTable) with a sorted-array binary-search fallback (uidnodes) for
    // cache misses/collisions -- a HashMap gives exact, always-correct O(1) lookup and
    // replaces both.
    Map<Long, UIDnode> uidHTable = new HashMap<>();
    List<UIDnode> uidnodes = new ArrayList<>();
    // Simplified relative to native's sorted Vector + binary search (find_frame_packet);
    // a HashMap gives the same exact-match lookup this needs, with no sort step.
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

    // Returns a placeholder node for a uid referenced without its own inline PC array
    // (e.g. a Stack_info/Java_info sub-record with no stack bytes). Deliberately NOT
    // registered in uidHTable/uidnodes, matching native exactly: this is a best-effort,
    // throwaway link target, not a durable node.
    private UIDnode get_uid_node(long uid) {
        if (uid == 0)
            return null;
        UIDnode node = uidHTable.get(uid);
        if (node != null)
            return node;
        node = new UIDnode(uid, 0);
        node.next = node; // self-loop sentinel
        return node;
    }

    // val32 must already be the zero-extended unsigned magnitude of a 32-bit PC (as
    // produced by u32()); maps the 32-bit-truncated sentinel patterns back to their
    // 64-bit form, matching native's funcAddr(). Only exercised for 32-bit target
    // processes (wsize == W32); this port's test data is all 64-bit.
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

    // Unifies native's two add_uid() overloads (uint32_t[]/uint64_t[] stacks), which
    // differ only in element size and whether 32-bit PCs get promoted via funcAddr().
    private UIDnode addUid(ByteBuffer buf, long uid, long arrOff, long stackSize, long linkUid) {
        boolean is32 = (wsize == W32);
        ElemReader reader = is32 ? (b, off) -> funcAddr32(u32(b, off)) : Experiment::u64;
        return addUid(buf, uid, arrOff, stackSize, linkUid, is32 ? 4 : 8, reader);
    }

    // JAVA_INFO (64-bit target only): stack entries are (bci, jmethodID) pairs per
    // the JVMTI/AsyncGetCallTrace ABI (JVMPI_CallFrame{lineno,jmethodID}), packed as
    // two 8-byte-aligned slots: the bci is a plain signed 32-bit value occupying the
    // first slot's low 4 bytes (the high 4 bytes are unused padding, a historical
    // struct-layout mismatch native calls out as "bug 6909545"), and the jmethodID
    // is the second slot, verbatim -- so read each directly from the original
    // buffer rather than staging a byte-shuffled copy first.
    private UIDnode addJavaInfoUid(ByteBuffer buf, long uid, long arrOff, long stackSize, long linkUid) {
        ElemReader reader = (b, off) -> ((off - arrOff) / 8) % 2 == 0 ? b.getInt((int) off) : b.getLong((int) off);
        return addUid(buf, uid, arrOff, stackSize, linkUid, 8, reader);
    }

    // Builds/extends the uid's node chain in `stackSize/elemSize` steps, one element
    // per node, reusing existing nodes where a previously-seen chain already covers
    // this position (this is exactly the tail-sharing dedup described above).
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

    // Native reads this lazily, only when a view actually needs clock-profile data
    // (Experiment::get_profile_events()); we read it eagerly at open() time instead,
    // like every other file in this port, since there's no view-driven scheduling here
    // yet and eager loading is directly testable on its own.
    void read_profile_file() {
        DataDescriptor dDscr = getDataDescriptor(DATA_CLOCK);
        if (dDscr == null)
            return; // no clock-profiling data collected in this experiment
        read_data_file(SP_PROFILE_FILE, String.format("Loading Clock Profiling Data: %s", new File(expt_name).getName()));
    }

    private static final String SP_HEAPTRACE_FILE = "heaptrace";

    // Like read_profile_file(): native reads this lazily (Experiment::get_heap_events()),
    // we read it eagerly. "heaptrace" uses the exact same field-driven packet format
    // as "profile" (log.xml's <profile name="heaptrace"><profpckt kind="5">...), so
    // read_data_file()/readGenericFieldPacket() already handle it -- the only new
    // logic needed is computing the derived HLEAKED property afterward.
    void read_heaptrace_file() {
        DataDescriptor dDscr = getDataDescriptor(DATA_HEAP);
        if (dDscr == null)
            return; // no heap tracing in this experiment
        read_data_file(SP_HEAPTRACE_FILE, String.format("Loading Heap Tracing Data: %s", new File(expt_name).getName()));
        compute_heap_leaked(dDscr);
    }

    private static final String SP_OVERVIEW_FILE = "overview";
    // sizeof(raw_prusage_64) (Exp_Layout.cc): 2 leading int32 fields (pr_lwpid,
    // pr_count), then 20 timestruc_64 fields (14 real + 6 filler, 16 bytes each),
    // then 22 uint64 fields (12 real + 10 filler, 8 bytes each).
    private static final int PRUSAGE64_SIZE = 8 + 20 * 16 + 22 * 8;

    // Mirrors native's Experiment::read_overview_file (Experiment.cc:4852-4962),
    // restricted to what this port actually needs: the piggyback
    // "update_last_event(data->pr_tstamp)" on the final record (Experiment.cc:4953-4958).
    // This is the ONLY eager, always-on timestamp source feeding the "Experiment Ended"/
    // "Data Collection Duration" header fields -- native resolves clock-profile data
    // lazily (get_profile_events(), gated by "read_ahead") and heap-trace data lazily
    // (get_heap_events(), only when a heap view is actually requested), and
    // write_header() runs once, at open() time, before any view is dispatched. The
    // samples/PrUsage bookkeeping (Sample start/end times, per-field deltas) that native
    // builds from this file isn't ported -- nothing else in this port consumes it.
    void read_overview_file() {
        File f = new File(String.format("%s/%s", expt_name, SP_OVERVIEW_FILE));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; // matches native: dwin->not_opened() -> silent return
        }
        if (wsize != W64 || data.length < PRUSAGE64_SIZE)
            return; // this port's test data is all 64-bit (see wsize's own doc comment)
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long nrecords = data.length / PRUSAGE64_SIZE;
        long lastOff = (nrecords - 1) * (long) PRUSAGE64_SIZE;
        long tvSec = u64(buf, lastOff + 8);
        long tvNsec = u64(buf, lastOff + 16);
        update_last_event(tvSec * NANOSEC + tvNsec);
    }

    // Mirrors native's alloc/free address-matching pass in Experiment::get_heap_events()
    // (Experiment.cc:3574-3789) + the HeapMap class (HeapMap.h/.cc): walk heap-trace
    // records in timestamp order, tracking the most recent allocation at each address,
    // and zero out HLEAKED for any allocation later matched by a free/munmap at the
    // same address -- whatever's never matched by experiment end stays "leaked"
    // (HLEAKED == HSIZE). Simplified relative to native: REALLOC_TRACE/MMAP_TRACE/
    // MUNMAP_TRACE aren't exercised by this port's test data (0 such records here) and
    // are handled with a direct, un-chunked interpretation rather than native's full
    // partial-unmap bookkeeping (HeapMap's UnmapChunk list) -- revisit if a future
    // experiment actually has mmap/munmap heap-trace records.
    private void compute_heap_leaked(DataDescriptor dDscr) {
        PropDescr hleakedProp = new PropDescr(Prop_type.PROP_HLEAKED, "HLEAKED");
        hleakedProp.vtype = VType_type.TYPE_UINT64;
        dDscr.addProperty(hleakedProp);

        int htypeOrd = Prop_type.PROP_HTYPE.ordinal();
        int hsizeOrd = Prop_type.PROP_HSIZE.ordinal();
        int hvaddrOrd = Prop_type.PROP_HVADDR.ordinal();
        int hovaddrOrd = Prop_type.PROP_HOVADDR.ordinal();
        int hleakedOrd = Prop_type.PROP_HLEAKED.ordinal();
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
                    if (oldIdx != null)
                        dDscr.setValue(hleakedOrd, oldIdx, 0);
                }
            } else if (htype == Heap_type.FREE_TRACE.value || htype == Heap_type.MUNMAP_TRACE.value) {
                Integer allocIdx = vaddr != 0 ? addrToAllocIdx.remove(vaddr) : null;
                if (allocIdx != null)
                    dDscr.setValue(hleakedOrd, allocIdx, 0);
            }
        }
    }

    private void read_data_file(String fname, String msg) {
        File f = new File(String.format("%s/%s", expt_name, fname));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; // matches native: dwin->not_opened() -> silent return
        }
        // Every currently-supported gprofng platform (x86, x86_64, aarch64, RISC-V) is
        // little-endian, so native's need_swap_endian is dead code in practice; we
        // always read little-endian rather than porting byte-swap detection.
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
            // Expected, not an error: the writer flushes in fixed PROFILE_BUFFER_CHUNK
            // increments, so a partially-filled final chunk leaves trailing zero bytes.
            // Native's invalid_packet counter is reserved for a raw-pointer-misalignment
            // check that has no equivalent here (we always read at exact byte offsets
            // into a ByteBuffer, never through a possibly-misaligned C pointer), so it's
            // never incremented in this port.
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

    // Field-driven packet reader for any packet kind described by a <profpckt>/<field>
    // block in log.xml (currently: OPROF_PCKT clock-profile samples in the "profile"
    // file). Native also special-cases the legacy PROF_PCKT format here (a for-loop
    // over per-microstate CPU-time fields, for backward compatibility with old SS12
    // experiments); not ported since it doesn't apply to OPROF_PCKT, the only format
    // modern experiments (including this port's test data) actually use.
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
                // TODO: native remaps this through mapTagValue() (a session-wide
                // per-property tag registry assigning compact display ids) -- not yet
                // ported; storing the raw id directly instead, the same simplification
                // already used for jthread tid in process_jthr_start_cmd/_end_cmd.
                dDscr.setValue(field.propID().ordinal(), recn, raw);
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
                default -> { } // DOUBLE/OBJ/DATE/BOOL/ENUM/LAST/NONE: matches native
                                // ("ignoring the following cases (why?)")
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
        fp.uid = u64(buf, offset + 8);       // Frame_packet.uid
        long hsize = u32(buf, offset + 4);   // Frame_packet.hsize
        long ptr = offset + hsize;
        long end = offset + tsize;
        while (ptr < end) {
            long subHsize = u32(buf, ptr);           // Common_info.hsize
            if (subHsize == 0 || ptr + subHsize > end)
                break;
            long kindRaw = u32(buf, ptr + 4);        // Common_info.kind
            boolean compressed = (kindRaw & COMPRESSED_INFO) != 0;
            int kind = (int) (kindRaw & ~COMPRESSED_INFO);
            switch (kind) {
                case STACK_INFO -> {
                    long stackOff = ptr + 16;         // sizeof(Stack_info)
                    long stackSize = subHsize - 16;
                    long uidn = u64(buf, ptr + 8);    // Stack_info.uid
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
                    long stackOff = ptr + 16;         // sizeof(Java_info)
                    long stackSize = subHsize - 16;
                    long uidj = u64(buf, ptr + 8);    // Java_info.uid
                    if (stackSize <= 0) {
                        fp.uidj = get_uid_node(uidj);
                    } else {
                        long linkUid = 0;
                        if (compressed) {
                            stackSize -= 8;
                            linkUid = u64(buf, stackOff + stackSize);
                        }
                        // 64-bit targets: confirmed by inspecting this experiment's actual
                        // on-disk data that these are (bci, jmethodID) pairs, not one PC
                        // per slot -- see addJavaInfoUid(). 32-bit targets don't need this
                        // (native's "bug 6909545" workaround only applies to 64-bit).
                        fp.uidj = wsize == W64
                                ? addJavaInfoUid(buf, uidj, stackOff, stackSize, linkUid)
                                : addUid(buf, uidj, stackOff, stackSize, linkUid);
                    }
                }
                case OMP_INFO -> fp.omp_state = (int) u32(buf, ptr + 8); // OMP_info.omp_state
                case OMP2_INFO -> {
                    long ompUid = u64(buf, ptr + 16); // OMP2_info.uid
                    fp.omp_uid = get_uid_node(ompUid);
                    fp.omp_state = (int) u32(buf, ptr + 8); // OMP2_info.omp_state
                }
                default -> { }
            }
            ptr += subHsize;
        }
        frmpckts.put(fp.uid, fp);
    }

    private void readUidPacket(ByteBuffer buf, long offset, int tsize) {
        long flags = u32(buf, offset + 4);   // Uid_packet.flags
        long uid = u64(buf, offset + 8);     // Uid_packet.uid
        long arrOff = offset + 16;           // sizeof(Uid_packet)
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

    private boolean has_java = true;    // TODO
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

    // Mirrors native's Experiment::getLastEvent (Experiment.h:209-214).
    public long getLastEvent() {
        return last_event != ZERO_TIME ? last_event : exp_start_time;
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

    // parses a "seconds.nanoseconds" timestamp string (the fractional part is already a
    // fixed-width nanosecond count, not a decimal fraction to be rescaled)
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

    // The following process_*_cmd methods are the actual experiment-record event
    // handlers (native: parse.cc); this is the bulk of what remains to be ported for
    // Experiment. Stubbed for now.
    void process_sample_cmd(String cmd, long ts, int id, String label) {
        throw new RuntimeException("Experiment.process_sample_cmd not implemented");
    }

    void process_desc_start_cmd(String cmd, long ts, String variant, String lineage, int follow, String msg) {
        throw new RuntimeException("Experiment.process_desc_start_cmd not implemented");
    }

    void process_desc_started_cmd(String cmd, long ts, String variant, String lineage, int follow, String msg) {
        throw new RuntimeException("Experiment.process_desc_started_cmd not implemented");
    }

    void process_jthr_start_cmd(String cmd, String name, String grpname, String prntname,
                                 long tid, long jthr, long jenv, long ts) {
        // TODO: tid is stored raw here; native remaps it through mapTagValue() (a
        // session-wide per-property tag registry, not yet ported) to get a compact
        // display tag. Also skips maintaining the tid-sorted jthreads_idx (used later
        // for stack-sample thread lookup). Neither matters for header display.
        JThread jthread = new JThread();
        jthread.name = name;
        jthread.group_name = grpname;
        jthread.parent_name = prntname;
        jthread.tid = tid;
        jthread.jthr = jthr;
        jthread.jenv = jenv;
        jthread.start = ts;
        jthread.end = MAX_TIME;
        jthreads.add(jthread);
    }

    void process_jthr_end_cmd(String cmd, long tid, long jthr, long jenv, long ts) {
        // TODO: tid is stored raw here; native remaps it through mapTagValue()
        JThread jthread = null;
        for (JThread jt : jthreads) {
            if (jt.tid == tid) {
                jthread = jt;
                break;
            }
        }
        if (jthread == null) {
            jthread = new JThread();
            jthread.tid = tid;
            jthread.jthr = jthr;
            jthread.jenv = jenv;
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
        // Skip non-text (non read+exec) segments. Unlike native, we don't attempt
        // ELF/stabs reading (SEG_FLAG_JVM/OMP pseudo-functions, DbeFile/archive lookup),
        // so a created LoadObject here is name/size/overlap-tracking only.
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
            // TODO: native fills address gaps within the module with synthetic
            // "<static>@0x..." functions (sorted by img_offset); not exercised by
            // current test data (no "dynfunc" events precede a "function" event here),
            // so simplified to registering the module's existing functions for overlap
            // tracking only.
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

    // Processes the accumulated, now ts-sorted map records: builds up the "currently
    // mapped" segment/function list and emits overlap warnings, mirroring native's
    // read_map_file() post-processing loop over `mrecs`.
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

    // Address->object resolution index, built lazily on first query from segItems
    // (sorted by base, with a running max-end for a bounded backward scan -- a
    // simplified stand-in for native's PRBTree-based interval tree, adequate since
    // real overlaps are rare here). Invalidated (rebuilt) if segItems grows after the
    // first query, e.g. once stage 5c starts registering jcm/JIT address ranges.
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

    // Resolves a raw PC (as found in a native or Java call-stack chain) to the
    // Function it falls within at the given sample timestamp. Mirrors native's
    // Experiment::map_Vaddr_to_PC (Experiment.cc:5908-5959) + LoadObject::find_function
    // (LoadObject.cc:434-498), simplified: real shared libraries have no per-function
    // data (ELF symbol resolution is out of scope), so an address inside a
    // LoadObject's mapped range but not covered by a specific known Function
    // (map.xml "function"/"dynfunc"/"jcm" entry) resolves to one placeholder Function
    // per LoadObject, standing in for native's synthesized "plug the hole" statics.
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

    // Resolves one Java call-stack frame to the JMethod it represents. This is a
    // *different* resolution path from map_Vaddr_to_PC: a uidj chain's paired values
    // are NOT code addresses -- they're (bci, jmethodID) pairs following the
    // JVMTI/AsyncGetCallTrace ABI (JVMPI_CallFrame{lineno,jmethodID},
    // libcollector/jprofile.c:43-56), resolved through jmaps (the same method_id
    // index read_java_classes_file() already builds), entirely independent of the
    // map.xml segment/jcm address-range machinery. Mirrors native's
    // Experiment::map_jmid_to_PC (Experiment.cc:6008-6025), simplified: bci-level
    // granularity (line/source mapping) and the JNI-transition frame-splicing native
    // does for bci == JNI_MARKER (-3) aren't needed for a flat function list, so both
    // are dropped -- a JNI-marker frame just resolves to the JMethod like any other.
    Function map_jmid_to_PC(long mid, long ts) {
        if (mid == 0)
            return DbeSession.getInstance().getJUnknownFunction();
        Object obj = jmapsLocate(mid, ts);
        if (!(obj instanceof JMethod jm))
            return DbeSession.getInstance().getJUnknownFunction();
        return jm;
    }

    // Sentinel distinguishing "no registered JThread at all for this (tid, ts)" from
    // "registered, and this specific JThread object was found" (which can itself be a
    // JVM-internal/system thread -- checked separately via group_name). Mirrors
    // native's JTHREAD_NONE macro ((JThread*)-1); JTHREAD_DEFAULT is plain null here,
    // matching native's (JThread*)0.
    private static final JThread JTHREAD_NONE = new JThread();

    // Finds the JThread that owns raw thread id `tid` at time `ts` (a tid can be
    // reused over time by different logical threads, so both must match). Linear
    // scan over `jthreads`, which is small (tens to low hundreds of entries) even
    // though this runs per-sample; native's jthreads_idx binary search is an
    // optimization we don't need at this scale. Mirrors native's
    // Experiment::map_pckt_to_Jthread (Experiment.cc:6286-6311).
    private JThread map_pckt_to_Jthread(long tid, long ts) {
        if (!has_java)
            return null; // JTHREAD_DEFAULT
        for (JThread jt : jthreads)
            if (jt.tid == tid && ts >= jt.start && ts < jt.end)
                return jt;
        return JTHREAD_NONE;
    }

    // Resolves one clock-profile sample to an ordered call stack, leaf (top-of-stack
    // / currently executing) first, root (outermost caller) last -- matching native's
    // CallStack/PathTree stack-array convention. Mirrors native's
    // CallStackP::add_stack_java_epilogue (CallStack.cc:421-468): regardless of
    // whether a Java or native stack would otherwise resolve, a sample belonging to a
    // thread that isn't a genuine user Java thread (either not registered as a Java
    // thread at all, or registered but flagged group_name=="system" -- GC/compiler/
    // etc internal threads) gets attributed entirely to <JVM-System> instead.
    // Simplified relative to native's CallStackP::add_stack: native also interleaves
    // the native (uidn) and Java (uidj) chains, splicing in native frames at each JNI
    // transition point within the Java chain (matched by name via JMethod::jni_match);
    // we don't do that splicing -- whenever a JAVA_INFO sub-record exists at all for
    // this sample (fp.uidj != null), we use the Java-resolved stack as-is, even if
    // every frame resolves to the <no Java callstack recorded> placeholder (matching
    // native, which never falls back to the native stack just because Java
    // resolution didn't find real names). The native stack is only used when there's
    // no Java stack at all.
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
            jthread = null; // promoted to JTHREAD_DEFAULT
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

    // Simplified relative to native's PRBTree-based "maps" (an address/time interval
    // tree that supports out-of-order insertion); since processMapRecords() already
    // replays records in timestamp order, a linear scan of currently-active segments
    // is equivalent. Returns false if this record turned out to be a duplicate load
    // (matches native: skip silently, without warning or inserting).
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
        // native's "sm_hi" lookups walk an address-ordered tree (locate_up), so
        // matches here need to be sorted by base to reproduce the same order.
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
            return; // matches native's early-return when jmaps has no match at this id/time
        // Deviates from native: it wraps jfunc in a fresh per-compilation JMethod
        // (dfunc, linked back via usrfunc) so recompilations later get merged back
        // together by name via a "comparable objects" pass we haven't ported. We
        // register jfunc itself against this address range instead, which gets the
        // same merged-by-identity result directly, without needing that machinery.
        MapRecord mrec = new MapRecord();
        mrec.kind = MapRecord.Kind.LOAD;
        mrec.obj = jfunc;
        mrec.base = vaddr;
        mrec.size = msize;
        mrec.ts = ts;
        mrecInsert(mrec);
    }

    // methodId/classId -> object, versioned by the timestamp the mapping became valid
    // (a class's own load time for classes; that same class-load time for its
    // methods, since a class's methods are always registered together with it in
    // jclasses). Mirrors native's `jmaps` (a PRBTree, i.e. a persistent/versioned
    // red-black tree); simplified here to a plain sorted-by-timestamp map per id,
    // since nothing in this port ever removes an entry (native's own
    // process_jcm_unload_cmd is a documented no-op, and ARCH_JUNLOAD_TYPE is dead/
    // unhandled in native itself), so a lookup is just "the latest registration at or
    // before this timestamp" -- a floor query.
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

    // Pckt_type-style ARCH_type values for the jclasses binary format (data_pckts.h;
    // ARCH_TYPE(x,y) = ((ARCH_x_TYPE << 8) | y), with ARCH_JCLASS_TYPE=10,
    // ARCH_JMETHOD_TYPE=11, ARCH_JCLASS_LOCATION_TYPE=14 -- this file predates and is
    // unrelated to the Pckt_type enum used by data.frameinfo/profile).
    private static final int ARCH_JCLASS = (10 << 8) | 3;
    private static final int ARCH_JMETHOD = (11 << 8) | 3;
    private static final int ARCH_JCLASS_LOCATION = (14 << 8) | 3;
    private static final String SP_JCLASSES_FILE = "jclasses";

    // Simplified relative to native: each class's actual containing jar/location
    // (ARCH_JCLASS_LOCATION records, resolved via DbeFile path lookup) isn't needed
    // for a flat function-name list, so every Java class's Module is parented under
    // one synthetic LoadObject instead of its real jar.
    private LoadObject javaClassesLo;
    private final Map<String, Module> javaModulesByClassName = new HashMap<>();

    void read_java_classes_file() {
        File f = new File(String.format("%s/%s", expt_name, SP_JCLASSES_FILE));
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return; // matches native: file not present/openable -> silent return
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
            // ARCH_JCLASS_LOCATION and anything else: skip (jar/location resolution
            // not needed for a flat function-name list)
            offset += tsize;
        }
    }

    private Module getOrCreateJavaModule(String className, String fileName) {
        // JVM internal form "Lpkg/pkg/Class;" -> "pkg.pkg.Class"
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

    // Reads a NUL-terminated string and returns both its value and the padded byte
    // length it occupies on disk (native's ARCH_STRLEN: (strlen(s)+4) & ~0x3).
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
        // finds the sample named sname and dumps its in-memory size (debug command
        // "dmem"); depends on the samples list / Sample class, not yet ported.
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
        // log.xml is a sequence of top-level elements with no enclosing root (it's
        // written incrementally during collection), so it isn't well-formed XML on its
        // own; wrap it in a synthetic root before handing it to the SAX parser.
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
        // TODO: mapTagValue(PROP_EXPID, userExpId) sets up a session-wide tag-registry
        // mapping (not yet ported); not needed for header display.
        post_process();
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

    // Opens the experiment directory and reads enough of it (log.xml + notes +
    // jclasses + map.xml + data.frameinfo + profile) to populate the header display,
    // the segment/dynamic-function/Java-method registries, the frame/uid stack graph,
    // and the raw clock-profile sample records. read_java_classes_file() must run
    // before read_map_file(): map.xml's "jcm" events resolve their methodId through
    // jmaps, which read_java_classes_file() populates (matches native's own ordering,
    // Experiment.cc:1690-1695). Does NOT yet read dyntext/overview/archives (native-
    // library symbol resolution, out of scope) or jdynclasses (dynamically-generated
    // class bytecode, only needed for source/disassembly views), the separate legacy
    // warnings file, nor the actual PC-address stack materialization from a sample's
    // resolved frame chain (still needed before a -functions view can aggregate
    // anything -- see Experiment.map_Vaddr_to_PC for the address->Function half of
    // that, and the not-yet-written PathTree-equivalent for the rest).
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
        read_frameinfo_file();
        read_profile_file();
        read_heaptrace_file();
        return status;
    }

    void read_map_file() {
        File mapFile = new File(String.format("%s/%s", expt_name, SP_MAP_FILE));
        if (!mapFile.exists())
            return;
        // map.xml has the same "sequence of top-level elements, no enclosing root"
        // shape as log.xml, and is parsed with the same handler.
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

        // See if there are comments or warnings for a load object; if so, queue them
        // to the experiment. (Native also does this for comments; we only have
        // warnings currently, since fetch_warnings()/fetch_comments() on LoadObject
        // aren't populated by anything yet beyond the TODO stub.)
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
            // ctime()'s format, minus the timezone (native ctime() never includes one;
            // we use UTC here rather than local time), plus its embedded trailing newline
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
                default -> "Native- and Java-APIs"; // 0, or JAVA|NATIVE
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
//                        prop.addState (ii, stateNames[ii], stateUNames[ii]);
//                }
//            }
        }

        private static final String SP_TAG_COLLECTOR =       "collector";
        private static final String SP_TAG_CPU =             "cpu";
        private static final String SP_TAG_DATAPTR =         "dataptr";
        private static final String SP_TAG_EVENT =           "event";
        // synthetic root element read_log_file() wraps log.xml's content in (log.xml
        // has no real root of its own -- see read_log_file()); silently ignored
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

    // Mirrors native's Experiment::getDataDescriptors (returns every registered
    // DataDescriptor); "data_id" (the DataDescriptor's getId()) is the same as its
    // ProfData_type ordinal, which is how get_raw_events looks it back up.
    public List<DataDescriptor> getDataDescriptors() {
        List<DataDescriptor> list = new ArrayList<>();
        for (DataDescriptor d : dataDscrs)
            if (d != null)
                list.add(d);
        return list;
    }

    public DataDescriptor get_raw_events(int data_id) {
        return data_id >= 0 && data_id < dataDscrs.length ? dataDscrs[data_id] : null;
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
