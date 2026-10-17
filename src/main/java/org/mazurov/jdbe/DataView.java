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

import java.util.Arrays;
import java.util.List;

public class DataView {

    private static final int MAX_SORT_DIMENSIONS = 10;
    private static final Data DATA_SORT_EOL = null;     /* marks end of sortedBy[] array */

    public enum DataViewType {
        DV_NORMAL,      // filterable, sortable
        DV_IMMUTABLE,   // reflects exact data in DataDescriptor
        DV_EXT_MANAGED  // sortable.  index[] entries managed externally.
    }

    private DataDescriptor ddscr;
    private long ddsize;
    private LargeArrays.LargeLongArray index; // sorted vector of data_id (index into dDscr)
    Data[] sortedBy; // columns for sort
    private int sortColCount = 0;
    private Expression filter;
    private Expression.Context filterCtx;
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
        Arrays.fill(sortedBy, DATA_SORT_EOL);
    }

    public void setFilter(Expression filter, Expression.Context ctx) {
        this.filter = filter;
        this.filterCtx = ctx;
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

    public long getIdByIdx(long idx) {
        return index == null ? idx : index.fetch(idx);
    }

    public int getIntValue(int prop_id, long idx) {
        return ddscr.getIntValue(prop_id, getIdByIdx(idx));
    }

    public long getLongValue(int prop_id, long idx) {
        return ddscr.getLongValue(prop_id, getIdByIdx(idx));
    }

    public long getULongValue(int prop_id, long idx) {
        return ddscr.getULongValue(prop_id, getIdByIdx(idx));
    }

    public Object getObjValue(int prop_id, long idx) {
        return ddscr.getObjValue(prop_id, getIdByIdx(idx));
    }

    public boolean checkUpdate() {
        long newSize = ddscr.getSize();
        if (ddsize == newSize)
            return false;
        if (index == null)
            return false;
        if (type == DataViewType.DV_EXT_MANAGED)
            return false;
        boolean updated = false;
        DataView tmpView = filter != null ? ddscr.createImmutableView() : null;
        while (ddsize < newSize) {
            if (filter == null) {
                index.append(ddsize);
            } else {
                filterCtx.put(tmpView, ddsize);
                if (filter.passes(filterCtx))
                    index.append(ddsize);
            }
            updated = true;
            ddsize += 1;
        }
        return updated;
    }

    public long getSize() {
        if (checkUpdate() && sortColCount > 0) {
            // note: after new filter is set, getSize() incurs cost of
            // sorting even if caller isn't interested in sort
            reorderIndex();
        }
        if (index == null)
            return ddscr.getSize();
        return index.size();
    }

    public void sort (int props[], int prop_count) {
        if (index == null) {
            return;
        }
        assert (prop_count >= 0 && prop_count < MAX_SORT_DIMENSIONS);
        boolean sort_changed = false; // see if sort has changed...
        for (int ii = 0; ii <= prop_count; ii++) {
            // sortedBy size is prop_count+1
            Data data;
            if (ii == prop_count)
                data = DATA_SORT_EOL; // special end of array marker
            else
                data = ddscr.getData(props[ii]);
            if (sortedBy[ii] != data) {
                sortedBy[ii] = data;
                sort_changed = true;
            }
        }
        sortColCount = prop_count;
        if (!checkUpdate() && !sort_changed)
            return;
        reorderIndex();
    }

    private void reorderIndex() {
        int n = (int) index.size();
        Long[] boxed = new Long[n];
        for (int i = 0; i < n; i++)
            boxed[i] = index.fetch(i);
        Arrays.sort(boxed, this::compareRows);
        for (int i = 0; i < n; i++)
            index.store(i, boxed[i]);
    }

    private int compareRows(long id1, long id2) {
        for (int ii = 0; ii < sortColCount; ii++) {
            Data d = sortedBy[ii];
            if (d == null)
                continue; // property doesn't exist in data; compare always matches
            int res = d.cmpValues(id1, id2);
            if (res != 0)
                return res;
        }
        return 0;
    }

    private int tvalcmp(long data_id, Data.Datum[] valColumns) {
        for (int ii = 0; ii < sortColCount; ii++) {
            Data d = sortedBy[ii];
            if (d == null)
                continue; // property doesn't exist in data; compare always matches
            int res = d.cmpDatumValue(data_id, valColumns[ii]);
            if (res != 0)
                return res;
        }
        return 0;
    }

    private boolean idxRootDimensionsMatch(long idx, Data.Datum[] valColumns) {
        if (idx < 0 || idx >= index.size())
            return false;
        long data_id = index.fetch(idx);
        for (int ii = 0; ii < sortColCount - 1; ii++) { // all but the leaf (last) column
            Data d = sortedBy[ii];
            if (d == null)
                continue; // property doesn't exist in data; compare always matches
            int res = d.cmpDatumValue(data_id, valColumns[ii]);
            if (res != 0)
                return false;
        }
        return true;
    }

    public long getIdxByVals(Data.Datum[] valColumns, Enums.Relation rel) {
        return getIdxByVals(valColumns, rel, -1, -1);
    }

    public long getIdxByVals(Data.Datum[] valColumns, Enums.Relation rel, long minIdx, long maxIdx) {
        if (index == null || sortColCount == 0)
            return -1;

        long lo = minIdx < 0 ? 0 : minIdx;
        long hi = (maxIdx < 0 || maxIdx >= index.size()) ? index.size() - 1 : maxIdx;

        long md = -1;
        while (lo <= hi) {
            md = (lo + hi) / 2;
            int cmp = tvalcmp(index.fetch(md), valColumns);
            if (cmp < 0) {
                lo = md + 1;
                continue;
            } else if (cmp > 0) {
                hi = md - 1;
                continue;
            }
            // cmp == 0, we have an exact match
            switch (rel) {
                case REL_LT -> hi = md - 1; // continue searching
                case REL_GT -> lo = md + 1; // continue searching
                case REL_LTEQ, REL_GTEQ, REL_EQ -> {
                    // note: "md" may not be deterministic if multiple matches exist
                    return md; // a match => done.
                }
            }
        }

        // no exact match found
        switch (rel) {
            case REL_LT, REL_LTEQ -> md = hi;
            case REL_GT, REL_GTEQ -> md = lo;
            case REL_EQ -> {
                return -1;
            }
        }
        return idxRootDimensionsMatch(md, valColumns) ? md : -1;
    }
}
