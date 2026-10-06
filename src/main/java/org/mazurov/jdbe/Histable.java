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

public class Histable {

    protected String name;       // Object name
    public long id;              // A unique id of this object, within its specific Type

    public String get_name() {
        return name;
    }

    public void set_name(String name) {
        this.name = name;
    }

    public long get_size() {
        return 0;
    }

    public long get_addr() {
        return 0;
    }

    public enum Type {
        INSTR, LINE, FUNCTION, MODULE, LOADOBJECT,
        EADDR, MEMOBJ, INDEXOBJ, PAGE, DOBJECT,
        SOURCEFILE, IOACTFILE, IOACTVFD, IOCALLSTACK,
        HEAPCALLSTACK, EXPERIMENT, OTHER
    };

    public Type get_type () {
        return Type.OTHER;
    }

    // NameFormat for functions and function based objects

    public enum NameFormat {
        NA(0), LONG(1), SHORT(2), MANGLED(3), SONAME(0x10),
        LONG_SONAME(1 | 0x10), SHORT_SONAME(2 | 0x10), MANGLED_SONAME(3 | 0x10);

        final int value;

        NameFormat(int value) {
            this.value = value;
        }
    };

    static NameFormat make_fmt(int fnfmt, boolean sofmt) {
        int v = sofmt ? (fnfmt | NameFormat.SONAME.value) : fnfmt;
        for (NameFormat nf : NameFormat.values()) {
            if (nf.value == v)
                return nf;
        }
        return NameFormat.NA;
    }

    static int fname_fmt(NameFormat fmt) {
        return fmt.value & ~NameFormat.SONAME.value;
    }

    static boolean soname_fmt(NameFormat fmt) {
        return (fmt.value & NameFormat.SONAME.value) != 0;
    }

}
