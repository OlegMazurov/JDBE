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

public class PreviewExp extends Experiment {

    private boolean is_group;
    protected Exp_status status;        // Error status

    Exp_status experiment_open (String path) {
        // Find experiment directory
        if ((status = find_expdir(path)) != Exp_status.SUCCESS) {
            int len = path.length();
            is_group = path.endsWith(".erg");
            return status;
        }
        else {
            is_group = false;
        }

        read_log_file();
        if (status == Exp_status.FAILURE) {
            return status;
        }

//        if (status == Exp_status.INCOMPLETE && resume_ts != MAX_TIME)
//            // experiment is incomplete and "resumed" (non-paused)
//            // PreviewExp does not process all the packets, therefore...
//            //    ... last_event does not reflect reality
//            //    ... we don't know the duration or the end.
//            last_event = ZERO_TIME; // mark last_event as uninitialized

        read_notes_file();
        return status;
    }

    public List<String> preview_info() {
        List<String> info = new ArrayList<>();
        info.add(is_group ? "Experiment Group" : "Experiment");
        info.add(expt_name);

        if (status == Exp_status.FAILURE /* != SUCCESS */) {
            if (is_group) {
//                Vector<char*> *grp_list = dbeSession->get_group_or_expt (expt_name);
//                for (int i = 0, grp_sz = grp_list->size (); i < grp_sz; i++)
//                {
//                    char *nm = grp_list->fetch (i);
//                    char *str = dbe_sprintf (GTXT ("Exp.#%d"), i + 1);
//                    info->append (str);
//                    info->append (nm);
//                }
//                delete grp_list;
            } else {
                info.add("Error message");
                info.add(mqueue_str (errorq, "No errors\n"));
            }
            return info;
        }
        info.add("Experiment header");
        info.add(mqueue_str(commentq, "Empty header\n"));
        info.add("Error message");
        info.add(mqueue_str(errorq, "No errors\n"));
        info.add("Warning message");
        info.add(mqueue_str(warnq, "No warnings\n"));
        info.add("Notes");
        info.add(mqueue_str(notesq, "\n"));
        return info;
    }

    public String getArgList() {
        return uarglist;
    }

    private String mqueue_str(Emsgqueue msgqueue, String null_str) {
        String mesgs = Emsg.pr_mesgs(msgqueue.fetch(), null_str, "");
        int idx = mesgs.indexOf('\n');
        if (idx >= 0) {
            mesgs = mesgs.substring(0, idx);
        }
        return mesgs;
    }
}
