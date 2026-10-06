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

public class Util {

    // like C's strtoull(str, NULL, 0): parses str as unsigned, auto-detecting the base
    // from a "0x"/"0X" prefix (hex) or a leading "0" (octal), decimal otherwise
    public static long strtoull(String str) {
        return parseCLong(str, true);
    }

    // like C's strtoll(str, NULL, 0): parses str as signed, with the same base
    // auto-detection
    public static long strtoll(String str) {
        return parseCLong(str, false);
    }

    private static long parseCLong(String str, boolean unsigned) {
        boolean negative = !unsigned && str.startsWith("-");
        String body = negative ? str.substring(1) : str;
        int radix = 10;
        if (body.startsWith("0x") || body.startsWith("0X")) {
            body = body.substring(2);
            radix = 16;
        } else if (body.length() > 1 && body.charAt(0) == '0') {
            radix = 8;
        }
        long value = unsigned ? Long.parseUnsignedLong(body, radix) : Long.parseLong(body, radix);
        return negative ? -value : value;
    }
}
