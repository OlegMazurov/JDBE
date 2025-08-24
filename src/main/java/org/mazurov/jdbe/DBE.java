package org.mazurov.jdbe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mazurov.jdbe.Enums.*;

public class DBE {

    public static int dbeInitView(int id, int cloneId) {
        return DbeSession.getInstance().createView(id, cloneId);
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
        //dbeDetectLoadMachineModel(0);
        return error;
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
//            value_styles->append (m->get_value_styles ());
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
//            valtype->append (m->get_vtype2 ());
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
//            TODO
//            _vtype = bm.get_vtype();
//            _vstyles_capable = bm.get_value_styles();
//            int e_visbits = bm.get_default_visbits(BaseMetric::EXCLUSIVE);
//            int i_visbits = bm.get_default_visbits(BaseMetric::INCLUSIVE);
//            _vstyles_e_default_values = convert_visbits_to_gui_checkbox_bits(bm, e_visbits);
//            _vstyles_i_default_values = convert_visbits_to_gui_checkbox_bits(bm, i_visbits);
//            // not all metrics shown in er_print cmd line should be selected in the GUI at startup:
//            if (has_clock_profiling_data && bm.get_hw_ctr()) {
//                boolean hide = true; // by default, hide HWCs
//                if ("c_stalls".equals(bm.get_hw_ctr().name) ||
//                    "K_c_stalls".equals(bm.get_hw_ctr().name)) {
//                    boolean is_time = (bm.get_value_styles () & VAL_TIMEVAL) != 0;
//                    if (is_time) {
//                        // By default, show time variant of c_stalls
//                        hide = false;
//                    }
//                }
//                if (hide) {
//                    _vstyles_e_default_values |= VAL_HIDE_ALL;
//                    _vstyles_i_default_values |= VAL_HIDE_ALL;
//                }
//            }
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

    public static Object[] dbeGetTabListInfo(int dbevindex) {
        DbeView dbev = DbeSession.getInstance().getView(dbevindex);

        // make sure the tabs are initialized properly
        dbev.get_settings().proc_tabs(DbeApplication.getInstance().rdtMode);
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
        Object[] res = new Object[0]; //MemorySpace::getMemObjects();
        return res;
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

    public static String dbeOpenExperimentList(int arg1, String[] arg3, boolean arg4) {
        throw new RuntimeException("dbeOpenExperimentList not implemented");
    }

    public static String dbeDropExperiment(int arg1, int[] arg2) {
        throw new RuntimeException("dbeDropExperiment not implemented");
    }

    public static String[] dbeGetExpName(int arg1) {
        throw new RuntimeException("dbeDropExperiment not implemented");
    }

    public static int[] dbeGetExpState(int arg1) {
        throw new RuntimeException("dbeGetExpState not implemented");
    }

    public static boolean dbeSetExpEnable(int arg1, boolean arg2) {
        throw new RuntimeException("dbeSetExpEnable not implemented");
    }

    public static String[] dbeGetExpInfo(int arg1) {
        throw new RuntimeException("dbeGetExpInfo not implemented");
    }

    public static int dbeUpdateNotes(int arg1, int arg2, int arg3, String arg4, boolean arg5) {
        throw new RuntimeException("dbeUpdateNotes not implemented");
    }

    public static String[] dbeGetLoadObjectName(int arg1) {
        throw new RuntimeException("dbeGetLoadObjectName not implemented");
    }

    public static String[] dbeGetSearchPath(int arg1) {
        throw new RuntimeException("dbeGetSearchPath not implemented");
    }

    public static void dbeSetSearchPath(int arg0, String[] res) {
        throw new RuntimeException("dbeSetSearchPath not implemented");
    }

    public static Object[] dbeGetPathmaps(int arg1) {
        throw new RuntimeException("dbeGetPathmaps not implemented");
    }

    public static String dbeAddPathmap(int arg0, String arg1, String arg2) {
        throw new RuntimeException("dbeAddPathmap not implemented");
    }

    public static String dbeGetMsg(int arg1, int arg2) {
        throw new RuntimeException("dbeGetMsg not implemented");
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

    public static void dbeSetSort(int arg1, int arg2, int arg3, boolean arg4) {
        throw new RuntimeException("dbeSetSort not implemented");
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

    public static String dbeGetFilterStr(int arg1) {
        throw new RuntimeException("dbeGetFilterStr not implemented");
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

    public static String dbeDefineIndxObj(String arg1, String arg2) {
        throw new RuntimeException("dbeDefineIndxObj not implemented");
    }

    public static void dbeSetSelObj(int arg1, long arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeSetSelObj not implemented");
    }

    public static void dbeSetSelObjV2(int arg1, long arg2) {
        throw new RuntimeException("dbeSetSelObjV2 not implemented");
    }

    public static long dbeGetSelObj(int arg1, int arg2, int arg3) {
        throw new RuntimeException("dbeGetSelObj not implemented");
    }

    public static long dbeGetSelObjV2(int arg1, String arg2) {
        throw new RuntimeException("dbeGetSelObjV2 not implemented");
    }

    public static int dbeGetSelIndex(int arg1, long arg2, int arg3, int arg4) {
        throw new RuntimeException(" not implemented");
    }

    public static String dbePrintData(int arg1, int arg2, int arg3, String arg4, String arg5) {
        throw new RuntimeException("dbePrintData not implemented");
    }

    public static Object[] dbeGetFuncList(int arg1, long arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeGetFuncList not implemented");
    }

    public static Object[] dbeGetTableDataV2(int arg1, String arg2, String arg3,
                                             String arg4, String arg5, long[] arg6) {
        throw new RuntimeException("dbeGetTableDataV2 not implemented");
    }

    public static String[] dbeGetNames(int arg1, int arg2) {
        throw new RuntimeException("dbeGetNames not implemented");
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

    public static Object[] dbeGetSummary(int arg1, long[] arg2, int arg3, int arg4) {
        throw new RuntimeException("dbeGetSummary not implemented");
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

    public static String dbeGetName(int arg0, int arg1) {
        throw new RuntimeException("dbeGetName not implemented");
    }

    public static long dbeGetStartTime(int arg0, int arg1) {
        throw new RuntimeException("dbeGetStartTime not implemented");
    }

    public static long dbeGetEndTime(int arg0, int arg1) {
        throw new RuntimeException("dbeGetEndTime not implemented");
    }

    public static int dbeGetClock(int arg0, int arg1) {
        throw new RuntimeException("dbeGetClock not implemented");
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

    public static String dbeGetObjNameV2(int arg1, long arg2) {
        throw new RuntimeException("dbeGetObjNameV2 not implemented");
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
