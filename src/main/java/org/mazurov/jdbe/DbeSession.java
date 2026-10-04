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

    private List<LoadObject> lobjs = new ArrayList<>();       // Auxiliary list of LoadObjects


    private DbeSession(Settings settings) {
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

        init();
    }

    private void init() {
        // make sure the metric list is initialized
        register_metric(BaseMetric.Type.SIZES);
        register_metric(BaseMetric.Type.ADDRESS);
        register_metric(BaseMetric.Type.ONAME);
    }

    private void reset() {
//        loadObjMap->reset ();
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
//        objs->destroy ();
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

    public static void createSession(Settings settings) {
        INSTANCE = new DbeSession(settings);
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

    public Experiment get_exp(int exp_ind) {
        if (exp_ind < 0 || exp_ind >= exps.size()) {
            return null;
        }
        Experiment exp = exps.get(exp_ind);
//        exp->setExpIdx(exp_ind);
        return exp;
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

    public String setExperimentsGroups(String[][] groups) {
        StringBuilder sb = new StringBuilder();
        for (String[] names : groups) {
//            ExpGroup *grp;
//            if (names.length == 1) {
//                grp = new ExpGroup(names[0]);
//            } else {
//                char *nm = dbe_sprintf (GTXT ("Group %d"), i + 1);
//                grp = new ExpGroup(nm);
//                free (nm);
//            }
//            expGroups->append(grp);
//            grp->groupId = expGroups->size ();

            for (String path : names) {
                int len = path.length();
                if ((len > 4) && path.endsWith(".erg")) {
//                    String[] lst = get_group_or_expt(path);
//                    for (int j1 = 0; j1 < lst->size (); j1++)
//                    {
//                        Experiment *exp = new Experiment ();
//                        append (exp);
//                        open_experiment (exp, lst->get (j1));
//                        if (exp->get_status () == Experiment::FAILURE)
//                            append_mesgs (&sb, path, exp);
//                    }
                } else {
//                    Experiment *exp = new Experiment ();
//                    append(exp);
//                    open_experiment(exp, path);
//                    if (exp->get_status () == Experiment::FAILURE) {
//                        append_mesgs( & sb, path, exp);
//                    }
                }
            }
        }

        for (DbeView dbev : views.values()) {
//            dbev.update_advanced_filter ();
//            int cmp = dbev->get_settings ()->get_compare_mode ();
//            dbev->set_compare_mode(CMP_DISABLE);
//            dbev->set_compare_mode(cmp);
        }
        return sb.isEmpty() ? null : sb.toString();
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
//        if (expr_spec == null) {
//            expr_spec = "";
//        }
//        QL::Result result (expr_spec);
//        QL::Parser qlparser (result);
//        if (qlparser.parse() != 0)
//            return null;
//        return result();
        return null;
    }

    public boolean has_java() {
        for (Experiment exp : exps) {
            if (exp.hasJava()) {
                return true;
            }
        }
        return false;
    }

    public String[] list_mach_models() {
        return new String[0];
    }

    private int findIndexSpaceByName(String mname) {
        for (int idx = 0; idx < dyn_indxobj.size(); ++idx) {
            IndexObject.IndexObjType_t mt = dyn_indxobj.get(idx);
            if (mt.name.equalsIgnoreCase(mname)) {
                return idx;
            }
        }
        return -1;
    }

    // Static function to define a new index object type
    private String indxobj_define(String mname, String i18nname, String index_expr_str,
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

}
