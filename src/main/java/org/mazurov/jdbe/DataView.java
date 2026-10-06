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

import java.util.List;

public class DataView {

    private static final int MAX_SORT_DIMENSIONS = 10;

    public enum DataViewType {
        DV_NORMAL,      // filterable, sortable
        DV_IMMUTABLE,   // reflects exact data in DataDescriptor
        DV_EXT_MANAGED  // sortable.  index[] entries managed externally.
    }

    DataDescriptor ddscr;
    long ddsize;
    LargeArrays.LargeLongArray index; // sorted vector of data_id (index into dDscr)
//            #define DATA_SORT_EOL ((Data *) -1)     /* marks end of sortedBy[] array */
    Data[] sortedBy; // columns for sort
//    FilterExp filter;
    DataViewType type;

    public DataView (DataDescriptor ddscr) {
        this(ddscr, DataViewType.DV_NORMAL);
    }

    public DataView(DataDescriptor ddscr, DataViewType type) {
        this.ddscr = ddscr;
        this.type = type;
        sortedBy = new Data[MAX_SORT_DIMENSIONS + 1];

        switch (type) {
            case DV_IMMUTABLE:
                ddsize = ddscr.getSize();
                index = null;
                break;
            case DV_NORMAL:
            case DV_EXT_MANAGED:
                ddsize = 0;
                index = new LargeArrays.LargeLongArray();
                break;
        }
//        Arrays.fill(sortedBy, DATA_SORT_EOL);
//        filter = null;
    }

    public DataDescriptor getDataDescriptor() {
        return ddscr;
    }

    public void appendDataDescriptorId(long pkt_id /* ddscr index */) {
        if (type != DataViewType.DV_EXT_MANAGED)
            return; // updates allowed only on externally managed DataViews
        if (pkt_id < 0 || pkt_id >= ddscr.getSize())
            return; // error!
        index.append(pkt_id);
    }

    public void setDataDescriptorValue(int prop_id, long pkt_id, long val) {
        ddscr.setValue(prop_id, pkt_id, val);
    }

    public long getDataDescriptorValue (int prop_id, long pkt_id) {
        return ddscr.getLongValue(prop_id, pkt_id);
    }

    public List<PropDescr> getProps() {
        return ddscr.getProps();
    }

    public PropDescr getProp(Enums.Prop_type prop_id) {
        return ddscr.getProp(prop_id);
    }

    public boolean checkUpdate() {
        throw new RuntimeException("DataView.checkUpdate() not implemented");
    }

}
