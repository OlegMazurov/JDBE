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

public class DbeStructs {

    enum ValueTag {
        VT_SHORT /*= 1*/,
        VT_INT,
        VT_LLONG,
        VT_FLOAT,
        VT_DOUBLE,
        VT_HRTIME,
        VT_LABEL,
        VT_ADDRESS,
        VT_OFFSET,
        VT_ULLONG
    }

    static class TValue
    {
        ValueTag tag;
        boolean sign;    // The print result will always begin with a sign (+ or -).
        // Native stores these in a union (short/int/float/double/char*/long variants);
        // Java has no aliasing concern, so plain fields stand in for whichever ones
        // are actually populated for a given `tag`. Only the double/long/string
        // variants are populated by this port so far (VT_DOUBLE / VT_LLONG,VT_ULLONG /
        // VT_LABEL); add more as other metric types get wired up.
        double d;
        long l;
        String str;

        double to_double() {
            return switch (tag) {
                case VT_DOUBLE, VT_FLOAT -> d;
                case VT_LLONG, VT_ULLONG, VT_INT, VT_SHORT, VT_ADDRESS, VT_OFFSET, VT_HRTIME -> l;
                default -> 0;
            };
        }

        // Mirrors native's TValue::to_str (util.cc:156-202), restricted to the value
        // types this port actually produces (VT_DOUBLE for CPU-time-in-seconds,
        // VT_LLONG/VT_ULLONG for counts/byte totals, VT_LABEL for the Name column).
        String to_str() {
            return switch (tag) {
                case VT_DOUBLE -> {
                    if (d == 0.0)
                        yield sign ? "+0.   " : "0.   ";
                    yield sign ? String.format("%+.3f", d) : String.format("%.3f", d);
                }
                case VT_LLONG -> sign ? String.format("%+d", l) : String.format("%d", l);
                case VT_ULLONG -> String.format("%d", l); // non-negative in practice
                case VT_LABEL -> str != null ? str : "";
                default -> "";
            };
        }

        int get_len() {
            return to_str().length();
        }
    }

    public record PathMap(String old_prefix, String new_prefix){}

    public record LoExpand(String libname, Enums.LibExpand expand){}
}
