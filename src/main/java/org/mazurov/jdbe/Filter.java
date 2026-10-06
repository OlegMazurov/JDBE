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

public class Filter {
    static public class FilterNumeric {
        Experiment exp;
        String cmd;
        String name;
        String pattern;
        String status;

        // First and Last items in selection
        long first;
        long last;

        public FilterNumeric(Experiment exp, String cmd, String name) {
            this.exp = exp;
            this.cmd = cmd;
            this.name = name;
            pattern = null;
            status = null;
//            items = null;
//            prop_name = null;
            first = -1;
            last = -1;
//            nselected = 0;
//            nitems = 0;
        }

        // set or update the range of items first and last
        public void set_range(long findex, long lindex, long total) {
            if (first == findex && last == lindex)
                return;
            first = findex;
            last = lindex;
//            nitems = total;
//            nselected = nitems;
            pattern = null;
            status = null;
        }

        // Return a string representation of the current ranges
        //    E.g. "1-5,7,9,10,12-13,73"
        String get_pattern() {
            throw new RuntimeException("FilterNumeric.get_pattern not implemented");
        }

        // Return a string for the current status: %, range, ...
        //    E.g. "100%" "100% [1-7]"  "25% [1-4]"
        String get_status () {
            throw new RuntimeException("FilterNumeric.get_status not implemented");
        }

        String get_advanced_filter () {
            throw new RuntimeException("FilterNumeric.get_advanced_filter not implemented");
        }

        // Sets selection according to the string representation
        // See above for return values and error handling
        boolean set_pattern(String str, boolean error) {
            throw new RuntimeException("FilterNumeric.set_pattern not implemented");
        };

        // Return true if "number" is included in selection
        public boolean is_selected(long number) {
            throw new RuntimeException("FilterNumeric.is_selected not implemented");
        }

        public String get_cmd () {
            return cmd;
        };

        String get_name () {
            return name;
        };

        long nelem() {
            throw new RuntimeException("FilterNumeric.nelem not implemented");
//            return nitems;
        };

    }
}
