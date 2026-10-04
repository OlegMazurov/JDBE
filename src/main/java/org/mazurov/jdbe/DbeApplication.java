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

public class DbeApplication extends Application {

    boolean rdtMode;
    private static DbeApplication INSTANCE;
    private String[] args;
    private Settings settings = new Settings();

    protected DbeApplication(String[] args) {
        INSTANCE = this;
        this.args = args;
        DbeSession.createSession(settings);
    }

    public static DbeApplication getInstance() {
        return INSTANCE;
    }

    public String[] getArgs() {
        return args;
    }

    public String[] initApplication(String fdhome, String licpath, ProgressUpdater func) {
        String[] data = new String[2];
        data[0] = "OK";
        data[1] = null;
        return data;
    }
}
