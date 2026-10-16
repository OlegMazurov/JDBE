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

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.mazurov.jdbe.Enums.*;

public class DBE {

    public static int dbeInitView(int id, int cloneId) {
        return DbeSession.getInstance().createView(id, cloneId);
    }

    public static String dbeSendSignal(int p, int signum) {
        throw new RuntimeException("DBE.dbeSendSignal not implemented");
    }

    public static String dbeSetExperimentsGroups(String[][] groups) {
//        int cmp_mode = dbeSession->get_settings ()->get_compare_mode ();
//        if (groups.length < 2) {
//            cmp_mode = CMP_DISABLE;
//        } else if (cmp_mode == CMP_DISABLE) {
//            cmp_mode = CMP_ENABLE;
//        }
//        for (int i = 0;; i++) {
//            DbeView dbev = DbeSession.getInstance().getView(i);
//            if (dbev == null) break;
//            dbev.get_settings()->set_compare_mode(cmp_mode);
//        }
        String error = DbeSession.getInstance().setExperimentsGroups(groups);

        // automatically load machine model if applicable
        dbeDetectLoadMachineModel(0);
        return error;
    }

    public static int[] dbeGetFounderExpId(int[] expIds) {
        int[] ret = new int[expIds.length];
        for (int i = 0; i < expIds.length; i++) {
            Experiment exp = DbeSession.getInstance().get_exp(expIds[i]);
            ret[i] = exp != null ? exp.getExpIdx() : -1;
        }
        return ret;
    }

    public static int[] dbeGetUserExpId(int[] expIds) {
        int[] ret = new int[expIds.length];
        for (int i = 0; i < expIds.length; i++) {
            Experiment exp = DbeSession.getInstance().get_exp(expIds[i]);
            ret[i] = exp != null ? exp.getUserExpId() : -1;
        }
        return ret;
    }

    public static int[] dbeGetExpGroupId(int[] expIds) {
        int[] ret = new int[expIds.length];
        for (int i = 0; i < expIds.length; i++) {
            Experiment exp = DbeSession.getInstance().get_exp(expIds[i]);
            ret[i] = exp != null ? 0 : -1;
        }
        return ret;
    }

    public static void dbeSetNameFormat(int index, int nameFormat, boolean soName) {
        DbeView dbev = DbeSession.getInstance().getView(index);
        dbev.set_name_format(nameFormat, soName);
    }

    public static String dbeGetFileAttributes(String fileName, String format) {
        if ("/bin/ls -dl ".equals(format)) {
            File file = new File(fileName);
            if (file.canRead()) {
                if (file.isDirectory()) {
                    return String.format("drwxrwxr-x %s%n", fileName);
                } else if (file.isFile()) {
                    return String.format("-rwxrwxr-x %s%n", fileName);
                }
            }
        }
        return "";
    }

    public static String dbeGetMachineModel() {
        return DbeSession.getInstance().get_mach_model();
    }

    public static boolean[] dbeGetExpEnable(int viewIindex) {
        DbeView dbev = DbeSession.getInstance().getView(viewIindex);
        int size = DbeSession.getInstance().nexps();
        if (dbev == null || size == 0)
            return null;

        // Get enabled experiment
        boolean[] enable = new boolean[size];
        for (int i = 0; i < size; ++i) {
            enable[i] = dbev.get_exp_enable(i) && !DbeSession.getInstance().get_exp(i).isBroken();
        }
        return enable;
    }

    public static String dbeGetFiles(String path, String format) {
        if (format == null) return "";

        StringBuilder sb = new StringBuilder();
        boolean format_aF = "/bin/ls -aF".equals(format);
        File[] list = new File(path).listFiles();
        if (list == null) return "";
        for (File entry : list) {
            sb.append (entry.getName());
            if (format_aF) {
                String attr = "@"; // Link
                if (entry.canRead()) { // Readable
                    if (entry.isDirectory()) {
                        attr = "/";
                    } else if (entry.isFile()) {// Regular file
                        attr = "";
                    }
                }
                sb.append(attr);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static boolean dbeGetViewModeEnable() {
        return true; //dbeSession->has_ompavail () || dbeSession->has_java ();
    }

    private static final int PROP_NONE = 0;
    private static final int PROP_THRID = 4;
    private static final int PROP_LWPID = 5;
    private static final int PROP_CPUID = 6;
    private static final int PROP_EXPID = 7;

    public static Object[] dbeGetEntityProps(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException();
        }
        ArrayList<Integer> prop_id = new ArrayList<>();
        ArrayList<String> prop_name = new ArrayList<>();
        ArrayList<String> prop_uname = new ArrayList<>();
        ArrayList<String> prop_cname = new ArrayList<>(); 

        prop_id.add(PROP_NONE);
        prop_name.add("NONE");
        prop_uname.add("Unknown");
        prop_cname.add("unknown");

        prop_id.add(PROP_LWPID);
        prop_name.add("LWPID");
        prop_uname.add("LWP");
        prop_cname.add("lwp");

        prop_id.add(PROP_THRID);
        prop_name.add("THRID");
        prop_uname.add("Thread");
        prop_cname.add("thread");

        prop_id.add(PROP_CPUID);
        prop_name.add("CPUID");
        prop_uname.add("CPU");
        prop_cname.add("cpu");

        prop_id.add(PROP_EXPID);
        prop_name.add("EXPID");
        prop_uname.add("Process"); 
        prop_cname.add("experiment");
        Object[] darray = new Object[4];
        darray[0] = prop_id.stream().mapToInt(i -> i).toArray();
        darray[1] = prop_name.toArray(new String[0]);
        darray[2] = prop_uname.toArray(new String[0]);
        darray[3] = prop_cname.toArray(new String[0]);
        return darray;
    }

    public static Object[] dbeGetCurMetricsV2(int dbevindex, int mtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        MetricList mlist = dbev.get_metric_list(MetricType.valueOf(mtype));
        ERIPC.ipc_log("dbeGetCurMetricsV2[%s] %d%n", MetricType.valueOf(mtype).toString(), mlist.get_items().size());
        return dbeGetMetricList(mlist);
    }

    public static Object[] dbeGetMetricList (MetricList mlist) {
        List<Metric> items = mlist.get_items();
        int size = items.size();

        int[] type = new int[size];
        int[] subtype = new int[size];
        int[] clock = new int[size];
        int[] flavors = new int[size];
        int[] vis = new int[size];
        boolean[] sorted = new boolean[size];
        int[] value_styles = new int[size];
        String[] aux = new String[size];
        String[] name = new String[size];
        String[] abbr = new String[size];
        String[] comd = new String[size];
        String[] unit = new String[size];
        String[] user_name = new String[size];
        String[] expr_spec = new String[size];
        String[] legend = new String[size];
        int[] valtype = new int[size];
        String[] data_type_name = new String[size];
        String[] data_type_uname = new String[size];
        String[] short_desc = new String[size];

        int sort_index = mlist.get_sort_ref_index();
        // Fill metric elements
        int idx = 0;
        for (Metric m : items) {
            type[idx] = m.get_type().value;
            subtype[idx] = m.get_subtype();
            flavors[idx] = m.get_flavors();
            abbr[idx] = m.get_abbr();
            String s = m.get_abbr_unit();
            if ((m.get_visbits() & VAL_RATIO) != 0) {
                s = null;
            }
            unit[idx] = s != null ? s : "";
            value_styles[idx] = m.get_value_styles();
            vis[idx] = m.get_visbits();
            sorted[idx] = (idx == sort_index);
//            clock->append (m->get_type () == Metric::HWCNTR ? dbeSession->get_clock(-1)
//                    : m->get_clock_unit ());
            aux[idx] = m.get_aux();
            name[idx] = m.get_name();
            comd[idx] = m.get_cmd();
            user_name[idx] = m.get_username();
            expr_spec[idx] = m.get_expr_spec();
//            legend->append (dbe_strdup (m->legend));
            valtype[idx] = m.get_vtype().ordinal() + 1;
//
            String _data_type_name = null;
            String _data_type_uname = null;
            ProfData_type data_type = m.get_packet_type();
            if (data_type != null
                    && data_type.getValue() >= 0
                    && data_type.getValue() < ProfData_type.DATA_LAST.getValue()) {
                _data_type_name =  get_prof_data_type_name(data_type);
                _data_type_uname = get_prof_data_type_uname(data_type);
            }
            data_type_name[idx] = _data_type_name;
            data_type_uname[idx] = _data_type_uname;
//
//            char* _short_desc = NULL;
//            if (m->get_type () == Metric::HWCNTR)
//            {
//                Hwcentry * hwctr = m->get_hw_ctr ();
//                if (hwctr)
//                    _short_desc = dbe_strdup (hwctr->short_desc);
//            }
//            short_desc->append (_short_desc);
            ++idx;
        }

        // Set Java array
        Object[] data = new Object[19];
        idx = 0;
        data[idx++] = type;
        data[idx++] = subtype;
        data[idx++] = clock;
        data[idx++] = flavors;
        data[idx++] = value_styles;
        data[idx++] = user_name;
        data[idx++] = expr_spec;
        data[idx++] = aux;
        data[idx++] = name;
        data[idx++] = abbr;
        data[idx++] = comd;
        data[idx++] = unit;
        data[idx++] = vis;
        data[idx++] = sorted;
        data[idx++] = legend;
        data[idx++] = valtype;
        data[idx++] = data_type_name;
        data[idx++] = data_type_uname;
        data[idx] = short_desc;
        return data;
    }

    public static Object[] dbeGetRefMetricsV2() {
        MetricList mlist = new MetricList(MetricType.MET_NORMAL);
        List<BaseMetric> base_metrics = DbeSession.getInstance().get_base_reg_metrics();
        for (BaseMetric bm : base_metrics) {
            if (bm.hasFlavor(BaseMetric.EXCLUSIVE)) {
                Metric m = new Metric(bm, BaseMetric.EXCLUSIVE);
                m.enable_all_visbits();
                mlist.append(m);
            } else if (bm.hasFlavor(BaseMetric.STATIC)) {
                Metric m = new Metric(bm, BaseMetric.EXCLUSIVE);
                m.enable_all_visbits();
                mlist.append(m);
            }
        }
        return dbeGetMetricList(mlist);
    }

    public static Object[][] dbeGetRefMetricTree(int dbevindex, boolean include_unregistered) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        MetricList mlist = dbev.get_metric_list(MetricType.MET_NORMAL);
        boolean has_clock_profiling_data = false;
        for (Metric m : mlist.get_items()) {
            if (m.get_packet_type() == ProfData_type.DATA_CLOCK) {
                has_clock_profiling_data = true;
                break;
            }
        }
        BaseMetricTreeNode curr = DbeSession.getInstance().get_reg_metrics_tree();
        return dbeGetMetricTreeNode(curr, mlist, include_unregistered, has_clock_profiling_data);
    }

    public static Object[] dbeGetRefMetricTreeValues(int dbevindex, String[] metricCmds, String[] nonMetricCmds) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        int numMetrics = metricCmds != null ? metricCmds.length : 0;
        int numNonMetrics = nonMetricCmds != null ? nonMetricCmds.length : 0;
        int totalColumns = numMetrics + numNonMetrics;
        Object[] valueTable = new Object[totalColumns];
        Object[] highlightTable = new Object[totalColumns];
        for (int i = 0; i < totalColumns; i++) {
            valueTable[i] = new double[] { 0.0 };
            highlightTable[i] = new boolean[] { false };
        }
        if (DbeSession.getInstance().nexps() == 0 || numMetrics == 0)
            return new Object[] { valueTable, highlightTable };

        BaseMetricTreeNode root = DbeSession.getInstance().get_reg_metrics_tree();
        List<BaseMetric> baseMetrics = new ArrayList<>();
        List<Integer> columnOf = new ArrayList<>(); 
        for (int i = 0; i < numMetrics; i++) {
            BaseMetricTreeNode node = root.find(metricCmds[i]);
            BaseMetric bm = node != null ? node.get_BaseMetric() : null;
            if (bm == null)
                continue; 
            baseMetrics.add(bm);
            columnOf.add(i);
        }
        MetricList bmlist = new MetricList(baseMetrics, MetricType.MET_SRCDIS);
        Hist_data data = dbev.get_hist_data(bmlist, Histable.Type.FUNCTION, 0, Hist_data.Mode.ALL);
        int bestCpuTimeIndx = -1;
        List<Metric> bmitems = bmlist.get_items();
        for (int k = 0; k < bmitems.size(); k++) {
            int col = columnOf.get(k);
            Metric mitem = bmitems.get(k);
            DbeStructs.TValue v = data.get_value_for_total(k);
            double val = switch (mitem.get_vtype()) {
                case VT_ULLONG, VT_LLONG -> (double) v.l;
                default -> v.d;
            };
            valueTable[col] = new double[] { val };
            if (mitem.get_type() == BaseMetric.Type.CP_TOTAL_CPU)
                bestCpuTimeIndx = col;
        }
        if (bestCpuTimeIndx >= 0)
            highlightTable[bestCpuTimeIndx] = new boolean[] { true };
        return new Object[] { valueTable, highlightTable };
    }

    private static int convertVisbitsToGuiCheckboxBits(BaseMetric bm, int visbits) {
        int valuebits = visbits;
        int value_styles = bm.get_value_styles();
        if ((value_styles & VAL_TIMEVAL) != 0 && (value_styles & VAL_VALUE) == 0) {
            int mask = ~(VAL_VALUE | VAL_TIMEVAL);
            valuebits = valuebits & mask;
            if ((visbits & VAL_VALUE) != 0)
                valuebits |= VAL_TIMEVAL;
            if ((visbits & VAL_TIMEVAL) != 0)
                valuebits |= VAL_TIMEVAL;
        }
        return valuebits;
    }

    public static Object[][] dbeGetMetricTreeNode(BaseMetricTreeNode curr, MetricList mlist,
                          boolean include_unregistered, boolean has_clock_profiling_data) {
        String _name;
        String _username;
        String _description = curr.get_description();

        // BaseMetric fields
        int _flavors = 0; // SubType bitmask: (e.g. EXCLUSIVE)
        int _vtype = 0; // ValueTag: e.g. VT_INT, VT_FLOAT, ...
        int _vstyles_capable = 0; // ValueType bitmask, e.g. VAL_TIMEVAL
        int _vstyles_e_default_values = 0; // default visibility settings, exclusive/static
        int _vstyles_i_default_values = 0; // default visibility settings, inclusive
        boolean _registered = curr.is_registered()
                || curr.get_num_registered_descendents() > 0;
        boolean _aggregation = curr.is_composite_metric()
                && curr.get_num_registered_descendents() > 0;
        boolean _has_value = false; 
        String _unit = null;
        String _unit_uname = null;

        BaseMetric bm = curr.get_BaseMetric();
        if (bm != null) {
            _name = bm.get_cmd();
            _username = bm.get_username();
            if (!include_unregistered && !curr.is_registered()) {
                throw new IllegalStateException();
            }
            _flavors = bm.get_flavors();
            _vtype = bm.get_vtype().ordinal() + 1;
            _vstyles_capable = bm.get_value_styles();
            int defaultStyles = (bm.hasFlavor(BaseMetric.EXCLUSIVE) || bm.hasFlavor(BaseMetric.INCLUSIVE))
                    ? bm.get_value_styles() & ~VAL_PERCENT : 0;
            _vstyles_e_default_values = convertVisbitsToGuiCheckboxBits(bm, defaultStyles);
            _vstyles_i_default_values = convertVisbitsToGuiCheckboxBits(bm, defaultStyles);
        } else {
            // not a base metric
            _name = curr.get_name();
            _username = curr.get_user_name();
            if (curr.get_unit() != null) { // represents a value
                _has_value = true;
                _unit = curr.get_unit();
                _unit_uname = curr.get_unit_uname();
            }
        }

        List<Object> fields = new ArrayList<>();
        fields.add(new String[] {_name}); // unique id string (dmetrics cmd)
        fields.add(new String[] {_username}); // user-visible name
        fields.add(new String[] {_description});
        fields.add(new int[] {_flavors}); // SubType bitmask: (e.g. EXCLUSIVE)
        fields.add(new int[] {_vtype}); // ValueTag: e.g. VT_INT, VT_FLOAT, ...
        fields.add(new int[] {_vstyles_capable}); // ValueType bitmask, e.g. VAL_TIMEVAL
        fields.add(new int[] {_vstyles_e_default_values});
        fields.add(new int[] {_vstyles_i_default_values});
        fields.add(new boolean[] {_registered}); // is a "live" metric
        fields.add(new boolean[] {_aggregation}); // value derived from children nodes
        fields.add(new boolean[] {_has_value}); // value generated from other source
        fields.add(new String[] {_unit}); // See BaseMetric.h, e.g. UNIT_SECONDS
        fields.add(new String[] {_unit_uname}); //See BaseMetric.h,

        // ----- children
        List<Object> children_list = new ArrayList<>();
        for (BaseMetricTreeNode child_node : curr.get_children()) {
            if (include_unregistered /* fetch everything */
                    || child_node.is_registered()
                    || child_node.get_num_registered_descendents() > 0) {
                if (!include_unregistered && /* not fetching everything */
                        !child_node.is_registered () &&
                        (child_node.get_BaseMetric () != null ||
                        child_node.is_composite_metric ())) {
                    List<BaseMetricTreeNode> registered_descendents = new ArrayList<>();
                    child_node.get_nearest_registered_descendents(registered_descendents);
                    for (BaseMetricTreeNode desc_node : registered_descendents) {
                        Object[] desc_data = dbeGetMetricTreeNode(desc_node, mlist,
                                include_unregistered, has_clock_profiling_data);
                        children_list.add(desc_data);
                    }
                } else {
                    Object[] child_data = dbeGetMetricTreeNode(child_node, mlist,
                            include_unregistered, has_clock_profiling_data);
                    children_list.add(child_data);
                }
            }
        }

        return new Object[][] {
            fields.toArray(new Object[0]),
            children_list.toArray(new Object[0])
        };
    }

    public static MetricList dbeGetMetricListV2(int dbevindex, int mtype,
                        int[] type, int[] subtype, boolean[] sort,
                        int[] vis, String[] cmd, String[] expr_spec, String[] legends)
    {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        MetricList mlist = new MetricList(MetricType.valueOf(mtype));
        for (int i = 0, msize = type.length; i < msize; i++) {
            BaseMetric bm = dbev.register_metric_expr(
                    BaseMetric.Type.valueOf(type[i]), cmd[i], expr_spec[i]);
            Metric m = new Metric(bm, subtype[i]);
            m.set_raw_visbits(vis[i]);
            if (m.legend == null) {
                m.legend = legends[i];
            }
            mlist.append(m);
            if (sort[i]) {
                mlist.set_sort_ref_index(i);
            }
        }
        return mlist;
    }

    public static boolean dbeGetJavaEnable() {
        return DbeSession.getInstance().has_java();
    }

    public static String[] dbeListMachineModels() {
        return DbeSession.getInstance().list_mach_models();
    }

    public static void dbeDetectLoadMachineModel (int dbevindex) {
    }

    public static String dbeSetPrintLimit(int dbevindex, int limit) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException("dbeSetPrintLimit");
        }
        return dbev.set_limit(limit);
    }

    public static int dbeGetPrintLimit(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException("dbeSetPrintLimit");
        }
        return dbev.get_limit();
    }

    // set printmode for data
    public static String dbeSetPrintMode(int dbevindex, String pmode) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException("dbeSetPrintMode");
        }
        return dbev.set_printmode(pmode);
    }

    // get printmode for data
    public static int dbeGetPrintMode(int dbevindex)
    {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException("dbeGetPrintMode");
        }
        return dbev.get_printmode().ordinal();
    }

    // get printmode for data
    public static String dbeGetPrintModeString(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (dbev == null) {
            throw new IllegalArgumentException("dbeGetPrintModeString");
        }
        return dbev.get_printmode_str();
    }

    public static Object[] dbeGetTabListInfo(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);

        // make sure the tabs are initialized properly
        dbev.get_settings().proc_tabs(DbeApplication.getInstance().rdtMode);
        dbev.get_settings().updateTabAvailability();
        DispTab[] tabs = dbev.get_TabList();

        // Get number of available tabs
        int size = 0;
        for (DispTab dsptab : tabs) {
            if (!dsptab.available)
                continue;
            size++;
        }
        int[] typelist = new int[size];
        String[] cmdlist = new String[size];
        int[] ordlist = new int[size];

        // Build list of avaliable tabs
        int i = 0;
        for (DispTab dsptab : tabs) {
            if (!dsptab.available)
                continue;
            typelist[i] = dsptab.type.value;
            cmdlist[i] = Command.get_cmd_str(dsptab.cmdtoken);
            ordlist[i] = dsptab.order;
            i++;
        }
        Object[] data = new Object[] {typelist, cmdlist, ordlist};
        return data;
    }

    // Return visibility state for all available tabs
    public static boolean[] dbeGetTabSelectionState(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        DispTab[] tabs = dbev.get_TabList();

        // Get number of available tabs
        int size = 0;
        for (DispTab dsptab : tabs) {
            if (!dsptab.available)
                continue;
            size++;
        }
        boolean[] states = new boolean[size];

        // Get visibility bit for all available tabs
        int i = 0;
        for (DispTab dsptab : tabs) {
            if (!dsptab.available)
                continue;
            states[i++] = dsptab.visible;
        }
        return states;
    }

    public static Object[] dbeGetIndxObjDescriptions(int arg1) {
        return DbeSession.getInstance().getIndxObjDescriptions();
    }

    // Return visibility state for all available index tabs
    public static boolean[] dbeGetIndxTabSelectionState(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        List<Boolean> indxtabs = dbev.get_IndxTabState();

        // Get visibility bit for all available tabs
        boolean[] states = new boolean[indxtabs.size()];
        for (int i = 0; i < states.length; ++i) {
            states[i] = indxtabs.get(i);
        }
        return states;
    }

    public static Object[] dbeGetMemObjects(int dbevindex) {
        return new Object[] {
                new int[0], new String[0], new char[0], new String[0],
                new String[0], new int[0], new String[0], new String[0]
        };
    }

    // Return visibility state for all available MemObj tabs
    public static  boolean[] dbeGetMemTabSelectionState(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        List<Boolean> memtabs = dbev.get_MemTabState();


        // Get visibility bit for all available tabs
        boolean[] states = new boolean[memtabs.size()];
        for (int i = 0; i < states.length; ++i) {
            states[i] = memtabs.get(i);
        }
        return states;
    }

    public static Object[] dbeGetLoadObjectList(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        List<LoadObject> lobjs = DbeSession.getInstance().get_text_segments();
        int size = lobjs.size();

        // Initialize Java boolean array
        String[] names = new String[size];
        int[] states = new int[size];
        int[] indices = new int[size];
        String[] paths = new String[size];
        int[] isJava = new int[size];


        dbev.lobjectsNoJava.clear();

        int new_index = 0;
        int index = 0;
        for (LoadObject lo : lobjs) {
            // Set 0, 1, or 2 for show/hide/api
            LibExpand expand = dbev.get_lo_expand(lo.seg_idx);

            String lo_name = lo.get_name();
            if (lo_name != null && lo_name.endsWith(".class>")) {
                isJava[new_index] = 1;
            } else {
                isJava[new_index] = 0;
            }
            dbev.lobjectsNoJava.add(index);

            names[new_index] = lo_name;
            states[new_index] = expand.value;
            indices[new_index] = lo.seg_idx;
            paths[new_index] = lo.get_pathname();
            new_index++;
            index++;
        }
        return new Object[] {
            names, states, indices, paths, isJava
        };
    }

    public static String[] dbeGetExpsProperty(String propName) {
        int nexps = DbeSession.getInstance().nexps();
        if (propName == null || nexps == 0)
            return null;
        String[] list = new String[nexps];
        StringBuilder sb = new StringBuilder();
        boolean empty = true;
        int prop = 99;
        if ("ERRORS".equalsIgnoreCase(propName)) {
            prop = 1;
        } else if ("WARNINGS".equalsIgnoreCase(propName)) {
            prop = 2;
        }
        if (prop < 3) {
            for (int i = 0; i < nexps; i++) {
                Experiment exp = DbeSession.getInstance().get_exp(i);
                String nm = exp.get_expt_name();
                sb.setLength(0);
                for (Emsg emsg = (prop == 1) ? exp.fetch_errors() : exp.fetch_warnings();
                     emsg != null; emsg = emsg.next) {
                    sb.append(String.format("%s: %s%n", nm, emsg.get_msg()));
                }
                String s = null;
                if (sb.length() > 0) {
                    s = sb.toString();
                    empty = false;
                }
                list[i] = s;
            }
        }
        if (empty) {
            list = null;
        }
        return list;
    }

    public static String[] dbeGetExpPreview(int dbevindex, String exp_name) {
        PreviewExp preview = new PreviewExp();
        preview.experiment_open(exp_name);
        preview.open_epilogue();

        // Initialize Java String array
        List<String> info = preview.preview_info();
        int size = info.size();
        String[] list = new String[size];

        // Get experiment names
        for (int i = 0; i < size; i++) {
            String str = info.get(i);
            if (str == null)
                str = "N/A";
            list[i] = str;
        }
        return list;
    }


    /*------------------------------------------------------------------*/

    public static String[] dbeGetInitMessages() {
        throw new RuntimeException("dbeGetInitMessages not implemented");
    }

    public static String dbeGetExpParams(int i, String arg1) {
        throw new RuntimeException("dbeGetExpParams not implemented");
    }

    public static String dbeOpenExperimentList(int dbevindex, String[][] groups, boolean sessionRestart) {
        return DbeSession.getInstance().setExperimentsGroups(groups);
    }

    public static String dbeDropExperiment(int arg1, int[] arg2) {
        throw new RuntimeException("dbeDropExperiment not implemented");
    }

    public static String[] dbeGetExpName(int dbevindex) {
        DbeSession session = DbeSession.getInstance();
        int size = session.nexps();
        if (size == 0) {
            return null;
        }
        String[] list = new String[size];
        for (int i = 0; i < size; i++) {
            Experiment exp = session.get_exp(i);
            String utargname = exp.getUtargname();
            list[i] = String.format("%s [%s]", exp.get_expt_name(),
                    utargname != null ? utargname : "(unknown)");
        }
        return list;
    }

    public static int[] dbeGetExpState(int dbevindex) {
        final int EXP_SUCCESS = 0;
        final int EXP_FAILURE = 1;
        final int EXP_INCOMPLETE = 2;
        final int EXP_BROKEN = 4;
        final int EXP_OBSOLETE = 8;

        DbeSession session = DbeSession.getInstance();
        int size = session.nexps();
        if (size == 0) {
            return null;
        }
        int[] state = new int[size];
        for (int i = 0; i < size; i++) {
            Experiment exp = session.get_exp(i);
            int set = EXP_SUCCESS;
            if (exp.get_status() == Experiment.Exp_status.FAILURE) {
                set |= EXP_FAILURE;
            }
            if (exp.get_status() == Experiment.Exp_status.INCOMPLETE) {
                set |= EXP_INCOMPLETE;
            }
            if (exp.isBroken()) {
                set |= EXP_BROKEN;
            }
            if (exp.isObsolete()) {
                set |= EXP_OBSOLETE;
            }
            state[i] = set;
        }
        return state;
    }

    public static boolean dbeSetExpEnable(int arg1, boolean arg2) {
        throw new RuntimeException("dbeSetExpEnable not implemented");
    }

    public static String[] dbeGetExpInfo(int dbevindex) {
        DbeSession session = DbeSession.getInstance();
        if (session.getView(dbevindex) == null) {
            throw new RuntimeException("dbeGetExpInfo: no such view " + dbevindex);
        }
        int size = session.nexps();
        if (size == 0) {
            return null;
        }

        String[] list = new String[size * 2 + 1];
        list[0] = Print.pr_load_objects(session.get_text_segments(), "");
        int k = 1;
        for (int i = 0; i < size; i++) {
            Experiment exp = session.get_exp(i);
            list[k++] = Emsg.pr_mesgs(exp.fetch_notes(), "", "");
            list[k++] = Emsg.pr_mesgs(exp.fetch_errors(), "No errors\n", "")
                    + Emsg.pr_mesgs(exp.fetch_warnings(), "No warnings\n", "")
                    + Emsg.pr_mesgs(exp.fetch_comments(), "", "")
                    + Emsg.pr_mesgs(exp.fetch_pprocq(), "", "");
        }
        return list;
    }

    public static int dbeUpdateNotes(int arg1, int arg2, int arg3, String arg4, boolean arg5) {
        throw new RuntimeException("dbeUpdateNotes not implemented");
    }

    public static String[] dbeGetLoadObjectName(int arg1) {
        throw new RuntimeException("dbeGetLoadObjectName not implemented");
    }

    public static String[] dbeGetSearchPath(int dbevindex) {
        return DbeSession.getInstance().get_search_path().toArray(new String[0]);
    }

    public static void dbeSetSearchPath(int dbevindex, String[] path) {
        DbeSession.getInstance().set_search_path(Arrays.asList(path), true);
    }

    public static Object[] dbeGetPathmaps(int dbevindex) {
        List<DbeStructs.PathMap> path = DbeSession.getInstance().get_pathmaps();
        String[] oldlist = new String[path.size()];
        String[] newlist = new String[path.size()];
        for (int i = 0; i < path.size(); i++) {
            oldlist[i] = path.get(i).old_prefix();
            newlist[i] = path.get(i).new_prefix();
        }
        return new Object[] { oldlist, newlist };
    }

    public static String dbeSetPathmaps(String[] from, String[] to) {
        if (from == null || to == null || from.length != to.length)
            return "dbeSetPathmaps: size of 'from' does not match for size of 'to'\n";
        List<DbeStructs.PathMap> newPath = new ArrayList<>();
        for (int i = 0; i < from.length; i++) {
            String err = Settings.add_pathmap(newPath, from[i], to[i]);
            if (err != null)
                return err;
        }
        List<DbeStructs.PathMap> path = DbeSession.getInstance().get_pathmaps();
        path.clear();
        path.addAll(newPath);
        return null;
    }

    public static String dbeAddPathmap(int dbevindex, String from, String to) {
        return Settings.add_pathmap(DbeSession.getInstance().get_pathmaps(), from, to);
    }

    public static String[] dbeGetOverviewText(int dbevindex) {
        List<String> info = new ArrayList<>();
        if (DbeSession.getInstance().nexps() == 0)
            return info.toArray(new String[0]);
        Experiment exp = DbeSession.getInstance().get_exp(0);
        info.add("Experiment      :" + exp.get_expt_name());
        if (exp.uarglist != null && !exp.uarglist.isEmpty())
            info.add(String.format("  Target        : '%s'", exp.uarglist));
        if (exp.hostname != null && !exp.hostname.isEmpty())
            info.add(String.format("  Host          : %s (%s, %s)", exp.hostname,
                    exp.architecture != null ? exp.architecture : "<CPU architecture not recorded>",
                    exp.os_version != null ? exp.os_version : "<OS version not recorded>"));
        String startStr = "";
        if (exp.start_sec != 0) {
            java.time.ZonedDateTime zdt = java.time.Instant.ofEpochSecond(exp.start_sec).atZone(java.time.ZoneOffset.UTC);
            startStr = zdt.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", java.util.Locale.US));
        }
        double seconds = (exp.last_event - exp.exp_start_time) * 1.e-9;
        info.add(String.format("  Start Time    : %s  Duration      : %.3f Seconds", startStr, seconds));
        info.add("");
        return info.toArray(new String[0]);
    }

    public static String dbeGetMsg(int dbevindex, int type) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (type == 1)
            return dbev.error_msg;
        if (type == 2)
            return dbev.warning_msg;
        return null;
    }

    public static int dbeSetFuncData(int dbevindex, long sel_obj, int type, int subtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        dbev.error_msg = null;
        dbev.warning_msg = null;
        if (type == FuncListDisp_type.DSP_CALLER.value || type == FuncListDisp_type.DSP_CALLEE.value
                || type == FuncListDisp_type.DSP_SELF.value) {
            dbeSetSelObjV2(dbevindex, sel_obj);
            return -1;
        }
        if (type != FuncListDisp_type.DSP_FUNCTION.value) {
            dbev.warning_msg = "DSP type not yet supported by this port";
            return -1;
        }
        MetricList mlist = dbev.get_metric_list(MetricType.MET_NORMAL);
        Hist_data data = dbev.get_hist_data(mlist, Histable.Type.FUNCTION, subtype, Hist_data.Mode.ALL);
        dbev.func_data = data;
        return -1;
    }

    public static Object[] dbeSetFuncDataV2(int dbevindex, long sel_obj, int type, int subtype) {
        long[] longs = new long[2];
        String[] strings = new String[2];
        longs[0] = dbeSetFuncData(dbevindex, sel_obj, type, subtype);
        strings[0] = dbeGetMsg(dbevindex, 1);
        return new Object[] { longs, strings };
    }

    public static void dbeDeleteView(int arg1) {
        throw new RuntimeException("dbeDeleteView not implemented");
    }

    public static void dbeSetCompareModeV2(int dbevindex, int cmp_mode) {
        throw new RuntimeException("dbeSetCompareModeV2 not implemented");
    }

    public static void dbeSetCurMetricsV2(
            int dbevindex, int cmp_mode, int mtype, int[] type, int[] subtype,
            boolean[] sort, int[] vis, String[] aux, String[] expr_spec, String[] legends) {
        throw new RuntimeException("dbeSetCurMetricsV2 not implemented");
    }

    public static void dbeSetSort(int dbevindex, int sort_index, int mtype, boolean reverse) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        dbev.setSort(sort_index, MetricType.valueOf(mtype), reverse);
    }
    public static int[] dbeGetAnoValue(int arg1) {
        throw new RuntimeException("dbeGetAnoValue not implemented");
    }

    public static void dbeSetAnoValue(int arg1, int[] arg2) {
        throw new RuntimeException("dbeSetAnoValue not implemented");
    }

    public static int dbeGetNameFormat(int arg1) {
        throw new RuntimeException("dbeGetNameFormat not implemented");
    }

    public static boolean dbeGetSoName(int arg1) {
        throw new RuntimeException("dbeGetSoName not implemented");
    }

    public static int dbeGetViewMode(int arg1) {
        throw new RuntimeException("dbeGetViewMode not implemented");
    }

    public static void dbeSetViewMode(int arg1, int arg2) {
        throw new RuntimeException("dbeSetViewMode not implemented");
    }

    public static int[] dbeGetTLValue(int arg1) {
        throw new RuntimeException("dbeGetTLValue not implemented");
    }

    public static void dbeSetTLValue(int arg1, int[] arg2) {
        throw new RuntimeException("dbeSetTLValue not implemented");
    }

    public static Object[] dbeGetExpSelection(int arg1) {
        throw new RuntimeException("dbeGetExpSelection not implemented");
    }

    public static String dbeSetFilterStr(int arg1, String arg2) {
        throw new RuntimeException("dbeSetFilterStr not implemented");
    }

    public static String dbeGetFilterStr(int dbevindex) {
        return DbeSession.getInstance().getView(dbevindex).get_filter();
    }

    public static Object[] dbeGetFilters(int arg1, int arg2) {
        throw new RuntimeException("dbeGetFilters not implemented");
    }

    public static boolean dbeUpdateFilters(int arg1, boolean[] arg2, String[] arg3) {
        throw new RuntimeException("dbeUpdateFilters not implemented");
    }

    public static int[] dbeGetLoadObjectState(int arg1) {
        throw new RuntimeException("dbeGetLoadObjectState not implemented");
    }

    public static void dbeSetLoadObjectState(int arg1, int[] arg2) {
        throw new RuntimeException("dbeSetLoadObjectState not implemented");
    }

    public static void dbeSetLoadObjectDefaults(int arg1) {
        throw new RuntimeException("dbeSetLoadObjectDefaults not implemented");
    }

    public static void dbeSetIndxTabSelectionState(int arg1, boolean[] arg2) {
        throw new RuntimeException("dbeGetIndxTabSelectionState not implemented");
    }

    public static void dbeSetTabSelectionState(int arg1, boolean[] arg2) {
        throw new RuntimeException("dbeSetTabSelectionState not implemented");
    }

    public static Object[] dbeGetCustomIndxObjects(int arg1) {
        throw new RuntimeException("dbeGetCustomIndxObjects not implemented");
    }

    public static String dbeDefineIndxObj(String name, String index_expr, String sdesc, String ldesc) {
        throw new RuntimeException("dbeDefineIndxObj not implemented");
    }

    private static final Map<Integer, Long> selObjV2 = new HashMap<>();

    public static void dbeSetSelObj(int dbevindex, long id, int type, int subtype) {
        if (type == FuncListDisp_type.DSP_FUNCTION.value) {
            DbeView dbev = DbeSession.getInstance().getView(dbevindex);
            Hist_data data = dbev.func_data;
            int selInd = (int) id;
            if (data != null && data.get_status() == Hist_data.Hist_status.SUCCESS
                    && selInd >= 0 && selInd < data.size()) {
                Histable obj = data.fetch(selInd).obj;
                if (obj != null)
                    dbeSetSelObjV2(dbevindex, obj.id);
            }
        } else if (type == FuncListDisp_type.DSP_TIMELINE.value) {
            dbeSetSelObjV2(dbevindex, id);
        }
    }

    public static void dbeSetSelObjV2(int dbevindex, long id) {
        selObjV2.put(dbevindex, id);
    }

    public static long dbeGetSelObj(int dbevindex, int type, int subtype) {
        if (type == FuncListDisp_type.DSP_FUNCTION.value)
            return selObjV2.getOrDefault(dbevindex, 0L);
        return 0L;
    }

    public static long dbeGetSelObjV2(int dbevindex, String typeStr) {
        return selObjV2.getOrDefault(dbevindex, -1L);
    }

    public static int dbeGetSelIndex(int dbevindex, long sel_obj, int type, int subtype) {
        if (type != FuncListDisp_type.DSP_FUNCTION.value)
            return -1;
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        Hist_data data = dbev.func_data;
        if (data == null || data.get_status() != Hist_data.Hist_status.SUCCESS)
            return -1;
        Histable chk_obj = DbeSession.getInstance().findObjectById(sel_obj);
        if (chk_obj == null)
            return -1;
        for (int i = 0; i < data.size(); i++)
            if (data.fetch(i).obj == chk_obj)
                return i;
        return -1;
    }

    public static String dbePrintData(int dbevindex, int type, int subtype, String printer,
                                       String fname, PrintStream outfile) {
        if (type != FuncListDisp_type.DSP_INDXOBJ.value)
            throw new RuntimeException("DBE.dbePrintData: only DSP_INDXOBJ is implemented");
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        MetricList mlist = dbev.get_metric_list(MetricType.MET_INDX);
        Hist_data data = dbev.get_hist_data(mlist, Histable.Type.INDEXOBJ, subtype, Hist_data.Mode.ALL);
        if (data.get_status() != Hist_data.Hist_status.SUCCESS)
            return DbeView.status_str(DbeView.DbeView_status.DBEVIEW_NO_DATA);
        Print.er_print_histogram cd = new Print.er_print_histogram(dbev, data, mlist,
                Print.Print_mode.MODE_LIST, dbev.get_limit(), mlist.get_sort_name(), null, true, true);
        cd.set_out_file(outfile);
        cd.data_dump();
        return null;
    }

    public static Object[] dbeGetCallTreeFuncs(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        List<Function> funcs = dbev.get_path_tree().get_funcs();
        long[] funcIds = new long[funcs.size()];
        String[] funcNames = new String[funcs.size()];
        for (int i = 0; i < funcs.size(); i++) {
            funcIds[i] = funcs.get(i).id;
            funcNames[i] = funcs.get(i).get_name();
        }
        return new Object[] { funcIds, funcNames, funcIds };
    }

    public static int dbeGetCallTreeNumLevels(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        return dbev.get_path_tree().get_ftree_depth();
    }

    public static Object[] dbeGetCallTreeLevel(int dbevindex, String mcmd, int level) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (mcmd == null)
            return null;
        BaseMetric bm = DbeSession.getInstance().find_base_reg_metric(mcmd);
        if (bm == null)
            return null;
        return dbev.get_path_tree().get_ftree_level(bm, level);
    }

    public static Object[] dbeGetCallTreeLevels(int dbevindex, String mcmd) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (mcmd == null)
            return null;
        BaseMetric bm = DbeSession.getInstance().find_base_reg_metric(mcmd);
        if (bm == null)
            return null;
        PathTree ptree = dbev.get_path_tree();
        int depth = ptree.get_ftree_depth();
        Object[] results = new Object[depth];
        for (int i = 0; i < depth; i++)
            results[i] = ptree.get_ftree_level(bm, i);
        return results;
    }

    public static Object[] dbeGetCallTreeLevelFuncs(int dbevindex, int startLevel, int endLevel) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        PathTree ptree = dbev.get_path_tree();
        int depth = ptree.get_ftree_depth();
        if (startLevel < 0)
            startLevel = 0;
        if (endLevel < 0 || endLevel >= depth)
            endLevel = depth - 1;
        if (startLevel == 0 && endLevel == depth - 1)
            return dbeGetCallTreeFuncs(dbevindex);

        List<Long> funcIds = new ArrayList<>();
        List<String> funcNames = new ArrayList<>();
        for (int level = startLevel; level <= endLevel; level++) {
            Object[] info = ptree.get_ftree_level(null, level);
            if (info == null)
                continue;
            long[] ids = (long[]) info[2];
            for (long fid : ids) {
                funcIds.add(fid);
                Histable obj = DbeSession.getInstance().findObjectById(fid);
                funcNames.add(obj != null ? obj.get_name() : null);
            }
        }
        long[] funcIdArr = funcIds.stream().mapToLong(Long::longValue).toArray();
        String[] funcNameArr = funcNames.toArray(new String[0]);
        return new Object[] { funcIdArr, funcNameArr, funcIdArr };
    }

    public static Object[] dbeGetCallTreeChildren(int dbevindex, String mcmd, int[] nodeIdxs) {
        if (nodeIdxs == null || nodeIdxs.length == 0)
            return null;
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (mcmd == null)
            return null;
        BaseMetric bm = DbeSession.getInstance().find_base_reg_metric(mcmd);
        if (bm == null)
            return null;
        PathTree ptree = dbev.get_path_tree();
        Object[] results = new Object[nodeIdxs.length];
        for (int i = 0; i < nodeIdxs.length; i++)
            results[i] = ptree.get_ftree_node_children(bm, nodeIdxs[i]);
        return results;
    }

    public static Object[] dbeGetFuncList(int dbevindex, int type, int subtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        Hist_data data = (type == FuncListDisp_type.DSP_FUNCTION.value) ? dbev.func_data : null;
        if (data == null || data.get_status() != Hist_data.Hist_status.SUCCESS)
            return null;

        List<Metric> items = data.get_metric_list();
        int nitems = (int) data.size();
        int nvisible = 0;
        for (Metric m : items)
            if (m.is_any_visible())
                nvisible++;

        Object[] table = new Object[nvisible + 1];
        table[nvisible] = null; 

        int nv = 0;
        for (int index = 0; index < items.size(); index++) {
            Metric mitem = items.get(index);
            if (!mitem.is_any_visible())
                continue;
            if (mitem.get_vtype() == ValueTag.VT_LABEL) {
                String[] jobjects = new String[nitems];
                for (int row = 0; row < nitems; row++)
                    jobjects[row] = data.get_value(index, row).str;
                table[nv++] = jobjects;
            } else {
                table[nv++] = dbeGetTableDataOneColumn(data, index);
            }
        }
        return table;
    }

    private static Object dbeGetTableDataOneColumn(Hist_data data, int metInd) {
        Metric m = data.get_metric_list().get(metInd);
        int nitems = (int) data.size();
        return switch (m.get_vtype()) {
            case VT_LABEL -> {
                String[] col = new String[nitems];
                for (int row = 0; row < nitems; row++)
                    col[row] = data.get_value(metInd, row).str;
                yield col;
            }
            case VT_DOUBLE -> {
                double[] col = new double[nitems];
                for (int row = 0; row < nitems; row++)
                    col[row] = data.get_value(metInd, row).d;
                yield col;
            }
            case VT_ULLONG, VT_LLONG -> {
                long[] col = new long[nitems];
                for (int row = 0; row < nitems; row++)
                    col[row] = data.get_value(metInd, row).l;
                yield col;
            }
            default -> null;
        };
    }

    public static Object[] dbeGetFuncListMini(int dbevindex, int type, int subtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        Hist_data data = (type == FuncListDisp_type.DSP_FUNCTION.value) ? dbev.func_data : null;
        if (data == null || data.get_status() != Hist_data.Hist_status.SUCCESS)
            return null;

        List<Metric> items = data.get_metric_list();
        int nvisible = 0;
        for (Metric m : items)
            if (m.is_any_visible())
                nvisible++;

        Object[] table = new Object[nvisible + 1];
        table[nvisible] = null;

        int nv = 0;
        for (int index = 0; index < items.size(); index++) {
            Metric mitem = items.get(index);
            if (!mitem.is_any_visible())
                continue;
            DbeStructs.TValue v = data.get_value_for_total(index);
            ERIPC.ipc_log("dbeGetFuncListMini: index=%d type=%s vtype=%s v.d=%f v.l=%d v.str=%s%n",
                    index, mitem.get_type(), mitem.get_vtype(), v.d, v.l, v.str);
            table[nv++] = switch (mitem.get_vtype()) {
                case VT_ULLONG, VT_LLONG -> new long[] { v.l };
                case VT_LABEL -> new String[] { v.str };
                default -> new double[] { v.d };
            };
        }
        return table;
    }

    public static Object[] dbeGetTableDataV2(int dbevindex, String mlistStr, String modeStr,
                                              String typeStr, String subtypeStr, long[] ids) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);

        if (mlistStr == null)
            return null;
        MetricType mt = switch (mlistStr) {
            case "MET_NORMAL" -> MetricType.MET_NORMAL;
            case "MET_CALL" -> MetricType.MET_CALL;
            case "MET_CALL_AGR" -> MetricType.MET_CALL_AGR;
            case "MET_DATA" -> MetricType.MET_DATA;
            case "MET_INDX" -> MetricType.MET_INDX;
            case "MET_IO" -> MetricType.MET_IO;
            case "MET_HEAP" -> MetricType.MET_HEAP;
            default -> null;
        };
        if (mt == null)
            return null;
        MetricList mlist = dbev.get_metric_list(mt);

        if (modeStr == null)
            return null;
        Hist_data.Mode mode = switch (modeStr) {
            case "CALLERS" -> Hist_data.Mode.CALLERS;
            case "CALLEES" -> Hist_data.Mode.CALLEES;
            case "SELF" -> Hist_data.Mode.SELF;
            case "ALL" -> Hist_data.Mode.ALL;
            default -> null;
        };
        if (mode == null)
            return null;

        if (typeStr == null)
            return null;
        Histable.Type type = switch (typeStr) {
            case "FUNCTION" -> Histable.Type.FUNCTION;
            case "INDEXOBJ" -> Histable.Type.INDEXOBJ;
            case "IOACTFILE" -> Histable.Type.IOACTFILE;
            case "IOACTVFD" -> Histable.Type.IOACTVFD;
            case "IOCALLSTACK" -> Histable.Type.IOCALLSTACK;
            case "HEAPCALLSTACK" -> Histable.Type.HEAPCALLSTACK;
            case "LINE" -> Histable.Type.LINE;
            case "INSTR" -> Histable.Type.INSTR;
            default -> null;
        };
        if (type == null)
            return null;

        int subtype = subtypeStr != null ? Integer.parseInt(subtypeStr) : 0;
        List<Histable> hobjs = null;
        if (ids != null) {
            hobjs = new ArrayList<>(ids.length);
            for (long id : ids)
                hobjs.add(DbeSession.getInstance().findObjectById(id));
        }
        Hist_data data = dbev.get_hist_data(mlist, type, subtype, mode, hobjs);
        return dbeGetTableDataV2Data(data);
    }

    private static Object[] dbeGetTableDataV2Data(Hist_data data) {
        if (data == null || data.get_status() != Hist_data.Hist_status.SUCCESS)
            return null;
        List<Metric> mlist = data.get_metric_list();
        int nitems = (int) data.size();

        List<Object> table = new ArrayList<>(mlist.size() + 1);
        for (int i = 0; i < mlist.size(); i++) {
            Metric mitem = mlist.get(i);
            if (!mitem.is_visible() && !mitem.is_tvisible() && !mitem.is_pvisible())
                continue;
            table.add(dbeGetTableDataOneColumn(data, i));
        }

        long[] idList = new long[nitems];
        for (int i = 0; i < nitems; i++)
            idList[i] = data.fetch(i).obj.id;
        table.add(idList);
        return table.toArray();
    }

    public static String[] dbeGetNames(int dbevindex, int type, long sel_obj) {
        return new String[] { "Name", "", "" };
    }

    public static Object[] dbeGetTotalMax(int arg1, int arg2, int arg3) {
        throw new RuntimeException("dbeGetTotalMax not implemented");
    }

    public static String dbeComposeFilterClause(int arg1, int arg2, int arg3, int[] arg4) {
        throw new RuntimeException("dbeComposeFilterClause not implemented");
    }

    public static Object[] dbeGetOverviewList(int arg1) {
        throw new RuntimeException("dbeGetOverviewList not implemented");
    }

    public static Object[] dbeGetStatisList(int arg1) {
        throw new RuntimeException("dbeGetStatisList not implemented");
    }

    public static Object[] dbeGetSummary(int dbevindex, long[] selObjs, int type, int subtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (selObjs == null || selObjs.length == 0 || type != FuncListDisp_type.DSP_FUNCTION.value)
            return null;
        Hist_data funcData = dbev.func_data;
        if (funcData == null || funcData.get_status() != Hist_data.Hist_status.SUCCESS)
            return null;

        List<Histable> objs = new ArrayList<>();
        for (long selObj : selObjs) {
            int sel_index = (int) selObj;
            if (sel_index < 0 || sel_index >= funcData.size())
                continue;
            Hist_data.HistItem item = funcData.fetch(sel_index);
            if (item.obj == null)
                continue;
            if ("<Total>".equals(item.obj.get_name())) {
                objs.clear();
                objs.add(item.obj);
                break;
            }
            objs.add(item.obj);
        }
        if (objs.isEmpty())
            return null;

        int[] saligns = { 1, 1, 1, 3, 3, 1, 1, 1 }; // TEXT_LEFT=1, TEXT_RIGHT=3 (enums.h)
        char[] mnemonic = { 'N', 'P', 'z', 'r', 'b', 'j', 'm', 'A' };
        String[] nameLabels = { "Name", "PC Address", "Size", "Source File",
                "Object File", "Load Object", "Mangled Name", "Aliases" };
        String name0 = objs.get(0).get_name();
        if (objs.size() > 1)
            name0 = name0 + ": Multiple Selection (" + objs.size() + " objects)";
        String[] nameValues = { name0, null, null, null, null, null, null, null };
        Object[] name_objs = { saligns, mnemonic, nameLabels, nameValues };

        MetricList propMlist = new MetricList(dbev.get_metric_ref(MetricType.MET_NORMAL));
        List<Metric> items = propMlist.get_items();
        int nitems = items.size();
        String[] jlabels = new String[nitems];
        double[] clockList = new double[nitems];
        double[] exclList = new double[nitems];
        double[] epList = new double[nitems];
        double[] inclList = new double[nitems];
        double[] ipList = new double[nitems];
        int[] vtype = new int[nitems];

        boolean first = true;
        for (Histable obj : objs) {
            Hist_data selfData = dbev.get_hist_data(propMlist, Histable.Type.FUNCTION, subtype,
                    Hist_data.Mode.SELF, List.of(obj));
            if (selfData.get_status() != Hist_data.Hist_status.SUCCESS)
                continue;
            DbeStructs.TValue[] values = selfData.size() > 0 ? selfData.fetch(0).value : null;

            int index2 = 0;
            for (int index = 0; index < items.size(); index++) {
                Metric mitem = items.get(index);
                if (mitem.get_subtype() == BaseMetric.STATIC)
                    continue;
                double dvalue = values != null ? values[index].to_double() : 0.0;
                double dtotal = findTotalValue(funcData, mitem.get_type(), mitem.get_subtype());
                double percentScale = dtotal == 0.0 ? 0.0 : 100.0 / dtotal;
                if (mitem.get_subtype() == BaseMetric.EXCLUSIVE) {
                    if (first) {
                        jlabels[index2] = mitem.get_username();
                        vtype[index2] = mitem.get_vtype().ordinal() + 1;
                    }
                    dvalue += exclList[index2];
                    exclList[index2] = dvalue;
                    epList[index2] = dvalue * percentScale;
                } else {
                    dvalue += inclList[index2];
                    if (dvalue > dtotal)
                        dvalue = dtotal;
                    inclList[index2] = dvalue;
                    ipList[index2] = dvalue * percentScale;
                    index2++;
                }
            }
            first = false;
        }

        Object[] metric_objs = { jlabels, clockList, exclList, epList, inclList, ipList, vtype };
        return new Object[] { name_objs, metric_objs };
    }

    private static double findTotalValue(Hist_data funcData, BaseMetric.Type baseType, int subtype) {
        List<Metric> fItems = funcData.get_metric_list();
        for (int i = 0; i < fItems.size(); i++) {
            Metric fm = fItems.get(i);
            if (fm.get_type() == baseType && fm.get_subtype() == subtype)
                return funcData.get_value_for_total(i).to_double();
        }
        return 0.0;
    }

    public static String dbeGetExpName(int arg0, String arg1) {
        throw new RuntimeException("dbeGetExpName not implemented");
    }

    public static String[] dbeGetHwcList(int arg0) {
        throw new RuntimeException("dbeGetHwcList not implemented");
    }

    public static String[][] dbeGetHwcName(int arg0) {
        throw new RuntimeException("dbeGetHwcName not implemented");
    }

    public static String[][] dbeGetHwcType(int arg0) {
        throw new RuntimeException("dbeGetHwcType not implemented");
    }

    public static int[] dbeGetRegList(int arg0, String arg1) {
        throw new RuntimeException("dbeGetRegList not implemented");
    }

    public static String[] dbeGetAttrList(int arg0) {
        throw new RuntimeException("dbeGetAttrList not implemented");
    }

    public static int dbeGetMaxReg(int arg0) {
        throw new RuntimeException("dbeGetMaxReg not implemented");
    }

    public static String[] dbeGetIfreqData(int arg1) {
        throw new RuntimeException("dbeGetIfreqData not implemented");
    }

    public static String[] dbeGetRaceData(int arg1, int arg2) {
        throw new RuntimeException("dbeGetRaceData not implemented");
    }

    public static String[] dbeGetDeadlockData(int arg1) {
        throw new RuntimeException("dbeGetDeadlockData not implemented");
    }

    public static String[] dbeGetDeadlockThreadStack(int arg1, long arg2) {
        throw new RuntimeException("dbeGetDeadlockThreadStack not implemented");
    }

    public static String[] dbeGetRaceDetailedInfo(int arg1, int arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeGetRaceDetailedInfo not implemented");
    }

    public static String[] dbeGetSubDeadlockData(int arg1, int arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeGetSubDeadlockData not implemented");
    }

    public static String[] dbeGetSubRaceData(int arg1, int arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeGetSubRaceData not implemented");
    }

    public static Object[] dbeGetLeakListInfo(int arg1, boolean arg2) {
        throw new RuntimeException("dbeGetLeakListInfo not implemented");
    }

    public static boolean dbeShowAllMetrics(int arg0) {
        throw new RuntimeException("dbeShowAllMetrics not implemented");
    }

    public static long dbeGetObject(int dbevindex, long sel_func, long sel_pc) {
        return sel_pc != 0 ? sel_pc : sel_func;
    }

    public static String dbeGetName(int dbevindex, int expId) {
        int id = expId < 0 ? 0 : expId;
        Experiment exp = DbeSession.getInstance().get_exp(id);
        if (exp == null)
            return null;
        return String.format("%s [%s, PID %d]", exp.get_expt_name(),
                exp.getUtargname() != null ? exp.getUtargname() : "(unknown)", exp.getPID());
    }

    public static String[] dbeGetExpVerboseName(int[] expIds) {
        String[] list = new String[expIds.length];
        for (int i = 0; i < expIds.length; i++)
            list[i] = dbeGetName(0, expIds[i]);
        return list;
    }

    public static long dbeGetStartTime(int dbevindex, int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId < 0 ? 0 : expId);
        return exp != null ? exp.getStartTime() : 0;
    }

    public static long dbeGetRelativeStartTime(int dbevindex, int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId < 0 ? 0 : expId);
        return exp != null ? exp.getRelativeStartTime() : 0;
    }

    public static long dbeGetEndTime(int dbevindex, int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId < 0 ? 0 : expId);
        return exp != null ? exp.getLastEvent() : 0;
    }

    public static int dbeGetClock(int dbevindex, int expId) {
        return DbeSession.getInstance().get_clock(expId);
    }

    public static long dbeGetWallStartSec(int dbevindex, int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId < 0 ? 0 : expId);
        return exp != null ? exp.getWallStartSec() : 0;
    }

    public static String dbeGetHostname(int dbevindex, int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId < 0 ? 0 : expId);
        return exp != null ? exp.getHostname() : null;
    }

    private static final String[] VTYPE_NAMES = {
            "NONE", "INT32", "UINT32", "INT64", "UINT64", "STRING", "DOUBLE", "OBJECT", "DATE", "BOOL", "ENUM"
    };

    public static Object[] dbeGetDataDescriptorsV2(int expId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId);
        if (exp == null)
            return null;
        List<Integer> dataId = new ArrayList<>();
        List<String> dataName = new ArrayList<>();
        List<String> dataUName = new ArrayList<>();
        List<Integer> auxProp = new ArrayList<>();
        for (DataDescriptor dataDscr : exp.getDataDescriptors()) {
            if ((dataDscr.getFlags() & Data_flag.DDFLAG_NOSHOW.value) != 0)
                continue;
            dataId.add(dataDscr.getId());
            dataName.add(dataDscr.getName());
            dataUName.add(dataDscr.getUName());
            auxProp.add(dataDscr.getId() == ProfData_type.DATA_HWC.getValue()
                    ? Prop_type.PROP_HWCTAG.ordinal() : Prop_type.PROP_NONE.ordinal());
        }
        return new Object[] {
                dataId.stream().mapToInt(Integer::intValue).toArray(),
                dataName.toArray(new String[0]),
                dataUName.toArray(new String[0]),
                auxProp.stream().mapToInt(Integer::intValue).toArray(),
        };
    }

    public static Object[] dbeGetDataPropertiesV2(int expId, int dataId) {
        Experiment exp = DbeSession.getInstance().get_exp(expId);
        if (exp == null)
            return null;
        DataDescriptor dataDscr = exp.get_raw_events(dataId);
        if (dataDscr == null)
            return null;
        List<PropDescr> props = dataDscr.getProps();
        int n = props.size();
        int[] propId = new int[n];
        String[] propUName = new String[n];
        int[] propTypeId = new int[n];
        String[] propTypeName = new String[n];
        int[] propFlags = new int[n];
        String[] propName = new String[n];
        Object[] propStateNames = new Object[n];
        Object[] propStateUNames = new Object[n];
        for (int i = 0; i < n; i++) {
            PropDescr prop = props.get(i);
            String pname = prop.name != null ? prop.name : "";
            String uname = prop.uname != null ? prop.uname : pname;
            int vtypeNum = prop.vtype.ordinal();
            if (vtypeNum < 0 || vtypeNum >= VTYPE_NAMES.length)
                vtypeNum = VType_type.TYPE_NONE.ordinal();
            int nStates = prop.getMaxState();
            String[] stateNames = null, stateUNames = null;
            if (nStates > 0) {
                stateNames = new String[nStates];
                stateUNames = new String[nStates];
                for (int k = 0; k < nStates; k++) {
                    stateNames[k] = prop.getStateName(k);
                    stateUNames[k] = prop.getStateUName(k);
                }
            }
            propId[i] = prop.propID.ordinal();
            propUName[i] = uname;
            propTypeId[i] = vtypeNum;
            propTypeName[i] = VTYPE_NAMES[vtypeNum];
            propFlags[i] = prop.flags;
            propName[i] = pname;
            propStateNames[i] = stateNames;
            propStateUNames[i] = stateUNames;
        }
        return new Object[] {
                propId, propUName, propTypeId, propTypeName, propFlags, propName, propStateNames, propStateUNames
        };
    }

    public static Object[] dbeGetExperimentDataDescriptors(int[] expIds) {
        int sz = expIds.length;
        Object[] expDscrInfo = new Object[sz];
        Object[] expDscrProps = new Object[sz];
        for (int ii = 0; ii < sz; ii++) {
            int expIdx = expIds[ii];
            Object[] ddscrInfo = dbeGetDataDescriptorsV2(expIdx);
            List<Object> ddscrProps = new ArrayList<>();
            if (ddscrInfo != null) {
                int[] dataId = (int[]) ddscrInfo[0];
                for (int j = 0; j < dataId.length; j++)
                    ddscrProps.add(dbeGetDataPropertiesV2(expIdx, dataId[j]));
            }
            expDscrInfo[ii] = ddscrInfo;
            expDscrProps[ii] = ddscrProps.toArray();
        }
        return new Object[] { expDscrInfo, expDscrProps };
    }

    static DataView getTimelinePackets(int dbevindex, int exp_id, int data_id, int entity_prop_id) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        int[] sortprops = { Prop_type.PROP_HWCTAG.ordinal(), entity_prop_id, Prop_type.PROP_TSTAMP.ordinal() };
        return dbev.get_filtered_events(exp_id, data_id, sortprops, sortprops.length);
    }

    static long getIdxByVals(DataView packets, int aux, int entity_prop_val, long time, Relation rel) {
        Data.Datum[] tval = new Data.Datum[3];
        tval[0] = new Data.Datum();
        tval[0].i = aux;
        tval[1] = new Data.Datum();
        tval[1].i = entity_prop_val;
        tval[2] = new Data.Datum();
        tval[2].l = time;
        return packets.getIdxByVals(tval, rel);
    }

    static boolean hasInvisbleTLEvents(Experiment exp, VMode view_mode) {
        return exp.hasJava() && view_mode == VMode.VMODE_USER;
    }

    static boolean isVisibleTLEvent(Experiment exp, VMode view_mode, DataView packets, long idx) {
        if (hasInvisbleTLEvents(exp, view_mode)) {
            Object obj = packets.getObjValue(Prop_type.PROP_JTHREAD.ordinal(), idx);
            Experiment.JThread jthread = obj instanceof Experiment.JThread ? (Experiment.JThread) obj : null;
            if (jthread == Experiment.JTHREAD_NONE || (jthread != null && jthread.is_system()))
                return false;
        }
        return true;
    }

    static boolean isValidIdx(DataView packets, int entity_prop_id, int aux, int entity_prop_val, long idx) {
        if (idx < 0 || idx >= packets.getSize())
            return false;
        int pkt_aux = packets.getIntValue(Prop_type.PROP_HWCTAG.ordinal(), idx);
        if (pkt_aux != aux)
            return false;
        if (entity_prop_id == Prop_type.PROP_EXPID.ordinal() || entity_prop_id == Prop_type.PROP_NONE.ordinal())
            return true; // not a packet property; we know the packet is in this experiment
        int pkt_ent = packets.getIntValue(entity_prop_id, idx);
        return pkt_ent == entity_prop_val;
    }

    static int getPropIdByName(String name) {
        try {
            return Prop_type.valueOf("PROP_" + name).ordinal();
        } catch (IllegalArgumentException e) {
            return Prop_type.PROP_NONE.ordinal();
        }
    }

    static boolean dbeHasTLData(int dbevindex, int exp_id, int data_id, int entity_prop_id,
                  int entity_prop_value, int aux) {
        DataView packets = getTimelinePackets(dbevindex, exp_id, data_id, entity_prop_id);
        if (packets == null || packets.getSize() == 0)
            return false;
        long start_ind = getIdxByVals(packets, aux, entity_prop_value, 0, Relation.REL_GTEQ); // time >= 0
        if (start_ind < 0)
            return false;

        DbeView dbev = DbeSession.getInstance().getView (dbevindex);
        VMode view_mode = dbev.get_view_mode();
        Experiment exp = DbeSession.getInstance().get_exp(exp_id);
        if (!hasInvisbleTLEvents(exp, view_mode))
            return true; // all events are visible, no further checking required
        long end_ind = getIdxByVals(packets, aux, entity_prop_value,
                MAX_TIME, Relation.REL_LTEQ);
        for (long ii = start_ind; ii <= end_ind; ii++) {
            if (!isVisibleTLEvent(exp, view_mode, packets, ii))
                continue;
            return true; // first visible packet => has data
        }
        return false;
    }

    public static boolean[] dbeHasTLData(int dbev_index, int[] exp_ids, int[] data_ids,
                  int[] entity_prop_ids, // LWP,CPU,THR, etc
                  int[] entity_prop_values, int[] auxs) {
        DbeView dbev = DbeSession.getInstance().getView(dbev_index);
        if (!dbev.isShowAll() && (dbev.isShowHideChanged () || dbev.isNewViewMode())) {
            // LIBRARY_VISIBILITY
            dbev.resetAndConstructShowHideStacks();
            if (dbev.isNewViewMode())
                dbev.resetNewViewMode();
            if (dbev.isShowHideChanged())
                dbev.resetShowHideChanged();
        }

        int sz = exp_ids.length;
        boolean[] hasVec = new boolean[sz];
        for (int ii = 0; ii < sz; ii++) {
            boolean hasData = dbeHasTLData(dbev_index, exp_ids[ii], data_ids[ii],
                    entity_prop_ids[ii], entity_prop_values[ii], auxs[ii]);
            hasVec[ii] = hasData;
        }
        return hasVec;
    }

    public static Object[] dbeGetExperimentTimeInfo(int[] expIds) {
        int sz = expIds.length;
        long[] offsetTime = new long[sz];
        long[] startTime = new long[sz];
        long[] endTime = new long[sz];
        long[] startWallSec = new long[sz];
        String[] hostname = new String[sz];
        int[] cpuFreq = new int[sz];
        for (int i = 0; i < sz; i++) {
            int expIdx = expIds[i];
            offsetTime[i] = dbeGetRelativeStartTime(0, expIdx);
            startTime[i] = dbeGetStartTime(0, expIdx);
            endTime[i] = dbeGetEndTime(0, expIdx);
            startWallSec[i] = dbeGetWallStartSec(0, expIdx);
            hostname[i] = dbeGetHostname(0, expIdx);
            cpuFreq[i] = dbeGetClock(0, expIdx);
        }
        return new Object[] { offsetTime, startTime, endTime, startWallSec, hostname, cpuFreq };
    }

    public static Object[] dbeGetEntities(int dbevindex, int exp_id, int entity_prop_id) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        Experiment exp = DbeSession.getInstance().get_exp(exp_id);
        if (exp == null)
            return null;

        List<Long> vals = new ArrayList<>();
        if (entity_prop_id == Prop_type.PROP_EXPID.ordinal()) {
            vals.add((long) exp_id);
        } else {
            TreeSet<Long> distinct = new TreeSet<>();
            for (ProfData_type dt : new ProfData_type[]{ProfData_type.DATA_CLOCK, ProfData_type.DATA_HEAP}) {
                DataDescriptor ddscr = exp.getDataDescriptor(dt);
                if (ddscr == null || ddscr.getProp(Prop_type.values()[entity_prop_id]) == null)
                    continue;
                long n = ddscr.getSize();
                for (long i = 0; i < n; i++)
                    distinct.add((long) ddscr.getIntValue(entity_prop_id, i));
            }
            vals.addAll(distinct);
        }

        VMode view_mode = dbev.get_view_mode();
        boolean show_java_threadnames = entity_prop_id == Prop_type.PROP_THRID.ordinal()
                && view_mode != VMode.VMODE_MACHINE;

        int sz = vals.size();
        int[] entity_prop_vals = new int[sz];
        String[] jthr_names = new String[sz];
        String[] jthr_g_names = new String[sz];
        String[] jthr_p_names = new String[sz];
        for (int i = 0; i < sz; i++) {
            long v = vals.get(i);
            entity_prop_vals[i] = (int) v;
            if (show_java_threadnames) {
                Experiment.JThread jthread = exp.get_jthread(v);
                if (jthread != null && jthread != Experiment.JTHREAD_NONE) {
                    jthr_names[i] = jthread.name;
                    jthr_g_names[i] = jthread.group_name;
                    jthr_p_names[i] = jthread.parent_name;
                }
            }
        }
        String entity_prop_name = Prop_type.values()[entity_prop_id].name().replaceFirst("^PROP_", "");
        return new Object[] {
                entity_prop_vals, jthr_names, jthr_g_names, jthr_p_names, new String[]{entity_prop_name}
        };
    }

    public static Object[] dbeGetEntitiesV2(int dbevindex, int[] exp_ids, int entity_prop_id) {
        Object[] res = new Object[exp_ids.length];
        for (int ii = 0; ii < exp_ids.length; ii++)
            res[ii] = dbeGetEntities(dbevindex, exp_ids[ii], entity_prop_id);
        return res;
    }

    public static Object[] dbeGetDataDescriptions(int arg0, int arg1) {
        throw new RuntimeException("dbeGetDataDescriptions not implemented");
    }

    public static Object[] dbeGetEvents(int arg1, int arg2, int arg3, int arg4,
                                        long arg5, long arg6, int arg7, int arg8) {
        throw new RuntimeException("dbeGetEvents not implemented");
    }

    public static String[] dbeGetStackNames(int arg1, long arg2) {
        throw new RuntimeException("dbeGetStackNames not implemented");
    }

    public static long[] dbeGetStackFunctions(int dbevindex, long stack) {
        List<Function> funcs = resolveStackFunctionsById(stack);
        if (funcs == null)
            return null;
        long[] ids = new long[funcs.size()];
        for (int i = 0; i < ids.length; i++)
            ids[i] = funcs.get(i).id;
        return ids;
    }

    public static Object[] dbeGetStacksFunctions(int dbevindex, long[] stacks) {
        Object[] res = new Object[stacks.length];
        for (int i = 0; i < stacks.length; i++)
            res[i] = dbeGetStackFunctions(dbevindex, stacks[i]);
        return res;
    }

    private static List<Function> resolveStackFunctionsById(long frinfo) {
        DbeSession session = DbeSession.getInstance();
        for (int i = 0; i < session.nexps(); i++) {
            List<Function> stack = session.get_exp(i).resolveStackById(frinfo);
            if (stack != null)
                return stack;
        }
        return null;
    }

    public static long[] dbeGetStackPCs(int arg0, long arg1) {
        throw new RuntimeException("dbeGetStackPCs not implemented");
    }

    public static Object[] dbeGetSamples(int dbevindex, int exp_id, long lo_idx, long hi_idx) {
        DataView packets = getTimelinePackets(dbevindex, exp_id, ProfData_type.DATA_SAMPLE.getValue(),
                Prop_type.PROP_EXPID.ordinal());
        if (packets == null || packets.getSize() == 0)
            return null;
        long lo = lo_idx < 0 ? 0 : lo_idx;
        long max = packets.getSize() - 1;
        long hi = (hi_idx < 0 || hi_idx > max) ? max : hi_idx;

        int n = (int) (hi - lo + 1);
        long[][] sarray = new long[n][];
        long[] starts = new long[n];
        long[] ends = new long[n];
        long[] rtimes = new long[n];
        String[] startNames = new String[n];
        String[] endNames = new String[n];
        int[] sampId = new int[n];

        for (long index = lo; index <= hi; index++) {
            int i = (int) (index - lo);
            Experiment.Sample sample = (Experiment.Sample) packets.getObjValue(Prop_type.PROP_SMPLOBJ.ordinal(), index);
            Experiment.PrUsage prusage = sample.prusage != null ? sample.prusage : new Experiment.PrUsage();
            sarray[i] = prusage.getMstateValues();
            starts[i] = sample.start_time;
            ends[i] = sample.end_time;
            rtimes[i] = prusage.pr_rtime;
            startNames[i] = sample.start_label;
            endNames[i] = sample.end_label;
            sampId[i] = sample.number;
        }
        return new Object[] { sarray, starts, ends, rtimes, startNames, endNames, sampId };
    }

    public static Object[] dbeGetGCEvents(int dbevindex, int exp_id, long lo_idx, long hi_idx) {
        DataView packets = getTimelinePackets(dbevindex, exp_id, ProfData_type.DATA_GCEVENT.getValue(),
                Prop_type.PROP_EXPID.ordinal());
        if (packets == null || packets.getSize() == 0)
            return null;
        long lo = lo_idx < 0 ? 0 : lo_idx;
        long max = packets.getSize() - 1;
        long hi = (hi_idx < 0 || hi_idx > max) ? max : hi_idx;

        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        List<Integer> eventId = new ArrayList<>();
        for (long index = lo; index <= hi; index++) {
            Object obj = packets.getObjValue(Prop_type.PROP_GCEVENTOBJ.ordinal(), index);
            if (obj instanceof Experiment.GCEvent gcevent) {
                starts.add(gcevent.start);
                ends.add(gcevent.end);
                eventId.add(gcevent.id);
            }
        }
        return new Object[] {
                starts.stream().mapToLong(Long::longValue).toArray(),
                ends.stream().mapToLong(Long::longValue).toArray(),
                eventId.stream().mapToInt(Integer::intValue).toArray(),
        };
    }

    public static long[][] dbeGetMemInfo(int arg1, int arg2) {
        throw new RuntimeException(" not implemented");
    }

    public static String[] dbeGetFuncNames(int arg1, long[] arg2) {
        if (arg2 == null)
            return null;
        String[] names = new String[arg2.length];
        for (int i = 0; i < arg2.length; i++) {
            Histable obj = DbeSession.getInstance().findObjectById(arg2[i]);
            names[i] = obj == null ? null : obj.get_name();
        }
        return names;
    }

    public static String[] dbeGetObjNamesV2(int dbevindex, long[] ids) {
        String[] res = new String[ids.length];
        for (int i = 0; i < ids.length; i++)
            res[i] = dbeGetObjNameV2(dbevindex, ids[i]);
        return res;
    }

    public static String dbeGetFuncName(int arg1, long arg2) {
        throw new RuntimeException("dbeGetFuncName not implemented");
    }

    public static String dbeGetObjNameV2(int dbevindex, long id) {
        Histable obj = DbeSession.getInstance().findObjectById(id);
        return obj == null ? null : obj.get_name();
    }

    public static String dbeGetDataDescriptor(int arg0, long arg1) {
        throw new RuntimeException("dbeGetDataDescriptor not implemented");
    }

    public static void dbeInit() {
        throw new RuntimeException("dbeInit not implemented");
    }

    public static String dbeGetDefaultExperimentName() {
        throw new RuntimeException("dbeGetDefaultExperimentName not implemented");
    }

    public static String dbeGetExperimentState() {
        throw new RuntimeException("dbeGetExperimentState not implemented");
    }

    public static long dbeGetExpStartTime() {
        throw new RuntimeException("dbeGetExpStartTime not implemented");
    }

    public static long dbeGetExpEndTime() {
        throw new RuntimeException("dbeGetExpEndTime not implemented");
    }

    public static Object[] dbeGetDataDescriptors() {
        throw new RuntimeException("dbeGetDataDescriptors not implemented");
    }

    public static Object[] dbeGetDataProperties(int arg1) {
        throw new RuntimeException("dbeGetDataProperties not implemented");
    }

    public static long[] dbeGetExprValues(int arg1, String arg2) {
        throw new RuntimeException("dbeGetExprValues not implemented");
    }

    public static Object[] dbeGetTLData(int dbevindex, int exp_id, int data_id,
                                         int entity_prop_id, int entity_prop_val, int aux,
                                         long start_ts, long delta, int numDeltas,
                                         boolean getRepresentatives, String[] chartProps) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        Experiment exp = DbeSession.getInstance().get_exp(exp_id);
        if (exp == null)
            return null;
        if (!getRepresentatives && (chartProps == null || chartProps.length == 0))
            return null;
        if (delta <= 0)
            return null;

        long tmp_ts = start_ts + delta * numDeltas;
        if (tmp_ts < start_ts)
            tmp_ts = MAX_TIME;
        final long end_ts = tmp_ts;

        DataView packets = getTimelinePackets(dbevindex, exp_id, data_id, entity_prop_id);
        if (packets == null)
            return null;

        VMode view_mode = dbev.get_view_mode();

        List<Long> representativeEvents = null;
        int[] binRepIdx = null;

        int[] propIds = null;
        Object[] propVals = null; // long[] (flat) or long[][] (per-state), one per chartProps entry
        int[] propNumStates = null;
        boolean[] propCumulativeChart = null;
        long[] propCumulativeRecentBinLastVal = null;
        long[] propCumulativeRecentBinHighVal = null;
        int[] propCumulativeRecentBin = null;

        boolean repsShowDuration = false;
        boolean statesUseDuration = false;
        boolean extendMicrostates = false;
        long ptimerTickDuration = exp.coll_params.ptimer_usec * 1000L; // nanoseconds per tick
        boolean hasDuration = packets.getProp(Prop_type.PROP_EVT_TIME) != null;
        if (hasDuration) {
            if (entity_prop_id == Prop_type.PROP_CPUID.ordinal()) {
                repsShowDuration = false;
                statesUseDuration = false;
            } else if (entity_prop_id == Prop_type.PROP_THRID.ordinal()
                    || entity_prop_id == Prop_type.PROP_LWPID.ordinal()) {
                repsShowDuration = true;
                statesUseDuration = true;
                extendMicrostates = data_id == ProfData_type.DATA_CLOCK.getValue() && ptimerTickDuration < delta;
            } else { // PROP_EXPID, PROP_NONE (experiment summary row), or unrecognized
                if (data_id == ProfData_type.DATA_SAMPLE.getValue() || data_id == ProfData_type.DATA_GCEVENT.getValue()) {
                    repsShowDuration = true;
                    statesUseDuration = true;
                } else if (data_id == ProfData_type.DATA_CLOCK.getValue()) {
                    repsShowDuration = false;
                    statesUseDuration = true;
                    extendMicrostates = true;
                } else {
                    repsShowDuration = false;
                    statesUseDuration = true;
                }
            }
        }
        final boolean reverseScan = repsShowDuration || extendMicrostates;

        // determine range of packet indices (lo_pkt_idx, hi_pkt_idx)
        long lo_pkt_idx, hi_pkt_idx;
        if (extendMicrostates && !(entity_prop_id == Prop_type.PROP_THRID.ordinal()
                || entity_prop_id == Prop_type.PROP_LWPID.ordinal())) {
            long exp_end_time = exp.getLastEvent() + 1;
            hi_pkt_idx = getIdxByVals(packets, aux, entity_prop_val, exp_end_time, Relation.REL_LT);
        } else {
            hi_pkt_idx = getIdxByVals(packets, aux, entity_prop_val, end_ts, Relation.REL_LT);
        }
        if (repsShowDuration) {
            // events that end "off screen" to the right: check if the next packet
            // actually started in-range (packet timestamps mark end of duration, not start)
            long idx;
            if (hi_pkt_idx >= 0)
                idx = hi_pkt_idx + 1;
            else
                idx = getIdxByVals(packets, aux, entity_prop_val, end_ts, Relation.REL_GTEQ);
            if (isValidIdx(packets, entity_prop_id, aux, entity_prop_val, idx)) {
                long pkt_ts = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx);
                long duration = packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), idx);
                pkt_ts -= duration;
                if (pkt_ts < end_ts)
                    hi_pkt_idx = idx;
            }
        }
        lo_pkt_idx = getIdxByVals(packets, aux, entity_prop_val, start_ts, Relation.REL_GTEQ);

        // allocate structs that return chart data
        boolean hasCumulativeCharts = false;
        if (chartProps != null && chartProps.length > 0) {
            int nprops = chartProps.length;
            propIds = new int[nprops];
            propVals = new Object[nprops];
            propNumStates = new int[nprops];
            propCumulativeChart = new boolean[nprops];
            propCumulativeRecentBinLastVal = new long[nprops];
            propCumulativeRecentBinHighVal = new long[nprops];
            propCumulativeRecentBin = new int[nprops];
            for (int propNum = 0; propNum < nprops; propNum++) {
                String propStr = chartProps[propNum];
                int items_per_prop = 0;
                int prop_id = Prop_type.PROP_NONE.ordinal();
                if ("EVT_COUNT".equals(propStr)) {
                    items_per_prop = 1; // use PROP_NONE for counting packets
                } else {
                    int lookup_prop_id = getPropIdByName(propStr);
                    PropDescr propDscr = packets.getProp(Prop_type.values()[lookup_prop_id]);
                    if (propDscr != null) {
                        switch (propDscr.vtype) {
                            case TYPE_INT32, TYPE_UINT32, TYPE_INT64, TYPE_UINT64 -> {
                                items_per_prop = propDscr.getMaxState() + 1; // extra slot for out-of-range
                                prop_id = lookup_prop_id;
                            }
                            default -> { /* TYPE_DOUBLE/STRING/OBJ/DATE: not implemented yet */ }
                        }
                    }
                }
                Object vals = items_per_prop == 0 ? null
                        : items_per_prop == 1 ? new long[numDeltas]
                        : new long[numDeltas][]; // on-demand per-bin state arrays
                boolean isCumulativeChart = prop_id == Prop_type.PROP_HCUR_LEAKS.ordinal()
                        || prop_id == Prop_type.PROP_HCUR_ALLOCS.ordinal();
                if (isCumulativeChart)
                    hasCumulativeCharts = true;
                propIds[propNum] = prop_id;
                propVals[propNum] = vals;
                propNumStates[propNum] = items_per_prop;
                propCumulativeChart[propNum] = isCumulativeChart;
            }
        }

        // Adjust idx range for calculating 'cumulative charts' e.g. heap size
        if (hasCumulativeCharts) {
            long lo_idx;
            if (lo_pkt_idx >= 0)
                lo_idx = lo_pkt_idx - 1;
            else
                lo_idx = getIdxByVals(packets, aux, entity_prop_val, start_ts, Relation.REL_LT);
            if (isValidIdx(packets, entity_prop_id, aux, entity_prop_val, lo_idx)) {
                for (int propNum = 0; propNum < propCumulativeChart.length; propNum++) {
                    if (!propCumulativeChart[propNum])
                        continue;
                    long value = packets.getLongValue(propIds[propNum], lo_idx);
                    propCumulativeRecentBinLastVal[propNum] = value;
                    propCumulativeRecentBinHighVal[propNum] = value;
                }
                lo_pkt_idx = lo_idx;
                if (hi_pkt_idx < lo_pkt_idx)
                    hi_pkt_idx = lo_pkt_idx;
            }
        }

        if (lo_pkt_idx >= 0 && hi_pkt_idx >= 0) {
            if (getRepresentatives) {
                representativeEvents = new ArrayList<>();
                binRepIdx = new int[numDeltas];
                Arrays.fill(binRepIdx, -1);
            }

            long start_idx, idx_inc;
            if (!reverseScan) {
                start_idx = lo_pkt_idx;
                idx_inc = 1;
            } else {
                start_idx = hi_pkt_idx;
                idx_inc = -1;
            }
            for (long ii = start_idx; ii >= lo_pkt_idx && ii <= hi_pkt_idx; ii += idx_inc) {
                if (!isVisibleTLEvent(exp, view_mode, packets, ii) && !hasCumulativeCharts)
                    continue;

                long pkt_end_ts = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ii);
                long pkt_dur = hasDuration ? packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), ii) : 0;
                long pkt_start_ts = pkt_end_ts - pkt_dur;
                if (pkt_end_ts < start_ts && !hasCumulativeCharts)
                    continue; // weird, should not happen
                if (pkt_start_ts >= end_ts)
                    continue; // could happen

                long bin_end_ts = pkt_end_ts >= end_ts ? end_ts - 1 : pkt_end_ts;
                int tmp_end_bin = (int) ((bin_end_ts - start_ts) / delta);
                long bin_start_ts = Math.max(pkt_start_ts, start_ts);
                int tmp_start_bin = (int) ((bin_start_ts - start_ts) / delta);

                boolean is_offscreen = tmp_end_bin < 0;
                if (tmp_end_bin < 0)
                    tmp_end_bin = 0;
                int pkt_end_bin = tmp_end_bin;
                int pkt_start_bin = tmp_start_bin;

                if (getRepresentatives && !is_offscreen) {
                    int bin = reverseScan ? pkt_start_bin : pkt_end_bin;
                    int eventIdx = binRepIdx[bin];
                    if (eventIdx == -1) {
                        binRepIdx[bin] = representativeEvents.size();
                        representativeEvents.add(ii);
                    }
                }

                if (propIds != null) {
                    for (int propNum = 0; propNum < propIds.length; propNum++) {
                        Object thisProp = propVals[propNum];
                        if (thisProp == null)
                            continue;
                        if (is_offscreen && !propCumulativeChart[propNum])
                            continue;
                        int propId = propIds[propNum];
                        long val = propId == Prop_type.PROP_NONE.ordinal() ? 1 : packets.getLongValue(propId, ii);
                        int nitems = propNumStates[propNum];
                        if (nitems < 1) {
                            continue;
                        } else if (nitems == 1) {
                            long[] thisPropVals = (long[]) thisProp;
                            int bin = statesUseDuration ? pkt_start_bin : pkt_end_bin;
                            if (!propCumulativeChart[propNum]) {
                                thisPropVals[bin] += val;
                            } else {
                                long high_value = propCumulativeRecentBinHighVal[propNum];
                                int last_bin = propCumulativeRecentBin[propNum];
                                if (last_bin < bin) {
                                    thisPropVals[last_bin] = high_value;
                                    long last_value = propCumulativeRecentBinLastVal[propNum];
                                    for (int kk = last_bin + 1; kk < bin; kk++)
                                        thisPropVals[kk] = last_value;
                                    high_value = 0;
                                    propCumulativeRecentBinHighVal[propNum] = 0;
                                    propCumulativeRecentBin[propNum] = bin;
                                }
                                long this_value = packets.getLongValue(propId, ii);
                                propCumulativeRecentBinLastVal[propNum] = this_value;
                                if (high_value < this_value) {
                                    high_value = this_value;
                                    propCumulativeRecentBinHighVal[propNum] = high_value;
                                }
                                if (ii == hi_pkt_idx) {
                                    thisPropVals[bin] = high_value;
                                    for (int kk = bin + 1; kk < numDeltas; kk++)
                                        thisPropVals[kk] = this_value;
                                }
                            }
                        } else {
                            // val is actually a state #
                            long[][] thisPropStateVals = (long[][]) thisProp;
                            long stateNum = (val >= 0 && val < nitems) ? val : nitems - 1;

                            long graph_pkt_dur = pkt_dur;
                            long graph_pkt_start_ts = pkt_start_ts;
                            int tmp2_start_bin = pkt_start_bin;
                            if (propId == Prop_type.PROP_MSTATE.ordinal() && statesUseDuration && extendMicrostates) {
                                // microstate stacks are shown/filtered with width=NTICK-1, but
                                // graphs need width=NTICK to account for all ticks
                                graph_pkt_dur += ptimerTickDuration;
                                graph_pkt_start_ts -= ptimerTickDuration;
                                long bst = Math.max(graph_pkt_start_ts, start_ts);
                                tmp2_start_bin = (int) ((bst - start_ts) / delta);
                            }
                            int graph_pkt_start_bin = statesUseDuration ? tmp2_start_bin : pkt_end_bin;

                            long weight;
                            if (propId == Prop_type.PROP_MSTATE.ordinal())
                                weight = packets.getLongValue(Prop_type.PROP_NTICK.ordinal(), ii) * ptimerTickDuration;
                            else if (graph_pkt_dur != 0)
                                weight = graph_pkt_dur;
                            else
                                weight = 1;
                            double value_per_bin;
                            if (graph_pkt_start_bin != pkt_end_bin)
                                value_per_bin = weight / ((double) graph_pkt_dur / delta);
                            else
                                value_per_bin = weight;

                            for (int evtbin = graph_pkt_start_bin; evtbin <= pkt_end_bin; evtbin++) {
                                long[] stateValues = thisPropStateVals[evtbin];
                                if (stateValues == null) {
                                    stateValues = new long[nitems];
                                    thisPropStateVals[evtbin] = stateValues;
                                }
                                double add;
                                if (graph_pkt_start_bin == pkt_end_bin
                                        || (evtbin > graph_pkt_start_bin && evtbin < pkt_end_bin)) {
                                    add = value_per_bin;
                                } else {
                                    long bin_start = start_ts + (long) evtbin * delta;
                                    long bin_end = start_ts + (long) (evtbin + 1) * delta - 1;
                                    if (evtbin == graph_pkt_start_bin) {
                                        add = graph_pkt_start_ts < bin_start ? value_per_bin
                                                : value_per_bin * ((double) (bin_end - graph_pkt_start_ts) / delta);
                                    } else { // rightmost bin
                                        add = pkt_end_ts > bin_end ? value_per_bin
                                                : value_per_bin * ((double) (pkt_end_ts - bin_start) / delta);
                                    }
                                }
                                stateValues[(int) stateNum] += (long) add;
                            }
                        }
                    }
                }
            }

            if (representativeEvents != null && reverseScan) {
                if (repsShowDuration) {
                    // prune events completely obscured by an earlier (longer) one; list is
                    // sorted by decreasing end-timestamp, so a start >= the previous kept
                    // event's start means this one is fully hidden
                    List<Long> pruned = new ArrayList<>();
                    long prev_start_ts = MAX_TIME;
                    for (long ii : representativeEvents) {
                        long tmp_end = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ii);
                        long tmp_dur = packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), ii);
                        long tmp_start = tmp_end - tmp_dur;
                        if (tmp_start >= prev_start_ts)
                            continue;
                        prev_start_ts = tmp_start;
                        pruned.add(ii);
                    }
                    Collections.reverse(pruned); // invert to increasing end-timestamp order
                    representativeEvents = pruned;
                } else {
                    Collections.reverse(representativeEvents);
                }
            }
        }

        Object representativeVals = getRepresentatives
                ? dbeGetTLDataRepVals(view_mode, start_ts, delta, numDeltas, packets, representativeEvents, repsShowDuration)
                : null;
        return new Object[] { representativeVals, propVals };
    }

    private static Object[] dbeGetTLDataRepVals(VMode view_mode, long start_ts, long delta, int numDeltas,
                                                 DataView packets, List<Long> representativeEvents, boolean showDuration) {
        int numrecs = representativeEvents == null ? 0 : representativeEvents.size();
        int[] startBins = new int[numrecs];
        int[] numBins = new int[numrecs];
        long[] eventIdxs = new long[numrecs];
        boolean hasStackIds = packets.getProp(Prop_type.PROP_FRINFO) != null;
        long[] stackIds = hasStackIds ? new long[numrecs] : null;
        boolean hasMstates = packets.getProp(Prop_type.PROP_MSTATE) != null;
        int[] mstates = hasMstates ? new int[numrecs] : null;
        boolean hasSampleVals = packets.getProp(Prop_type.PROP_SMPLOBJ) != null;
        long[][] sampleVals = hasSampleVals ? new long[numrecs][] : null;
        long[] timeStart = new long[numrecs];
        long[] timeEnd = new long[numrecs];

        int prevEndBin = -1;
        for (int eventIdx = 0; eventIdx < numrecs; eventIdx++) {
            long packetIdx = representativeEvents.get(eventIdx);
            long pkt_tstamp = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), packetIdx);
            long pkt_dur = showDuration ? packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), packetIdx) : 0;
            timeStart[eventIdx] = pkt_tstamp - pkt_dur;
            timeEnd[eventIdx] = pkt_tstamp;

            int startBin = (int) ((pkt_tstamp - pkt_dur - start_ts) / delta);
            if (startBin <= prevEndBin)
                startBin = prevEndBin + 1;
            int endBin = (int) ((pkt_tstamp - start_ts) / delta);
            if (endBin >= numDeltas)
                endBin = numDeltas - 1;
            int binCnt = endBin - startBin + 1;
            prevEndBin = endBin;

            startBins[eventIdx] = startBin;
            numBins[eventIdx] = binCnt;
            eventIdxs[eventIdx] = packetIdx;
            if (stackIds != null)
                stackIds[eventIdx] = packets.getLongValue(Prop_type.PROP_FRINFO.ordinal(), packetIdx);
            if (mstates != null)
                mstates[eventIdx] = packets.getIntValue(Prop_type.PROP_MSTATE.ordinal(), packetIdx);
            if (sampleVals != null) {
                Object obj = packets.getObjValue(Prop_type.PROP_SMPLOBJ.ordinal(), packetIdx);
                Experiment.Sample sample = obj instanceof Experiment.Sample ? (Experiment.Sample) obj : null;
                Experiment.PrUsage prusage = sample != null ? sample.prusage : null;
                if (prusage == null)
                    prusage = new Experiment.PrUsage();
                sampleVals[eventIdx] = prusage.getMstateValues();
            }
        }
        return new Object[] { startBins, numBins, eventIdxs, stackIds, mstates, sampleVals, timeStart, timeEnd };
    }

    static long getTLVisibleIdxByStepping(Experiment exp, VMode view_mode, int entity_prop_id, DataView packets,
                                           int aux, int entity_prop_val, long idx, long move_count, int direction) {
        if (direction == 0)
            move_count = 0;
        while (true) {
            if (!isValidIdx(packets, entity_prop_id, aux, entity_prop_val, idx))
                return -1;
            if (isVisibleTLEvent(exp, view_mode, packets, idx)) {
                if (move_count <= 0)
                    break;
                move_count--;
            }
            if (direction == 0)
                return -1;
            idx += direction;
        }
        return idx;
    }

    static long getTLVisibleIdxByVals(Experiment exp, VMode view_mode, int entity_prop_id, DataView packets,
                                       int aux, int entity_prop_val, long time, Relation rel) {
        long idx = getIdxByVals(packets, aux, entity_prop_val, time, rel);
        if (!hasInvisbleTLEvents(exp, view_mode))
            return idx;
        if (idx < 0)
            return idx;
        if (rel == Relation.REL_EQ)
            return -1; // would require bi-directional search... not supported for now
        int direction = (rel == Relation.REL_LT || rel == Relation.REL_LTEQ) ? -1 : 1;
        return getTLVisibleIdxByStepping(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                idx, 0 /* first match */, direction);
    }

    public static long[] dbeGetTLEventCenterTime(int dbevindex, int exp_id, int data_id, int entity_prop_id,
                                                  int entity_prop_val, int aux, long event_id, long move_count) {
        DataView packets = getTimelinePackets(dbevindex, exp_id, data_id, entity_prop_id);
        if (packets == null)
            return null;
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        VMode view_mode = dbev.get_view_mode();
        Experiment exp = DbeSession.getInstance().get_exp(exp_id);

        int direction;
        if (move_count == 0)
            direction = 0;
        else if (move_count < 0) {
            move_count = -move_count;
            direction = -1;
        } else {
            direction = 1;
        }
        long idx = getTLVisibleIdxByStepping(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                event_id, move_count, direction);
        if (idx < 0)
            return null;
        long ts = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx);
        long dur = packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), idx);
        long center = ts - dur / 2;
        return new long[] { idx, center };
    }

    public static long dbeGetTLEventIdxNearTime(int dbevindex, int exp_id, int data_id, int entity_prop_id,
                                                 int entity_prop_val, int aux, int searchDirection, long tstamp) {
        DataView packets = getTimelinePackets(dbevindex, exp_id, data_id, entity_prop_id);
        if (packets == null)
            return -1;
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        VMode view_mode = dbev.get_view_mode();
        Experiment exp = DbeSession.getInstance().get_exp(exp_id);

        if (searchDirection < 0) {
            long idx = getTLVisibleIdxByVals(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                    tstamp, Relation.REL_LTEQ);
            if (idx != -1)
                return idx;
            searchDirection = 1; // couldn't find to left, try to right
        }
        if (searchDirection > 0) {
            long idx = getTLVisibleIdxByVals(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                    tstamp, Relation.REL_GTEQ);
            if (idx != -1)
                return idx;
            // couldn't find to right, fall through to generic
        }
        long idx1 = getTLVisibleIdxByVals(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                tstamp, Relation.REL_LT);
        long idx2 = getTLVisibleIdxByVals(exp, view_mode, entity_prop_id, packets, aux, entity_prop_val,
                tstamp, Relation.REL_GTEQ);
        if (idx1 == -1)
            return idx2;
        if (idx2 == -1)
            return idx1;

        long t1 = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx1);
        long t2 = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx2);
        long t2dur = packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), idx2);
        long delta1 = tstamp - t1; // should always be positive
        long delta2 = (t2 - t2dur) - tstamp; // if negative, overlaps idx1
        return delta1 > delta2 ? idx2 : idx1;
    }

    private static List<Function> resolveTLStackFunctions(Experiment exp, DataView packets, long idx) {
        if (packets.getProp(Prop_type.PROP_FRINFO) == null)
            return null;
        long frinfo = packets.getLongValue(Prop_type.PROP_FRINFO.ordinal(), idx);
        if (frinfo == 0)
            return null;
        long thrid = packets.getIntValue(Prop_type.PROP_THRID.ordinal(), idx);
        long ts = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx);
        return exp.resolveStack(frinfo, thrid, ts);
    }

    private static Object[] getTLDetailValues(Experiment exp, int data_id, DataView packets, long idx) {
        long[] value = new long[15];
        value[0] = packets.getIntValue(Prop_type.PROP_LWPID.ordinal(), idx);
        value[1] = packets.getIntValue(Prop_type.PROP_THRID.ordinal(), idx);
        value[2] = packets.getIntValue(Prop_type.PROP_CPUID.ordinal(), idx);
        value[3] = packets.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), idx);

        List<Function> stack = resolveTLStackFunctions(exp, packets, idx);
        long[] funcIds = null;
        String[] funcNames = null;
        if (stack != null) {
            funcIds = new long[stack.size()];
            funcNames = new String[stack.size()];
            for (int i = 0; i < stack.size(); i++) {
                Function f = stack.get(i);
                funcIds[i] = f.id;
                funcNames[i] = f.get_name();
            }
            value[4] = packets.getLongValue(Prop_type.PROP_FRINFO.ordinal(), idx);
            value[5] = funcIds[0];
        }

        if (data_id == ProfData_type.DATA_CLOCK.getValue()) {
            value[6] = packets.getIntValue(Prop_type.PROP_MSTATE.ordinal(), idx);
            value[7] = exp.coll_params.ptimer_usec * 1000L // nanoseconds
                    * packets.getLongValue(Prop_type.PROP_NTICK.ordinal(), idx);
            value[8] = packets.getIntValue(Prop_type.PROP_OMPSTATE.ordinal(), idx);
            value[9] = packets.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), idx); // visual duration
        } else if (data_id == ProfData_type.DATA_HEAP.getValue() || data_id == ProfData_type.DATA_HEAPSZ.getValue()) {
            value[6] = packets.getIntValue(Prop_type.PROP_HTYPE.ordinal(), idx);
            value[7] = packets.getLongValue(Prop_type.PROP_HSIZE.ordinal(), idx);
            value[8] = packets.getLongValue(Prop_type.PROP_HVADDR.ordinal(), idx);
            value[9] = packets.getLongValue(Prop_type.PROP_HOVADDR.ordinal(), idx);
            value[10] = packets.getLongValue(Prop_type.PROP_HLEAKED.ordinal(), idx);
            value[11] = packets.getLongValue(Prop_type.PROP_HFREED.ordinal(), idx);
            value[12] = packets.getLongValue(Prop_type.PROP_HCUR_ALLOCS.ordinal(), idx);
            value[13] = packets.getLongValue(Prop_type.PROP_HCUR_LEAKS.ordinal(), idx);
        }

        return new Object[] { value, funcIds, funcNames, funcIds, funcNames };
    }

    public static Object[] dbeGetTLDetails(int dbevindex, int exp_id, int data_id,
                                            int entity_prop_id, long event_id) {
        Experiment exp = DbeSession.getInstance().get_exp(exp_id < 0 ? 0 : exp_id);
        if (exp == null)
            return null;
        DataView packets = getTimelinePackets(dbevindex, exp_id, data_id, entity_prop_id);
        if (packets == null)
            return null;
        return getTLDetailValues(exp, data_id, packets, event_id);
    }

    public static int dbeGetNxtEvent(int arg1, String arg2, String arg3, long arg4) {
        throw new RuntimeException("dbeGetNxtEvent not implemented");
    }

    public static int dbeGetPrvEvent(int arg1, String arg2, String arg3, long arg4) {
        throw new RuntimeException("dbeGetPrvEvent not implemented");
    }

    public static long[] dbeGetAggregatedValue(int arg1, String arg2, String arg3, String arg4,
                                               long arg5, long arg6, int arg7, String arg8, String arg9) {
        throw new RuntimeException("dbeGetAggregatedValue not implemented");
    }

    public static String dbeGetExprValue(int arg1, int arg2, String arg3) {
        throw new RuntimeException("dbeGetExprValue not implemented");
    }

    public static long[] dbeGetListValues(long arg1) {
        throw new RuntimeException("dbeGetListValues not implemented");
    }

    public static String[] dbeGetListNames(long arg1) {
        throw new RuntimeException("dbeGetListNames not implemented");
    }

    public static String[] dbeGetLineInfo(long arg1) {
        throw new RuntimeException("dbeGetLineInfo not implemented");
    }

    public static int dbeSetAlias(String arg1, String arg2, String arg3) {
        throw new RuntimeException("dbeSetAlias not implemented");
    }

    public static String[] dbeGetAlias(String arg1) {
        throw new RuntimeException("dbeGetAlias not implemented");
    }

    public static long[][] dbeGetXYPlotData(int arg1, String arg2, String arg3, String arg4, String arg5,
                                            String arg6, String arg7, String arg8, String arg9) {
        throw new RuntimeException("dbeGetXYPlotData not implemented");
    }


}
