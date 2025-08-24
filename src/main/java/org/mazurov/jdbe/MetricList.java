package org.mazurov.jdbe;

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
        // set all metrics visible
        for (Metric m : items) {
            m.enable_all_visbits();
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
