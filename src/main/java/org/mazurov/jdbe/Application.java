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

public class Application {

    protected String prog_version;
    protected String prog_name;
    protected String whoami;
    private String cur_dir;
    protected Emsgqueue commentq = new Emsgqueue("app_commentq");

    public Emsg fetch_comments() {
        return commentq.fetch();
    }

    public void queue_comment(Emsg m) {
        commentq.append(m);
    }

    public void delete_comments() {
        commentq = new Emsgqueue("app_commentq");
    }

    public void set_name(String name) {
        prog_name = name; //get_realpath(name);
    }

    public String get_cur_dir() {
        if (cur_dir == null) {
            cur_dir = System.getProperty("user.dir");
        }
        return cur_dir;
    }

    public static void print_version_info() {
        System.out.printf(
                        "GNU %s binutils version %s\n" +
                        "Copyright (C) 2026 Free Software Foundation, Inc.\n" +
                        "License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.\n" +
                        "This is free software: you are free to change and redistribute it.\n" +
                        "There is NO WARRANTY, to the extent permitted by law.\n",
                "Analyzer", "0.1"); // TODO: fix name/version
    }

}
