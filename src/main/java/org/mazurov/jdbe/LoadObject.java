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

public class LoadObject extends Histable {
    enum seg_type {
        SEG_TEXT,
        SEG_DATA,
        SEG_BSS,
        SEG_HEAP,
        SEG_STACK,
        SEG_DEVICE,
        SEG_UNKNOWN
    };

    seg_type type = seg_type.SEG_UNKNOWN;
    int seg_idx;                  // for compatibility (ADDRESS)
    String pathname;               // User name of object file
    long size;                     // size of loadobject in bytes
    public List<Module> seg_modules = new ArrayList<>();  // list of modules
    public List<Function> functions = new ArrayList<>();  // ordered list of functions
    public Module noname;          

    public Function placeholderFunc;

    public LoadObject(String loname) {
        set_pathname(loname);
    }

    public void set_pathname(String loname) {
        String nm = loname;
        if (nm.startsWith("./"))
            nm = nm.substring(2);
        pathname = nm;
        String base = new java.io.File(pathname).getName();
        name = base.startsWith("<") ? base : "<" + base + ">";
    }

    public String get_pathname() {
        return pathname;
    }

    @Override
    public long get_size() {
        return size;
    }

    @Override
    public Type get_type() {
        return Type.LOADOBJECT;
    }

    public Emsg fetch_warnings() {
        return null;
    }
}
