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
import java.util.Arrays;
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

    static class RestartRequestedException extends RuntimeException {
    }

    private static final String IPC_PROTOCOL_CURR = "IPC_PROTOCOL_38";
    private static String ipc_protocol = null; // null: no confirmation line is printed

    private static void check_env_args(String[] args) {
        int indx = 1; // skip "-IPC" (args[0])
        while (args.length - indx >= 2) {
            String option = args[indx++];
            if (!option.equals("-E"))
                continue;
            String cmdEnvVar = args[indx++];
            int sep = cmdEnvVar.indexOf('=');
            if (sep < 0)
                continue;
            String key = cmdEnvVar.substring(0, sep);
            String val = cmdEnvVar.substring(sep + 1);
            if (key.equals("SP_IPC_PROTOCOL"))
                ipc_protocol = val.equals(IPC_PROTOCOL_CURR) ? IPC_PROTOCOL_CURR : "IPC_PROTOCOL_UNKNOWN";
        }
    }

    private static void print_ipc_protocol_confirmation() {
        if (ipc_protocol != null)
            writePlainString(String.format("ER_IPC: %s\n", ipc_protocol));
    }

    public static void ipc_mainLoop() throws IOException {
        try {
            mainLoop();
        } catch (RestartRequestedException rex) {
            throw rex;
        } catch (Exception ex) {
            ex.printStackTrace(ipcLog);
            throw ex;
        }
    }

    static void mainLoop() throws IOException {

        // Keep the log contiguous across a restart.
        if (ipcLog == null) {
            ipcLog = new PrintStream(new FileOutputStream("ipc.log"), true);
        }
        ipc_log("Args: %s%n", String.join(" ", DbeApplication.getInstance().getArgs()));

        check_env_args(DbeApplication.getInstance().getArgs());
        print_ipc_protocol_confirmation();
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
                case "reExec" -> {
                    ipc_log("  reExec requested; throwing RestartRequestedException%n");
                    throw new RestartRequestedException();
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
                case "setCurrentDirectory" -> {
                    String arg1 = req.readString();
                    ipc_log("  arg = %s%n", arg1);
                    int res = -1;
                    if (arg1 != null) {
                        java.io.File dir = new java.io.File(arg1);
                        if (dir.isDirectory()) {
                            System.setProperty("user.dir", dir.getAbsolutePath());
                            res = 0;
                        }
                    }
                    req.writeInt(res);
                }
                case "getLocale" -> {
                    req.writeString(Locale.getDefault().toString());
                }
                case "setLocale" -> {
                    String arg1 = req.readString(); 
                    ipc_log("  arg = %s%n", arg1);
                    Locale.setDefault(new Locale(arg1));
                    req.writeString(Locale.getDefault().toString());
                }
                case "setExperimentsGroups" -> {
                    Object[] rawGroups = (Object[]) req.readArray();
                    String[][] groups = new String[rawGroups.length][];
                    for (int i = 0; i < rawGroups.length; i++)
                        groups[i] = (String[]) rawGroups[i];
                    ipc_log ("  groups.size = %d%n", groups.length);
                    String message = null;
                    if (groups.length > 0) {
                        int idx = 0;
                        for (String[] group : groups) {
                            ipc_log("  Group %d%n : %s%n", idx++, Arrays.toString(group));
                        }
                        message = dbeSetExperimentsGroups(groups);
                    }
                    req.writeString(message);
                }
                case "getFounderExpId" -> {
                    int[] arg = (int[]) req.readArray();
                    ipc_log("  expIds = %d%n", arg.length);
                    int[] res = dbeGetFounderExpId(arg);
                    req.writeArray(res);
                }
                case "getUserExpId" -> {
                    int[] arg = (int[]) req.readArray();
                    ipc_log("  expIds = %d%n", arg.length);
                    int[] res = dbeGetUserExpId(arg);
                    req.writeArray(res);
                }
                case "getExpVerboseName" -> {
                    int[] arg = (int[]) req.readArray();
                    ipc_log("  expIds = %d%n", arg.length);
                    String[] res = dbeGetExpVerboseName(arg);
                    req.writeArray(res);
                }
                case "getExpGroupId" -> {
                    int[] arg = (int[]) req.readArray();
                    ipc_log("  expIds = %d%n", arg.length);
                    int[] res = dbeGetExpGroupId(arg);
                    req.writeArray(res);
                }
                case "getExperimentTimeInfo" -> {
                    int[] expIds = (int[]) req.readArray();
                    ipc_log("  cnt = %d%n", expIds.length);
                    Object[] res = dbeGetExperimentTimeInfo(expIds);
                    req.writeArray(res);
                }
                case "getExperimentDataDescriptors" -> {
                    int[] expIds = (int[]) req.readArray();
                    ipc_log("  cnt = %d%n", expIds.length);
                    Object[] res = dbeGetExperimentDataDescriptors(expIds);
                    req.writeArray(res);
                }
                case "hasTLData" -> {
                    int dbevindex = req.readInt();
                    int[] exp_ids = (int[]) req.readArray();
                    int[] data_ids = (int[]) req.readArray();
                    int[] eprop_ids = (int[]) req.readArray();
                    int[] eprop_vals = (int[]) req.readArray();
                    int[] auxs = (int[]) req.readArray();
                    ipc_log("  dbev_id = %d, cnt = %d\n", dbevindex, exp_ids.length);
                    boolean[] res = dbeHasTLData(dbevindex, exp_ids, data_ids, eprop_ids, eprop_vals, auxs);
                    req.writeArray(res);
                }
                case "getTLData" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    int data_id = req.readInt();
                    int entity_prop_id = req.readInt();
                    int entity_prop_val = req.readInt();
                    int aux = req.readInt();
                    long start_ts = req.readLong();
                    long delta = req.readLong();
                    int ndelta = req.readInt();
                    boolean getRepresentatives = req.readBoolean();
                    String[] chartProps = (String[]) req.readArray();
                    ipc_log("  args = %d, %d, %d, %d, %d, %d, %d, %d, %d, %b\n", dbevindex, exp_id, data_id,
                            entity_prop_id, entity_prop_val, aux, start_ts, delta, ndelta, getRepresentatives);
                    Object[] res = dbeGetTLData(dbevindex, exp_id, data_id, entity_prop_id, entity_prop_val, aux,
                            start_ts, delta, ndelta, getRepresentatives, chartProps);
                    req.writeArray(res);
                }
                case "getTLEventCenterTime" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    int data_id = req.readInt();
                    int entity_prop_id = req.readInt();
                    int entity_prop_val = req.readInt();
                    int aux = req.readInt();
                    long event_id = req.readLong();
                    long move_count = req.readLong();
                    ipc_log("  args = %d, %d, %d, %d, %d, %d, %d, %d\n", dbevindex, exp_id, data_id,
                            entity_prop_id, entity_prop_val, aux, event_id, move_count);
                    long[] res = dbeGetTLEventCenterTime(dbevindex, exp_id, data_id, entity_prop_id,
                            entity_prop_val, aux, event_id, move_count);
                    req.writeArray(res);
                }
                case "getTLEventIdxNearTime" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    int data_id = req.readInt();
                    int entity_prop_id = req.readInt();
                    int entity_prop_val = req.readInt();
                    int aux = req.readInt();
                    int searchDirection = req.readInt();
                    long tstamp = req.readLong();
                    ipc_log("  args = %d, %d, %d, %d, %d, %d, %d, %d\n", dbevindex, exp_id, data_id,
                            entity_prop_id, entity_prop_val, aux, searchDirection, tstamp);
                    long res = dbeGetTLEventIdxNearTime(dbevindex, exp_id, data_id, entity_prop_id,
                            entity_prop_val, aux, searchDirection, tstamp);
                    req.writeLong(res);
                }
                case "getSamples" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    long lo_idx = req.readLong();
                    long hi_idx = req.readLong();
                    ipc_log("  dbevindex= %d, exp_id = %d, lo_idx:%d, hi_idx:%d\n", dbevindex, exp_id, lo_idx, hi_idx);
                    Object[] res = dbeGetSamples(dbevindex, exp_id, lo_idx, hi_idx);
                    req.writeArray(res);
                }
                case "getGCEvents" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    long lo_idx = req.readLong();
                    long hi_idx = req.readLong();
                    ipc_log("  dbevindex= %d, exp_id = %d, lo_idx:%d, hi_idx:%d\n", dbevindex, exp_id, lo_idx, hi_idx);
                    Object[] res = dbeGetGCEvents(dbevindex, exp_id, lo_idx, hi_idx);
                    req.writeArray(res);
                }
                case "getTLDetails" -> {
                    int dbevindex = req.readInt();
                    int exp_id = req.readInt();
                    int data_id = req.readInt();
                    int entity_prop_id = req.readInt();
                    long event_id = req.readLong();
                    ipc_log("  args = %d, %d, %d, %d, %d\n", dbevindex, exp_id, data_id,
                            entity_prop_id, event_id);
                    Object[] res = dbeGetTLDetails(dbevindex, exp_id, data_id, entity_prop_id, event_id);
                    req.writeArray(res);
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
                    String arg1 = req.readString(); 
                    String arg2 = req.readString(); 
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
                case "getExpName" -> {
                    String[] res = dbeGetExpName(0);
                    req.writeArray(res);
                }
                case "getExpState" -> {
                    int[] res = dbeGetExpState(0);
                    req.writeArray(res);
                }
                case "getExpInfo" -> {
                    int arg1 = req.readInt();
                    ipc_log("  args = %d%n", arg1);
                    String[] res = dbeGetExpInfo(arg1);
                    req.writeArray(res);
                }
                case "getFiles" -> {
                    String arg1 = req.readString(); 
                    String arg2 = req.readString(); 
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
                case "getEntitiesV2" -> {
                    int arg1 = req.readInt();
                    int[] arg2 = (int[]) req.readArray();
                    int arg3 = req.readInt();
                    ipc_log("  args = %d, %d, %d\n", arg1, arg2 == null ? -1 : arg2.length, arg3);
                    Object[] res = dbeGetEntitiesV2(arg1, arg2, arg3);
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
                case "getRefMetricTreeValues" -> {
                    int dbevindex = req.readInt();
                    String[] metcmds = (String[]) req.readArray();
                    String[] nonmetcmds = (String[]) req.readArray();
                    ipc_log("  args = %d, metcmds.length=%d, nonmetcmds.length=%d\n",
                            dbevindex, metcmds != null ? metcmds.length : 0, nonmetcmds != null ? nonmetcmds.length : 0);
                    Object[] res = dbeGetRefMetricTreeValues(dbevindex, metcmds, nonmetcmds);
                    req.writeArray(res);
                }
                case "getOverviewText" -> {
                    int arg1 = req.readInt();
                    ipc_log("  arg = %d\n", arg1);
                    String[] res = dbeGetOverviewText(arg1);
                    req.writeArray(res);
                }
                case "setSort" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    int arg3 = req.readInt();
                    boolean arg4 = req.readBoolean();
                    ipc_log("  args = %d, %d, %d, %c%n", arg1, arg2, arg3, arg4 ? 'T' : 'F');
                    dbeSetSort(arg1, arg2, arg3, arg4);
                    req.writeString(null);
                }
                case "setSelObj" -> {
                    int arg1 = req.readInt();
                    long arg2 = req.readLong();
                    int arg3 = req.readInt();
                    int arg4 = req.readInt();
                    ipc_log("  args = %d, %d, %d, %d\n", arg1, arg2, arg3, arg4);
                    dbeSetSelObj(arg1, arg2, arg3, arg4);
                    req.writeString(null);
                }
                case "setSelObjV2" -> {
                    int arg1 = req.readInt();
                    long arg2 = req.readLong();
                    ipc_log("  args = %d, %d\n", arg1, arg2);
                    dbeSetSelObjV2(arg1, arg2);
                    req.writeString(null);
                }
                case "getSelObj" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    int arg3 = req.readInt();
                    long res2 = dbeGetSelObj(arg1, arg2, arg3);
                    ipc_log("  args = %d, %d, %d, res = %d\n", arg1, arg2, arg3, res2);
                    req.writeLong(res2);
                }
                case "getSelObjV2" -> {
                    int arg1 = req.readInt();
                    String arg2 = req.readString();
                    long res2 = dbeGetSelObjV2(arg1, arg2);
                    ipc_log("  arg1 = %d  arg2 = %s, res = %d\n", arg1, str2str(arg2), res2);
                    req.writeLong(res2);
                }
                case "getSelIndex" -> {
                    int arg1 = req.readInt();
                    long arg2 = req.readLong();
                    int arg3 = req.readInt();
                    int arg4 = req.readInt();
                    int res = dbeGetSelIndex(arg1, arg2, arg3, arg4);
                    ipc_log("  args = %d, %d, %d, %d, res = %d\n", arg1, arg2, arg3, arg4, res);
                    req.writeInt(res);
                }
                case "getObjNameV2" -> {
                    int arg1 = req.readInt();
                    long arg2 = req.readLong();
                    String res = dbeGetObjNameV2(arg1, arg2);
                    ipc_log("  arg1 = %d, arg2 = %d, res = %s\n", arg1, arg2, str2str(res));
                    req.writeString(res);
                }
                case "getObjNamesV2" -> {
                    int arg1 = req.readInt();
                    long[] arg2 = (long[]) req.readArray();
                    ipc_log("  arg1 = %d, size = %d\n", arg1, arg2 == null ? -1 : arg2.length);
                    String[] res = dbeGetObjNamesV2(arg1, arg2);
                    req.writeArray(res);
                }
                case "setFuncDataV2" -> {
                    int dbevindex = req.readInt();
                    long sel_obj = req.readLong();
                    int type = req.readInt();
                    int subtype = req.readInt();
                    ipc_log("  args = %d, %d, %d, %d\n", dbevindex, sel_obj, type, subtype);
                    Object[] res = dbeSetFuncDataV2(dbevindex, sel_obj, type, subtype);
                    req.writeArray(res);
                }
                case "getMsg" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    ipc_log("  args = %d, %d\n", arg1, arg2);
                    String res = dbeGetMsg(arg1, arg2);
                    req.writeString(res);
                }
                case "getNames" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    long arg3 = req.readLong();
                    ipc_log("  args = %d, %d, %d\n", arg1, arg2, arg3);
                    String[] res = dbeGetNames(arg1, arg2, arg3);
                    req.writeArray(res);
                }
                case "getObject" -> {
                    int arg1 = req.readInt();
                    long arg2 = req.readLong();
                    long arg3 = req.readLong();
                    ipc_log("  args = %d, %d, %d\n", arg1, arg2, arg3);
                    long res = dbeGetObject(arg1, arg2, arg3);
                    req.writeLong(res);
                }
                case "getStackFunctions" -> {
                    long arg1 = req.readLong();
                    ipc_log("  args = %d\n", arg1);
                    long[] res = dbeGetStackFunctions(0, arg1);
                    req.writeArray(res);
                }
                case "getFuncNames" -> {
                    int arg1 = req.readInt();
                    long[] arg2 = (long[]) req.readArray();
                    ipc_log("  arg1 = %d, argc = %d\n", arg1, arg2 == null ? 0 : arg2.length);
                    String[] res = dbeGetFuncNames(arg1, arg2);
                    req.writeArray(res);
                }
                case "getStacksFunctions" -> {
                    long[] arg1 = (long[]) req.readArray();
                    ipc_log("  argc = %d\n", arg1 == null ? 0 : arg1.length);
                    Object[] res = dbeGetStacksFunctions(0, arg1);
                    req.writeArray(res);
                }
                case "getCallTreeFuncs" -> {
                    int arg1 = req.readInt();
                    ipc_log("  args = %d\n", arg1);
                    Object[] res = dbeGetCallTreeFuncs(arg1);
                    req.writeArray(res);
                }
                case "getFuncList" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    int arg3 = req.readInt();
                    ipc_log("  args = %d, %d, %d\n", arg1, arg2, arg3);
                    Object[] res = dbeGetFuncList(arg1, arg2, arg3);
                    req.writeArray(res);
                }
                case "getFilterStr" -> {
                    int arg1 = req.readInt();
                    ipc_log("  args = %d\n", arg1);
                    String res = dbeGetFilterStr(arg1);
                    req.writeString(res);
                }
                case "getFuncListMini" -> {
                    int arg1 = req.readInt();
                    int arg2 = req.readInt();
                    int arg3 = req.readInt();
                    ipc_log("  args = %d, %d, %d\n", arg1, arg2, arg3);
                    Object[] res = dbeGetFuncListMini(arg1, arg2, arg3);
                    req.writeArray(res);
                }
                case "getTableDataV2" -> {
                    int arg1 = req.readInt();
                    String arg2 = req.readString();
                    String arg3 = req.readString();
                    String arg4 = req.readString();
                    String arg5 = req.readString();
                    long[] arg6 = (long[]) req.readArray();
                    ipc_log("  args = %d, %s, %s, %s, %s, %d\n", arg1, arg2, arg3, arg4, arg5,
                            arg6 == null ? -1 : arg6.length);
                    Object[] res = dbeGetTableDataV2(arg1, arg2, arg3, arg4, arg5, arg6);
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
                case "getSummary" -> {
                    int arg1 = req.readInt();
                    long[] arg2 = (long[]) req.readArray();
                    int arg3 = req.readInt();
                    int arg4 = req.readInt();
                    ipc_log("  args = %d, [%d], %d, %d%n", arg1, arg2 == null ? -1 : arg2.length, arg3, arg4);
                    Object[] res = dbeGetSummary(arg1, arg2, arg3, arg4);
                    req.writeArray(res);
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
                case "getSearchPath" -> {
                    ipc_log("  no args\n");
                    String[] res = dbeGetSearchPath(0);
                    req.writeArray(res);
                }
                case "setSearchPath" -> {
                    String[] arg1 = (String[]) req.readArray();
                    ipc_log("  %d strings\n", arg1.length);
                    dbeSetSearchPath(0, arg1);
                    req.writeString(null);
                }
                case "getPathmaps" -> {
                    Object[] res = dbeGetPathmaps(0);
                    req.writeArray(res);
                }
                case "setPathmaps" -> {
                    String[] from = (String[]) req.readArray();
                    String[] to = (String[]) req.readArray();
                    ipc_log("  %d strings\n", from != null ? from.length : 0);
                    String res = dbeSetPathmaps(from, to);
                    req.writeString(res);
                }
                case "addPathmap" -> {
                    String arg1 = req.readString();
                    String arg2 = req.readString();
                    ipc_log("  args = '%s', '%s'\n", str2str(arg1), str2str(arg2));
                    String res = dbeAddPathmap(0, arg1, arg2);
                    req.writeString(res);
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
