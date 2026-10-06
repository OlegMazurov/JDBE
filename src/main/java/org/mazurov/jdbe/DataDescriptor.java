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

import static org.mazurov.jdbe.Enums.ProfData_type;
import static org.mazurov.jdbe.Enums.VType_type;

import java.util.ArrayList;
import java.util.List;

public class DataDescriptor {
    /*
     * An instance of this class stores the data packets for a specific
     * type of profiling, for example, clock profiling.
     *
     * Each packet consists of values for various properties.
     * For example, a timestamp is a property which is accessed with PROP_TSTAMP.
     *
     * Ideally, DataDescriptor contents are considered immutable after the
     * data is read in.  setValue() should only be used during creation.
     * - The packets are in fixed order.  This allows DataDescriptor <pkt_id>
     *   to be treated as a stable handle.
     * - Sorting/filtering is handled by the DataView class
     * - In the future, if we need to add the ability to append new packets,
     *   we might add a flag to show when the class is immutable and/or appendible
     */

    private final boolean isMaster;
    private final int flags;        // see Data_flag enum
    private final ProfData_type type;
    private final DataDescriptor parent;

    long size;
    boolean resolveFrameInfoDone;

    List<PropDescr> props;
    Data[] data;
//    List<List<Long>> setsTBR; // Sets of unique values

    public DataDescriptor(ProfData_type type, int flags) {
        isMaster = true;
        this.type = type;
        this.flags = flags;
        this.parent = null;

        // master data, shared with reference copies:
        size = 0;
        resolveFrameInfoDone = false;
        props = new ArrayList<>();
        data = new Data[Enums.Prop_type.PROP_LAST.ordinal()];
//        setsTBR = new Vector<Vector<long long>*>;
    }

    public DataDescriptor(ProfData_type type, DataDescriptor dDscr) {
        isMaster = false;
        this.type = type;
        this.flags = dDscr.flags;
        this.parent = dDscr;

        props = dDscr.props;
        data = dDscr.data;
//        setsTBR = dDscr.setsTBR;
    }

    // packets' descriptions
    public int getId() {
        return type.getValue();
    }

    public String getName() {
        return type.name();
    }

    public String getUName() {
        return type.getUserName();
    }

    public List<PropDescr> getProps() {
        return props;
    }

    public PropDescr getProp(Enums.Prop_type prop_id) {
        for (PropDescr propDscr : props) {
            if (propDscr.propID == prop_id) {
                return propDscr;
            }
        }
        return null;
    }

    public long getSize () {
        return parent != null ? parent.size : size;   // number of packets
    }

    long getFlags () {
        return flags;
    }

    // class to provide sorting and filtering
    DataView createView() {
        return new DataView(this);
    }

    DataView createImmutableView() {
        return new DataView(this, DataView.DataViewType.DV_IMMUTABLE);
    }

    DataView createExtManagedView() {
        return new DataView(this, DataView.DataViewType.DV_EXT_MANAGED);
    }

    // packet property values (<pkt_id> is stable packet handle)
    int getIntValue(int prop_id, long idx) {
        Data d = getData(prop_id);
        if (d == null || idx >= d.getSize())
            return 0;
        return d.fetchInt(idx);
    }

    long getULongValue(int prop_id, long idx) {
        Data d = getData(prop_id);
        if (d == null || idx >= d.getSize())
            return 0L;
        return d.fetchULong(idx);
    }

    long getLongValue(int prop_id, long idx) {
        Data d = getData(prop_id);
        if (d == null || idx >= d.getSize())
            return 0L;
        return d.fetchLong(idx);
    }

    Object getObjValue(int prop_id, long idx) {
        Data d = getData(prop_id);
        if (d == null || idx >= d.getSize())
            return 0L;
        return d.fetchObject(idx);

    }

    List<Long> getSet(int prop_id) { // list of sorted, unique values
        throw new RuntimeException("DataDescriptor.getSet() not implemented");
    }

    // table creation/reset
    void addProperty(PropDescr propDscr) { // add property to all packets
        if (propDscr == null)
            return;
//        if (propDscr.propID < 0)
//            return;
        PropDescr oldProp = getProp(propDscr.propID);
        if (oldProp != null) {
            Data.checkCompatibility(propDscr.vtype, oldProp.vtype); //YXXX depends on experiment correctness
            return;
        }
        props.add(propDscr);
        data[propDscr.propID.ordinal()] = Data.newData(propDscr.vtype);
//        setsTBR->store (propDscr->propID, NULL);
    }

    long addRecord() {            // add packet
        if (!isMaster)
            return -1;
        return size++;
    }

    Data getData(int prop_id) {  // get all packets
        if (prop_id < 0 || prop_id >= data.length)
            return null;
        return data[prop_id];
    }

    void setDatumValue(int prop_id, long idx, Data.Datum val) {
        if (idx >= getSize())
            return;
        Data d = getData(prop_id);
        if (d != null) {
            VType_type datum_type = val.type;
            VType_type data_type = d.type();
            Data.checkCompatibility(datum_type, data_type);
            d.setDatumValue(idx, val);
//            List<Long> set = setsTBR->fetch (prop_id);
//            if (set != NULL)// Sets are maintained
//                checkEntity (set, d->fetchLong (idx));
        }
    }

    void setValue(int prop_id, long idx, long val) {
        if (idx >= getSize())
            return;
        Data d = getData(prop_id);
        if (d != null) {
            d.setValue(idx, val);
//            Vector<long long> *set = setsTBR->fetch (prop_id);
//            if (set != NULL)// Sets are maintained
//                checkEntity (set, d->fetchLong (idx));
        }
    }

    void setObjValue(int prop_id, long idx, Object val) {
        if (idx >= getSize())
            return;
        Data d = getData(prop_id);
        if (d != null) {
            d.setObjValue(idx, val);
        }
    }

    void reset() {                // remove all packets (ym: TBR?)
        if (!isMaster)
            return;
        for (int i = 0; i < data.length; i++) {
            Data d = data[i];
            if (d != null) {
                d.reset();
            }
//            List<Long> set = setsTBR.get(i);
//            if (set != null)
//                set.reset ();
        }
        size = 0;
    }

    public void setResolveFrInfoDone () {
        if (parent != null) parent.resolveFrameInfoDone = true;
        else resolveFrameInfoDone = true;
    }

    public boolean isResolveFrInfoDone () {
        return parent != null ? parent.resolveFrameInfoDone : resolveFrameInfoDone;
    }
}
