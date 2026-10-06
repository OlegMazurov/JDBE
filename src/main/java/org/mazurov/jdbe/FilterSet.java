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
import java.util.List;

public class FilterSet {
    public static int SAMPLE_FILTER_IDX = 0;
    public static int THREAD_FILTER_IDX = 1;
    public static int LWP_FILTER_IDX = 2;
    public static int CPU_FILTER_IDX = 3;

    DbeView dbev;
    Experiment exp;
    boolean enbl;
    List<Filter.FilterNumeric> dfilter;

    public FilterSet(DbeView dbev, Experiment exp) {
        this.dbev = dbev;
        this.exp = exp;
        this.enbl = false;
        dfilter = new ArrayList<>();
        Filter.FilterNumeric f = new Filter.FilterNumeric(exp, "sample", "Samples");
//        f.prop_name = NTXT ("SAMPLE_MAP");
        dfilter.add(f);
        f = new Filter.FilterNumeric(exp, "thread", "Threads");
//        f.prop_name = NTXT ("THRID");
        dfilter.add(f);
        f = new Filter.FilterNumeric(exp, "LWP", "LWPs");
//        f.prop_name = NTXT ("LWPID");
        dfilter.add(f);
        f = new Filter.FilterNumeric (exp, "cpu", "CPUs");
//        f.prop_name = NTXT ("CPUID");
        dfilter.add(f);
        f = new Filter.FilterNumeric (exp, "gcevent", "GCEvents");
//        f.prop_name = "GCEVENT_MAP";
        dfilter.add(f); // must add new numeric below
    }

    public String get_advanced_filter () {
        throw new RuntimeException("FilterSet.get_advanced_filter not implemented");
    }

    public Filter.FilterNumeric get_filter(int index) {
        if (index < dfilter.size() && index >= 0)
            return dfilter.get(index);
        return null;
    }

    public boolean
    get_enabled () {
        return enbl;
    }

    public void set_enabled (boolean b) {
        enbl = b;
    }

    List<Filter.FilterNumeric> get_all_filters () {
        return dfilter;
    }
}
