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

// A resolved Java method (from jclasses), used both as the "canonical" identity for
// a method and as the object registered against JIT-compiled address ranges (see
// Experiment.process_jcm_load_cmd). Deviates from native here: native creates a
// separate JMethod wrapper object per JIT-compiled instance (one per "jcm" event,
// referencing this one via usrfunc) so recompilations of the same method get merged
// back together later via a "comparable objects" pass; we skip that and just reuse
// this single canonical JMethod directly across every compiled instance's address
// range, which gets the same merged-by-name result without needing that machinery.
public class JMethod extends Function {

    public long mid;
    public String signature;    // raw JVM method descriptor, e.g. "(Ljava/lang/Object;)V"
    public String mangledName;  // untranslated "<dotted class name>.<method name>"

    public JMethod(long id) {
        super(id);
    }

    // `signature` must already be set before calling this (matches native's call
    // order: set_signature() before set_name() in Experiment::read_java_classes_file).
    @Override
    public void set_name(String mangledName) {
        this.mangledName = mangledName;
        String translated = translateMethod(mangledName, signature);
        this.name = translated != null ? translated : mangledName;
    }

    // Mirrors native's translate_method (Function.cc:1049-1091): builds
    // "<mname>(<arg1>, <arg2>, ...)" from the raw JVM parameter descriptor. No return
    // type is included, matching the call from JMethod::set_name (ret_type=false).
    static String translateMethod(String mname, String sig) {
        if (sig == null)
            return null;
        int lparen = sig.indexOf('(');
        int rparen = sig.indexOf(')');
        if (lparen < 0 || rparen < 0 || rparen < lparen)
            return null;
        String params = sig.substring(lparen + 1, rparen);
        StringBuilder sb = new StringBuilder(mname).append('(');
        int[] pos = {0};
        boolean first = true;
        while (pos[0] < params.length()) {
            String t = translateField(params, pos);
            if (t == null)
                return null;
            if (!first)
                sb.append(", ");
            first = false;
            sb.append(t);
        }
        sb.append(')');
        return sb.toString();
    }

    // Mirrors native's translate_method_field (Function.cc:956-1042): consumes one
    // JVM type descriptor starting at pos[0], advances pos[0] past it, and returns the
    // readable Java source-level type name.
    private static String translateField(String s, int[] pos) {
        if (pos[0] >= s.length())
            return null;
        char c = s.charAt(pos[0]);
        switch (c) {
            case 'Z' -> { pos[0]++; return "boolean"; }
            case 'B' -> { pos[0]++; return "byte"; }
            case 'C' -> { pos[0]++; return "char"; }
            case 'S' -> { pos[0]++; return "short"; }
            case 'I' -> { pos[0]++; return "int"; }
            case 'J' -> { pos[0]++; return "long"; }
            case 'F' -> { pos[0]++; return "float"; }
            case 'D' -> { pos[0]++; return "double"; }
            case 'V' -> { pos[0]++; return "void"; }
            case 'L' -> {
                int semi = s.indexOf(';', pos[0]);
                if (semi < 0)
                    return null;
                String cls = s.substring(pos[0] + 1, semi).replace('/', '.');
                pos[0] = semi + 1;
                return cls;
            }
            case '[' -> {
                pos[0]++;
                String elem = translateField(s, pos);
                return elem == null ? null : elem + "[]";
            }
            default -> {
                return null;
            }
        }
    }
}
