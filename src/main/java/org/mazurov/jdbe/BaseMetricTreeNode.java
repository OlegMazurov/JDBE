package org.mazurov.jdbe;

import java.util.ArrayList;
import java.util.List;

import static org.mazurov.jdbe.Enums.*;

public class BaseMetricTreeNode {

    static final String UNIT_SECONDS = "SECONDS";
    static final String UNIT_SECONDS_UNAME = "secs.";
    static final String UNIT_BYTES = "BYTES";
    static final String UNIT_BYTES_UNAME = "bytes";

    // Name values for intermediate parent nodes that aren't defined elsewhere
    static final String L1_DURATION = "PROFDATA_TYPE_DURATION";
    static final String L1_DURATION_UNAME = "Experiment Duration";
    static final String L1_GCDURATION = "PROFDATA_TYPE_GCDURATION";
    static final String L1_GCDURATION_UNAME     = "Java Garbage Collection Duration";
    static final String L2_HWC_DSPACE =          "PROFDATA_TYPE_HWC_DSPACE";
    static final String L2_HWC_DSPACE_UNAME     = "Memoryspace Hardware Counters";
    static final String L2_HWC_GENERAL =         "PROFDATA_TYPE_HWC_GENERAL";
    static final String L2_HWC_GENERAL_UNAME    = "General Hardware Counters";
    static final String L1_MPI_STATES = "PROFDATA_TYPE_MPI_STATES";
    static final String L1_MPI_STATES_UNAME = "MPI States";
    static final String L1_OTHER =               "PROFDATA_TYPE_OTHER";
    static final String L1_OTHER_UNAME          = "Derived and Other Metrics";
    static final String L1_STATIC =              "PROFDATA_TYPE_STATIC";
    static final String L1_STATIC_UNAME         = "Static";
    static final String L_CP_TOTAL =             "L_CP_TOTAL";
    static final String L_CP_TOTAL_CPU =         "L_CP_TOTAL_CPU";


    private BaseMetricTreeNode root;    // root of tree
    private BaseMetricTreeNode parent;  // my parent
    private boolean aggregation;        // value is based on children's values
    private String name;                // bm->get_cmd() for metrics, unique string otherwise
    private String uname;               // user-visible text
    private String unit;                // see UNIT_* defines
    private String unit_uname;          // see UNIT_*_UNAME defines
    List<BaseMetricTreeNode> children = new ArrayList<>();  // my children
    private boolean isCompositeMetric;  // value is sum of children
    BaseMetric bm;                      // metric for this node, or null
    boolean registered;                 // metric has been officially registered
    int num_registered_descendents;     // does not include self

    public BaseMetricTreeNode() {
        root = this;
        build_basic_tree();
    }

    BaseMetricTreeNode(BaseMetric item) {
        root = this;
        bm = item;
        name = bm.get_cmd();
        uname = bm.get_username();
        unit = null; //YXXX populate from base_metric (requires updating base_metric)
        unit_uname = null;
    }

    BaseMetricTreeNode(String _name, String _uname, String _unit, String _unit_uname) {
        root = this;
        name = _name;
        uname = _uname;
        unit = _unit;
        unit_uname = _unit_uname;
    }

    private BaseMetricTreeNode TREE_INSERT_DATA_TYPE(ProfData_type t) {
        return add_child(get_prof_data_type_name(t), get_prof_data_type_uname(t));
    }

    private BaseMetricTreeNode find(String _name) {
        if (_name == null) {
            return null;
        }
        if (_name.equals(get_name())) {
            return this;
        }
        if (bm != null && _name.equals(bm.get_cmd())) {
            return this;
        }
        for (BaseMetricTreeNode child : children) {
            BaseMetricTreeNode found = child.find(_name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public BaseMetricTreeNode register_metric(BaseMetric item) {
        BaseMetricTreeNode found = root.find(item.get_cmd());
        if (found == null) {
            switch (item.get_type()) {
                case CP_TOTAL -> found = root.find(L_CP_TOTAL);
                case CP_TOTAL_CPU -> found = root.find(L_CP_TOTAL_CPU);
            }
            if (found != null && found.bm == null) {
                found.bm = item;
            }
        }

        if (found == null) {
            switch (item.get_type()) {
                case HEAP_ALLOC_BYTES:
                case HEAP_ALLOC_CNT:
                case HEAP_LEAK_BYTES:
                case HEAP_LEAK_CNT:
                    found = root.find(get_prof_data_type_name(ProfData_type.DATA_HEAP));
                    break;
                case CP_KERNEL_CPU:
                case CP_TOTAL:
                    found = root.find(get_prof_data_type_name(ProfData_type.DATA_CLOCK));
                    break;
                case CP_LMS_DFAULT:
                case CP_LMS_TFAULT:
                case CP_LMS_KFAULT:
                case CP_LMS_STOPPED:
                case CP_LMS_WAIT_CPU:
                case CP_LMS_SLEEP:
                case CP_LMS_USER_LOCK:
                case CP_TOTAL_CPU:
                    found = root.find(L_CP_TOTAL);
                    break;
                case CP_LMS_USER:
                case CP_LMS_SYSTEM:
                case CP_LMS_TRAP:
                    found = root.find(L_CP_TOTAL_CPU);
                    break;
                case HWCNTR:
                    found = root.find(item.hasFlavor(BaseMetric.DATASPACE) ?
                            L2_HWC_DSPACE : L2_HWC_GENERAL);
                    break;
                case SYNC_WAIT_TIME:
                case SYNC_WAIT_COUNT:
                    found = root.find(get_prof_data_type_name (ProfData_type.DATA_SYNCH));
                    break;
                case OMP_WORK:
                case OMP_WAIT:
                case OMP_OVHD:
                    found = root.find(get_prof_data_type_name(ProfData_type.DATA_OMP));
                    break;
                case IO_READ_TIME:
                case IO_READ_BYTES:
                case IO_READ_CNT:
                case IO_WRITE_TIME:
                case IO_WRITE_BYTES:
                case IO_WRITE_CNT:
                case IO_OTHER_TIME:
                case IO_OTHER_CNT:
                case IO_ERROR_TIME:
                case IO_ERROR_CNT:
                    found = root.find(get_prof_data_type_name(ProfData_type.DATA_IOTRACE));
                    break;
                case ONAME:
                case SIZES:
                case ADDRESS:
                    found = root.find(L1_STATIC);
                    break;
                default:
                    found = root.find(L1_OTHER);
                    break;
            }
            assert (found != null);
            switch (item.get_type()) {
                case CP_TOTAL, CP_TOTAL_CPU -> found.isCompositeMetric = true;
            }
            found = found.add_child(item);
        }
        register_node(found);
        return found;
    }

    void register_node(BaseMetricTreeNode node) {
        if (!node.registered) {
            node.registered = true;
            BaseMetricTreeNode tmp = node.parent;
            while (tmp != null) {
                tmp.num_registered_descendents++;
                tmp = tmp.parent;
            }
        }
    }

    BaseMetricTreeNode add_child(BaseMetric item) {
        return add_child(new BaseMetricTreeNode(item));
    }

    BaseMetricTreeNode add_child(String _name, String _uname,
			       String _unit, String _unit_uname) {
        return add_child (new BaseMetricTreeNode (_name, _uname, _unit, _unit_uname));
    }

    BaseMetricTreeNode add_child(String _name, String _uname) {
        return add_child (new BaseMetricTreeNode (_name, _uname, null, null));
    }
    BaseMetricTreeNode add_child(BaseMetricTreeNode new_node) {
        new_node.parent = this;
        new_node.root = root;
        children.add(new_node);
        return new_node;
    }

    private void build_basic_tree() {
        BaseMetricTreeNode level1, level2;
        // register L1_DURATION here because it has a value but is not a true metric
        register_node(add_child(L1_DURATION, L1_DURATION_UNAME, UNIT_SECONDS,
                UNIT_SECONDS_UNAME));
        register_node(add_child(L1_GCDURATION, L1_GCDURATION_UNAME, UNIT_SECONDS,
                UNIT_SECONDS_UNAME));
        TREE_INSERT_DATA_TYPE(ProfData_type.DATA_HEAP);
        level1 = TREE_INSERT_DATA_TYPE (ProfData_type.DATA_CLOCK);
        level1 = level1.add_child(L_CP_TOTAL, "XXX Total Thread Time");
        level1.isCompositeMetric = true;
        level2 = level1.add_child(L_CP_TOTAL_CPU, "XXX Total CPU Time");
        level2.isCompositeMetric = true;

        add_child(L1_OTHER, L1_OTHER_UNAME);
        level1 = TREE_INSERT_DATA_TYPE (ProfData_type.DATA_HWC);
        level1.add_child(L2_HWC_DSPACE, L2_HWC_DSPACE_UNAME);
        level1.add_child(L2_HWC_GENERAL, L2_HWC_GENERAL_UNAME);
        TREE_INSERT_DATA_TYPE(ProfData_type.DATA_SYNCH);
        TREE_INSERT_DATA_TYPE(ProfData_type.DATA_OMP);
        TREE_INSERT_DATA_TYPE(ProfData_type.DATA_IOTRACE);
        add_child(L1_STATIC, L1_STATIC_UNAME);
    }


    List<BaseMetricTreeNode> get_children() {
        return children;
    }

    boolean is_registered () {
        return registered;
    }

    public int get_num_registered_descendents() {
        return num_registered_descendents;
    }

    public boolean is_composite_metric () {
        return isCompositeMetric;
    }

    public BaseMetric get_BaseMetric() {
        return bm;
    }

    public String get_name() {
        return name;
    }

    public String get_user_name() {
        return uname;
    }

    public String get_unit() {
        return unit;
    }

    public String get_unit_uname() {
        return unit_uname;
    }

    public String get_description() {
//        TODO
//        if (bm != null) {
//            Hwcentry hw_ctr = bm.get_hw_ctr();
//            if (hw_ctr) {
//                return hw_ctr.short_desc;
//            }
//        }
        return null;
    }

    private void int_get_registered_descendents(
            List<BaseMetricTreeNode> dest, boolean nearest_only) {
        if (is_registered()) {
            dest.add(this);
            if (nearest_only) {
                return; // soon as we hit a live node, stop following branch
            }
        }
        for (BaseMetricTreeNode child : get_children()) {
            child.int_get_registered_descendents(dest, nearest_only);
        }
    }

    public void get_nearest_registered_descendents(List<BaseMetricTreeNode> dest) {
        if (dest == null || !dest.isEmpty()) {
            throw new IllegalStateException();
        }
        int_get_registered_descendents(dest, true);
    }

}
