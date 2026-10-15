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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mazurov.jdbe.Enums.Prop_type;
import static org.mazurov.jdbe.Enums.ProfData_type;

public class PathTree {

    enum Slot { CPU, HEAP_ALLOC_BYTES, HEAP_ALLOC_CNT, HEAP_LEAK_BYTES, HEAP_LEAK_CNT }

    private static final int NSLOTS = Slot.values().length;

    static class FNode {
        FNode parent;
        Function func;
        Map<Function, FNode> children = new HashMap<>();
        long[] exclVals = new long[NSLOTS]; 
        long[] inclVals = new long[NSLOTS]; 
        FNode funclistNext; 
    }

    private final FNode root = new FNode();
    private final Map<Function, FNode> fnMap = new HashMap<>();
    private boolean built;

    private final int indxtype;

    public PathTree(DbeView _dbev) {
        this(_dbev, -1);
    }

    public PathTree(DbeView _dbev, int _indxtype) {
        indxtype = _indxtype;
    }

    void insertSample(List<Function> stackLeafToRoot, Slot slot, long val) {
        insertSample(stackLeafToRoot, new Slot[] {slot}, new long[] {val});
    }

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

    private void ensureBuilt() {
        if (built)
            return;
        built = true;
        root.func = DbeSession.getInstance().getTotalFunction();
        fnMap.put(root.func, root);
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
            insertSample(stack,
                    new Slot[] {Slot.HEAP_ALLOC_BYTES, Slot.HEAP_ALLOC_CNT, Slot.HEAP_LEAK_BYTES, Slot.HEAP_LEAK_CNT},
                    new long[] {hsize, 1, hleaked, hleaked != 0 ? 1 : 0});
        }
    }

    private boolean indxBuilt;
    private final Map<Long, IndexObject> indxObjs = new HashMap<>();
    private final Map<Long, long[]> indxVals = new HashMap<>();
    private final long[] indxGrandTotal = new long[NSLOTS];
    private IndexObject totalIndxObj;

    private void ensureBuiltIndx() {
        if (indxBuilt)
            return;
        indxBuilt = true;
        totalIndxObj = new IndexObject(indxtype, -1);
        totalIndxObj.set_name("<Total>");
        totalIndxObj.nameIsFinal = true;
        DbeSession session = DbeSession.getInstance();
        for (int i = 0; i < session.nexps(); i++) {
            Experiment exp = session.get_exp(i);
            insertIndxClockRecords(exp);
            insertIndxHeapRecords(exp);
        }
    }

    private void insertIndxClockRecords(Experiment exp) {
        DataDescriptor dDscr = exp.getDataDescriptor(ProfData_type.DATA_CLOCK);
        if (dDscr == null)
            return;
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        int ntickOrd = Prop_type.PROP_NTICK.ordinal();
        int thridOrd = Prop_type.PROP_THRID.ordinal();
        int cpuidOrd = Prop_type.PROP_CPUID.ordinal();
        long size = dDscr.getSize();
        long nsPerTick = exp.coll_params.ptimer_usec * 1000L;
        for (long i = 0; i < size; i++) {
            long ts = dDscr.getLongValue(tstampOrd, i);
            long ntick = dDscr.getLongValue(ntickOrd, i);
            long thrid = dDscr.getLongValue(thridOrd, i);
            long cpuid = dDscr.getLongValue(cpuidOrd, i);
            addIndxSample(exp, thrid, cpuid, ts, Slot.CPU, ntick * nsPerTick);
        }
    }

    private void insertIndxHeapRecords(Experiment exp) {
        DataDescriptor dDscr = exp.getDataDescriptor(ProfData_type.DATA_HEAP);
        if (dDscr == null)
            return;
        int tstampOrd = Prop_type.PROP_TSTAMP.ordinal();
        int thridOrd = Prop_type.PROP_THRID.ordinal();
        int cpuidOrd = Prop_type.PROP_CPUID.ordinal();
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
            long thrid = dDscr.getLongValue(thridOrd, i);
            long cpuid = dDscr.getLongValue(cpuidOrd, i);
            long hsize = dDscr.getLongValue(hsizeOrd, i);
            long hleaked = dDscr.getLongValue(hleakedOrd, i);
            addIndxSample(exp, thrid, cpuid, ts,
                    new Slot[] {Slot.HEAP_ALLOC_BYTES, Slot.HEAP_ALLOC_CNT, Slot.HEAP_LEAK_BYTES, Slot.HEAP_LEAK_CNT},
                    new long[] {hsize, 1, hleaked, hleaked != 0 ? 1 : 0});
        }
    }

    private void addIndxSample(Experiment exp, long thrid, long cpuid, long ts, Slot slot, long val) {
        addIndxSample(exp, thrid, cpuid, ts, new Slot[] {slot}, new long[] {val});
    }

    private void addIndxSample(Experiment exp, long thrid, long cpuid, long ts, Slot[] slots, long[] vals) {
        Long key = indxKey(exp, thrid, cpuid);
        if (key == null)
            return;
        long[] totals = indxVals.computeIfAbsent(key, k -> new long[NSLOTS]);
        IndexObject obj = indxObjs.computeIfAbsent(key, k -> new IndexObject(indxtype, k));
        if (!obj.nameIsFinal) {
            if (indxtype == IndexObject.IndexObjTypes_t.INDEX_THREADS.ordinal()) {
                Experiment.ThreadName tn = exp.describeThread(thrid, ts);
                obj.set_name(tn.name());
                obj.nameIsFinal = tn.isFinal();
            } else if (indxtype == IndexObject.IndexObjTypes_t.INDEX_CPUS.ordinal()) {
                obj.set_name("CPU " + cpuid);
                obj.nameIsFinal = true;
            }
        }
        for (int k = 0; k < slots.length; k++) {
            totals[slots[k].ordinal()] += vals[k];
            indxGrandTotal[slots[k].ordinal()] += vals[k];
        }
    }

    private Long indxKey(Experiment exp, long thrid, long cpuid) {
        if (indxtype == IndexObject.IndexObjTypes_t.INDEX_THREADS.ordinal())
            return ((long) exp.getUserExpId() << IndexObject.INDXOBJ_EXPID_SHIFT) | thrid;
        if (indxtype == IndexObject.IndexObjTypes_t.INDEX_CPUS.ordinal())
            return cpuid;
        return null;
    }

    private Hist_data computeMetricsIndx(MetricList mlist, Hist_data.Mode mode, List<Histable> objs) {
        ensureBuiltIndx();
        List<Metric> items = mlist.get_items();
        int[] exclCol = new int[NSLOTS];
        int[] inclCol = new int[NSLOTS];
        java.util.Arrays.fill(exclCol, -1);
        java.util.Arrays.fill(inclCol, -1);
        for (int i = 0; i < items.size(); i++) {
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

        Hist_data hist_data = new Hist_data(items, Histable.Type.INDEXOBJ);

        if (mode == Hist_data.Mode.SELF) {
            if (objs == null || objs.isEmpty() || !(objs.get(0) instanceof IndexObject obj))
                return hist_data;
            Hist_data.HistItem hi = hist_data.append_hist_item(obj);
            long[] vals = indxVals.get(obj.id);
            for (Slot s : Slot.values()) {
                long v = vals == null ? 0 : vals[s.ordinal()];
                setMetricValue(hi, exclCol[s.ordinal()], s, v);
                setMetricValue(hi, inclCol[s.ordinal()], s, v);
            }
            return hist_data;
        }
        if (mode != Hist_data.Mode.ALL)
            throw new RuntimeException("PathTree.computeMetricsIndx: Mode." + mode + " is not implemented");

        Hist_data.HistItem totalItem = hist_data.append_hist_item(totalIndxObj);
        for (Slot s : Slot.values()) {
            setMetricValue(totalItem, exclCol[s.ordinal()], s, indxGrandTotal[s.ordinal()]);
            setMetricValue(totalItem, inclCol[s.ordinal()], s, indxGrandTotal[s.ordinal()]);
        }
        for (Map.Entry<Long, long[]> e : indxVals.entrySet()) {
            Hist_data.HistItem hi = hist_data.append_hist_item(indxObjs.get(e.getKey()));
            for (Slot s : Slot.values()) {
                long v = e.getValue()[s.ordinal()];
                setMetricValue(hi, exclCol[s.ordinal()], s, v);
                setMetricValue(hi, inclCol[s.ordinal()], s, v);
            }
        }
        return hist_data;
    }

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

    long getGrandTotal(Slot slot) {
        ensureBuilt();
        return root.inclVals[slot.ordinal()];
    }

    List<Function> get_funcs() {
        ensureBuilt();
        return new ArrayList<>(fnMap.keySet());
    }

    Hist_data compute_metrics(MetricList mlist, Histable.Type type, Hist_data.Mode mode, List<Histable> objs) {
        if (type == Histable.Type.INDEXOBJ)
            return computeMetricsIndx(mlist, mode, objs);
        if (type != Histable.Type.FUNCTION)
            throw new RuntimeException("PathTree.compute_metrics: only Histable.Type.FUNCTION/INDEXOBJ is implemented");
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

        Function totalFunc = DbeSession.getInstance().getTotalFunction();
        for (Slot s : Slot.values()) {
            if (exclCol[s.ordinal()] < 0 && inclCol[s.ordinal()] < 0)
                continue;
            for (Map.Entry<Function, long[]> e : computeTotals(s).entrySet()) {
                if (e.getKey() == totalFunc)
                    continue;
                Hist_data.HistItem hi = hist_data.append_hist_item(e.getKey());
                setMetricValue(hi, exclCol[s.ordinal()], s, e.getValue()[0]);
                setMetricValue(hi, inclCol[s.ordinal()], s, e.getValue()[1]);
            }
        }
        return hist_data;
    }

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
        if (objs == null || objs.isEmpty())
            return hist_data;
        List<Function> targetPath = new ArrayList<>(objs.size());
        for (Histable h : objs) {
            if (!(h instanceof Function))
                return hist_data;
            targetPath.add((Function) h);
        }
        ensureBuilt();

        if (mode == Hist_data.Mode.SELF) {
            if (targetPath.size() == 1) {
                Function target = targetPath.get(0);
                Hist_data.HistItem hi = hist_data.append_hist_item(target);
                boolean rootSelf = target == root.func;
                for (Slot s : Slot.values()) {
                    if (attrCol[s.ordinal()] < 0 && exclCol[s.ordinal()] < 0 && inclCol[s.ordinal()] < 0)
                        continue;
                    long[] totals = computeTotals(s).get(target);
                    long excl = totals == null ? 0 : totals[0];
                    long incl = totals == null ? 0 : totals[1];
                    long selfVal = rootSelf ? incl : excl;
                    setMetricValue(hi, attrCol[s.ordinal()], s, selfVal);
                    setMetricValue(hi, exclCol[s.ordinal()], s, selfVal);
                    setMetricValue(hi, inclCol[s.ordinal()], s, incl);
                }
            } else {
                Map<Function, long[]> selfAcc = new HashMap<>();
                getSelfMetricsPath(selfAcc, attrCol, exclCol, inclCol, targetPath, root, false, 0,
                        new ArrayList<>());
                for (Map.Entry<Function, long[]> e : selfAcc.entrySet()) {
                    Hist_data.HistItem hi = hist_data.append_hist_item(e.getKey());
                    long[] v = e.getValue();
                    for (Slot s : Slot.values()) {
                        setMetricValue(hi, attrCol[s.ordinal()], s, v[s.ordinal()]);
                        setMetricValue(hi, exclCol[s.ordinal()], s, v[s.ordinal()]);
                        setMetricValue(hi, inclCol[s.ordinal()], s, v[NSLOTS + s.ordinal()]);
                    }
                }
            }
            return hist_data;
        }

        Map<Function, long[]> acc = new HashMap<>();
        List<FNode> path = new ArrayList<>();
        if (mode == Hist_data.Mode.CALLERS)
            getClrMetrics(acc, attrCol, targetPath, root, -1, 0, path);
        else // CALLEES
            getCleMetrics(acc, attrCol, targetPath, root, -1, -1, 0, path);
        for (Map.Entry<Function, long[]> e : acc.entrySet()) {
            Hist_data.HistItem hi = hist_data.append_hist_item(e.getKey());
            for (Slot s : Slot.values())
                setMetricValue(hi, attrCol[s.ordinal()], s, e.getValue()[s.ordinal()]);
        }
        return hist_data;
    }

    private static void addTo(Map<Function, long[]> acc, Function f, int slotOrd, long delta) {
        acc.computeIfAbsent(f, k -> new long[NSLOTS])[slotOrd] += delta;
    }

    private void getClrMetrics(Map<Function, long[]> acc, int[] attrCol, List<Function> objs, FNode node,
                                int pmatchDepth, int depth, List<FNode> path) {
        path.add(node);
        int nobj = objs.size();
        boolean match = depth + 1 >= nobj;
        if (match) {
            for (int i = 0; i < nobj; i++) {
                if (path.get(depth - nobj + 1 + i).func != objs.get(i)) {
                    match = false;
                    break;
                }
            }
        }

        if (match && depth >= nobj) {
            Function callerFunc = path.get(depth - nobj).func;
            Function outerCallerFunc = (pmatchDepth >= 0 && pmatchDepth >= nobj)
                    ? path.get(pmatchDepth - nobj).func : null;
            for (Slot s : Slot.values()) {
                if (attrCol[s.ordinal()] < 0)
                    continue;
                long val = node.inclVals[s.ordinal()];
                addTo(acc, callerFunc, s.ordinal(), val);
                if (outerCallerFunc != null)
                    addTo(acc, outerCallerFunc, s.ordinal(), -val);
            }
        }

        for (FNode child : node.children.values())
            getClrMetrics(acc, attrCol, objs, child, match ? depth : pmatchDepth, depth + 1, path);
        path.remove(path.size() - 1);
    }

    private void getCleMetrics(Map<Function, long[]> acc, int[] attrCol, List<Function> objs, FNode node,
                                int pcleDepth, int pmatchDepth, int depth, List<FNode> path) {
        path.add(node);
        int nobj = objs.size();
        boolean match = depth + 1 >= nobj;
        if (match) {
            for (int i = 0; i < nobj; i++) {
                if (path.get(depth - nobj + 1 + i).func != objs.get(i)) {
                    match = false;
                    break;
                }
            }
        }

        boolean isDirectCallee = pmatchDepth >= 0 && depth == pmatchDepth + 1;
        Function outerCalleeFunc = pcleDepth >= 0 ? path.get(pcleDepth).func : null;
        for (Slot s : Slot.values()) {
            if (attrCol[s.ordinal()] < 0)
                continue;
            if (match) {
                long leafVal = node.exclVals[s.ordinal()];
                long callSiteVal = node.inclVals[s.ordinal()] - leafVal;
                if (isDirectCallee)
                    addTo(acc, node.func, s.ordinal(), callSiteVal);
                if (outerCalleeFunc != null) {
                    if (isDirectCallee)
                        addTo(acc, outerCalleeFunc, s.ordinal(), -callSiteVal);
                    addTo(acc, outerCalleeFunc, s.ordinal(), -leafVal);
                }
            } else if (isDirectCallee) {
                long val = node.inclVals[s.ordinal()];
                addTo(acc, node.func, s.ordinal(), val);
                if (outerCalleeFunc != null)
                    addTo(acc, outerCalleeFunc, s.ordinal(), -val);
            }
        }

        int childPcle = isDirectCallee ? depth : pcleDepth;
        int childPmatch = match ? depth : pmatchDepth;
        for (FNode child : node.children.values())
            getCleMetrics(acc, attrCol, objs, child, childPcle, childPmatch, depth + 1, path);
        path.remove(path.size() - 1);
    }

    private void getSelfMetricsPath(Map<Function, long[]> acc, int[] attrCol, int[] exclCol, int[] inclCol,
                                     List<Function> objs, FNode node, boolean seen, int depth,
                                     List<FNode> path) {
        path.add(node);
        int nobj = objs.size();
        boolean match = depth + 1 >= nobj;
        if (match) {
            for (int i = 0; i < nobj; i++) {
                if (path.get(depth - nobj + 1 + i).func != objs.get(i)) {
                    match = false;
                    break;
                }
            }
        }

        if (match) {
            boolean inclOk = !seen;
            long[] vals = acc.computeIfAbsent(node.func, k -> new long[2 * NSLOTS]);
            for (Slot s : Slot.values()) {
                if (attrCol[s.ordinal()] < 0 && exclCol[s.ordinal()] < 0 && inclCol[s.ordinal()] < 0)
                    continue;
                vals[s.ordinal()] += (node == root) ? node.inclVals[s.ordinal()] : node.exclVals[s.ordinal()];
                if (inclOk)
                    vals[NSLOTS + s.ordinal()] += node.inclVals[s.ordinal()];
            }
        }

        for (FNode child : node.children.values())
            getSelfMetricsPath(acc, attrCol, exclCol, inclCol, objs, child, seen || match, depth + 1, path);
        path.remove(path.size() - 1);
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
