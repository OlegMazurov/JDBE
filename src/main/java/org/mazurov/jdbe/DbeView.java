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

import static org.mazurov.jdbe.Enums.*;

public class DbeView {

    int vindex;       // index of this view -- set by Analyzer
    boolean func_scope;
    int phaseIdx;
    Settings settings;

    List<BaseMetric> reg_metrics = new ArrayList<>();   // list of registered metrics
    private final Map<MetricType, MetricList> metrics_lists = new HashMap<>();
    private final Map<MetricType, MetricList> metrics_ref_lists = new HashMap<>();

    private List<PathTree> indxspaces;
    private List<Hist_data> indx_data; // index object data
    private List<Histable> sel_idxobj; // selected index objects

    List<Integer> lobjectsNoJava = new ArrayList<>(); // List of indices into LoadObjects excluding java classes

    private List<LibExpand> lo_expands;

    public DbeView(Settings settings, int index) {
        init();

        this.vindex = index;
        this.settings = new Settings(settings);

        // Initialize index spaces
        int sz = settings.get_IndxTabState().size();
        indxspaces = new ArrayList<>(sz);
        indx_data = new ArrayList<>(sz);
        sel_idxobj = new ArrayList<>(sz);
        for (int i = 0; i < sz; i++) {
            indxspaces.add(new PathTree(this, i));
            indx_data.add(null);
            sel_idxobj.add(null);
        }

        // set lo_expands for already existing LoadObjects
        List<LoadObject> lobjs = DbeSession.getInstance().get_text_segments();
        for (LoadObject lo : lobjs) {
//            lo_expands.store(lo.seg_idx, LibExpand.LIBEX_SHOW);
            set_lo_expand(lo.seg_idx, LibExpand.LIBEX_SHOW);
        }

    }
    public DbeView(DbeView oldView, int index) {
        this.vindex = index;
        init();
    }

    private void init() {
        phaseIdx = 0;
//        derived_metrics = new DerivedMetrics;
//        derived_metrics->add_definition (GTXT ("CPI"), GTXT ("Cycles Per Instruction"), GTXT ("cycles/insts"));
//        derived_metrics->add_definition (GTXT ("IPC"), GTXT ("Instructions Per Cycle"), GTXT ("insts/cycles"));
//        derived_metrics->add_definition (GTXT ("K_CPI"), GTXT ("Kernel Cycles Per Instruction"), GTXT ("K_cycles/K_insts"));
//        derived_metrics->add_definition (GTXT ("K_IPC"), GTXT ("Kernel Instructions Per Cycle"), GTXT ("K_insts/K_cycles"));
        reset();
    }

    public Settings get_settings() {
        return settings;
    }

    public void set_name_format(int nameFormat, boolean soName) {
        // TODO: implement
    }

    public boolean get_exp_enable (int n) {
//        TODO:
//        return filters ? filters->fetch(n)->get_enabled () : true;
        return true;
    }

    public DispTab[] get_TabList() {
        return settings.get_TabList();
    };

    public List<Boolean> get_IndxTabState() {
        return settings.get_IndxTabState();
    };

    public List<Boolean> get_MemTabState() {
        return settings.get_MemTabState();
    };

    public void reset_metric_list(MetricList mlist, int cmp_mode) {
        MetricType mtype = mlist.get_type();
        switch (mtype) {
            case MET_NORMAL:
            case MET_COMMON:
                metrics_lists.put(MetricType.MET_COMMON, new MetricList(mlist));
                remove_compare_metrics(metrics_lists.get(MetricType.MET_COMMON));
                break;
            // ignoring the following cases (why?)
            case MET_SRCDIS:
            case MET_CALL:
            case MET_DATA:
            case MET_INDX:
            case MET_CALL_AGR:
            case MET_IO:
            case MET_HEAP:
                break;
        }

        if (cmp_mode != -1) {
            settings.set_compare_mode(cmp_mode);
//            if (comparingExperiments()) {
//                add_compare_metrics(mlist);
//            }
        }

        switch (mtype) {
            case MET_NORMAL:
                metrics_lists.put(mtype, mlist);
                // fall through to next case
            case MET_COMMON:
                metrics_lists.get(MetricType.MET_SRCDIS).set_metrics(mlist);
                metrics_lists.get(MetricType.MET_CALL).set_metrics(mlist);
                metrics_lists.get(MetricType.MET_CALL_AGR).set_metrics(mlist);
                remove_compare_metrics (metrics_lists.get(MetricType.MET_CALL_AGR));
                metrics_lists.get(MetricType.MET_DATA).set_metrics(mlist);
                metrics_lists.get(MetricType.MET_INDX).set_metrics(mlist);
                metrics_lists.get(MetricType.MET_IO).set_metrics(mlist);
                metrics_lists.get(MetricType.MET_HEAP).set_metrics(mlist);
                break;
            case MET_CALL_AGR:
                metrics_lists.put(MetricType.MET_CALL_AGR, mlist);
                remove_compare_metrics (mlist);
                break;
            case MET_SRCDIS:
            case MET_CALL:
            case MET_DATA:
            case MET_INDX:
            case MET_IO:
            case MET_HEAP:
                metrics_lists.put(mtype, mlist);
                break;
            default:
                throw new IllegalArgumentException();
        }
        reset_data(false);
    }

    private void reset_data (boolean all) {
//        // clear the precomputed data
//        func_data = null;
//        line_data = null;
//        pc_data = null;
//        src_data = null;
//        dis_data = null;
//        fitem_data = null;
//        callers = null;
//        callees = null;
//        dobj_data = null;
//        dlay_data = null;
//        iofile_data = null;
//        iovfd_data = null;
//        iocs_data = null;
//        heapcs_data = null;
//
//        // and reset the selections
//        if (all) {
//            sel_obj = null;
//            sel_dobj = null;
//            lastSelInstr = null;
//            lastSelFunc = null;
//            // Set selected object <Total> if possible
//            Function ft = DbeSession.getInstance().get_Total_Function ();
//            set_sel_obj(ft);
//        }
//        sel_binctx = null;
//
//        dspace.reset();
//        iospace.reset();
//        heapspace.reset();
//
//        // loop over MemorySpaces, resetting each one
//        for (long i = 0, sz = VecSize (memspaces); i < sz; i++) {
//            MemorySpace ms = memspaces.get(i);
//            ms.reset();
//        }
//
//        // loop over IndexSpaces, resetting cached data
//        indx_data.destroy();
//        for (long i = 0, sz = VecSize (indxspaces); i < sz; i++) {
//            indx_data.store(i, null);
//            sel_idxobj.store(i, null);
//        }
    }

    public void remove_compare_metrics(MetricList mlist) {
//        List<Metric> items = mlist.get_items();
//        List<Metric> items_old = items.copy();
//        items.reset();
//        int sort_index = mlist->get_sort_ref_index ();
//        mlist->set_sort_ref_index (0);
//        for (int i = 0, sz = items_old->size (); i < sz; i++)
//        {
//            Metric *m = items_old->fetch (i);
//            if (m->get_expr_spec () == NULL)
//            {
//                // this is a 'non-compare' metric; add it
//                items->append (m);
//                if (sort_index == i)
//                    mlist->set_sort_ref_index (items->size () - 1);
//                continue;
//            }
//            // is the 'non-compare' version of the metric already in the list?
//            int ind = mlist->get_listorder (m->get_cmd (), m->get_subtype ());
//            if (ind == -1)
//            {
//                // not in the list; add it
//                BaseMetric *bm = dbeSession->find_metric (m->get_type (), m->get_cmd (), NULL);
//                Metric *new_met = new Metric (bm, m->get_subtype ());
//                new_met->set_raw_visbits (m->get_visbits () & ~(CMP_DELTA | CMP_RATIO));
//                items->append (new_met);
//                if (sort_index == i)
//                    mlist->set_sort_ref_index (items->size () - 1);
//            }
//            delete m;
//        }
//        delete items_old;
        reset_data(false);
    }

    private void reset() {
        phaseIdx++;

        // reset all the per-experiment arrays
//        filters->destroy ();
//        lo_expands->reset ();
//        cur_filter_str = null;
//        prev_filter_str = null;
//        cur_filter_expr = null;
//        noParFilter = false;
//        for (int exp_id = 0; exp_id < dataViews->size (); ++exp_id)
//        {
//            Vector<DataView*> *expDataViewList = dataViews->fetch (exp_id);
//            if (expDataViewList)
//                expDataViewList->destroy ();
//        }
//        dataViews->destroy ();
        reset_metrics();

        // now reset any cached data
//        reset_data (true);
//        ompDisMode = false;
//        showAll = true;
//        showHideChanged = false;
//        newViewMode = false;
    }

    public BaseMetric register_metric_expr(BaseMetric.Type type,String cmd, String expr_spec) {
        return DbeSession.getInstance().register_metric_expr(type, cmd, expr_spec);
    }


    public MetricList get_metric_list(MetricType metricType) {
        if (metrics_lists.get(MetricType.MET_COMMON) == null) {
            List<BaseMetric> base_metrics = DbeSession.getInstance().get_base_reg_metrics();
            metrics_lists.put(MetricType.MET_SRCDIS, new MetricList(base_metrics, MetricType.MET_SRCDIS));
            metrics_lists.put(MetricType.MET_COMMON, new MetricList (base_metrics, MetricType.MET_COMMON));
            metrics_lists.put(MetricType.MET_NORMAL, new MetricList (base_metrics, MetricType.MET_NORMAL));
            metrics_lists.put(MetricType.MET_CALL, new MetricList (base_metrics, MetricType.MET_CALL));
            metrics_lists.put(MetricType.MET_CALL_AGR, new MetricList (base_metrics, MetricType.MET_CALL_AGR));
            metrics_lists.put(MetricType.MET_DATA, new MetricList (base_metrics, MetricType.MET_DATA));
            metrics_lists.put(MetricType.MET_INDX, new MetricList (base_metrics, MetricType.MET_INDX));
            metrics_lists.put(MetricType.MET_IO, new MetricList (base_metrics, MetricType.MET_IO));
            metrics_lists.put(MetricType.MET_HEAP, new MetricList (base_metrics, MetricType.MET_HEAP));
//        TODO:
//
//            // set the defaults
//            if (settings->str_dmetrics == null)
//                settings->str_dmetrics = strdup (Command::DEFAULT_METRICS);
//            char *status = setMetrics (settings->str_dmetrics, true);
//            if (status != NULL)
//            {
//                fprintf (stderr, "XXX setMetrics(\"%s\") failed: %s\n", settings->str_dmetrics, status);
//                abort ();
//            }
//
//            // set the default sort
//            setSort (settings->str_dsort, MET_NORMAL, true);
        }
        return metrics_lists.get(metricType);
    }

    private void reset_metrics() {
        metrics_lists.clear();
        metrics_ref_lists.clear();
    }

    public void addIndexSpace(int subtype) {
//        PathTree is = new PathTree(this, subtype);
//        indxspaces.store (subtype, is);
//        indx_data.store (subtype, null);
//        sel_idxobj.store (subtype, null);
        settings.indxobj_define(subtype, false);
    }

    public LibExpand get_lo_expand(int idx) {
        if (idx < lo_expands.size())
            return lo_expands.get(idx);
        return LibExpand.LIBEX_SHOW;
    }

    public void set_lo_expand(int idx, LibExpand flag) {
        // LIBRARY_VISIBILITY
//        if (flag == LibExpand.LIBEX_HIDE) {
//            resetShowAll();
//            dbeSession.set_lib_visibility_used();
//        }
        // if no change
        if (idx < lo_expands.size() && flag == get_lo_expand(idx))
            return;
//        setShowHideChanged(); // this is necessary if called from er_print

        // change the flag
        while (idx >= lo_expands.size()) lo_expands.add(null);
        lo_expands.set(idx, flag);

        // and reset the data
//        fflush(stderr);
//        purge_events();
        reset_data(true);
    }

}
