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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hist_data {

    // HistItem contains all the data about a single histogram item.
    public static class HistItem {
        public Histable obj;  // info on the object
        int type;       // annotated src/dis: type
        public DbeStructs.TValue[] value;  // one numeric value per MetricList column
        long size;

        HistItem (long n) {
            size = n;
            value = new DbeStructs.TValue[(int) n];
            for (int i = 0; i < n; i++)
                value[i] = new DbeStructs.TValue();
        }
    }

    public enum Hist_status {
        SUCCESS /*= 0*/,
        NO_DATA
    }

    public enum Mode {
        ALL,
        CALLERS,
        CALLEES,
        SELF,
        MODL,
        LAYOUT,
        DETAIL
    }

    enum Sort_order {
        ASCEND,
        DESCEND
    }

    enum Sort_type {
        ALPHA,
        VALUE,
        AUX
    }

    public final Histable.Type type;
    private final List<Metric> metrics;
    private Hist_status status;
    private final ArrayList<HistItem> hist_items = new ArrayList<>();
    private final Map<Histable, HistItem> hi_map = new HashMap<>();
    private int sort_ind = -1;
    private boolean rev_sort;

    public Hist_data(List<Metric> metrics, Histable.Type type) {
        this.metrics = metrics;
        this.type = type;
        status = Hist_status.SUCCESS;
    }

    public Hist_status get_status () {
        return status;
    }

    public long size() {
        return hist_items.size();
    }

    public List<Metric> get_metric_list() {
        return metrics;
    }

    public HistItem fetch(int index) {
        return hist_items.get(index);
    }

    // Get-or-create by object identity, mirroring native's Hist_data::append_hist_item
    // (Hist_data.cc:708-723).
    public HistItem append_hist_item(Histable obj) {
        HistItem hi = hi_map.get(obj);
        if (hi == null) {
            hi = new HistItem(metrics.size());
            hi.obj = obj;
            hist_items.add(hi);
            hi_map.put(obj, hi);
        }
        return hi;
    }

    public HistItem find_hist_item(Histable obj) {
        return hi_map.get(obj);
    }

    // Mirrors native's Hist_data::get_value (Hist_data.cc:962-1013), simplified: no
    // DELTA/RATIO branches (compare-mode only, not supported here). The Name column
    // (ONAME) isn't stored in HistItem.value at all -- it's computed on demand from
    // the row's object, matching native (Hist_data.cc:1006-1009).
    DbeStructs.TValue get_value(int metricInd, int row) {
        return get_value_for_item(hist_items.get(row), metricInd);
    }

    // Mirrors native's Hist_data::get_value(TValue*, int, HistItem*) overload, used by
    // dbeGetFuncListMini to read a single column off the <Total> row without needing
    // its row index (which shifts depending on sort direction). Returns an empty TValue
    // rather than crashing if no <Total> row exists (e.g. a view whose MetricList has
    // no CPU/heap-slot metrics at all -- PathTree.compute_metrics's "anyComputed" early
    // return never creates one).
    public DbeStructs.TValue get_value_for_total(int metricInd) {
        HistItem hi = find_hist_item(DbeSession.getInstance().getTotalFunction());
        if (hi == null) {
            ERIPC.ipc_log("get_value_for_total: no <Total> HistItem found (hist_items.size()=%d, metricInd=%d)%n",
                    hist_items.size(), metricInd);
            return new DbeStructs.TValue();
        }
        return get_value_for_item(hi, metricInd);
    }

    private DbeStructs.TValue get_value_for_item(HistItem hi, int metricInd) {
        Metric m = metrics.get(metricInd);
        if (m.get_type() == BaseMetric.Type.ONAME) {
            DbeStructs.TValue v = new DbeStructs.TValue();
            v.tag = DbeStructs.ValueTag.VT_LABEL;
            v.str = hi.obj.get_name();
            return v;
        }
        return hi.value[metricInd];
    }

    // Mirrors native's Hist_data::get_histmetrics (Hist_data.cc:1015-1048): for each
    // visible column, measure the widest formatted value across every row, then fold
    // that together with the wrapped header text via Metric::legend_width.
    Metric.HistMetric[] get_histmetrics() {
        int n = metrics.size();
        Metric.HistMetric[] hm = new Metric.HistMetric[n];
        for (int i = 0; i < n; i++)
            hm[i] = new Metric.HistMetric();
        for (int i = 0; i < n; i++) {
            Metric m = metrics.get(i);
            // The Name column (VT_LABEL) is excluded from width measurement here --
            // confirmed empirically against the oracle: its data rows can be 500+
            // characters (long overloaded-method names), yet the printed header
            // never widens to accommodate them, so native must skip label-typed
            // values in this loop (matches the same "vtype == VT_LABEL -> skip"
            // pattern used elsewhere in Print.cc's metric-width computations, e.g.
            // dump_detail).
            if (!m.is_value_visible() || m.get_vtype() == Enums.ValueTag.VT_LABEL)
                continue;
            Metric.HistMetric h = hm[i];
            for (int row = 0; row < hist_items.size(); row++) {
                int len = get_value(i, row).get_len();
                if (h.maxvalue_width < len)
                    h.maxvalue_width = len;
            }
        }
        for (int i = 0; i < n; i++)
            metrics.get(i).legend_width(hm[i], 2);
        return hm;
    }

    // Mirrors native's Hist_data::sort (Hist_data.cc:494-539), simplified: only the
    // ALPHA (Name column)/VALUE comparisons (no AUX/MODL/DELTA/RATIO -- none apply to
    // a plain function list), and the generic tie-break chain (name, then every other
    // column, then object id) is dropped in favor of Java's stable sort leaving ties
    // in insertion order. <Total> is still force-moved to the front (or back, if
    // `reverse`) after sorting, exactly matching native's explicit name-prefix check.
    void sort(int ind, boolean reverse) {
        if (ind < 0 || ind >= metrics.size())
            return;
        sort_ind = ind;
        rev_sort = reverse;
        Metric m = metrics.get(ind);
        boolean alpha = m.get_type() == BaseMetric.Type.ONAME;
        Comparator<HistItem> cmp = alpha
                ? Comparator.comparing(hi -> hi.obj.get_name() == null ? "" : hi.obj.get_name())
                : Comparator.comparingDouble(hi -> hi.value[ind].to_double());
        if (!alpha)
            cmp = cmp.reversed(); // VALUE metrics default to DESCEND
        if (reverse)
            cmp = cmp.reversed();
        hist_items.sort(cmp);

        for (int i = 0; i < hist_items.size(); i++) {
            HistItem hi = hist_items.get(i);
            String name = hi.obj.get_name();
            if (name != null && name.startsWith("<Total>")) {
                int idx0 = reverse ? hist_items.size() - 1 : 0;
                if (i != idx0) {
                    hist_items.remove(i);
                    hist_items.add(idx0, hi);
                }
                break;
            }
        }
    }

    void resort(MetricList mlist) {
        sort(mlist.get_sort_ref_index(), mlist.get_sort_rev());
    }

    // Mirrors native's Hist_data::print_label (Hist_data.cc:827-867), simplified: the
    // compare-mode "legend" first line (always empty outside -compare) is dropped.
    void print_label(PrintStream out, Metric.HistMetric[] hm, int space) {
        StringBuilder sb1 = new StringBuilder(), sb2 = new StringBuilder(), sb3 = new StringBuilder();
        String pad = " ".repeat(Math.max(space, 0));
        sb1.append(pad);
        sb2.append(pad);
        sb3.append(pad);
        for (int i = 0; i < metrics.size(); i++) {
            Metric m = metrics.get(i);
            if (!m.is_any_visible())
                continue;
            Metric.HistMetric h = hm[i];
            int len = h.width;
            String prefix = "";
            if (i > 0 && m.get_type() == BaseMetric.Type.ONAME) {
                prefix = " ";
                len--;
            }
            sb1.append(prefix).append(padRight(h.legend1, len));
            sb2.append(prefix).append(padRight(h.legend2, len));
            sb3.append(prefix).append(padRight(h.legend3, len));
        }
        out.println(sb1);
        out.println(sb2);
        out.println(sb3);
    }

    // Mirrors native's Hist_data::print_content (Hist_data.cc:869-882).
    void print_content(PrintStream out, Metric.HistMetric[] hm, int limit) {
        int cnt = hist_items.size();
        if (limit > 0 && cnt > limit)
            cnt = limit;
        for (int i = 0; i < cnt; i++)
            out.println(print_row(i, hm, " "));
    }

    // Mirrors native's Hist_data::print_row + append_str (Hist_data.cc:884-960),
    // simplified: no is_tvisible()/is_pvisible() branches (this port never marks a
    // metric time- or percent-visible -- see Metric.legend_width's doc comment).
    private String print_row(int row, Metric.HistMetric[] hm, String mark) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < metrics.size(); i++) {
            Metric m = metrics.get(i);
            if (!m.is_any_visible())
                continue;
            Metric.HistMetric h = hm[i];
            int startLen = sb.length();
            if (m.is_visible()) {
                String s = get_value(i, row).to_str();
                if (m.get_type() == BaseMetric.Type.ONAME) {
                    sb.append(mark);
                    if (i + 1 == metrics.size())
                        sb.append(s);
                    else
                        sb.append(padRight(s, h.maxvalue_width)).append(' ');
                    continue;
                }
                sb.append(padLeft(s, h.maxvalue_width));
            }
            int len = sb.length() - startLen;
            if (h.width > len && i + 1 != metrics.size())
                sb.append(" ".repeat(h.width - len));
        }
        return sb.toString();
    }

    private static String padLeft(String s, int width) {
        return width > s.length() ? " ".repeat(width - s.length()) + s : s;
    }

    private static String padRight(String s, int width) {
        return width > s.length() ? s + " ".repeat(width - s.length()) : s;
    }
}
