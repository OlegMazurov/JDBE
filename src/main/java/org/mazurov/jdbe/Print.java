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

import java.io.File;
import java.io.PrintStream;
import java.util.List;

public class Print {

    public static String pr_load_objects(List<LoadObject> loadobjects, String lead) {
        StringBuilder sb = new StringBuilder();
        for (LoadObject lo : loadobjects) {
            String lo_name = lo.get_name();
            if (lo_name != null && lo_name.endsWith(".class>"))
                continue;

            // print the segment name
            sb.append(lead).append(' ').append(lo_name)
                    .append(" (").append(lo.get_pathname()).append(")\n");

            // and any warnings
            Emsg m = lo.fetch_warnings();
            if (m != null)
                sb.append(Emsg.pr_mesgs(m, null, "       "));
        }
        return sb.toString();
    }

    public static void print_load_object(PrintStream out_file) {
        List<LoadObject> loadobjects = DbeSession.getInstance().get_text_segments();
        String msg = pr_load_objects(loadobjects, "\t");
        out_file.print("Load Object Coverage:\n");
        out_file.print(msg);
        out_file.print("----------------------------------------------------------------\n");
    }

    public static void print_header(Experiment exp, PrintStream out_file) {
        out_file.printf("Experiment: %s\n", exp.get_expt_name());
        out_file.print(Emsg.pr_mesgs(exp.fetch_notes(), "", ""));
        out_file.print(Emsg.pr_mesgs(exp.fetch_errors(), "No errors\n", ""));
        out_file.print(Emsg.pr_mesgs(exp.fetch_warnings(), "No warnings\n", ""));
        out_file.print(Emsg.pr_mesgs(exp.fetch_comments(), "", ""));
        out_file.print(Emsg.pr_mesgs(exp.fetch_pprocq(), "", ""));
    }

    // Printing options.
    enum Print_destination {
        DEST_PRINTER      /*= 0*/,
        DEST_FILE         /*= 1*/,
        DEST_OPEN_FILE    /*= 2*/
    }

    enum Print_mode {
        MODE_LIST,
        MODE_DETAIL,
        MODE_GPROF,
        MODE_ANNOTATED
    }

    static class Print_params {
        Print_destination dest;     // printer or file
        String name;                // of printer or file
        int ncopies;                // # of copies
        boolean header;             // print header first
        File openfile;               // if destination is DEST_OPEN_FILE
    }

    static class er_print_common_display {
        PrintStream out_file;
        Print_params pr_params = new Print_params();

        public er_print_common_display() {
            out_file = null;
            pr_params.header = false;
        }
        public void set_out_file(PrintStream o) {
            out_file = o;
            pr_params.dest = Print_destination.DEST_FILE;
        }

        public void data_dump() {
            throw new RuntimeException("er_print_common_display.datadump() not implemented");
        }

        // shared by er_print_experiment's header path (and, natively, the
        // statistics/overview paths' first-experiment header)
        boolean load;      // print load-object coverage before the first experiment
        int exp_idx1;      // first experiment index this display covers

        void header_dump(int exp_idx) {
            if (load && exp_idx == exp_idx1) {
                load = false;
                print_load_object(out_file);
            }
            print_header(DbeSession.getInstance().get_exp(exp_idx), out_file);
        }
    }

    static class er_print_histogram extends er_print_common_display {
        DbeView dbev;
        Hist_data hist_data;
        MetricList mlist;
        Print_mode type;
        int number_entries;
        String sort_metric;
        Histable sel_obj;

        public er_print_histogram(DbeView dbv, Hist_data data, MetricList metrics_list,
                            Print_mode disp_type, int limit, String sort_name,
                            Histable sobj, boolean show_load, boolean show_header) {
            dbev = dbv;
            hist_data = data;
            mlist = metrics_list;
            type = disp_type;
            number_entries = limit;
            sort_metric = sort_name;
            sel_obj = sobj;
            exp_idx1 = 0;
            load = show_load;
        }

        // Mirrors native's er_print_histogram::data_dump (Print.cc:933-975),
        // simplified: only Print_mode.MODE_LIST is implemented (MODE_DETAIL/
        // MODE_GPROF/MODE_ANNOTATED need subsystems -- annotated source/disasm,
        // caller-callee trees -- this port doesn't have).
        @Override
        public void data_dump() {
            if (hist_data.get_status() != Hist_data.Hist_status.SUCCESS)
                return;
            if (sel_obj == null && type != Print_mode.MODE_LIST)
                out_file.printf("%s%s\n\n", sortedByMetricPrefix(), sort_metric);
            int limit = (int) hist_data.size();
            if (number_entries > 0 && number_entries < limit)
                limit = number_entries;
            switch (type) {
                case MODE_LIST -> dump_list(limit);
                default -> throw new RuntimeException(
                        "er_print_histogram.data_dump: only Print_mode.MODE_LIST is implemented");
            }
        }

        private String sortedByMetricPrefix() {
            return switch (hist_data.type) {
                case FUNCTION -> "Functions sorted by metric: ";
                case INSTR -> "PCs sorted by metric: ";
                case LINE -> "Lines sorted by metric: ";
                case DOBJECT -> "Dataobjects sorted by metric: ";
                default -> "Objects sorted by metric: ";
            };
        }

        // Mirrors native's er_print_histogram::dump_list (Print.cc:331-385),
        // restricted to PM_TEXT output (PM_HTML/PM_DELIM_SEP_LIST aren't ported).
        private void dump_list(int limit) {
            String title = sortedByMetricPrefix() + sort_metric;
            Metric.HistMetric[] hist_metric = hist_data.get_histmetrics();
            out_file.printf("%s\n\n", title);
            hist_data.print_label(out_file, hist_metric, 0);
            hist_data.print_content(out_file, hist_metric, limit);
            out_file.println();
        }
    }

    static class er_print_gprof extends er_print_common_display {
        public er_print_gprof(DbeView dbv, List<Histable> cstack) {
            throw new RuntimeException("er_print_gprof not implemented");
        }
    }

    static class er_print_experiment extends er_print_common_display {
        DbeView dbev;
        int exp_idx2;
        boolean header, stat, over, odetail;

        public er_print_experiment(DbeView me, int bgn_idx, int end_idx, boolean show_load,
                                    boolean show_header, boolean show_stat, boolean show_over, boolean show_odetail) {
            dbev = me;
            exp_idx1 = bgn_idx;
            exp_idx2 = end_idx;
            load = show_load;
            header = show_header;
            stat = show_stat;
            over = show_over;
            odetail = show_odetail;
        }

        public void data_dump() {
            if (stat) {
                // depends on statistics_sum()/statistics_dump() (Stats_data), not yet
                // ported.
                throw new RuntimeException("er_print_experiment statistics display not implemented");
            } else if (over) {
                // depends on overview_sum()/overview_dump() (Ovw_data), not yet ported.
                throw new RuntimeException("er_print_experiment overview display not implemented");
            } else if (header) {
                for (int index = exp_idx1; index <= exp_idx2; index++) {
                    if (index != exp_idx1)
                        out_file.print("----------------------------------------------------------------\n");
                    header_dump(index);
                }
            }
        }
    }

    static class er_print_leaklist extends er_print_common_display {
        public er_print_leaklist(DbeView dbv, boolean show_leak, boolean show_alloca, int limit) {
            throw new RuntimeException("er_print_leaklist not implemented");
        }
    }

    static class er_print_heapactivity extends er_print_common_display {
        public er_print_heapactivity(DbeView dbev, Histable.Type _type, boolean _printStat, int _limit) {
            throw new RuntimeException("er_print_heapactivity not implemented");
        }
    }

    static class er_print_ioactivity extends er_print_common_display {
        public er_print_ioactivity (DbeView dbev, Histable.Type _type, boolean _printStat, int _limit) {
            throw new RuntimeException("er_print_ioactivity not implemented");
        }
    }

}
