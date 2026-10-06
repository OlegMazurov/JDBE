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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mazurov.jdbe.Enums.*;

public class DBE {

    public static int dbeInitView(int id, int cloneId) {
        return DbeSession.getInstance().createView(id, cloneId);
    }

    public static String dbeSendSignal(int p, int signum) {
        // sends an OS signal to a process (native: kill(p, signum)); needs a
        // platform-specific mechanism (Java has no portable arbitrary-signal API), not
        // yet ported.
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

    // Mirrors native's dbeGetFounderExpId (Dbe.cc:518-535), simplified: native tracks
    // fork/exec process-descendant chains (exp->getBaseFounder()) so a descendant
    // process reports the root ancestor's experiment id; this port has no such
    // descendant tracking, so every experiment is its own founder.
    public static int[] dbeGetFounderExpId(int[] expIds) {
        int[] ret = new int[expIds.length];
        for (int i = 0; i < expIds.length; i++) {
            Experiment exp = DbeSession.getInstance().get_exp(expIds[i]);
            ret[i] = exp != null ? exp.getExpIdx() : -1;
        }
        return ret;
    }

    // Mirrors native's dbeGetUserExpId (Dbe.cc:537-...): the "user visible" id used for
    // EXPID filters and timeline processes.
    public static int[] dbeGetUserExpId(int[] expIds) {
        int[] ret = new int[expIds.length];
        for (int i = 0; i < expIds.length; i++) {
            Experiment exp = DbeSession.getInstance().get_exp(expIds[i]);
            ret[i] = exp != null ? exp.getUserExpId() : -1;
        }
        return ret;
    }

    // Mirrors native's dbeGetExpGroupId (Dbe.cc:561-577): the compare-group id
    // (Experiment::groupId). This port has no expGroups/compare-group mechanism, so
    // every experiment stays at its default, un-grouped value (0, matching native's own
    // Experiment constructor default before any group assignment happens).
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
            // A kind of "/bin/ls -dl " simulation
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
        ArrayList<String> prop_cname = new ArrayList<>(); //must match TLModeCmd vals!

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
        prop_uname.add("Process"); // placeholder...
        // ...until we finalize how to expose user-level Experiments, descendents
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
            // Mirrors native's m->get_vtype2() (Metric.cc:66-...): a compare-mode-aware
            // wrapper around get_vtype() that only adjusts the tag for VAL_DELTA/VAL_RATIO
            // visbits, neither of which this port's metrics ever set (no compare mode) --
            // so plain get_vtype() is equivalent here. The wire value is native's
            // ValueTag enum (VT_SHORT=1, ...), i.e. this enum's ordinal()+1 (see
            // ValueTag.values[]'s matching 1-based offset just above).
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

    // Minimal stub for native's dbeGetRefMetricTreeValues (Dbe.cc:1534-~1800): that
    // function computes real per-metric <Total> values, HWC-cycles-to-time conversion,
    // and "which column is hot" highlighting, across compare groups. None of that is
    // ported here -- this just returns correctly-shaped, all-zero/false tables (right
    // column/row counts) so the metrics-tree UI doesn't abort on an unrecognized
    // response shape. Revisit if the GUI's metric picker needs real preview values.
    // Mirrors native's dbeGetRefMetricTreeValues (Dbe.cc:1534-~1800), simplified: no
    // compare-group support (ngroups is always 1 -- see dbeGetExpGroupId's own doc
    // comment), no HWC-cycles-to-time conversion (no HWC metrics in this port), and
    // "non_metric_cmds" (e.g. experiment/GC duration pseudo-columns) aren't computed,
    // left as 0 -- revisit if the Overview window needs those specifically. The real
    // per-metric <Total> values now come from the same get_hist_data/get_value_for_total
    // machinery verified against the oracle for -functions.
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
        List<Integer> columnOf = new ArrayList<>(); // baseMetrics[k] belongs at valueTable[columnOf[k]]
        for (int i = 0; i < numMetrics; i++) {
            BaseMetricTreeNode node = root.find(metricCmds[i]);
            BaseMetric bm = node != null ? node.get_BaseMetric() : null;
            if (bm == null)
                continue; // leave that column at its 0.0 default
            baseMetrics.add(bm);
            columnOf.add(i);
        }
        // MET_SRCDIS yields one (INCLUSIVE) Metric per BaseMetric -- exactly one column
        // per requested metric_cmd. The <Total> row's EXCLUSIVE/INCLUSIVE values are
        // always identical (PathTree.compute_metrics sets both from the same grand
        // total), so INCLUSIVE-only is sufficient here.
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

    // Mirrors native's convert_visbits_to_gui_checkbox_bits (Dbe.cc:1259-1281): when a
    // metric supports VAL_TIMEVAL but not VAL_VALUE (e.g. CP_TOTAL_CPU), the GUI's
    // checkbox bit for "show by default" is VAL_TIMEVAL, not VAL_VALUE, even though
    // Metric/BaseMetric otherwise use VAL_VALUE to mean "enabled".
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
        boolean _has_value = false; //not used yet; for nodes that don't have metrics
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
            // Wire value is native's 1-based ValueTag enum (VT_SHORT=1, ...) -- see the
            // matching ordinal()+1 conversion in dbeGetMetricList's valtype[] above.
            _vtype = bm.get_vtype().ordinal() + 1;
            _vstyles_capable = bm.get_value_styles();
            // Mirrors native's BaseMetric::default_visbits, which is only ever populated
            // by MetricList::setMetrics(DEFAULT_METRICS, fromRcFile=true) -- a full
            // dmetrics-command-string parser this port doesn't have. Substituting the
            // same simpler rule this port's own MetricList constructor already uses (and
            // has verified against the oracle) for "is this metric shown by default":
            // every EXCLUSIVE/INCLUSIVE metric is on, with its supported styles minus
            // the percent column; STATIC metrics (other than ONAME, which the GUI
            // apparently always shows regardless of these bits) are off.
            int defaultStyles = (bm.hasFlavor(BaseMetric.EXCLUSIVE) || bm.hasFlavor(BaseMetric.INCLUSIVE))
                    ? bm.get_value_styles() & ~VAL_PERCENT : 0;
            _vstyles_e_default_values = convertVisbitsToGuiCheckboxBits(bm, defaultStyles);
            _vstyles_i_default_values = convertVisbitsToGuiCheckboxBits(bm, defaultStyles);
            // HWC-specific hiding heuristic (native: hide all HWCs by default except the
            // time variant of c_stalls) isn't ported -- no HWC metrics in this port yet.
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
                //Special case for metrics that aren't registered
                // but have registered children
                // Linux example: Total Time is unregistered, CPU Time is registered
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
        // TODO: depends on DataSpace ("is_datamode_available") and machine-model detection,
        // neither of which is ported; this is an optional dataspace-analysis
        // auto-configuration step, not required for header/basic function display.
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
        // Mirrors native's DbeSession::check_tab_avail(), called after every experiment
        // load (DbeSession.cc:1884-1894); done lazily here instead since this port has
        // no equivalent hook wired into every load path yet.
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

    // Mirrors native's MemorySpace::getMemObjects (MemorySpace.cc:218-262), simplified:
    // this port has no dataspace/memory-object subsystem (dyn_memobj is always empty),
    // so each of the 8 fields is an empty array -- but the OUTER array must still have
    // exactly 8 elements; the GUI indexes into it unconditionally regardless of whether
    // any memory objects are actually defined.
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

        // lobjectsNoJava is a trimmed list of indices provided to front-end skipping the Java
        // classes. lobjectsNoJava preserves the mapping of the index into the complete lobjs
        // vector. What front-end sees as lobj[i] is really lobj[lobjectsNoJava[i]];

        // This list is constructed every time GetLoadObjectList() or GetLoadObjectState() is
        // called. Possibility of further optimization by making it more persistent.
        // Only consumer of this list is dbeSetLoadObjectState
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
        // TODO: sessionRestart (DbeSession.reset()) not wired up; not needed for a
        // first, single experiment-list load.
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

    // Mirrors native's dbeGetSearchPath/dbeSetSearchPath (Dbe.cc:981-1004).
    public static String[] dbeGetSearchPath(int dbevindex) {
        return DbeSession.getInstance().get_search_path().toArray(new String[0]);
    }

    public static void dbeSetSearchPath(int dbevindex, String[] path) {
        DbeSession.getInstance().set_search_path(Arrays.asList(path), true);
    }

    // Mirrors native's dbeGetPathmaps/dbeSetPathmaps/dbeAddPathmap (Dbe.cc:1006-1060).
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

    // Mirrors native's dbeGetOverviewText (Dbe.cc:1902-1964), simplified: no compare-group
    // support (ngroups is always 1 here -- this port has no expGroups mechanism yet), so
    // the "Base Group"/"Compare Group N" header variants and multi-experiment-per-group
    // text aren't ported.
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

    // Mirrors native's dbeGetMsg (Dbe.cc:1064-1080). Message_type enums.h: ERROR_MSG=1,
    // WARNING_MSG=2, PSTAT_MSG=3, PWARN_MSG=4 -- processor status messages (3/4) aren't
    // ported (no processor-usage subsystem in this port).
    public static String dbeGetMsg(int dbevindex, int type) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        if (type == 1)
            return dbev.error_msg;
        if (type == 2)
            return dbev.warning_msg;
        return null;
    }

    // Mirrors native's dbeSetFuncData (Dbe.cc:3687-...), restricted to DSP_FUNCTION --
    // the function list, which is this port's only implemented Histable.Type so far
    // (DbeView.get_hist_data). sel_obj resolution to a previously-selected row (native's
    // "find org_obj in the new data, or match by line/pc for DSP_LINE/DSP_PC") isn't
    // ported since this port has no Histable-object-ID registry yet (see dbeGetSelObj) --
    // sel_index is always -1 (no row pre-selected), matching native's own behavior when
    // sel_obj is 0/unset, which is the case on first load.
    public static int dbeSetFuncData(int dbevindex, long sel_obj, int type, int subtype) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);
        dbev.error_msg = null;
        dbev.warning_msg = null;
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
        // longs[1]/strings[1] (source-file id/name for DSP_SOURCE/DSP_DISASM) are left
        // at 0/null -- those display types aren't ported yet.
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

    // Mirrors native's dbeSetSort (Dbe.cc:1970-1979).
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

    // Mirrors native's dbeGetFilterStr (Dbe.cc:2439-2447).
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

    // Minimal stand-in for native's per-DbeView sel_obj tracking (Dbe.cc:2975-3100),
    // which resolves/stores actual Histable objects via dbeSession->findObjectById().
    // This port has no such object-ID registry yet, so these just round-trip the raw id
    // the GUI gave us, keyed by (dbevindex, type, subtype) for V1 / dbevindex for V2 --
    // enough for "select a row, read the selection back" without the full registry.
    private static final Map<String, Long> selObj = new HashMap<>();
    private static final Map<Integer, Long> selObjV2 = new HashMap<>();

    public static void dbeSetSelObj(int dbevindex, long id, int type, int subtype) {
        selObj.put(dbevindex + ":" + type + ":" + subtype, id);
    }

    public static void dbeSetSelObjV2(int dbevindex, long id) {
        selObjV2.put(dbevindex, id);
    }

    public static long dbeGetSelObj(int dbevindex, int type, int subtype) {
        return selObj.getOrDefault(dbevindex + ":" + type + ":" + subtype, 0L);
    }

    public static long dbeGetSelObjV2(int dbevindex, String typeStr) {
        return selObjV2.getOrDefault(dbevindex, 0L);
    }

    // Mirrors native's dbeGetSelIndex (Dbe.cc:3237-~3310): finds the row index of a
    // given sel_obj (a Histable pointer) within the relevant Hist_data's hist_items,
    // with special-cased matching for DSP_LINE/DSP_PC (same function, different
    // line/pc). This port has no Histable-object-ID registry (see dbeGetSelObj's own
    // doc comment) -- sel_obj here is just a round-tripped raw id, not a resolvable
    // object reference, so there's no object to search hist_items for. Native itself
    // returns -1 whenever sel_obj is 0/null (nothing selected), which is the common case
    // for a freshly loaded experiment and the only case this port can match faithfully.
    public static int dbeGetSelIndex(int dbevindex, long sel_obj, int type, int subtype) {
        return -1;
    }

    public static String dbePrintData(int dbevindex, int type, int subtype, String printer,
                                       String fname, PrintStream outfile) {
        throw new RuntimeException("dbePrintData not implemented");
    }

    // Mirrors native's dbeGetFuncList (Dbe.cc:4831-5084), restricted to DSP_FUNCTION
    // (the only Histable.Type this port's get_hist_data computes). The "annotated
    // src/dis/layout" ji_list column and per-type name formatting (DSP_SOURCE/DISASM/
    // DLAYOUT) aren't ported -- not reachable since only func_data exists.
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
        table[nvisible] = null; // ji_list: only for annotated src/dis/layout, not reachable here

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

    // Mirrors native's dbeGetTableDataOneColumn(Hist_data*, int) (Dbe.cc:5514-5590),
    // restricted to the vtypes this port's metrics actually use (VT_LABEL for the Name
    // column, VT_DOUBLE for CPU-time seconds, VT_ULLONG/VT_LLONG for byte/count metrics
    // -- see PathTree.setMetricValue). VAL_RATIO (percent column) isn't ported (never
    // set by this port's MetricList).
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

    // Mirrors native's dbeGetFuncListMini (Dbe.cc:4746-4829), restricted to DSP_FUNCTION
    // like dbeGetFuncList. Each column is a length-1 array holding only the <Total> row's
    // value (no VT_INT/VT_ADDRESS/VAL_RATIO branches -- not used by this port's metrics).
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

    // Mirrors native's dbeGetTableDataV2 (Dbe.cc:5372-5473) + dbeGetTableDataV2Data
    // (Dbe.cc:5476-5510). Argument-string parsing mirrors native's own validation
    // (returns null for a string native itself wouldn't recognize). The actual
    // aggregation is delegated to DbeView.get_hist_data/PathTree.compute_metrics, which
    // only implement Histable.Type.FUNCTION + Hist_data.Mode.ALL -- any other
    // combination (Caller-Callees' CALLERS/CALLEES/SELF, Call Tree, Heap/IO call-stack
    // views, index objects) throws there with a message naming the unsupported combo,
    // rather than being speculatively special-cased here before it's actually hit.
    // The cstack/ids filter (native: resolved to Histable* via findObjectById and used
    // to pick the CALLERS/CALLEES root) is ignored -- this port has no object-ID
    // registry (see dbeGetSelIndex) and the only mode it computes (ALL) doesn't use it.
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

    // Mirrors native's dbeGetNames (Dbe.cc:5854-5919), restricted to the default case
    // ("Name", "", "") -- DSP_FUNCTION itself falls through to that case natively too.
    // DSP_SOURCE/DSP_DISASM/DSP_LINE/DSP_PC/DSP_DLAYOUT aren't ported (no source/disasm/
    // line/pc display types in this port yet).
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

    // Mirrors native's dbeGetSummary (Dbe.cc:6687-7061), restricted to DSP_FUNCTION (the
    // only display type this port's func_data/get_hist_data support) and -- per explicit
    // user decision -- to the "Name" field of the name area. PC Address/Source File/
    // Object File/Load Object/Mangled Name/Aliases all need source-file and module/
    // load-object location-info machinery (DbeFile::get_location_info,
    // Module::read_stabs) this port doesn't have, matching its earlier ELF/source-file
    // scope decision; those fields are sent as null (label present, value blank) so the
    // Summary panel's 8-field layout still matches what the GUI expects. The metrics
    // area is fully real, built via the SELF-mode machinery PathTree.compute_metrics_call
    // added for the Caller-Callees "function item" row.
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
                // Multi-selection that includes <Total> collapses to just <Total>.
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

    // Grand total (the func_data <Total> row's value) for the metric matching
    // (baseType, subtype), used as the percent denominator -- mirrors native's
    // data->get_totals() (Dbe.cc:6908), simplified to look the metric up by identity in
    // func_data's own metric list rather than needing a parallel totals structure.
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

    public static long dbeGetObject(int arg1, long arg2, long arg3) {
        throw new RuntimeException("dbeGetObject not implemented");
    }

    // Mirrors native's dbeGetName (Dbe.cc:7326-7343): full experiment name with path,
    // process name, and PID.
    public static String dbeGetName(int dbevindex, int expId) {
        int id = expId < 0 ? 0 : expId;
        Experiment exp = DbeSession.getInstance().get_exp(id);
        if (exp == null)
            return null;
        return String.format("%s [%s, PID %d]", exp.get_expt_name(),
                exp.getUtargname() != null ? exp.getUtargname() : "(unknown)", exp.getPID());
    }

    // Mirrors native's dbeGetExpVerboseName (Dbe.cc:7345-7356).
    public static String[] dbeGetExpVerboseName(int[] expIds) {
        String[] list = new String[expIds.length];
        for (int i = 0; i < expIds.length; i++)
            list[i] = dbeGetName(0, expIds[i]);
        return list;
    }

    // Mirrors native's dbeGetStartTime/dbeGetRelativeStartTime/dbeGetEndTime/
    // dbeGetClock/dbeGetWallStartSec/dbeGetHostname (Dbe.cc:7358-7406).
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

    // Mirrors native's dbeGetDataDescriptorsV2 (Dbe.cc:8676-8706).
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

    // Mirrors native's dbeGetDataPropertiesV2 (Dbe.cc:8708-8775).
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

    // Mirrors native's dbeGetExperimentDataDescriptors (Dbe.cc:8817-8850).
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

    // Mirrors native's dbeGetExperimentTimeInfo (Dbe.cc:8777-8815), simplified: no
    // "force fetch data descriptors" workaround (this port doesn't defer experiment
    // data loading, so there's nothing to force).
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

    public static Object[] dbeGetEntities(int arg1, int arg2, int arg3) {
        throw new RuntimeException("dbeGetEntities not implemented");
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

    public static long[] dbeGetStackFunctions(int arg0, long arg1) {
        throw new RuntimeException("dbeGetStackFunctions not implemented");
    }

    public static long[] dbeGetStackPCs(int arg0, long arg1) {
        throw new RuntimeException("dbeGetStackPCs not implemented");
    }

    public static long[][] dbeGetSamples(int arg1, int arg2) {
        throw new RuntimeException("dbeGetSamples not implemented");
    }

    public static long[][] dbeGetMemInfo(int arg1, int arg2) {
        throw new RuntimeException(" not implemented");
    }

    public static String[] dbeGetFuncNames(int arg1, long[] arg2) {
        throw new RuntimeException("dbeGetFuncNames not implemented");
    }

    public static String[] dbeGetObjNamesV2(int arg1, long[] arg2) {
        throw new RuntimeException("dbeGetObjNamesV2 not implemented");
    }

    public static String dbeGetFuncName(int arg1, long arg2) {
        throw new RuntimeException("dbeGetFuncName not implemented");
    }

    // Mirrors native's dbeGetObjNameV2 (Dbe.cc:8647-8658): resolves an opaque object id
    // (dbeSession->findObjectById) to its display name. This port has no Histable-
    // object-ID registry (see dbeGetSelObj's own doc comment), so there's no real name
    // to resolve to. Returning null here is unsafe -- at least one real GUI call site
    // (AnTable's "Filter Similarly Named Function" action) passes the result straight
    // into another call with no null-check, so a null would NPE every time that
    // reachable, user-triggered action is used, not just in some rare edge case.
    // Returning a visibly-fake placeholder instead of an empty string so it's obvious in
    // the GUI that this still needs a real object-ID registry.
    public static String dbeGetObjNameV2(int dbevindex, long id) {
        return "ObjName2";
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

    public static int[] dbeGetTLData(int arg1, String arg2, String arg3, String arg4,
                                     long arg5, long arg6, int arg7) {
        throw new RuntimeException("dbeGetTLData not implemented");
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
