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

import static org.mazurov.jdbe.DbeStructs.*;
import static org.mazurov.jdbe.Enums.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbeSession {

    private static DbeSession INSTANCE;
    private Settings settings;
    private Map<Integer, DbeView> views = new HashMap<>();
    private List<Experiment> exps = new ArrayList<>();
    private List<BaseMetric> reg_metrics = new ArrayList<>();
    private BaseMetricTreeNode reg_metrics_tree; // Hierarchy of BaseMetrics

    private List<Map<Long, Histable>> idxobjs = new ArrayList<>();
    private List<IndexObject.IndexObjType_t> dyn_indxobj; // Index Object definitions
    private int dyn_indxobj_indx;

    private List<Histable> objs = new ArrayList<>();          // All Histable objects, indexed by id
    private List<LoadObject> lobjs = new ArrayList<>();       // Auxiliary list of LoadObjects
    private Map<String, LoadObject> loadObjMap = new HashMap<>(); // keyed by pathname
    private List<String> search_path = new ArrayList<>();
    private List<String> classpath = new ArrayList<>();

    // the platform gprofng itself is running on (compared against an experiment's
    // recorded platform, e.g. to decide whether byte-swapping is needed)
    public static final Platform_t platform = detectPlatform();

    private static Platform_t detectPlatform() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("sparc"))
            return Platform_t.Sparc;
        if (arch.contains("aarch64") || arch.contains("arm64"))
            return Platform_t.Aarch64;
        if (arch.contains("riscv"))
            return Platform_t.RISCV;
        return Platform_t.Intel;
    }


    public Settings get_settings() {
        return settings;
    }

    private DbeSession(Settings settings, boolean ipcOrRdtMode) {
        // Matches native's DbeSession::DbeSession (DbeSession.cc:112): self-assigns the
        // singleton pointer as the very first thing, before read_rc()/indxobj_define()
        // run below -- both reach for DbeSession.getInstance() (INDXOBJDEF's
        // DbeSession.indxobj_define, in particular), which would otherwise still be null
        // mid-construction.
        INSTANCE = this;
        this.settings = new Settings(settings);

        // define Index objects
        dyn_indxobj = new ArrayList<>();
        dyn_indxobj_indx = 0;
        String s = String.format("((EXPID_CMP<<%d) | THRID)",
                (long) IndexObject.INDXOBJ_EXPID_SHIFT);
        indxobj_define("Threads", "Threads", s, null, null);
        indxobj_define("CPUs", "CPUs", "(CPUID)", null, null);
        indxobj_define("Samples", "Samples", "(SAMPLE_MAP)", null, null);
        indxobj_define("GCEvents", "GCEvents", "(GCEVENT_MAP)", null, null);
        indxobj_define("Seconds", "Seconds", "(TSTAMP/1000000000)", null, null);
        indxobj_define("Processes", "Processes", "(EXPID_CMP)", null, null);
        s = String.format("((EXPGRID<<%d) | (EXPID<<%d))",
                IndexObject.INDXOBJ_EXPGRID_SHIFT,
                IndexObject.INDXOBJ_EXPID_SHIFT);
        indxobj_define("Experiment_IDs", "Experiment_IDs", s, null, null);

        this.settings.read_rc(ipcOrRdtMode);
        init();
    }

    private void init() {
        // make sure the metric list is initialized
        register_metric(BaseMetric.Type.SIZES);
        register_metric(BaseMetric.Type.ADDRESS);
        register_metric(BaseMetric.Type.ONAME);

        // Matches native's DbeSession::init() (DbeSession.cc:521): apply the search
        // path accumulated by read_rc()'s ADDPATH directives (str_search_path stays
        // null, and this is a no-op, if none were configured).
        set_search_path(settings.str_search_path, true);
    }

    private void reset() {
        loadObjMap.clear();
//
//        for (DbeView dbev : views.values()) {
//            dbev.reset();
//        }
//
//        destroy_map (DbeFile *, dbeFiles);
//        destroy_map (DbeJarFile *, dbeJarFiles);
        exps.clear();
        lobjs.clear();      // all LoadObjects belong to objs
//        dobjs->destroy ();    // deletes d_unknown and d_total as well
        objs.clear();
//        comp_lobjs->clear ();
//        comp_dbelines->clear ();
//        comp_sources->clear ();
//        sourcesMap->clear ();
//        sources->reset ();

        // Delete the data object name hash table.
//        for (int i = 0; i < HTableSize; i++)
//        {
//            List *list = dnameHTable[i];
//            while (list)
//            {
//                List *tmp = list;
//                list = list->next;
//                delete tmp;
//            }
//        }
//        delete[] dnameHTable;

        // IndexObect definitions remain, objects themselves may go
        for (Map<Long,Histable> v : idxobjs) {
            if (v != null) {
                v.values().clear();
                v.clear();
            }
        }
        init ();
    }

    public void reset_data() {
        for (Map<Long, Histable> v : idxobjs) {
            if (v != null) {
                v.values().clear();
                v.clear();
            }
        }
    }

    public static void createSession(Settings settings, boolean ipcOrRdtMode) {
        new DbeSession(settings, ipcOrRdtMode); // self-assigns INSTANCE; see the ctor
    }

    public static DbeSession getInstance() {
        return INSTANCE;
    }

    public boolean is_interactive() {
        return false;
    }
    public String get_mach_model() {
        return null;
    }

    public List<LoadObject> get_LoadObjects() {
        return lobjs;
    };

    public int nexps() {
        return exps.size();
    }

    // Mirrors native's DbeSession::get_clock (DbeSession.cc:2062-2083).
    public int get_clock(int whichexp) {
        if (whichexp != -1) {
            Experiment exp = get_exp(whichexp);
            return exp != null ? exp.clock : 0;
        }
        for (int i = 0; i < nexps(); i++) {
            Experiment exp = get_exp(i);
            if (exp != null && exp.clock != 0)
                return exp.clock;
        }
        return 0;
    }

    public int ngoodexps() {
        return exps.size();
    }

    // Mirrors native's DbeSession::is_datamode_available (DbeSession.cc:1896-1907): this
    // port has no dataspace subsystem, so no experiment ever has dataspaceavail set.
    public boolean is_datamode_available() {
        return false;
    }

    // Mirrors native's DbeSession::is_timeline_available (DbeSession.cc:1974-1986),
    // matching the is_leaklist_available/is_heapdata_available/etc. pattern just above.
    public boolean is_timeline_available() {
        for (Experiment exp : exps) {
            if (exp.timelineavail)
                return true;
        }
        return false;
    }

    // Mirrors native's DbeSession::is_racelist_available (DbeSession.cc:1948-1959).
    public boolean is_racelist_available() {
        for (Experiment exp : exps) {
            if (exp.racelistavail)
                return true;
        }
        return false;
    }

    // Mirrors native's DbeSession::is_deadlocklist_available (DbeSession.cc:1961-1972).
    public boolean is_deadlocklist_available() {
        for (Experiment exp : exps) {
            if (exp.deadlocklistavail)
                return true;
        }
        return false;
    }

    public Experiment get_exp(int exp_ind) {
        if (exp_ind < 0 || exp_ind >= exps.size()) {
            return null;
        }
        Experiment exp = exps.get(exp_ind);
//        exp->setExpIdx(exp_ind);
        return exp;
    }

    public int find_experiment(String path) {
        for (int index = 0; index < exps.size(); index++) {
            if (exps.get(index).get_expt_name().equals(path))
                return index;
        }
        return -1;
    }

    public int createView(int index, int cloneIndex) {
        if (getView(index) != null) {
            throw new IllegalArgumentException("DbeView[" + index + "] exists");
        }
        DbeView oldView = getView(cloneIndex);
        DbeView newView;
        if (oldView == null) {
            newView = new DbeView(settings, index);
        } else {
            newView = new DbeView(oldView, index);
        }
        views.put(index, newView);
        return index;
    }

    public DbeView getView(int index) {
        return views.get(index);
    }

//    private void append (Experiment exp) {
//        exp.setExpIdx (exps->size ());
//        exp->setUserExpId (++user_exp_id_counter);
//        exps->append(exp);
//        if (exp->founder_exp)
//        {
//            if (exp->founder_exp->children_exps == NULL)
//                exp->founder_exp->children_exps = new Vector<Experiment *>;
//            exp->founder_exp->children_exps->append (exp);
//            if (exp->founder_exp->groupId > 0)
//            {
//                exp->groupId = exp->founder_exp->groupId;
//                expGroups->get (exp->groupId - 1)->append (exp);
//            }
//        }
//        if (exp->groupId == 0)
//        {
//            long ind = VecSize (expGroups);
//            if (ind > 0)
//            {
//                ExpGroup *gr = expGroups->get (ind - 1);
//                exp->groupId = gr->groupId;
//                gr->append (exp);
//            }
//        }
//    }

    private static final String SP_GROUP_HEADER = "#analyzer experiment group";

    public ArrayList<String> get_group_or_expt(String path) {
        ArrayList<String> exp_list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.startsWith(SP_GROUP_HEADER)) {
                // it's not an experiment group
                exp_list.add(canonical_path(path));
            } else {
                // it is an experiment group, read the list to get them all
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) != '#') {
                        String name = line.trim().split("\\s+")[0];
                        if (!name.isEmpty())
                            exp_list.add(canonical_path(name));
                    }
                }
            }
        } catch (IOException e) {
            // not readable (yet) -- treat as a plain experiment path
            exp_list.add(canonical_path(path));
        }
        return exp_list;
    }

    private static String canonical_path(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return path;
        }
    }

    // TODO: does not yet track ExpGroup (multi-experiment comparison groups; that
    // subsystem isn't ported) or refresh the compare-mode-derived metrics
    // (update_advanced_filter/add_compare_metrics), neither of which matter for a
    // single ungrouped experiment.
    public String setExperimentsGroups(String[][] groups) {
        StringBuilder sb = new StringBuilder();
        for (String[] names : groups) {
            for (String path : names) {
                if (path.length() > 4 && path.endsWith(".erg")) {
                    for (String p : get_group_or_expt(path)) {
                        Experiment exp = new Experiment();
                        append(exp);
                        open_experiment(exp, p);
                        if (exp.get_status() == Experiment.Exp_status.FAILURE)
                            append_mesgs(sb, path, exp);
                    }
                } else {
                    Experiment exp = new Experiment();
                    append(exp);
                    open_experiment(exp, path);
                    if (exp.get_status() == Experiment.Exp_status.FAILURE)
                        append_mesgs(sb, path, exp);
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void append(Experiment exp) {
        exp.setExpIdx(exps.size());
        exp.setUserExpId(exps.size() + 1);
        exps.add(exp);
    }

    private void open_experiment(Experiment exp, String path) {
        exp.open(path);
        if (exp.get_status() != Experiment.Exp_status.FAILURE)
            exp.open_epilogue();

        for (DbeView dbev : views.values())
            dbev.add_experiment(exp.getExpIdx(), true);
    }

    private void append_mesgs(StringBuilder sb, String path, Experiment exp) {
        if (exp.fetch_errors() != null) {
            sb.append(path).append(": ").append(Emsg.pr_mesgs(exp.fetch_errors(), "", ""));
        }
        if (exp.fetch_warnings() != null) {
            sb.append(path).append(": ");
            sb.append(is_interactive()
                    ? "Experiment has warnings, see experiment panel for details\n"
                    : "Experiment has warnings, see header for details\n");
        }
        // TODO: descendant-experiment ("has N descendant(s)...") notice skipped;
        // depends on children_exps, which isn't tracked yet.
    }

    BaseMetric find_metric(BaseMetric.Type type, String cmd, String expr_spec) {
        for (BaseMetric bm : reg_metrics) {
            if (bm.get_type() == type && (expr_spec == null || expr_spec.equals(bm.get_expr_spec()))) {
                if ((type == BaseMetric.Type.DERIVED || type == BaseMetric.Type.HWCNTR)
                        && cmd != null && cmd.equals(bm.get_cmd())) {
                    continue;
                }
                return bm;
            }
        }
        return null;
    }

    private void insert_metric(BaseMetric mtr, List<BaseMetric> mlist) {
        if (!mtr.hasFlavor(BaseMetric.STATIC)) {
            // insert in front of the first STATIC
            for (int i = 0; i < mlist.size(); ++i) {
                BaseMetric m = mlist.get(i);
                if (m.hasFlavor(BaseMetric.STATIC)) {
                    mlist.add(i, mtr);
                    return;
                }
            }
        }
        mlist.add(mtr);
    }

    BaseMetric register_metric(BaseMetric.Type type) {
        BaseMetric m = find_metric(type, null, null);
        if (m != null) {
            return m;
        }
        m = new BaseMetric(type);
        insert_metric(m, reg_metrics);
        update_metric_tree(m);
        return m;
    }


    public List<BaseMetric> get_base_reg_metrics() {
        List<BaseMetric> mlist = new ArrayList<>();
        for (BaseMetric metric : reg_metrics) {
            if (metric.get_expr_spec() == null) {
                mlist.add(metric);
            }
        }
        return mlist;
    }

    public BaseMetricTreeNode get_reg_metrics_tree() {
        if (reg_metrics_tree == null) {
            // Can't init earlier because BaseMetric() requires DbeSession::ql_parse
            reg_metrics_tree = new BaseMetricTreeNode();
        }
        return reg_metrics_tree;
    }

    private void update_metric_tree(BaseMetric m) {
        get_reg_metrics_tree().register_metric(m);
    }

    public BaseMetric register_metric_expr(BaseMetric.Type type, String cmd, String expr_spec) {
        BaseMetric m = find_metric(type, cmd, expr_spec);
        if (m != null) {
            return m;
        }
        BaseMetric bm = find_metric(type, cmd, null); // clone this version
        m = new BaseMetric(bm);
        m.set_expr_spec(expr_spec);
        insert_metric(m, reg_metrics);
        return m;
    }

    public Expression ql_parse (String expr_spec) {
//        TODO not implemented
        if (expr_spec == null) {
            expr_spec = "";
        }
//        QL::Result result (expr_spec);
//        QL::Parser qlparser (result);
//        if (qlparser.parse() != 0)
//            return null;
//        return result();
        return new Expression();
    }

    public boolean has_java() {
        for (Experiment exp : exps) {
            if (exp.hasJava()) {
                return true;
            }
        }
        return false;
    }

    public boolean is_leaklist_available() {
        for (Experiment exp : exps) {
            if (exp.leaklistavail)
                return true;
        }
        return false;
    }

    public boolean is_heapdata_available() {
        for (Experiment exp : exps) {
            if (exp.heapdataavail)
                return true;
        }
        return false;
    }

    public boolean is_iodata_available() {
        for (Experiment exp : exps) {
            if (exp.iodataavail)
                return true;
        }
        return false;
    }

    public boolean is_ifreq_available() {
        for (Experiment exp : exps) {
            if (exp.ifreqavail)
                return true;
        }
        return false;
    }

    public String[] list_mach_models() {
        return new String[0];
    }

    public int findIndexSpaceByName(String mname) {
        for (int idx = 0; idx < dyn_indxobj.size(); ++idx) {
            IndexObject.IndexObjType_t mt = dyn_indxobj.get(idx);
            if (mt.name.equalsIgnoreCase(mname)) {
                return idx;
            }
        }
        return -1;
    }

    public String getIndexSpaceName(int index) {
        if (index < 0 || index >= dyn_indxobj.size())
            return null;
        return dyn_indxobj.get(index).name;
    }

    public Module createModule(LoadObject lo, String nm) {
        Module mod = new Module();
        objs.add(mod);
        mod.id = objs.size() - 1;
        mod.loadobject = lo;
        mod.set_name(nm != null ? nm : "<Unknown>");
        lo.seg_modules.add(mod);
        return mod;
    }

    // Mirrors native's DbeSession::findObjectById(uint64_t) (DbeSession.h:278-283):
    // every Histable (Function, Module, LoadObject, ...) is appended to `objs` at
    // creation time with id == its index, so this is just an index lookup.
    public Histable findObjectById(long id) {
        return (id >= 0 && id < objs.size()) ? objs.get((int) id) : null;
    }

    public Function createFunction() {
        Function func = new Function(objs.size());
        objs.add(func);
        return func;
    }

    public JMethod createJMethod() {
        JMethod func = new JMethod(objs.size());
        objs.add(func);
        return func;
    }

    private Function unknownFunction;

    // Session-wide placeholder for PCs that resolve to no known LoadObject at all
    // (e.g. a corrupt/truncated stack, or one of the SP_*_MARKER sentinel values).
    // Matches native's f_unknown (DbeSession.cc), except created lazily on first use
    // rather than eagerly for every session.
    public Function getUnknownFunction() {
        if (unknownFunction == null) {
            unknownFunction = createFunction();
            unknownFunction.set_name("<Unknown>");
            unknownFunction.flags |= Function.FUNC_FLAG_SIMULATED;
        }
        return unknownFunction;
    }

    private Function jUnknownFunction;

    // Session-wide placeholder specifically for Java-stack (uidj) resolution
    // failures (mid == 0, or the methodId isn't found in jmaps) -- distinct from the
    // general <Unknown>. Matches native's DbeSession::get_JUnknown_Function()
    // (DbeSession.cc:762-775).
    public Function getJUnknownFunction() {
        if (jUnknownFunction == null) {
            jUnknownFunction = createFunction();
            jUnknownFunction.set_name("<no Java callstack recorded>");
            jUnknownFunction.flags |= Function.FUNC_FLAG_SIMULATED;
        }
        return jUnknownFunction;
    }

    private Function jvmSystemFunction;

    // Session-wide placeholder for samples whose thread isn't a genuine user Java
    // thread (unregistered entirely, or registered but flagged as a JVM-internal
    // "system" thread -- GC/compiler/etc) -- used regardless of whether that sample
    // also has a resolvable Java or native stack. Matches native's
    // DbeSession::get_jvm_Function() (DbeSession.cc:777-787).
    private Function totalFunction;

    // The <Total> aggregate row (grand total across every sample). Matches native's
    // DbeSession::f_total (DbeSession.cc:~686-711), simplified: native also creates a
    // matching <Total> LoadObject/Module, which we don't need since nothing here
    // groups by module/load-object.
    public Function getTotalFunction() {
        if (totalFunction == null) {
            totalFunction = createFunction();
            totalFunction.set_name("<Total>");
            totalFunction.flags |= Function.FUNC_FLAG_SIMULATED;
        }
        return totalFunction;
    }

    public Function getJvmSystemFunction() {
        if (jvmSystemFunction == null) {
            jvmSystemFunction = createFunction();
            jvmSystemFunction.set_name("<JVM-System>");
            jvmSystemFunction.flags |= Function.FUNC_FLAG_SIMULATED;
        }
        return jvmSystemFunction;
    }

    // Simplified relative to native: real gprofng de-duplicates load objects across
    // experiments by pathname+checksum (loadObjMap->sync_create_item); we only need
    // single-experiment, name-keyed lookup for now.
    public LoadObject createLoadObject(String pathname) {
        LoadObject lo = loadObjMap.get(pathname);
        if (lo != null)
            return lo;
        lo = new LoadObject(pathname);
        loadObjMap.put(pathname, lo);
        append(lo);
        return lo;
    }

    private void append(LoadObject lo) {
        objs.add(lo);
        lo.id = objs.size() - 1;
        lobjs.add(lo);
        lo.seg_idx = lobjs.size() - 1;
    }

    public Prop_type registerPropertyName(String name) {
        if (name == null)
            return Prop_type.PROP_NONE;
        try {
            return Prop_type.valueOf("PROP_" + name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Prop_type.PROP_NONE;
        }
    }

    public boolean check_ignore_fs_warn() {
        return settings.get_ignore_fs_warn();
    }

    // Static function to define a new index object type
    String indxobj_define(String mname, String i18nname, String index_expr_str,
                                   String short_description, String long_description) {
        if (mname == null || mname.isEmpty()) {
            return "No index object type name has been specified.";
        }
        if (!Character.isAlphabetic(mname.charAt(0))) {
            return String.format("Index Object type name %s does not begin with an alphabetic character", mname);
        }
        for (int i = 1; i < mname.length(); ++i) {
            char ch = mname.charAt(i);
            if (!Character.isAlphabetic(ch) && ch != '_') {
                return String.format("Index Object type name %s contains a non-alphanumeric character", mname);
            }
        }

        // make sure the name is not in use
//        if (MemorySpace::findMemSpaceByName (mname) != NULL)
//        return dbe_sprintf (GTXT ("Memory/Index Object type name %s is already defined"),
//                mname);

        int idxx = findIndexSpaceByName(mname);
        if (idxx >= 0) {
            IndexObject.IndexObjType_t mt = dyn_indxobj.get(idxx);
            if (mt.index_expr_str.equals(index_expr_str)) {
                // It's a redefinition, but the new definition is the same
                return null;
            }
            return String.format("Memory/Index Object type name %s is already defined", mname);
        }
        if (index_expr_str == null)
            return "No index-expr has been specified.";
        if (index_expr_str.length() == 0) {
            return String.format("Index Object index expression is invalid: %s", index_expr_str);
        }

        // verify that the index expression parses correctly
        Expression expr = ql_parse (index_expr_str);
        if (expr == null) {
            return String.format("Index Object index expression is invalid: %s", index_expr_str);
        }

        // It's OK, create the new table entry
        IndexObject.IndexObjType_t tot = new IndexObject.IndexObjType_t();
        tot.type = dyn_indxobj_indx++;
        tot.name = mname;
        tot.i18n_name = i18nname;
        tot.short_description = short_description;
        tot.long_description = long_description;
        tot.index_expr_str = index_expr_str;
        tot.index_expr = expr;
        tot.mnemonic = mname.charAt(0);

        // add it to the list
        dyn_indxobj.add(tot);
        idxobjs.add(new HashMap<>());

        // tell the session
        settings.indxobj_define(tot.type, false);

        for (DbeView dbev : views.values()){
            dbev.addIndexSpace(tot.type);
        }
        return null;
    }

    public Object[] getIndxObjDescriptions() {
        int size = dyn_indxobj_indx;
        if (size == 0) {
            return null;
        }
        int[] type = new int[dyn_indxobj_indx];
        String[] desc = new String[dyn_indxobj_indx];
        String[] i18ndesc = new String[dyn_indxobj_indx];
        char[] mnemonic = new char[dyn_indxobj_indx];
        int[] orderList = new int[dyn_indxobj_indx];
        String[] exprList = new String[dyn_indxobj_indx];
        String[] sdesc = new String[dyn_indxobj_indx];
        String[] ldesc = new String[dyn_indxobj_indx];

        for (int i = 0; i < dyn_indxobj.size(); i++) {
            IndexObject.IndexObjType_t tot = dyn_indxobj.get(i);
//            if (tot.memObj == null) {
                type[i] = tot.type;
                desc[i] = tot.name;
                i18ndesc[i] = tot.i18n_name;
                sdesc[i] = tot.short_description;
                ldesc[i] = tot.long_description;
                mnemonic[i] = tot.mnemonic;
                orderList[i] = settings.get_IndxTabOrder().get(i);
                exprList[i] = tot.index_expr_str;
//            }
        }
        Object[] res = new Object[] {
                type, desc, mnemonic, i18ndesc, orderList, exprList, sdesc, ldesc
        };
        return res;
    }

    public List<LoadObject> get_text_segments() {
        List<LoadObject> tlobjs = new ArrayList<>();
        for (LoadObject lo : lobjs) {
            if (lo.type == LoadObject.seg_type.SEG_TEXT) {
                tlobjs.add(lo);
            }
        }
        return tlobjs;
    }

    public List<String> get_search_path() {
        return search_path;
    }

    public boolean add_path(String path) {
        return add_path(path, search_path);
    }

    public boolean add_classpath(String path) {
        return add_path(path, classpath);
    }

    private boolean add_path(String path, List<String> pathes) {
        boolean result = false;
        for (String spath : path.split(":")) {
            if (!spath.isEmpty() && !pathes.contains(spath)) {
                pathes.add(spath);
                result = true;
            }
        }
        return result;
    }

    public void set_search_path(List<String> path, boolean reset) {
        if (reset)
            search_path.clear();
        if (path != null) {
            for (String name : path)
                add_path(name);
        }
        // TODO: set_need_refind() (DbeFile path-resolution cache invalidation) is not
        // yet ported -- no such cache exists in JDBE yet, so there is nothing to
        // invalidate.
    }

    public void set_search_path(String lpath, boolean reset) {
        set_search_path(lpath != null ? Arrays.asList(lpath.split(":")) : null, reset);
    }

    public List<PathMap> get_pathmaps() {
        return settings.get_pathmaps();
    }

    public boolean find_obj(PrintStream dis_file, InputStream inp_file, Histable obj, String name,
                             String sel, Histable.Type type, boolean xdefault) {
        // depends on map_NametoFunction/map_NametoModule/map_NametoLoadObject/
        // map_NametoDataObject (name resolution across the load-object/function/module
        // registry) and an interactive ask_which() prompt; not yet ported.
        throw new RuntimeException("DbeSession.find_obj not implemented");
    }

}
