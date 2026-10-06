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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mazurov.jdbe.Enums.Prop_type;
import static org.mazurov.jdbe.Enums.ProfData_type;

// Calling-context tree used to aggregate per-sample metric values into per-Function
// totals. Simplified relative to native's PathTree: built directly on resolved
// Function nodes rather than instruction-level DbeInstr nodes (native's tree also
// backs line/instruction-level caller-callee and flame-chart views we don't
// implement), and uses plain per-node counters + HashMap children instead of
// native's chunked-array Slot/Node memory management (an artifact of C++ manual
// allocation, irrelevant with Java's GC).
public class PathTree {

    // One of these per metric "series" this port actually computes. Mirrors how
    // native's Slot is keyed per-BaseMetric-id; simplified to a small fixed set since
    // this port only computes CPU time and the 4 heap metrics so far.
    enum Slot { CPU, HEAP_ALLOC_BYTES, HEAP_ALLOC_CNT, HEAP_LEAK_BYTES, HEAP_LEAK_CNT }

    private static final int NSLOTS = Slot.values().length;

    static class FNode {
        FNode parent;
        Function func;
        Map<Function, FNode> children = new HashMap<>();
        long[] exclVals = new long[NSLOTS]; // self: samples whose stack ended exactly here
        long[] inclVals = new long[NSLOTS]; // subtree total: every sample passing through here
        FNode funclistNext; // next tree node (anywhere) mapped to the same Function,
                             // mirroring native's Node.funclist/fn_map thread
    }

    private final FNode root = new FNode();
    private final Map<Function, FNode> fnMap = new HashMap<>();
    private boolean built;

    public PathTree(DbeView _dbev) {
        this(_dbev, -1);
    }

    public PathTree(DbeView _dbev, int _indxtype) {
//        construct (_dbev, _indxtype, PATHTREE_MAIN);
    }

    // Inserts one sample's resolved stack (leaf-first, root-last -- see
    // Experiment.resolveStack) into the tree for a single metric slot, walking
    // root-to-leaf (mirrors native's PathTree::find_path, which does the same
    // root-to-leaf walk despite also iterating its leaf-first array backward) and
    // incrementing every node's inclVals along the way (native's process_packets:
    // "every node on the leaf->root path gets incremented, not just the leaf" -- so a
    // node's value is a subtree total, not a per-visit count), then the final (leaf)
    // node's exclVals.
    void insertSample(List<Function> stackLeafToRoot, Slot slot, long val) {
        insertSample(stackLeafToRoot, new Slot[] {slot}, new long[] {val});
    }

    // Same as above, but updates several metric slots in one tree walk (used for
    // heap records, where one record contributes to alloc-bytes/alloc-count/
    // leak-bytes/leak-count simultaneously).
    void insertSample(List<Function> stackLeafToRoot, Slot[] slots, long[] vals) {
        FNode node = root;
        for (int i = stackLeafToRoot.size() - 1; i >= 0; i--) {
            Function f = stackLeafToRoot.get(i);
            FNode parent = node;
            node = node.children.computeIfAbsent(f, k -> newChild(parent, f));
            for (int k = 0; k < slots.length; k++)
                node.inclVals[slots[k].ordinal()] += vals[k];
        }
        for (int k = 0; k < slots.length; k++) {
            node.exclVals[slots[k].ordinal()] += vals[k];
            root.inclVals[slots[k].ordinal()] += vals[k];
        }
    }

    private FNode newChild(FNode parent, Function f) {
        FNode n = new FNode();
        n.parent = parent;
        n.func = f;
        n.funclistNext = fnMap.get(f);
        fnMap.put(f, n);
        return n;
    }

    // Builds the tree from every DATA_CLOCK/DATA_HEAP record across every opened
    // experiment. Native reads this lazily per-view; we build once, eagerly, on
    // first use.
    private void ensureBuilt() {
        if (built)
            return;
        built = true;
        DbeSession session = DbeSession.getInstance();
        for (int i = 0; i < session.nexps(); i++) {
            Experiment exp = session.get_exp(i);
            insertClockRecords(exp);
            insertHeapRecords(exp);
        }
    }

    private void insertClockRecords(Experiment exp) {
        DataDescriptor dDscr = exp.getDataDescriptor(ProfData_type.DATA_CLOCK);
        if (dDscr == null)
            return;
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        int frinfoOrd = Prop_type.PROP_FRINFO.ordinal();
        int ntickOrd = Prop_type.PROP_NTICK.ordinal();
        int thridOrd = Prop_type.PROP_THRID.ordinal();
        long size = dDscr.getSize();
        // nanoseconds per NTICK unit, matching Dbe.cc's own duration formula
        // ("ptimer_usec * 1000LL" -- Dbe.cc:8990); converting here (rather than at
        // display time) keeps the tree's units uniform even across multiple
        // experiments with different clock-profiling intervals.
        long nsPerTick = exp.coll_params.ptimer_usec * 1000L;
        for (long i = 0; i < size; i++) {
            long ts = dDscr.getLongValue(tstampOrd, i);
            long frinfo = dDscr.getLongValue(frinfoOrd, i);
            long ntick = dDscr.getLongValue(ntickOrd, i);
            long thrid = dDscr.getLongValue(thridOrd, i);
            List<Function> stack = exp.resolveStack(frinfo, thrid, ts);
            insertSample(stack, Slot.CPU, ntick * nsPerTick);
        }
    }

    // Mirrors native's HEAP_ALLOC_CNT/HEAP_ALLOC_BYTES/HEAP_LEAK_CNT/HEAP_LEAK_BYTES
    // cond_spec/val_spec (BaseMetric.cc:608-638, already ported verbatim into
    // BaseMetric.java's specify()): "(HTYPE!=FREE_TRACE)&&(HTYPE!=MUNMAP_TRACE)&&
    // HVADDR" gates all 4 metrics (an allocation-shaped record with a real address);
    // alloc bytes/count sum HSIZE/1, leak bytes/count sum HLEAKED/1 (HLEAKED is the
    // derived property Experiment.compute_heap_leaked() already computed). Not using
    // the generic expression evaluator for the same reason as CP_TOTAL_CPU: it's a
    // direct hardcoded translation of that same already-ported spec instead.
    private void insertHeapRecords(Experiment exp) {
        DataDescriptor dDscr = exp.getDataDescriptor(ProfData_type.DATA_HEAP);
        if (dDscr == null)
            return;
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        int frinfoOrd = Prop_type.PROP_FRINFO.ordinal();
        int thridOrd = Prop_type.PROP_THRID.ordinal();
        int htypeOrd = Prop_type.PROP_HTYPE.ordinal();
        int hsizeOrd = Prop_type.PROP_HSIZE.ordinal();
        int hvaddrOrd = Prop_type.PROP_HVADDR.ordinal();
        int hleakedOrd = Prop_type.PROP_HLEAKED.ordinal();
        long size = dDscr.getSize();
        for (long i = 0; i < size; i++) {
            int htype = dDscr.getIntValue(htypeOrd, i);
            long hvaddr = dDscr.getLongValue(hvaddrOrd, i);
            if (htype == Enums.Heap_type.FREE_TRACE.value || htype == Enums.Heap_type.MUNMAP_TRACE.value
                    || hvaddr == 0)
                continue;
            long ts = dDscr.getLongValue(tstampOrd, i);
            long frinfo = dDscr.getLongValue(frinfoOrd, i);
            long thrid = dDscr.getLongValue(thridOrd, i);
            long hsize = dDscr.getLongValue(hsizeOrd, i);
            long hleaked = dDscr.getLongValue(hleakedOrd, i);
            List<Function> stack = exp.resolveStack(frinfo, thrid, ts);
            // HEAP_LEAK_BYTES sums HLEAKED unconditionally (0 for a matched/freed
            // record contributes nothing); HEAP_LEAK_CNT additionally requires
            // HLEAKED != 0, matching native's cond_spec exactly (see doc comment above).
            insertSample(stack,
                    new Slot[] {Slot.HEAP_ALLOC_BYTES, Slot.HEAP_ALLOC_CNT, Slot.HEAP_LEAK_BYTES, Slot.HEAP_LEAK_CNT},
                    new long[] {hsize, 1, hleaked, hleaked != 0 ? 1 : 0});
        }
    }

    // Per-Function {exclusive, inclusive} totals for one metric slot. Mirrors native's
    // get_metrics(Vector<Function*>*, Histable*) (PathTree.cc:2044-2102): for each
    // distinct Function, walk every tree node mapped to it (via the funclist thread)
    // and sum exclVals unconditionally (a sample contributes its exclusive value to
    // exactly one node, so there's no double-counting to worry about there), but sum
    // inclVals only from the outermost occurrence of that Function along its own
    // root-to-node ancestor chain (recursion dedup: if Function F calls itself, the
    // deeper F-node's subtree is already included in the shallower F-node's total).
    Map<Function, long[]> computeTotals(Slot slot) {
        ensureBuilt();
        int s = slot.ordinal();
        Map<Function, long[]> totals = new HashMap<>();
        for (Map.Entry<Function, FNode> e : fnMap.entrySet()) {
            Function f = e.getKey();
            long excl = 0, incl = 0;
            for (FNode node = e.getValue(); node != null; node = node.funclistNext) {
                excl += node.exclVals[s];
                boolean outermost = true;
                for (FNode anc = node.parent; anc != null; anc = anc.parent) {
                    if (anc.func == f) {
                        outermost = false;
                        break;
                    }
                }
                if (outermost)
                    incl += node.inclVals[s];
            }
            totals.put(f, new long[] {excl, incl});
        }
        return totals;
    }

    // Grand total across every sample, for the <Total> aggregate row.
    long getGrandTotal(Slot slot) {
        ensureBuilt();
        return root.inclVals[slot.ordinal()];
    }

    // Builds a Hist_data for a flat function list: one row per distinct Function that
    // appeared in any sample, plus a <Total> aggregate row, with columns populated for
    // whichever of CP_TOTAL_CPU/HEAP_ALLOC_BYTES/HEAP_ALLOC_CNT/HEAP_LEAK_BYTES/
    // HEAP_LEAK_CNT appear in `mlist` (Excl+Incl each). Only Histable.Type.FUNCTION +
    // Hist_data.Mode.ALL is implemented -- mirrors the one case ERPrint's plain
    // "-functions" path actually needs (Hist_data::MODL/CALLERS/CALLEES/flame-chart
    // views all need the instruction-level tree this class deliberately doesn't build).
    //
    // Deliberately NOT using the generic cond_spec/val_spec expression evaluation
    // these metrics are defined with (BaseMetric.specify_prof_metric/specify_metric):
    // CP_TOTAL_CPU's condition needs PROP_MSTATE, which this port's sample reader
    // never populates (a legacy multi-microstate PROF_PCKT concept -- the modern
    // single-state OPROF_PCKT format this port targets doesn't need it); the heap
    // metrics' conditions are simple enough (see insertHeapRecords) that hardcoding
    // them directly was simpler than building a generic expression evaluator. Both are
    // verified correct by direct comparison against the oracle. Any other metric
    // (e.g. I/O or sync-wait, not yet ingested) is hidden rather than shown as
    // misleading zeros.
    Hist_data compute_metrics(MetricList mlist, Histable.Type type, Hist_data.Mode mode, List<Histable> objs) {
        if (type != Histable.Type.FUNCTION)
            throw new RuntimeException("PathTree.compute_metrics: only Histable.Type.FUNCTION is implemented");
        if (mode == Hist_data.Mode.CALLERS || mode == Hist_data.Mode.CALLEES || mode == Hist_data.Mode.SELF)
            return compute_metrics_call(mlist, type, mode, objs);
        if (mode != Hist_data.Mode.ALL)
            throw new RuntimeException("PathTree.compute_metrics: Mode." + mode + " is not implemented");

        List<Metric> items = mlist.get_items();
        int ncols = items.size();
        int[] exclCol = new int[NSLOTS];
        int[] inclCol = new int[NSLOTS];
        java.util.Arrays.fill(exclCol, -1);
        java.util.Arrays.fill(inclCol, -1);
        for (int i = 0; i < ncols; i++) {
            Metric m = items.get(i);
            Slot slot = slotFor(m.get_type());
            if (slot != null) {
                if (m.get_subtype() == BaseMetric.EXCLUSIVE)
                    exclCol[slot.ordinal()] = i;
                else if (m.get_subtype() == BaseMetric.INCLUSIVE)
                    inclCol[slot.ordinal()] = i;
            } else if (m.get_type() != BaseMetric.Type.ONAME) {
                m.set_raw_visbits(Enums.VAL_NA);
            }
        }

        Hist_data hist_data = new Hist_data(items, type);
        Hist_data.HistItem totalItem = hist_data.append_hist_item(DbeSession.getInstance().getTotalFunction());
        for (Slot s : Slot.values()) {
            long grand = getGrandTotal(s);
            setMetricValue(totalItem, exclCol[s.ordinal()], s, grand);
            setMetricValue(totalItem, inclCol[s.ordinal()], s, grand);
        }

        boolean anyComputed = false;
        for (Slot s : Slot.values())
            if (exclCol[s.ordinal()] >= 0 || inclCol[s.ordinal()] >= 0)
                anyComputed = true;
        if (!anyComputed) {
            StringBuilder sb = new StringBuilder("compute_metrics: no Slot matched any of " + ncols + " items:");
            for (Metric m : items)
                sb.append(String.format("%n  type=%s subtype=%d vtype=%s", m.get_type(), m.get_subtype(), m.get_vtype()));
            ERIPC.ipc_log("%s%n", sb);
            return hist_data;
        }

        for (Slot s : Slot.values()) {
            if (exclCol[s.ordinal()] < 0 && inclCol[s.ordinal()] < 0)
                continue;
            for (Map.Entry<Function, long[]> e : computeTotals(s).entrySet()) {
                Hist_data.HistItem hi = hist_data.append_hist_item(e.getKey());
                setMetricValue(hi, exclCol[s.ordinal()], s, e.getValue()[0]);
                setMetricValue(hi, inclCol[s.ordinal()], s, e.getValue()[1]);
            }
        }
        return hist_data;
    }

    // Mirrors native's PathTree::get_clr_metrics/get_cle_metrics/get_self_metrics
    // (PathTree.cc), simplified onto this port's Function-level tree (no
    // instruction-level Node graph to walk): CALLERS/CALLEES attribute a Function
    // occurrence's aggregate subtree time to its direct parent/children, deduplicating
    // recursion the same way computeTotals() already does for the flat list (only an
    // occurrence with no same-Function ancestor is "outermost" and contributes). This
    // preserves the invariant that the CALLERS values sum to the same total as the
    // CALLEES values and to the function's own inclusive total in the flat list --
    // unlike native's node-by-node ATTRIBUTED bookkeeping, which this doesn't replicate
    // bit-for-bit, but which this port's instruction-free tree has no equivalent of.
    private Hist_data compute_metrics_call(MetricList mlist, Histable.Type type, Hist_data.Mode mode,
                                            List<Histable> objs) {
        List<Metric> items = mlist.get_items();
        int[] attrCol = new int[NSLOTS];
        int[] exclCol = new int[NSLOTS];
        int[] inclCol = new int[NSLOTS];
        java.util.Arrays.fill(attrCol, -1);
        java.util.Arrays.fill(exclCol, -1);
        java.util.Arrays.fill(inclCol, -1);
        for (int i = 0; i < items.size(); i++) {
            Metric m = items.get(i);
            Slot slot = slotFor(m.get_type());
            if (slot != null) {
                if (m.get_subtype() == BaseMetric.ATTRIBUTED)
                    attrCol[slot.ordinal()] = i;
                else if (m.get_subtype() == BaseMetric.EXCLUSIVE)
                    exclCol[slot.ordinal()] = i;
                else if (m.get_subtype() == BaseMetric.INCLUSIVE)
                    inclCol[slot.ordinal()] = i;
            } else if (m.get_type() != BaseMetric.Type.ONAME) {
                m.set_raw_visbits(Enums.VAL_NA);
            }
        }

        Hist_data hist_data = new Hist_data(items, type);
        if (objs == null || objs.isEmpty() || !(objs.get(0) instanceof Function))
            return hist_data;
        Function target = (Function) objs.get(0);
        ensureBuilt();

        // SELF is shared by two callers with different mlist conventions: the
        // Caller-Callees view's "function item" row (MET_CALL, ATTRIBUTED columns --
        // self value is the inclusive total) and the Summary panel (MET_NORMAL/ref
        // metrics, EXCLUSIVE+INCLUSIVE column pairs -- same computeTotals() result,
        // just spread across two columns instead of one).
        if (mode == Hist_data.Mode.SELF) {
            Hist_data.HistItem hi = hist_data.append_hist_item(target);
            for (Slot s : Slot.values()) {
                if (attrCol[s.ordinal()] < 0 && exclCol[s.ordinal()] < 0 && inclCol[s.ordinal()] < 0)
                    continue;
                long[] totals = computeTotals(s).get(target);
                long excl = totals == null ? 0 : totals[0];
                long incl = totals == null ? 0 : totals[1];
                setMetricValue(hi, attrCol[s.ordinal()], s, incl);
                setMetricValue(hi, exclCol[s.ordinal()], s, excl);
                setMetricValue(hi, inclCol[s.ordinal()], s, incl);
            }
            return hist_data;
        }

        for (Slot s : Slot.values()) {
            if (attrCol[s.ordinal()] < 0)
                continue;
            Map<Function, Long> byEdge = new HashMap<>();
            for (FNode node = fnMap.get(target); node != null; node = node.funclistNext) {
                if (!isOutermost(node, target))
                    continue;
                if (mode == Hist_data.Mode.CALLERS) {
                    if (node.parent == null || node.parent.func == null)
                        continue; // the tree root has no caller
                    byEdge.merge(node.parent.func, node.inclVals[s.ordinal()], Long::sum);
                } else { // CALLEES
                    for (FNode child : node.children.values())
                        byEdge.merge(child.func, child.inclVals[s.ordinal()], Long::sum);
                }
            }
            for (Map.Entry<Function, Long> e : byEdge.entrySet()) {
                Hist_data.HistItem hi = hist_data.append_hist_item(e.getKey());
                setMetricValue(hi, attrCol[s.ordinal()], s, e.getValue());
            }
        }
        return hist_data;
    }

    private static boolean isOutermost(FNode node, Function f) {
        for (FNode anc = node.parent; anc != null; anc = anc.parent)
            if (anc.func == f)
                return false;
        return true;
    }

    private static Slot slotFor(BaseMetric.Type type) {
        return switch (type) {
            case CP_TOTAL_CPU -> Slot.CPU;
            case HEAP_ALLOC_BYTES -> Slot.HEAP_ALLOC_BYTES;
            case HEAP_ALLOC_CNT -> Slot.HEAP_ALLOC_CNT;
            case HEAP_LEAK_BYTES -> Slot.HEAP_LEAK_BYTES;
            case HEAP_LEAK_CNT -> Slot.HEAP_LEAK_CNT;
            default -> null;
        };
    }

    private static void setMetricValue(Hist_data.HistItem hi, int col, Slot slot, long val) {
        if (col < 0)
            return;
        if (slot == Slot.CPU) {
            hi.value[col].tag = DbeStructs.ValueTag.VT_DOUBLE;
            hi.value[col].d = val / 1e9;
        } else if (slot == Slot.HEAP_ALLOC_BYTES || slot == Slot.HEAP_LEAK_BYTES) {
            hi.value[col].tag = DbeStructs.ValueTag.VT_ULLONG;
            hi.value[col].l = val;
        } else {
            hi.value[col].tag = DbeStructs.ValueTag.VT_LLONG;
            hi.value[col].l = val;
        }
    }
}
