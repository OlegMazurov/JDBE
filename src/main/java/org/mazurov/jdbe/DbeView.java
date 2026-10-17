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
import java.util.*;

import static org.mazurov.jdbe.Enums.*;

public class DbeView {

    public enum DbeView_status {
        DBEVIEW_SUCCESS,
        DBEVIEW_NO_DATA,
        DBEVIEW_IO_ERROR,
        DBEVIEW_BAD_DATA,
        DBEVIEW_BAD_SYMBOL_DATA,
        DBEVIEW_NO_SEL_OBJ
    }

    public static String status_str (DbeView_status status) {
        return switch (status) {
            case DBEVIEW_SUCCESS -> null;
            case DBEVIEW_NO_DATA -> "Data not available for this filter selection";
            case DBEVIEW_IO_ERROR -> "Unable to open file";
            case DBEVIEW_BAD_DATA -> "Data corrupted";
            case DBEVIEW_BAD_SYMBOL_DATA -> "Functions/Modules information corrupted";
            case DBEVIEW_NO_SEL_OBJ -> "No selected object, bring up Functions Tab";
        };
    }

    ArrayList<FilterSet> filters;
    int vindex; // index of this view -- set by Analyzer
    boolean func_scope;
    boolean filter_active; // true if filtering is active, i.e., some packets were dropped
    int phaseIdx;
    List<DataView[]> dataViews = new ArrayList<>();
    Settings settings;
    String cur_filter_str;
    Expression cur_filter_expr;

    private boolean showAll = true;
    private boolean showHideChanged;
    private boolean newViewMode;

    public boolean isShowAll() { return showAll; }
    public boolean isShowHideChanged() { return showHideChanged; }
    public boolean isNewViewMode() { return newViewMode; }
    public void resetShowHideChanged() { showHideChanged = false; }
    public void resetNewViewMode() { newViewMode = false; }

    public void resetAndConstructShowHideStacks() {
    }

    List<BaseMetric> reg_metrics = new ArrayList<>();   // list of registered metrics
    private final Map<MetricType, MetricList> metrics_lists = new HashMap<>();
    private final Map<MetricType, MetricList> metrics_ref_lists = new HashMap<>();

    private List<PathTree> indxspaces;
    private List<Hist_data> indx_data; // index object data
    private List<Histable> sel_idxobj; // selected index objects

    public Hist_data get_indx_data(int subtype) {
        return subtype < 0 || subtype >= indx_data.size() ? null : indx_data.get(subtype);
    }

    public void set_indx_data(int subtype, Hist_data data) {
        if (subtype >= 0 && subtype < indx_data.size())
            indx_data.set(subtype, data);
    }

    List<Integer> lobjectsNoJava = new ArrayList<>(); // List of indices into LoadObjects excluding java classes

    private List<LibExpand> lo_expands;

    public DbeView(Settings settings, int index) {
        init();

        filters = new ArrayList<>();
        lo_expands = new ArrayList<>();

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

    public int get_limit() {
        return settings.get_limit();
    }

    public String set_limit(int limit) {
        settings.set_limit(limit);
        return null;
    }

    // Controlling the print format mode
    public String set_printmode (String str) {
        return settings.set_printmode(str);
    };

    public PrintMode get_printmode() {
        return settings.print_mode;
    };

    public char get_printdelimiter () {
        return settings.print_delim;
    };

    public String get_printmode_str () {
        return settings.str_printmode;
    };

    // controlling the name format
    public Command.Cmd_status set_name_format (String str) {
        return settings.set_name_format(str);
    };

    public void set_name_format (int name_format, boolean soname) {
        settings.set_name_format(name_format, soname);
    };

    Histable.NameFormat get_name_format () {
        return settings.name_format;
    }

    public VMode get_view_mode() {
        return settings.get_view_mode();
    }

    public Command.Cmd_status set_view_mode(String str, boolean fromRC) {
        VMode old = settings.get_view_mode();
        Command.Cmd_status ret = settings.set_view_mode(str, fromRC);
        if (old != settings.get_view_mode())
            phaseIdx++;
        return ret;
    }

    public Command.Cmd_status set_en_desc(String str, boolean fromRC) {
        // Tell the session
        DbeSession.getInstance().get_settings().set_en_desc(str, fromRC);
        // and tell our settings
        return settings.set_en_desc(str, fromRC);
    }

    public Command.Cmd_status proc_compcom(String cmd, boolean isSrc, boolean rc) {
        throw new RuntimeException("DbeView.proc_compcom not implemented");
    }

    public Command.Cmd_status proc_thresh(String cmd, boolean isSrc, boolean rc) {
        throw new RuntimeException("DbeView.proc_thresh not implemented");
    }

    public boolean comparingExperiments() {
        return false;
    }

    public CmpMode get_compare_mode() {
        return settings.get_compare_mode();
    }

    public void set_compare_mode(CmpMode mode) {
        if (mode == get_compare_mode())
            return;
        settings.set_compare_mode(mode);
        if (comparingExperiments()) {
        }
    }

    FilterSet get_filter_set(int n) {
        // fflush (stderr); What?
        if (n >= filters.size())
            return null;
        return filters.get(n);
    }

    Filter.FilterNumeric get_FilterNumeric(int nexp, int idx) {
        FilterSet fs = get_filter_set(nexp);
        return fs != null ? fs.get_filter(idx) : null;
    }

    public String get_filter() {
        return cur_filter_str;
    }

    public String set_filter(String filter_spec) {
        if (java.util.Objects.equals(filter_spec, cur_filter_str))
            return null; // nothing changed
        if (filter_spec == null) {
            cur_filter_str = null;
            cur_filter_expr = null;
            purge_events();
            reset_data(false);
            return null;
        }
        Expression expr = DbeSession.getInstance().ql_parse(filter_spec);
        if (expr == null)
            return String.format("Invalid filter specification `%s'%n", filter_spec);
        cur_filter_str = filter_spec;
        cur_filter_expr = expr;
        purge_events();
        reset_data(false);
        return null;
    }

    boolean set_pattern(int m, String pattern) {
        throw new RuntimeException("DbeView.set_pattern not implemented");
//        boolean error = false;
//
//        // Store original setting in case of error
//        int nexps = DbeSession.getInstance().nexps();
//        int orig_phaseIdx = phaseIdx;
//        boolean[] orig_enable = new boolean[nexps];
//        String[] orig_pattern = new String[nexps];
//        for (int i = 0; i < nexps; i++) {
//            orig_pattern[i] = get_FilterNumeric(i, m).get_pattern();
//            orig_enable[i] = get_exp_enable(i);
//            set_exp_enable(i, false);
//        }
//
//        // Copy the pattern so that we could safely modify it
//        char *buf = dbe_strdup (pattern);
//        FilterNumeric fexp = NULL;
//        char *pb, *pe;
//        pb = pe = buf;
//        for (boolean done = false; !done; pe++) {
//            if (*pe == ':'){
//                // experiment filter;
//                *pe = '\0';
//                fexp = new FilterNumeric(null, null, null);
//                fexp.set_range(1, nexps, nexps);
//                fexp.set_pattern (pb, &error);
//                if (error)
//                    break;
//                pb = pe + 1;
//            } else if (*pe == '+' || *pe == '\0') {
//                // entity filter
//                if (*pe == '\0')
//                    done = true;
//                else
//                    *pe = '\0';
//                for (int i = 0; i < nexps; i++) {
//                    if (fexp == null || fexp.is_selected (i + 1)) {
//                        FilterNumeric f = get_FilterNumeric (i, m);
//                        f->set_pattern (pb, &error);
//                        if (error) {
//                            break;
//                        }
//                        set_exp_enable(i, true);
//                    }
//                }
//                if (error)
//                    break;
//                fexp = null;
//                pb = pe + 1;
//            }
//        }
//
//        if (error) {
//            for (int i = 0; i < nexps; i++) {
//                boolean err = false;
//                set_exp_enable(i, orig_enable[i]);
//                Filter.FilterNumeric f = get_FilterNumeric(i, m);
//                f.set_pattern(orig_pattern[i], err);
//            }
//            phaseIdx = orig_phaseIdx;
//        } else {
//            update_advanced_filter();
//            filter_active = true;
//        }
//        return !error;
    }

    public boolean get_exp_enable(int n) {
        return !filters.isEmpty() ? filters.get(n).get_enabled() : true;
    }

    public void add_experiment(int index, boolean enabled) {
        reset_data(true);
        Experiment exp = DbeSession.getInstance().get_exp(index);
        FilterSet filterset = new FilterSet(this, exp);
        filterset.set_enabled(enabled);
        while (filters.size() <= index)
            filters.add(null);
        filters.set(index, filterset);

        assert index == dataViews.size();
        DataView[] expDataViewList = new DataView[ProfData_type.DATA_LAST.getValue()];
        while (dataViews.size() <= index) {
            dataViews.add(null);
        }
        dataViews.set(index, expDataViewList);

        reset_metrics();
    }

    public void drop_experiment(int index) {
        phaseIdx++;
        filters.remove(index);

        // reset any cached data
        reset_data(true);

        dataViews.remove(index);
    }

    void set_exp_enable(int n, boolean e) {
        FilterSet fs = filters.get(n);
        if (fs.get_enabled() != e) {
            fs.set_enabled(e);
            purge_events(n);
            phaseIdx++;
        }
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
            settings.set_compare_mode(CmpMode.fromInt(cmp_mode));
            if (comparingExperiments()) {
//                add_compare_metrics(mlist);
            }
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

    public void reset_data (boolean all) {
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
        dataViews.clear();
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

    public MetricList get_metric_ref(MetricType mtype) {
        if (metrics_ref_lists.get(MetricType.MET_COMMON) == null) {
            List<BaseMetric> base_metrics = DbeSession.getInstance().get_base_reg_metrics();
            metrics_ref_lists.put(MetricType.MET_SRCDIS, new MetricList(base_metrics, MetricType.MET_SRCDIS));
            metrics_ref_lists.put(MetricType.MET_COMMON, new MetricList(base_metrics, MetricType.MET_COMMON));
            metrics_ref_lists.put(MetricType.MET_NORMAL, new MetricList(base_metrics, MetricType.MET_NORMAL));
            metrics_ref_lists.put(MetricType.MET_CALL, new MetricList(base_metrics, MetricType.MET_CALL));
            metrics_ref_lists.put(MetricType.MET_CALL_AGR, new MetricList(base_metrics, MetricType.MET_CALL_AGR));
            metrics_ref_lists.put(MetricType.MET_DATA, new MetricList(base_metrics, MetricType.MET_DATA));
            metrics_ref_lists.put(MetricType.MET_INDX, new MetricList(base_metrics, MetricType.MET_INDX));
            metrics_ref_lists.put(MetricType.MET_IO, new MetricList(base_metrics, MetricType.MET_IO));
            metrics_ref_lists.put(MetricType.MET_HEAP, new MetricList(base_metrics, MetricType.MET_HEAP));
        }
        return metrics_ref_lists.get(mtype);
    }

    public String getSort(MetricType mtype) {
        return get_metric_list(mtype).get_sort_name();
    }

    public String getSortCmd(MetricType mtype) {
        return get_metric_list(mtype).get_sort_cmd();
    }

    public String setMetrics(String mspec, boolean fromRcFile) {
        throw new RuntimeException("DbeView.setMetrics not implemented");
    }

    public String setSort(String sort_list, MetricType mtype, boolean fromRcFile) {
        throw new RuntimeException("DbeView.setSort not implemented");
    }

    public void setSort(int visindex, MetricType mtype, boolean reverse) {
        MetricList mlist = get_metric_list(mtype);
        List<Metric> items = mlist.get_items();
        if (visindex >= items.size())
            return;
        mlist.set_sort(visindex, reverse);
        resortData(mtype);
        if (mtype == MetricType.MET_NORMAL) {
            String nameNormal = items.get(visindex).get_username();
            MetricList mlistCc = get_metric_list(MetricType.MET_CALL);
            List<Metric> itemsCc = mlistCc.get_items();
            int idxCc = -1;
            for (int i = 0; i < itemsCc.size(); i++) {
                String nameCc = itemsCc.get(i).get_username();
                if (nameCc != null && nameNormal != null && nameNormal.startsWith(nameCc)) {
                    idxCc = i;
                    break;
                }
            }
            if (idxCc != -1) {
                mlistCc.set_sort(idxCc, reverse);
                resortData(MetricType.MET_CALL);
                Metric m = itemsCc.get(idxCc);
                MetricList cList = get_metric_list(MetricType.MET_CALL_AGR);
                Metric m1 = cList.find_metric(m.get_cmd(), m.get_subtype());
                if (m1 != null)
                    cList.set_sort_metric(m1.get_cmd(), m1.get_subtype(), reverse);
            }
        }
        if (mtype == MetricType.MET_CALL) {
            MetricList mlistNorm = get_metric_list(MetricType.MET_NORMAL);
            List<Metric> itemsNorm = mlistNorm.get_items();
            String nameCc = items.get(visindex).get_username();
            int idxNorm = -1;
            for (int i = 0; i < itemsNorm.size(); i++) {
                String nameNormal = itemsNorm.get(i).get_username();
                if (mlistNorm.get_sort_ref_index() == i && nameNormal != null && nameCc != null
                        && nameNormal.startsWith(nameCc)) {
                    idxNorm = i;
                    break;
                }
            }
            if (idxNorm == -1) {
                for (int i = 0; i < itemsNorm.size(); i++) {
                    String nameNormal = itemsNorm.get(i).get_username();
                    if (nameNormal != null && nameCc != null && nameNormal.startsWith(nameCc)) {
                        idxNorm = i;
                        break;
                    }
                }
            }
            if (idxNorm != -1) {
                mlistNorm.set_sort(idxNorm, reverse);
                resortData(MetricType.MET_NORMAL);
            }
            Metric m = items.get(visindex);
            MetricList cList = get_metric_list(MetricType.MET_CALL_AGR);
            Metric m1 = cList.find_metric(m.get_cmd(), m.get_subtype());
            if (m1 != null)
                cList.set_sort_metric(m1.get_cmd(), m1.get_subtype(), reverse);
        }
    }

    public void resortData(MetricType mtype) {
        if (mtype == MetricType.MET_NORMAL && func_data != null) {
            MetricList mlist = get_metric_list(mtype);
            func_data.resort(mlist);
        }
    }

    private void reset_metrics() {
        metrics_lists.clear();
        metrics_ref_lists.clear();
    }

    public void addIndexSpace(int subtype) {
        indxspaces.add(new PathTree(this, subtype));
        indx_data.add(null);
        sel_idxobj.add(null);
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
        purge_events();
        reset_data(true);
    }

    // returns true if any change
    public boolean set_libexpand(String liblist, LibExpand flag) {
        boolean changed = settings.set_libexpand(liblist, flag, false);
        return changed;
    }

    public boolean set_libdefaults() {
        boolean changed = settings.set_libdefaults();
        if (changed)
            update_lo_expands();
        return changed;
    }

    public void update_lo_expands() {
        for (LoadObject lo : DbeSession.getInstance().get_text_segments()) {
            LibExpand flag = settings.get_lo_setting(lo.get_pathname());
            set_lo_expand(lo.seg_idx, flag);
        }
    }

    void purge_events() {
        purge_events(-1);
    }

    void purge_events (int n) {
        phaseIdx++;
        int lst;
        if (n == -1)
            lst = filters.size();
        else
            lst = n > filters.size() ? filters.size() : n + 1;
        for (int i = n == -1 ? 0 : n; i < lst; i++) {
            DataView[] expDataViewList = dataViews.get(i);
            if (expDataViewList != null) {
                // clear out all the data_ids, but don't change the vector size
                Arrays.fill(expDataViewList, null);
            }
        }
        filter_active = false;
    }

    private PathTree mainPathTree;

    public PathTree get_path_tree() {
        if (mainPathTree == null)
            mainPathTree = new PathTree(this);
        return mainPathTree;
    }

    Hist_data func_data;
    String error_msg;
    String warning_msg;

    public Hist_data get_hist_data(MetricList mlist, Histable.Type type, int subtype, Hist_data.Mode mode) {
        return get_hist_data(mlist, type, subtype, mode, null);
    }

    public Hist_data get_hist_data(MetricList mlist, Histable.Type type, int subtype, Hist_data.Mode mode,
                                    List<Histable> hobjs) {
        if (type == Histable.Type.INDEXOBJ) {
            Hist_data data = indxspaces.get(subtype).compute_metrics(mlist, type, mode, hobjs);
            data.resort(mlist);
            return data;
        }
        if (type != Histable.Type.FUNCTION)
            throw new RuntimeException("DbeView.get_hist_data: only Histable.Type.FUNCTION/INDEXOBJ is implemented");
        Hist_data hist_data = get_path_tree().compute_metrics(mlist, type, mode, hobjs);
        hist_data.resort(mlist);
        return hist_data;
    }

    public DataView get_filtered_events (int idx, int data_id) {
        if (idx < 0 || idx >= dataViews.size())
            return null;
        DataView[] expDataViewList = dataViews.get(idx);
        if (expDataViewList == null) {
            return null;
        }

        DataView dview = expDataViewList[data_id];
        Experiment exp = DbeSession.getInstance().get_exp(idx);
        if (dview != null) {
            // if show-hide is on force a reconstruction of hide stacks
            // LIBRARY_VISIBILITY
//            if (!showAll && (showHideChanged || newViewMode)) {
//                DataDescriptor dDscr = exp.get_raw_events(data_id);
//                constructShowHideStack(dDscr, exp);
//            }
            return dview;
        }

        int orig_data_id = data_id;
        data_id = exp.base_data_id(data_id);
        if (orig_data_id != data_id) {
            // orig_data_id is a derived DataView.  Get the master DataView:
            dview = expDataViewList[data_id];
        }
        if (dview == null) {
//            Expression saved = cur_filter_expr;
//            if (!adjust_filter(exp))
//                return null;

            DataDescriptor dDscr = exp.get_raw_events(data_id);
//            if (!showAll && (showHideChanged || newViewMode)) {
//                constructShowHideStack(dDscr, exp);
//            }

            Emsg m = exp.fetch_warnings();
            if (m != null) {
                warning_msg = m.get_msg();
            }

            if (dDscr != null) {
                dview = dDscr.createView();
                if (cur_filter_expr != null)
                    dview.setFilter(cur_filter_expr, new Expression.Context(this, exp));
                if (dview.getSize() < dDscr.getSize()) {
                    filter_active = true;
                }
            }
            expDataViewList[data_id] = dview;
        }
        if (orig_data_id != data_id) {
            // create the derived DataView:
            dview = exp.create_derived_data_view(orig_data_id, dview);
            expDataViewList[orig_data_id] = dview;
        }
        return dview;
    }

    public DataView get_filtered_events (int idx, int data_id, int sortprops[], int sortprop_count) {
        DataView packets = get_filtered_events (idx, data_id);
        if (packets != null) {
            packets.sort(sortprops, sortprop_count);
        }
        return packets;
    }

    public void dump_profile(PrintStream out) {
        throw new RuntimeException("DbeView.dump_profile not implemented");
    }

    public void dump_sync(PrintStream out) {
        throw new RuntimeException("DbeView.dump_sync not implemented");
    }

    public void dump_iotrace(PrintStream out) {
        throw new RuntimeException("DbeView.dump_iotrace not implemented");
    }

    public void dump_hwc(PrintStream out) {
        throw new RuntimeException("DbeView.dump_hwc not implemented");
    }

    public void dump_heap(PrintStream out) {
        throw new RuntimeException("DbeView.dump_heap not implemented");
    }

    public void dump_gc_events(PrintStream out) {
        throw new RuntimeException("DbeView.dump_gc_events not implemented");
    }
}
