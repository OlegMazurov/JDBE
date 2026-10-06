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

import static org.mazurov.jdbe.Enums.VType_type;

public abstract class Data {

    static class Datum {
        VType_type type;
        int i;      // INT32/UINT32
        long l;     // INT64/UINT64
        double d;   // DOUBLE
        String str; // STRING
        Object obj; // OBJ
    }

    static Data newData(VType_type vtype) {
        switch (vtype) {
            case TYPE_INT32:
            case TYPE_UINT32:
                return new DataINT32();
            case TYPE_INT64:
            case TYPE_UINT64:
                return new DataINT64();
            case TYPE_OBJ:
            case TYPE_STRING:
                return new DataOBJECT();
            case TYPE_DOUBLE:
                return new DataDOUBLE();
            default:
                return null;
        }
    }

    VType_type type() {
        return VType_type.TYPE_NONE;
    }

    public abstract void reset();

    public abstract long getSize();

    public abstract int fetchInt(long i);

    public abstract long fetchULong(long i);

    public abstract long fetchLong(long i);

    public abstract String fetchString(long i);

    public abstract double fetchDouble(long i);

    public abstract Object fetchObject(long i);

    public abstract void setDatumValue(long i, Datum val);

    public abstract void setValue(long i, long val);

    public abstract void setObjValue(long i, Object val);

    public abstract int cmpValues(long idx1, long idx2);

    public abstract int cmpDatumValue(long idx, Datum val);

    public static void checkCompatibility(VType_type v1, VType_type v2) {
        switch (v1) {
            case TYPE_NONE:
            case TYPE_STRING:
            case TYPE_DOUBLE:
            case TYPE_OBJ:
            case TYPE_DATE:
                assert (v1 == v2);
                break;
            case TYPE_INT32:
            case TYPE_UINT32:
                assert (v2 == VType_type.TYPE_INT32 || v2 == VType_type.TYPE_UINT32);
                break;
            case TYPE_INT64:
            case TYPE_UINT64:
                assert (v2 == VType_type.TYPE_INT64 || v2 == VType_type.TYPE_UINT64);
                break;
            default:
                assert (false);
        }
    }

    static class DataINT32 extends Data {
        LargeArrays.LargeIntArray data = new LargeArrays.LargeIntArray();

        public VType_type type() {
            return VType_type.TYPE_INT32;
        }

        public void reset() {
            data.reset();
        }

        @Override
        public long getSize() {
            return data.size();
        }

        @Override
        public int fetchInt(long i) {
            return data.fetch(i);
        }

        @Override
        public long fetchULong(long i) {
            return data.fetch(i) & 0xFFFFFFFFL; // zero-extend, matching (unsigned long long)(uint32_t) in native
        }

        @Override
        public long fetchLong(long i) {
            return data.fetch(i);
        }

        @Override
        public String fetchString(long i) {
            return String.format("%d", data.fetch(i));
        }

        @Override
        public double fetchDouble(long i) {
            return (double) data.fetch(i);
        }

        @Override
        public Integer fetchObject(long i) {
            return data.fetch(i);
        }

        @Override
        public void setDatumValue(long i, Datum val) {
            data.store(i, val.i);
        }

        @Override
        public void setValue(long i, long val) {
            data.store(i, Math.toIntExact(val));
        }

        @Override
        public void setObjValue(long i, Object val) {
            data.store(i, (Integer) val);
        }

        @Override
        public int cmpValues(long idx1, long idx2) {
            return Integer.compare(data.fetch(idx1), data.fetch(idx2));
        }

        @Override
        public int cmpDatumValue(long idx, Datum val) {
            return Integer.compare(data.fetch(idx), val.i);
        }
    }

    static class DataINT64 extends Data {
        LargeArrays.LargeLongArray data = new LargeArrays.LargeLongArray();

        @Override
        public VType_type type() {
            return VType_type.TYPE_INT64;
        }

        @Override
        public void reset() {
            data.reset();
        }

        @Override
        public long getSize() {
            return data.size();
        }

        @Override
        public int fetchInt(long i) {
            return (int) data.fetch(i);
        }

        @Override
        public long fetchULong(long i) {
            return data.fetch(i);
        }

        @Override
        public long fetchLong(long i) {
            return data.fetch(i);
        }

        @Override
        public String fetchString(long i) {
            return String.format("%d", data.fetch(i));
        }

        @Override
        public double fetchDouble(long i) {
            return (double) data.fetch(i);
        }

        @Override
        public Object fetchObject(long i) {
            return null;
        }

        @Override
        public void setDatumValue(long i, Datum val) {
            data.store(i, val.l);
        }

        @Override
        public void setValue(long i, long val) {
            data.store(i, val);
        }

        @Override
        public void setObjValue(long i, Object val) {
        }

        @Override
        public int cmpValues(long idx1, long idx2) {
            return Long.compare(data.fetch(idx1), data.fetch(idx2));
        }

        @Override
        public int cmpDatumValue(long idx, Datum val) {
            return Long.compare(data.fetch(idx), val.l);
        }
    }

    static class DataOBJECT extends Data {
        LargeArrays.LargeObjectArray data = new LargeArrays.LargeObjectArray();

        @Override
        public VType_type type() {
            return VType_type.TYPE_OBJ;
        }

        @Override
        public void reset() {
            data.reset();
        }

        @Override
        public long getSize() {
            return data.size();
        }

        @Override
        public int fetchInt(long i) {
            return 0;
        }

        @Override
        public long fetchULong(long i) {
            return 0;
        }

        @Override
        public long fetchLong(long i) {
            return 0;
        }

        @Override
        public String fetchString(long i) {
            Object o = data.fetch(i);
            return o == null ? "" : o.toString();
        }

        @Override
        public double fetchDouble(long i) {
            return 0;
        }

        @Override
        public Object fetchObject(long i) {
            return data.fetch(i);
        }

        @Override
        public void setDatumValue(long i, Datum val) {
            data.store(i, val.obj);
        }

        @Override
        public void setValue(long i, long val) {
        }

        @Override
        public void setObjValue(long i, Object val) {
            data.store(i, val);
        }

        @Override
        public int cmpValues(long idx1, long idx2) {
            return 0;
        }

        @Override
        public int cmpDatumValue(long idx, Datum val) {
            return 0;
        }
    }

    static class DataDOUBLE extends Data {
        LargeArrays.LargeDoubleArray data = new LargeArrays.LargeDoubleArray();

        @Override
        public VType_type type() {
            return VType_type.TYPE_DOUBLE;
        }

        @Override
        public void reset() {
            data.reset();
        }

        @Override
        public long getSize() {
            return data.size();
        }

        @Override
        public int fetchInt(long i) {
            return (int) data.fetch(i);
        }

        @Override
        public long fetchULong(long i) {
            return (long) data.fetch(i);
        }

        @Override
        public long fetchLong(long i) {
            return (long) data.fetch(i);
        }

        @Override
        public String fetchString(long i) {
            return String.format("%f", data.fetch(i));
        }

        @Override
        public double fetchDouble(long i) {
            return data.fetch(i);
        }

        @Override
        public Object fetchObject(long i) {
            return null;
        }

        @Override
        public void setDatumValue(long i, Datum val) {
            data.store(i, val.d);
        }

        @Override
        public void setValue(long i, long val) {
            data.store(i, (double) val);
        }

        @Override
        public void setObjValue(long i, Object val) {
        }

        @Override
        public int cmpValues(long idx1, long idx2) {
            return Double.compare(data.fetch(idx1), data.fetch(idx2));
        }

        @Override
        public int cmpDatumValue(long idx, Datum val) {
            return Double.compare(data.fetch(idx), val.d);
        }
    }
}
