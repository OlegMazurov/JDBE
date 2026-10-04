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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.mazurov.jdbe.IPCIO.*;
import static org.mazurov.jdbe.DBE.*;

public class ERIPC {

    static PrintStream ipcLog;

    static void ipc_log(String fmt, Object... values) {
        if (ipcLog == null) return;
        ipcLog.format(fmt, values);
    }

    static void ipc_log(Object[] array) {
        if (ipcLog == null) return;
        if (array == null) {
            ipcLog.format(" array: null");
            return;
        }
        ipcLog.format("  array: size = %d%n", array.length);
        for (Object obj : array) {
            if (obj == null) {
                ipcLog.format("    null%n");
            } else if (obj instanceof String[] stringArray) {
                ipcLog.format("    String[]: size = %d%n", stringArray.length);
            } else if (obj instanceof int[] intArray) {
                ipcLog.format("    int[]: size = %d%n", intArray.length);
            } else if (obj instanceof boolean[] boolArray) {
                ipcLog.format("    boolean[]: size = %d%n", boolArray.length);
            } else {
                ipcLog.format("    %s%n", obj.getClass().toString());
            }
        }
    }

    static String bool2str(boolean v) {
        return v ? "true" : "false";
    }

    static String str2str(String str) {
        return str == null ? "NULL" : str;
    }

    public static void ipc_mainLoop() throws IOException {

        ipcLog = new PrintStream(new FileOutputStream("ipc.log"), true);
        ipc_log("Args: %s%n", String.join(" ", DbeApplication.getInstance().getArgs()));

        writePlainString("ER_IPC: IPC_PROTOCOL_38\n");
        setProgress (100, "Restart engine");

        // Main loop -- read a request from the wire, do it, return the response
        for (;;) {
            // Fetch the request
            IPCrequest req = readRequestHeader();
            String inp = req != null ? req.readString() : null;
            if (inp == null) {
                ipc_log("NULL ipc command received, exiting\n");
                return;
            }
            ipc_log("ipc: %s\n", inp);

            switch (inp) {
                case "initApplication" -> {
                    boolean nbm = req.readBoolean();
                    String arg1 = req.readString();
                    String arg2 = req.readString();
                    String[] arg3 = (String[])req.readArray();
                    ipc_log("  nbm: %s, arg1: '%s', arg2: '%s'\n", bool2str(nbm), str2str(arg1), str2str(arg2));
                    // set the session to be interactive
                    //dbeSession->set_interactive(true);

                    DbeApplication.getInstance().set_name(nbm ? "analyzer-NBM" : "analyzer");

                    String[] res = DbeApplication.getInstance().initApplication(arg1, arg2, IPCIO::setProgress);
                    req.writeArray(res);
                }
                case "initView" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    ipc_log("  new view = %d; clone of view %d\n", arg1, arg2);
                    dbeInitView(arg1,arg2);
                    req.writeString(null);
                }
                case "getCurrentDirectory" -> {
                    Path currentRelativePath = Paths.get("");
                    String res = currentRelativePath.toAbsolutePath().toString();
                    req.writeString (res);
                }
                case "getLocale" -> {
                    req.writeString(Locale.getDefault().toString());
                }
                case "setLocale" -> {
                    String arg1 = req.readString(); // locale
                    ipc_log("  arg = %s%n", arg1);
                    Locale.setDefault(new Locale(arg1));
                    req.writeString(Locale.getDefault().toString());
                }
                case "setExperimentsGroups" -> {
                    Object[] groups = (Object[])req.readArray();
                    ipc_log ("  groups.size = %d%n", groups.length);
                    String message = null;
                    if (groups.length > 0) {
                        int idx = 0;
                        for (Object group : groups) {
                            ipc_log("  Group %d%n : %s%n", idx++, group.toString());
                        }
//                        message = dbeSetExperimentsGroups(groups);
                    }
                    req.writeString(message);
                }
                case "setNameFormat" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    boolean arg3 = req.readBoolean();
                    ipc_log("  args = %d, %d, %b%n", arg1, arg2, arg3);
                    dbeSetNameFormat(arg1, arg2, arg3);
                    req.writeString(null);
                }
                case "getFileAttributes" -> {
                    String arg1 = req.readString(); // filename
                    String arg2 = req.readString(); // format
                    ipc_log("  arg1 = %s  arg2 = %s\n", arg1, arg2);
                    String res = dbeGetFileAttributes(arg1, arg2);
                    req.writeString(res);
                }
                case "getCompareModeV2" -> {
                    int viewIndex = req.readInt();
                    int res = 0; //CMP_DISABLE;
//                    if (dbeSession->expGroups && dbeSession->expGroups->size () > 1)
//                        res = getView (viewIndex)->get_compare_mode ();
                    ipc_log ("  viewIndex = %d returns %d%n", viewIndex, res);
                    req.writeInt(res);
                }
                case "getMachineModel" -> {
                    String sts = dbeGetMachineModel();
                    ipc_log ("  returns '%s'%n", sts);
                    req.writeString(sts);
                }
                case "getExpEnable" -> {
                    int arg1 = req.readInt();
                    ipc_log("  arg1 = %d%n", arg1);
                    boolean[] res = dbeGetExpEnable(arg1);
                    req.writeArray(res);
                }
                case "getFiles" -> {
                    String arg1 = req.readString(); // dirname
                    String arg2 = req.readString(); // format
                    ipc_log ("  arg1 = %s  arg2 = %s%n", arg1, arg2);
                    String res = dbeGetFiles(arg1, arg2);
                    req.writeString(res);
                }
                case "getViewModeEnable" -> {
                    boolean res = dbeGetViewModeEnable();
                    req.writeBoolean(res);
                }
                case "getEntityProps" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  args = %d\n", arg1);
                    Object[] res = dbeGetEntityProps(arg1);
                    req.writeArray(res);
                }
                case "getCurMetricsV2" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    ipc_log("  args = %d, %d\n", arg1, arg2);
                    Object[] res = dbeGetCurMetricsV2(arg1, arg2);
                    ipc_log(res);
                    req.writeArray(res);
                }
                case "getRefMetricsV2" -> {
                    Object[] res = dbeGetRefMetricsV2();
                    ipc_log(res);
                    req.writeArray(res);
                }
                case "getRefMetricTree" -> {
                    int dbevindex = req.readInt();
                    boolean includeUnregistered = req.readBoolean();
                    ipc_log("  args = %d, %b\n", dbevindex, includeUnregistered);
                    Object[][] res = dbeGetRefMetricTree(dbevindex, includeUnregistered);
                    ipc_log(res[0]);
                    ipc_log(res[1]);
                    req.writeArray(res);
                }
                case "getJavaEnable" -> {
                    boolean res = dbeGetJavaEnable();
                    req.writeBoolean(res);
                }
                case "setCurMetricsV2" -> {
                    int dbevindex = req.readInt();
                    int cmp_mode = req.readInt();
                    int mtype = req.readInt();
                    int[] type = (int[]) req.readArray();
                    int[] subtype = (int[]) req.readArray();
                    boolean[] sort = (boolean[]) req.readArray();
                    int[] vis = (int[]) req.readArray();
                    String[] cmd = (String[]) req.readArray();
                    String[] expr_spec = (String[]) req.readArray();
                    String[] legends = (String[]) req.readArray();
                    ipc_log("  args = %d %d %d [%d] [%d] [%d] [%d] [%d] [%d] [%d]%n",
                            dbevindex, cmp_mode, mtype, type.length, subtype.length,
                            sort.length, vis.length, cmd.length, expr_spec.length, legends.length);
                    MetricList mlist = dbeGetMetricListV2(dbevindex, mtype, type, subtype, sort,
                            vis, cmd, expr_spec, legends);
                    DbeSession.getInstance().getView(dbevindex).reset_metric_list(mlist, cmp_mode);
                    req.writeResponseGeneric();
                }
                case "listMachineModels" -> {
                    String[] res = dbeListMachineModels();
                    req.writeArray(res);
                }
                case "getTabListInfo" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  arg = %d\n", arg1);
                    Object[] res = dbeGetTabListInfo(arg1);
                    req.writeArray(res);
                }
                case "getTabSelectionState" -> {
                    int arg1 = req.readInt();
                    ipc_log("  args = %d\n", arg1);
                    boolean[] res = dbeGetTabSelectionState(arg1);
                    req.writeArray(res);
                }
                case "getIndxObjDescriptions" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  args = %d\n", arg1);
                    Object[] res = dbeGetIndxObjDescriptions(arg1);
                    req.writeArray(res);
                }
                case "getIndxTabSelectionState" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  arg = %d\n", arg1);
                    boolean[] res = dbeGetIndxTabSelectionState(arg1);
                    ipc_log("  -- returned %d-vector [bool]\n", res != null ? res.length : 0);
                    req.writeArray(res);
                }
                case "getMemObjects" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  args = %d\n", arg1);
                    Object[] res = dbeGetMemObjects(arg1);
                    req.writeArray(res);
                }
                case "getMemTabSelectionState" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  arg = %d\n", arg1);
                    boolean[] res = dbeGetMemTabSelectionState(arg1);
                    req.writeArray (res);
                }
                case "getLoadObjectList" -> {
                    int arg1 = req.readInt();
                    ipc_log ("  arg = %d\n", arg1);
                    Object[] res = dbeGetLoadObjectList(arg1);
                    if (res == null || res.length == 0) {
                        ipc_log("  returning null for LoadObjectList\n");
                    } else {
                        String[] s = (String[]) res[0];
                        ipc_log ("  returning %d vectors for %d LoadObjects\n",
                                res.length, s != null ? s.length : 0);
                    }
                    req.writeArray(res);
                }
                case "getExpsProperty" -> {
                    String arg = req.readString();
                    String[] res = dbeGetExpsProperty(arg);
                    req.writeArray(res);
                }
                case "getHomeDirectory" -> {
                    String res = System.getProperty("user.home"); // Get HOME directory
                    req.writeString(res);
                }
                case "getExpPreview" -> {
                    // XXX add another argument == DbeView index
                    String arg1 = req.readString();
                    ipc_log ("  arg = %s\n", arg1);
                    String[] res = dbeGetExpPreview(0, arg1);
                    req.writeArray(res);
                }

                default -> {
                    ipc_log("Unrecognized input cmd \"%s\"; Aborting.\n", inp);
                    return;
                }
            }
            ipc_log("  processing IPC command %s complete\n", inp);
            System.out.flush();
        }
    }
}