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
import java.util.List;
import java.util.Objects;

import static org.mazurov.jdbe.Enums.*;

public class MetricList {

    private final Enums.MetricType mtype;
    private final List<Metric> items;

    // the sort reference index
    private int sort_ref_index;
    private boolean sort_reverse;


    public MetricList(List<BaseMetric> base_metrics, Enums.MetricType mtype) {
        this.mtype = mtype;
        items = new ArrayList<>();
        sort_ref_index = 0;
        sort_reverse = false;

        // loop over the base_metrics, and add in all the appropriate subtypes
        for (BaseMetric mtr : base_metrics) {
            if (mtr.is_internal()) continue;

            switch (mtype) {
                case MET_DATA:
                    if ((mtr.get_flavors () & BaseMetric.DATASPACE) != 0) {
                        items.add(new Metric(mtr, BaseMetric.DATASPACE));
                    }
                    break;
                case MET_INDX: {
                    if ((mtr.get_flavors () & BaseMetric.INCLUSIVE) != 0
                            || (mtr.get_flavors () & BaseMetric.EXCLUSIVE) != 0)
                    {
                        boolean found = false;
                        for (Metric item2 : items) {
                            if (item2.get_subtype() == BaseMetric.EXCLUSIVE
                                    && Objects.equals(item2.get_cmd(), mtr.get_cmd())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            items.add(new Metric(mtr, BaseMetric.EXCLUSIVE));
                        }
                    }
                }
                break;

                case MET_CALL:
                case MET_CALL_AGR:
                    if ((mtr.get_flavors() & BaseMetric.ATTRIBUTED) != 0) {
                        items.add(new Metric(mtr, BaseMetric.ATTRIBUTED));
                    }
                    // now fall through to add exclusive and inclusive

                case MET_NORMAL:
                case MET_COMMON:
                    if ((mtr.get_flavors() & BaseMetric.EXCLUSIVE) != 0) {
                        items.add(new Metric(mtr, BaseMetric.EXCLUSIVE));
                    }
                    if ((mtr.get_flavors() & BaseMetric.INCLUSIVE) != 0) {
                        items.add(new Metric(mtr, BaseMetric.INCLUSIVE));
                    }
                    break;
                case MET_SRCDIS:
                    if ((mtr.get_flavors() & BaseMetric.INCLUSIVE) != 0) {
                        items.add(new Metric(mtr, BaseMetric.INCLUSIVE));
                    }
                    break;
                case MET_IO: {
                    if (mtr.get_packet_type() == ProfData_type.DATA_IOTRACE
                            && ((mtr.get_flavors() & BaseMetric.INCLUSIVE) != 0
                            || (mtr.get_flavors() & BaseMetric.EXCLUSIVE) != 0))
                    {
                        boolean found = false;
                        for (Metric item2 : items) {
                            if (item2.get_subtype() == BaseMetric.EXCLUSIVE
                                    && Objects.equals(item2.get_cmd(), mtr.get_cmd())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            items.add(new Metric(mtr, BaseMetric.EXCLUSIVE));
                        }
                    }
                }
                break;
                case MET_HEAP: {
                    if (mtr.get_packet_type() == ProfData_type.DATA_HEAP
                            && ((mtr.get_flavors() & BaseMetric.INCLUSIVE) != 0
                            || (mtr.get_flavors() & BaseMetric.EXCLUSIVE) != 0))
                    {
                        boolean found = false;
                        for (Metric item2 : items) {
                            if (item2.get_subtype() == BaseMetric.EXCLUSIVE &&
                                    Objects.equals(item2.get_cmd(), mtr.get_cmd())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            items.add(new Metric(mtr, BaseMetric.EXCLUSIVE));
                        }
                    }
                }
                break;
            }

            // add the static
            if ((mtr.get_flavors() & BaseMetric.STATIC) != 0) {
                switch (mtype) {
                    case MET_NORMAL:
                    case MET_COMMON:
                    case MET_CALL:
                    case MET_CALL_AGR:
                    case MET_SRCDIS:
                        items.add(new Metric(mtr, BaseMetric.STATIC));
                        break;
                    default:
                        if (mtr.get_type() == BaseMetric.Type.ONAME) {
                            items.add(new Metric(mtr, BaseMetric.STATIC));
                        }
                        break;
                }
            }
        }
        // Set default visibility. Native computes this via a full setMetrics()/
        // DEFAULT_METRICS command-string parser (consulting each BaseMetric's
        // per-subtype default_visbits), which isn't ported -- this is a simplification
        // matching its actual default *result* for every metric type this port
        // currently computes: EXCLUSIVE/INCLUSIVE metrics show their plain value (no
        // percent column); STATIC metrics are hidden except ONAME (the Name column,
        // always shown).
        for (Metric m : items) {
            if (m.get_subtype() == BaseMetric.STATIC && m.get_type() != BaseMetric.Type.ONAME)
                m.set_raw_visbits(VAL_NA);
            else {
                m.enable_all_visbits();
                m.set_raw_visbits(m.get_visbits() & ~VAL_PERCENT);
            }
        }
    }

    public MetricList(Enums.MetricType _mtype) {
        mtype = _mtype;
        items = new ArrayList<>();
        sort_ref_index = 0;
        sort_reverse = false;
    }

    public MetricList(MetricList old) {
        mtype = old.mtype;
        items = new ArrayList<>();
        sort_ref_index = old.get_sort_ref_index();
        sort_reverse = old.get_sort_rev();
        items.addAll(old.items);
    }

    public MetricType get_type() {
        return mtype;
    }

    public List<Metric> get_items() {
        return items;
    }

    public void append(Metric m) {
        items.add(m);
    }

    public boolean get_sort_rev() {   // get the boolean reverse for the sort metric
        return sort_reverse;
    }

    public void set_sort_rev (boolean v) {
        sort_reverse = v;
    }

    public int get_sort_ref_index() {
        return sort_ref_index;
    }

    public void set_sort_ref_index(int ind) {
        sort_ref_index = ind;
    }

    public Metric get_sort_metric() {
        int i = get_sort_ref_index();
        return i >= 0 && i < items.size() ? items.get(i) : null;
    }

    public String get_sort_name() {
        Metric item = get_sort_metric();
        if (item == null)
            return "";
        String n = item.get_name();
        return sort_reverse ? "-" + n : n;
    }

    public String get_sort_cmd() {
        Metric item = get_sort_metric();
        if (item == null)
            return "";
        String n = item.get_mcmd(false);
        return sort_reverse ? "-" + n : n;
    }

    public String set_sort(String mspec, boolean fromRcFile) {
        // metric-spec sort parsing (matching by mcmd/name against items) not yet ported.
        throw new RuntimeException("MetricList.set_sort not implemented");
    }

    // Mirrors native's MetricList::set_sort(int, bool) (MetricList.cc:618-632): set_sort
    // by the visible column index the GUI sends (Analyzer, not er_print).
    public void set_sort(int visindex, boolean reverse) {
        if (visindex < items.size()) {
            Metric mitem = items.get(visindex);
            if (mitem.is_any_visible()) {
                sort_ref_index = visindex;
                sort_reverse = reverse;
                return;
            }
        }
        // Native falls back here to set_fallback_sort(), which goes through the
        // string-based sort-spec parser this port's set_sort(String,...) doesn't
        // implement yet. Not reachable from the GUI in practice (it only ever sends the
        // index of a column it is itself displaying, hence visible), so left unported.
        throw new RuntimeException(
                "MetricList.set_sort: fallback sort not implemented (visindex=" + visindex + ")");
    }

    // Mirrors native's MetricList::find_metric(char*, BaseMetric::SubType)
    // (MetricList.cc:801-808), simplified to a direct cmd+subtype match (native's
    // get_listorder() indirection isn't needed here).
    public Metric find_metric(String cmd, int subtype) {
        for (Metric m : items) {
            if (m.get_subtype() == subtype && Objects.equals(m.get_cmd(), cmd))
                return m;
        }
        return null;
    }

    // Mirrors native's MetricList::set_sort_metric (MetricList.cc:635-665), simplified:
    // the 'any'/'all'/'hwc'/'bit' keyword forms are er_print "-sort" command-line
    // syntax, not reachable from the GUI's column-click-driven setSort path that is
    // this method's only caller in this port (DbeView.setSort cross-tab sync).
    public boolean set_sort_metric(String mname, int subtype, boolean reverse) {
        for (int i = 0; i < items.size(); i++) {
            Metric m = items.get(i);
            if (subtype == m.get_subtype() && Objects.equals(mname, m.get_cmd())) {
                sort_ref_index = i;
                sort_reverse = reverse;
                return true;
            }
        }
        return false;
    }

    // a string formatted from the metric list, suitable for a "metrics <string>" command
    public String get_metrics() {
        StringBuilder sb = new StringBuilder();
        for (Metric item : items) {
            if (sb.length() != 0)
                sb.append(':');
            sb.append(item.get_mcmd(false));
        }
        return sb.toString();
    }

    // print the list of metrics to a file
    //  debug = false: print the name and mcmd of each metric
    //  debug = true: also print subtype/vtype/visibility/sort details for each metric
    public void print_metric_list(PrintStream dis_file, String leader, boolean debug) {
        dis_file.print(leader);
        if (items.isEmpty()) {
            dis_file.print("metric list is empty; aborting\n");
            return;
        }

        // Find the longest metric name & command
        int max_len = 0;
        int max_len2 = 0;
        for (Metric item : items) {
            max_len = Math.max(max_len, item.get_name().length());
            max_len2 = Math.max(max_len2, item.get_mcmd(true).length());
        }
        String fmt_name = debug ? "%" + max_len + "s: %-" + max_len2 + "s" : "%" + max_len + "s: %s";

        for (int index = 0; index < items.size(); index++) {
            Metric item = items.get(index);
            dis_file.printf(fmt_name, item.get_name(), item.get_mcmd(true));
            if (debug)
                dis_file.printf("\t[st %2d, VT %d, vis = %4s, T=%d, sort = %c]",
                        item.get_subtype(), item.get_vtype().ordinal(), item.get_vis_str(),
                        item.is_time_val() ? 1 : 0, sort_ref_index == index ? 'Y' : 'N');
            dis_file.print('\n');
        }
        dis_file.print('\n');
        dis_file.flush();
    }

    public void set_metrics(MetricList mlist) {
        // verify that the type is appropriate for the call
        if (mtype == MetricType.MET_NORMAL || mtype == MetricType.MET_COMMON
                || (mlist.mtype != MetricType.MET_NORMAL && mlist.mtype != MetricType.MET_COMMON))
            throw new IllegalArgumentException();

        List<Metric> mlist_items = mlist.get_items();
        items.clear();

        int sort_ind = mlist.get_sort_ref_index();
        for (int i = 0, mlist_sz = mlist_items.size(); i < mlist_sz; i++) {
            Metric mtr = mlist_items.get(i);
            if (!mtr.is_any_visible()) {
                continue;
            }

            //  Add a new Metric with probably a new sub_type to this->items:
            //    for MET_CALL and MET_CALL_AGR the matching entry to an e. or i. is itself
            //    for MET_DATA, the matching entry to an e. or i. is the d. metric
            //    for MET_INDX, the matching entry to an e. or i. is the e. metric
            //    for MET_IO, the matching entry to an e. or i. is the e. metric
            //    for MET_HEAP, the matching entry to an e. or i. is the e. metric
            //    Save static entries (SIZES and ADDRESS) only for MET_NORMAL, MET_CALL, MET_CALL_AGR, MET_SRCDIS
            switch (mtr.get_type()) {
                case SIZES:
                case ADDRESS:
                    switch (mtype) {
                        case MET_NORMAL:
                        case MET_COMMON:
                        case MET_CALL:
                        case MET_CALL_AGR:
                        case MET_SRCDIS:
                            break;
                        default:
                            continue;
                    }
                    break;
                default:
                    break;
            }

            int st = mtr.get_subtype();
            if (st != BaseMetric.STATIC) {
                if (mtype == MetricType.MET_CALL || mtype == MetricType.MET_CALL_AGR) {
                    if ((mtr.get_flavors() & BaseMetric.ATTRIBUTED) == 0)
                        continue;
                    st = BaseMetric.ATTRIBUTED;
                } else if (mtype == MetricType.MET_DATA) {
                    if ((mtr.get_flavors() & BaseMetric.DATASPACE) == 0)
                        continue;
                    st = BaseMetric.DATASPACE;
                } else if (mtype == MetricType.MET_INDX) {
                    if ((mtr.get_flavors() & BaseMetric.EXCLUSIVE) == 0)
                        continue;
                    st = BaseMetric.EXCLUSIVE;
                } else if (mtype == MetricType.MET_IO) {
                    if (mtr.get_packet_type() != ProfData_type.DATA_IOTRACE ||
                            (mtr.get_flavors() & BaseMetric.EXCLUSIVE) == 0)
                        continue;
                    st = BaseMetric.EXCLUSIVE;
                } else if (mtype == MetricType.MET_HEAP) {
                    if (mtr.get_packet_type() != ProfData_type.DATA_HEAP ||
                            (mtr.get_flavors() & BaseMetric.EXCLUSIVE) == 0)
                        continue;
                    st = BaseMetric.EXCLUSIVE;
                } else if (mtype == MetricType.MET_SRCDIS) {
                    if ((mtr.get_flavors() & BaseMetric.INCLUSIVE) == 0)
                        continue;
                    st = BaseMetric.INCLUSIVE;
                }
            }

            boolean found = false;
            for (int i1 = 0, items_sz = items.size(); i1 < items_sz; i1++) {
                Metric m1 = items.get(i1);
                if (mtr.get_id() == m1.get_id() && st == m1.get_subtype()) {
                    if (sort_ind == i)
                        sort_ind = i1;
                    found = true;
                    break;
                }
            }
            if (found)
                continue;
            Metric m = new Metric(mtr);
            m.set_subtype(st);
            m.set_raw_visbits(mtr.get_visbits());
            if (sort_ind == i)
                sort_ind = items.size();
            items.add(m);
        }
        if (sort_ind >= items.size())
            sort_ind = 0;
        if (mtype == MetricType.MET_IO)
            sort_ind = 0;
        if (mtype == MetricType.MET_HEAP)
            sort_ind = 0;
        sort_ref_index = sort_ind;
    }

}
