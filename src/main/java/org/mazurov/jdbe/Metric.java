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

import static org.mazurov.jdbe.Enums.*;

public class Metric extends BaseMetric {

    private int subtype;
    private String name;
    private String abbr;
    private String abbr_unit;
    private int visbits; // ValueType, e.g. VAL_VALUE|VAL_TIMEVAL

    public Metric(BaseMetric item, int st) {
        super(item);
        name = null;
        abbr = null;
        abbr_unit = null;
        set_subtype(st);
        visbits = VAL_NA;
        if (item.get_type() == Type.DERIVED) {
            visbits = VAL_VALUE;
        }
    }

    public Metric(Metric item)
    {
        super(item);
//        baseMetric = item.baseMetric;
        subtype = item.subtype;
        name = item.name;
        abbr = item.abbr;
        abbr_unit = item.abbr_unit;
        visbits = item.visbits;
    }


    public int get_subtype() {
        return subtype;
    }

    public String get_name() {
        return name;
    }

    public String get_abbr() {
        return abbr;
    }

    public String get_abbr_unit() {
        return abbr_unit;
    }

    public void enable_all_visbits() {
        visbits = get_value_styles();
    }

    public boolean is_any_visible() {
        return !(visbits == -1 || visbits == VAL_NA || (visbits & VAL_HIDE_ALL) != 0)
                && (visbits & (VAL_VALUE | VAL_TIMEVAL | VAL_PERCENT)) != 0;
    }
    public int get_visbits() {
        return visbits;
    }

    public void set_raw_visbits(int _visbits) {
        visbits = _visbits;
    }

    public void set_subtype(int st) {
        subtype = st;
        switch (get_type()) {
            case CP_LMS_USER:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE) {
                    name = "Exclusive User CPU Time";
                    abbr = "Excl. User CPU";
                } else if (st == INCLUSIVE) {
                    name = "Inclusive User CPU Time";
                    abbr = "Incl. User CPU";
                } else if (st == ATTRIBUTED) {
                    name = "Attributed User CPU Time";
                    abbr = "Attr. User CPU";
                } else {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_USER metric subtype %d", st));
                }
                break;

            case CP_LMS_WAIT_CPU:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE) {
                    name = "Exclusive Wait CPU Time";
                    abbr = "Excl. Wait CPU";
                } else if (st == INCLUSIVE) {
                    name = "Inclusive Wait CPU Time";
                    abbr = "Incl. Wait CPU";
                } else if (st == ATTRIBUTED) {
                    name = "Attributed Wait CPU Time";
                    abbr = "Attr. Wait CPU";
                } else {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_WAIT_CPU metric subtype %d", st));
                }
                break;

            case CP_LMS_USER_LOCK:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive User Lock Time";
                    abbr = "Excl. User Lock";
                } else if (st == INCLUSIVE) {
                    name = "Inclusive User Lock Time";
                    abbr = "Incl. User Lock";
                } else if (st == ATTRIBUTED) {
                    name = "Attributed User Lock Time";
                    abbr = "Attr. User Lock";
                } else {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_USER_LOCK metric subtype %d", st));
                }
                break;

            case CP_LMS_SYSTEM:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive System CPU Time";
                    abbr = "Excl. Sys. CPU";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive System CPU Time";
                    abbr = "Incl. Sys. CPU";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed System CPU Time";
                    abbr = "Attr. Sys. CPU";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_SYSTEM metric subtype %d", st));
                }
                break;

            case SYNC_WAIT_TIME:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Sync Wait Time";
                    abbr = "Excl. Sync Wait";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Sync Wait Time";
                    abbr = "Incl. Sync Wait";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Sync Wait Time";
                    abbr = "Attr. Sync Wait";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected LWT metric subtype %d", st));
                }
                break;

            case CP_LMS_TFAULT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Text Page Fault Time";
                    abbr = "Excl. Text Fault";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Text Page Fault Time";
                    abbr = "Incl. Text Fault";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Text Page Fault Time";
                    abbr = "Attr. Text Fault";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_TFAULT metric subtype %d", st));
                }
                break;

            case CP_LMS_DFAULT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Data Page Fault Time";
                    abbr = "Excl. Data Fault";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Data Page Fault Time";
                    abbr = "Incl. Data Fault";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Data Page Fault Time";
                    abbr = "Attr. Data Fault";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_DFAULT metric subtype %d", st));
                }
                break;

            case CP_KERNEL_CPU:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Kernel CPU Time";
                    abbr = "Excl. Kernel CPU";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Kernel CPU Time";
                    abbr = "Incl. Kernel CPU";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Kernel CPU Time";
                    abbr = "Attr. Kernel CPU";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_KERNEL_CPU metric subtype %d", st));
                }
                break;

            case HWCNTR:
            {
                String sstr, estr1, estr2;
//                if (get_hw_ctr() == null)
//                    abort ();
                sstr = get_username ();
                if (st == EXCLUSIVE)
                {
                    estr1 = "Exclusive ";
                    estr2 = "Excl. ";
                }
                else if (st == INCLUSIVE)
                {
                    estr1 = "Inclusive ";
                    estr2 = "Incl. ";
                }
                else if (st == ATTRIBUTED)
                {
                    estr1 = "Attributed ";
                    estr2 = "Attr. ";
                }
                else if (st == DATASPACE)
                {
                    estr1 = "Data-derived ";
                    estr2 = "Data. ";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected HWC %s metric subtype %d",
                            get_aux(), st));
                }
                name = String.format("%s%s", estr1, sstr);
                abbr = String.format("%s%s", estr2, sstr);
                break;
            }

            case DERIVED:
            {
                switch (st)
                {
                    case EXCLUSIVE:
                        name = String.format("Exclusive %s", get_username());
                        abbr = String.format("Excl. %s", get_cmd());
                        break;
                    case INCLUSIVE:
                        name = String.format("Inclusive %s", get_username());
                        abbr = String.format("Incl. %s", get_cmd());
                        break;
                    case ATTRIBUTED:
                        name = String.format("Attributed %s", get_username());
                        abbr = String.format("Attr. %s", get_cmd());
                        break;
                    default:
                        throw new IllegalStateException(String.format("Unexpected derived %s metric subtype %d",
                                get_username(), st));
                }
                break;
            }

            case OMP_MASTER_THREAD:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Master Thread Time";
                    abbr = "Excl. Master Thread";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Master Thread Time";
                    abbr = "Incl. Master Thread";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Master Thread Time";
                    abbr = "Attr. Master Thread";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected Master Thread metric subtype %d", st));
                }
                break;

            case CP_TOTAL:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Total Thread Time";
                    abbr = "Excl. Total Thread";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Total Thread Time";
                    abbr = "Incl. Total Thread";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Total Thread Time";
                    abbr = "Attr. Total Thread";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected TOTAL metric subtype %d", st));
                }
                break;

            case SYNC_WAIT_COUNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Sync Wait Count";
                    abbr = "Excl. Sync Wait Count";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Sync Wait Count";
                    abbr = "Incl. Sync Wait Count";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Sync Wait Count";
                    abbr = "Attr. Sync Wait Count";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected LWCNT metric subtype %d", st));
                }
                break;

            case CP_TOTAL_CPU:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Total CPU Time";
                    abbr = "Excl. Total CPU";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Total CPU Time";
                    abbr = "Incl. Total CPU";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Total CPU Time";
                    abbr = "Attr. Total CPU";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected TOTAL_CPU metric subtype %d", st));
                }
                break;
            case CP_LMS_TRAP:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Trap CPU Time";
                    abbr = "Excl. Trap CPU";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Trap CPU Time";
                    abbr = "Incl. Trap CPU";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Trap CPU Time";
                    abbr = "Attr. Trap CPU";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_TRAP metric subtype %d", st));
                }
                break;

            case CP_LMS_KFAULT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Kernel Page Fault Time";
                    abbr = "Excl. Kernel Page Fault";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Kernel Page Fault Time";
                    abbr = "Incl. Kernel Page Fault";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Kernel Page Fault Time";
                    abbr = "Attr. Kernel Page Fault";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_KFAULT metric subtype %d", st));
                }
                break;

            case CP_LMS_SLEEP:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Sleep Time";
                    abbr = "Excl. Sleep";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Sleep Time";
                    abbr = "Incl. Sleep";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Sleep Time";
                    abbr = "Attr. Sleep";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_SLEEP metric subtype %d", st));
                }
                break;

            case CP_LMS_STOPPED:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Stopped Time";
                    abbr = "Excl. Stopped";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Stopped Time";
                    abbr = "Incl. Stopped";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Stopped Time";
                    abbr = "Attr. Stopped";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected CP_LMS_STOPPED metric subtype %d", st));
                }
                break;

            case HEAP_ALLOC_BYTES:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Bytes Allocated";
                    abbr = "Excl. Bytes Allocated";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Bytes Allocated";
                    abbr = "Incl. Bytes Allocated";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Bytes Allocated";
                    abbr = "Attr. Bytes Allocated";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected BYTES_MALLOCD metric subtype %d", st));
                }
                break;

            case HEAP_ALLOC_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Allocations";
                    abbr = "Excl. Allocations";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Allocations";
                    abbr = "Incl. Allocations";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Allocations";
                    abbr = "Attr. Allocations";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected MALLOCS metric subtype %d", st));
                }
                break;

            case HEAP_LEAK_BYTES:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Bytes Leaked";
                    abbr = "Excl. Bytes Leaked";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Bytes Leaked";
                    abbr = "Incl. Bytes Leaked";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Bytes Leaked";
                    abbr = "Attr. Bytes Leaked";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected BYTES_LEAKED metric subtype %d", st));
                }
                break;

            case HEAP_LEAK_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Leaks";
                    abbr = "Excl. Leaks";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Leaks";
                    abbr = "Incl. Leaks";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Leaks";
                    abbr = "Attr. Leaks";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected LEAKS metric subtype %d", st));
                }
                break;

            case IO_READ_BYTES:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Read Bytes";
                    abbr = "Excl. Read Bytes";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Read Bytes";
                    abbr = "Incl. Read Bytes";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Read Bytes";
                    abbr = "Attr. Read Bytes";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected READ_BYTES metric subtype %d", st));
                }
                break;

            case IO_WRITE_BYTES:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Write Bytes";
                    abbr = "Excl. Write Bytes";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Write Bytes";
                    abbr = "Incl. Write Bytes";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Write Bytes";
                    abbr = "Attr. Write Bytes";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected WRITE_BYTES metric subtype %d", st));
                }
                break;

            case IO_READ_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Read Count";
                    abbr = "Excl. Read Count";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Read Count";
                    abbr = "Incl. Read Count";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Read Count";
                    abbr = "Attr. Read Count";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected READCNT metric subtype %d", st));
                }
                break;

            case IO_WRITE_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Write Count";
                    abbr = "Excl. Write Count";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Write Count";
                    abbr = "Incl. Write Count";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Write Count";
                    abbr = "Attr. Write Count";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected WRITECNT metric subtype %d", st));
                }
                break;

            case IO_OTHER_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Other I/O Count";
                    abbr = "Excl. Other I/O Count";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Other I/O Count";
                    abbr = "Incl. Other I/O Count";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Other I/O Count";
                    abbr = "Attr. Other I/O Count";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OTHERIOCNT metric subtype %d", st));
                }
                break;

            case IO_ERROR_CNT:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive I/O Error Count";
                    abbr = "Excl. I/O Error Count";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive I/O Error Count";
                    abbr = "Incl. I/O Error Count";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed I/O Error Count";
                    abbr = "Attr. I/O Error Count";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected IOERRORCNT metric subtype %d", st));
                }
                break;

            case IO_READ_TIME:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Read Time";
                    abbr = "Excl. Read Time";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Read Time";
                    abbr = "Incl. Read Time";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Read Time";
                    abbr = "Attr. Read Time";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected READ_TIME metric subtype %d", st));
                }
                break;

            case IO_WRITE_TIME:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Write Time";
                    abbr = "Excl. Write Time";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Write Time";
                    abbr = "Incl. Write Time";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Write Time";
                    abbr = "Attr. Write Time";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected WRITE_TIME metric subtype %d", st));
                }
                break;

            case IO_OTHER_TIME:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Other I/O Time";
                    abbr = "Excl. Other I/O Time";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Other I/O Time";
                    abbr = "Incl. Other I/O Time";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Other I/O Time";
                    abbr = "Attr. Other I/O Time";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OTHERIO_TIME metric subtype %d", st));
                }
                break;

            case IO_ERROR_TIME:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive I/O Error Time";
                    abbr = "Excl. I/O Error Time";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive I/O Error Time";
                    abbr = "Incl. I/O Error Time";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed I/O Error Time";
                    abbr = "Attr. I/O Error Time";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected IOERROR_TIME metric subtype %d", st));
                }
                break;

            case SIZES:
                name = "Size";
                abbr = "Size";
                abbr_unit = "bytes";
                break;

            case ADDRESS:
                name = "PC Address";
                abbr = "PC Addr.";
                break;

            case ONAME:
                name = "Name";
                abbr = "Name";
                break;

            case OMP_NONE:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Non-OpenMP Time";
                    abbr = "Excl. Non-OMP";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Non-OpenMP Time";
                    abbr = "Incl. Non-OMP";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Non-OpenMP Time";
                    abbr = "Attr. Non-OMP";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected Non-OpenMP metric subtype %d", st));
                }
                break;
            case OMP_OVHD:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Overhead Time";
                    abbr = "Excl. OMP ovhd.";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Overhead Time";
                    abbr = "Incl. OMP ovhd.";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Overhead Time";
                    abbr = "Attr. OMP ovhd.";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Overhead metric subtype %d", st));
                }
                break;
            case OMP_WORK:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Work Time";
                    abbr = "Excl. OMP Work";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Work Time";
                    abbr = "Incl. OMP Work";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Work Time";
                    abbr = "Attr. OMP Work";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Work metric subtype %d", st));
                }
                break;
            case OMP_IBAR:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Implicit Barrier Time";
                    abbr = "Excl. OMP i-barr.";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Implicit Barrier Time";
                    abbr = "Incl. OMP i-barr.";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Implicit Barrier Time";
                    abbr = "Attr. OMP i-barr.";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Implicit Barrier metric subtype %d", st));
                }
                break;
            case OMP_EBAR:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Explicit Barrier Time";
                    abbr = "Excl. OMP e-barr.";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Explicit Barrier Time";
                    abbr = "Incl. OMP e-barr.";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Explicit Barrier Time";
                    abbr = "Attr. OMP e-barr.";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Explicit Barrier metric subtype %d", st));
                }
                break;
            case OMP_WAIT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Wait Time";
                    abbr = "Excl. OMP Wait";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Wait Time";
                    abbr = "Incl. OMP Wait";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Wait Time";
                    abbr = "Attr. OMP Wait";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Wait metric subtype %d", st));
                }
                break;
            case OMP_SERL:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Serial Time";
                    abbr = "Excl. OMP serl";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Serial Time";
                    abbr = "Incl. OMP serl";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Serial Time";
                    abbr = "Attr. OMP serl";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Slave Idle metric subtype %d", st));
                }
                break;
            case OMP_RDUC:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Reduction Time";
                    abbr = "Excl. OMP rduc";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Reduction Time";
                    abbr = "Incl. OMP rduc";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Reduction Time";
                    abbr = "Attr. OMP rduc";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Reduction metric subtype %d", st));
                }
                break;
            case OMP_LKWT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Lock Wait Time";
                    abbr = "Excl. OMP lkwt";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Lock Wait Time";
                    abbr = "Incl. OMP lkwt";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Lock Wait Time";
                    abbr = "Attr. OMP lkwt";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Lock Wait metric subtype %d", st));
                }
                break;
            case OMP_CTWT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Critical Section Wait Time";
                    abbr = "Excl. OMP ctwt";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Critical Section Wait Time";
                    abbr = "Incl. OMP ctwt";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Critical Section Wait Time";
                    abbr = "Attr. OMP ctwt";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Critical Section Wait metric subtype %d", st));
                }
                break;
            case OMP_ODWT:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Ordered Section Wait Time";
                    abbr = "Excl. OMP odwt";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Ordered Section Wait Time";
                    abbr = "Incl. OMP odwt";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Ordered Section Wait Time";
                    abbr = "Attr. OMP odwt";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Ordered Section Wait metric subtype %d", st));
                }
                break;
            case OMP_MSTR:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Master Serial Time";
                    abbr = "Excl. OMP ser.";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Master Serial Time";
                    abbr = "Incl. OMP ser.";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Master Serial Time";
                    abbr = "Attr. OMP ser.";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Master Serial metric subtype %d", st));
                }
                break;
            case OMP_SNGL:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Single Region Time";
                    abbr = "Excl. OMP sngl";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Single Region Time";
                    abbr = "Incl. OMP sngl";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Single Region Time";
                    abbr = "Attr. OMP sngl";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Single Region metric subtype %d", st));
                }
                break;
            case OMP_ORDD:
                abbr_unit = "sec.";
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive OpenMP Ordered Region Time";
                    abbr = "Excl. OMP ordd";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive OpenMP Ordered Region Time";
                    abbr = "Incl. OMP ordd";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed OpenMP Ordered Region Time";
                    abbr = "Attr. OMP ordd";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected OpenMP Ordered Region metric subtype %d", st));
                }
                break;
            case RACCESS:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Race Accesses";
                    abbr = "Excl. Race Accesses";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Race Accesses";
                    abbr = "Incl. Race Accesses";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Race Accesses";
                    abbr = "Attr. Race Accesses";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected Race Access metric subtype %d", st));
                }
                break;
            case DEADLOCKS:
                if (st == EXCLUSIVE)
                {
                    name = "Exclusive Deadlocks";
                    abbr = "Excl. Deadlocks";
                }
                else if (st == INCLUSIVE)
                {
                    name = "Inclusive Deadlocks";
                    abbr = "Incl. Deadlocks";
                }
                else if (st == ATTRIBUTED)
                {
                    name = "Attributed Deadlocks";
                    abbr = "Attr. Deadlocks";
                }
                else
                {
                    throw new IllegalStateException(String.format("Unexpected Deadlocks metric subtype %d", st));
                }
                break;
            default:
                throw new IllegalStateException();
        }
    } // set_subtype
}
