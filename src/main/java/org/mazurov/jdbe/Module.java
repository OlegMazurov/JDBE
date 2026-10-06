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

public class Module extends Histable {
    public static final int MOD_FLAG_UNKNOWN = 0x1;

    public int flags;               // flags used for marking traversals
//    Sp_lang_code lang_code;           // What is source lang. in module
    String file_name;                  // Full path to actual source file
    Histable main_source;               // TODO: should be SourceFile, once ported
    public LoadObject loadobject;
    public List<Function> functions = new ArrayList<>(); // unordered list of functions

    public Type get_type () {
        return Type.MODULE;
    }

    public void set_file_name(String fnm) {
        file_name = fnm;
    }

    public Histable getMainSrc() {
        return main_source;
    }

    // Mirrors native's Module::find_jmethod (Module.cc:1827-1840): linear scan by
    // mangled name + signature, used to avoid creating a duplicate JMethod if a
    // jclasses record is somehow seen twice for the same method.
    public JMethod find_jmethod(String fullname, String signature) {
        for (Function f : functions) {
            if (f instanceof JMethod jm && fullname.equals(jm.mangledName)
                    && (signature == null ? jm.signature == null : signature.equals(jm.signature)))
                return jm;
        }
        return null;
    }
}
