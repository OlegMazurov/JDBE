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

import static org.mazurov.jdbe.Enums.Prop_type;

class Expression {

    enum OpCode {
        NUM, NAME,
        QWE, COLON,
        AND, OR, EQV, NEQV, BITAND, BITOR, BITXOR,
        EQ, NE, LT, GT, LE, GE,
        LS, RS, ADD, MINUS, MUL, DIV, REM, DEG,
        NOT, BITNOT,
        COMMA, IN, SOMEIN, ORDRIN,
        FUNC, JAVA, FILE, HASPROP
    }

    final OpCode op;
    final Expression arg0, arg1;
    final long numVal;       // OP_NUM
    final Prop_type propId;  // OP_NAME, OP_HASPROP's operand
    final String strVal;     // OP_FUNC/OP_JAVA/OP_FILE's quoted pattern argument

    Expression(OpCode op, Expression arg0, Expression arg1) {
        this.op = op;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.numVal = 0;
        this.propId = null;
        this.strVal = null;
    }

    Expression(OpCode op, Expression arg0) {
        this(op, arg0, null);
    }

    static Expression num(long v) {
        return new Expression(OpCode.NUM, v);
    }

    private Expression(OpCode op, long numVal) {
        this.op = op;
        this.arg0 = null;
        this.arg1 = null;
        this.numVal = numVal;
        this.propId = null;
        this.strVal = null;
    }

    static Expression name(Prop_type propId) {
        return new Expression(OpCode.NAME, propId);
    }

    private Expression(OpCode op, Prop_type propId) {
        this.op = op;
        this.arg0 = null;
        this.arg1 = null;
        this.numVal = 0;
        this.propId = propId;
        this.strVal = null;
    }

    static Expression strCall(OpCode op, String strVal) {
        return new Expression(op, strVal);
    }

    private Expression(OpCode op, String strVal) {
        this.op = op;
        this.arg0 = null;
        this.arg1 = null;
        this.numVal = 0;
        this.propId = null;
        this.strVal = strVal;
    }

    static class Context {
        final Experiment exp;
        final DbeView dbev;
        DataView dview;
        long eventId;

        Context(DbeView dbev, Experiment exp) {
            this.dbev = dbev;
            this.exp = exp;
        }

        void put(DataView dview, long eventId) {
            this.dview = dview;
            this.eventId = eventId;
        }
    }

    boolean passes(Context ctx) {
        List<Long> r = eval(ctx);
        return r == null || r.get(0) != 0;
    }

    List<Long> eval(Context ctx) {
        return switch (op) {
            case NUM -> List.of(numVal);
            case NAME -> {
                if (propId == Prop_type.PROP_STACK || propId == Prop_type.PROP_USTACK
                        || propId == Prop_type.PROP_MSTACK || propId == Prop_type.PROP_XSTACK)
                    yield evalStackList(ctx);
                Long v = getVal(propId, ctx);
                yield v == null ? null : List.of(v);
            }
            case NOT -> unary(ctx, v -> v == 0 ? 1L : 0L);
            case BITNOT -> unary(ctx, v -> ~v);
            case MINUS -> binaryScalar(ctx, (a, b) -> a - b);
            case ADD -> binaryScalar(ctx, Long::sum);
            case MUL -> binaryScalar(ctx, (a, b) -> a * b);
            case BITAND -> binaryScalar(ctx, (a, b) -> a & b);
            case BITOR -> binaryScalar(ctx, (a, b) -> a | b);
            case BITXOR -> binaryScalar(ctx, (a, b) -> a ^ b);
            case LS -> binaryScalar(ctx, (a, b) -> a << b);
            case RS -> binaryScalar(ctx, (a, b) -> a >>> b); // uint64 >> is a logical (zero-fill) shift
            case DIV -> binaryScalar(ctx, (a, b) -> b == 0 ? 0L : Long.divideUnsigned(a, b));
            case REM -> binaryScalar(ctx, (a, b) -> b == 0 ? 0L : Long.remainderUnsigned(a, b));
            case EQ -> binaryScalar(ctx, (a, b) -> a.longValue() == b.longValue() ? 1L : 0L);
            case NE -> binaryScalar(ctx, (a, b) -> a.longValue() != b.longValue() ? 1L : 0L);
            case LT -> binaryScalar(ctx, (a, b) -> Long.compareUnsigned(a, b) < 0 ? 1L : 0L);
            case GT -> binaryScalar(ctx, (a, b) -> Long.compareUnsigned(a, b) > 0 ? 1L : 0L);
            case LE -> binaryScalar(ctx, (a, b) -> Long.compareUnsigned(a, b) <= 0 ? 1L : 0L);
            case GE -> binaryScalar(ctx, (a, b) -> Long.compareUnsigned(a, b) >= 0 ? 1L : 0L);
            case EQV -> binaryScalar(ctx, (a, b) -> (a == 0) == (b == 0) ? 1L : 0L);
            case NEQV -> binaryScalar(ctx, (a, b) -> (a == 0) == (b == 0) ? 0L : 1L);
            case DEG -> binaryScalar(ctx, (base, exp) -> {
                long result = 1;
                for (long i = exp; i != 0; i--)
                    result *= base;
                return result;
            });
            case AND -> evalAndOr(ctx, true);
            case OR -> evalAndOr(ctx, false);
            case QWE -> {
                List<Long> c = arg0.eval(ctx);
                if (c == null)
                    yield null;
                yield c.get(0) != 0 ? arg1.arg0.eval(ctx) : arg1.arg1.eval(ctx);
            }
            case COLON -> null; // never evaluated directly; only reached via its parent OP_QWE
            case COMMA -> {
                List<Long> l = arg0.eval(ctx);
                List<Long> r = arg1.eval(ctx);
                if (l == null || r == null)
                    yield null;
                List<Long> combined = new ArrayList<>(l.size() + r.size());
                combined.addAll(r);
                combined.addAll(l);
                yield combined;
            }
            case IN -> evalIn(ctx, Relation.IN);
            case SOMEIN -> evalIn(ctx, Relation.SOMEIN);
            case ORDRIN -> evalIn(ctx, Relation.ORDRIN);
            case FUNC, JAVA, FILE -> List.of(0L);
            case HASPROP -> {
                if (ctx.dview == null)
                    yield null;
                yield List.of(ctx.dview.getProp(arg0.propId) != null ? 1L : 0L);
            }
        };
    }

    private interface LongBinOp {
        long apply(Long a, Long b);
    }

    private interface LongUnOp {
        long apply(long a);
    }

    private List<Long> unary(Context ctx, LongUnOp fn) {
        List<Long> a = arg0.eval(ctx);
        return a == null ? null : List.of(fn.apply(a.get(0)));
    }

    private List<Long> binaryScalar(Context ctx, LongBinOp fn) {
        List<Long> a = arg0.eval(ctx);
        if (a == null)
            return null;
        List<Long> b = arg1.eval(ctx);
        if (b == null)
            return null;
        return List.of(fn.apply(a.get(0), b.get(0)));
    }

    private List<Long> evalAndOr(Context ctx, boolean isAnd) {
        long dominant = isAnd ? 0 : 1;
        List<Long> a = arg0.eval(ctx);
        if (a != null) {
            if ((a.get(0) != 0 ? 1 : 0) == dominant)
                return List.of(dominant);
            List<Long> b = arg1.eval(ctx);
            return b == null ? null : List.of(b.get(0) != 0 ? 1L : 0L);
        }
        List<Long> b = arg1.eval(ctx);
        if (b != null && (b.get(0) != 0 ? 1 : 0) == dominant)
            return List.of(dominant);
        return null;
    }

    private enum Relation { IN, SOMEIN, ORDRIN }

    private List<Long> evalIn(Context ctx, Relation rel) {
        List<Long> needle = arg0.eval(ctx);
        List<Long> haystack = arg1.eval(ctx);
        if (needle == null || haystack == null)
            return null;
        boolean result = switch (rel) {
            case IN -> needle.stream().allMatch(haystack::contains);
            case SOMEIN -> needle.stream().anyMatch(haystack::contains);
            case ORDRIN -> {
                int n = needle.size(), h = haystack.size();
                outer:
                for (int start = 0; start + n <= h; start++) {
                    for (int i = 0; i < n; i++)
                        if (!haystack.get(start + i).equals(needle.get(i)))
                            continue outer;
                    yield true;
                }
                yield false;
            }
        };
        return List.of(result ? 1L : 0L);
    }

    private Long getVal(Prop_type propId, Context ctx) {
        return switch (propId) {
            case PROP_FREQ_MHZ -> ctx.exp != null && ctx.exp.clock != 0 ? (long) ctx.exp.clock : null;
            case PROP_PID -> ctx.exp == null ? null : (long) ctx.exp.getPID();
            case PROP_EXPID -> ctx.exp == null ? null : (long) ctx.exp.getUserExpId();
            case PROP_EXPID_CMP -> ctx.exp == null ? null : (long) ctx.exp.getUserExpId();
            case PROP_EXPGRID -> 0L;
            case PROP_NTICK_USEC -> {
                if (ctx.exp == null || ctx.dview == null || ctx.dview.getProp(Prop_type.PROP_NTICK) == null)
                    yield null;
                yield ctx.dview.getIntValue(Prop_type.PROP_NTICK.ordinal(), ctx.eventId)
                        * ctx.exp.coll_params.ptimer_usec;
            }
            case PROP_ATSTAMP, PROP_ETSTAMP -> {
                if (ctx.exp == null || ctx.dview == null || ctx.dview.getProp(Prop_type.PROP_TSTAMP) == null)
                    yield null;
                long tstamp = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
                yield propId == Prop_type.PROP_ATSTAMP ? tstamp : tstamp - ctx.exp.getStartTime();
            }
            case PROP_TSTAMP, PROP_TSTAMP_LO, PROP_TSTAMP_HI -> getTstamp(propId, ctx);
            case PROP_IOHEAPBYTES -> {
                if (ctx.dview == null)
                    yield null;
                Prop_type p = ctx.dview.getProp(Prop_type.PROP_IONBYTE) != null
                        ? Prop_type.PROP_IONBYTE : Prop_type.PROP_HSIZE;
                if (ctx.dview.getProp(p) == null)
                    yield null;
                yield ctx.dview.getLongValue(p.ordinal(), ctx.eventId);
            }
            case PROP_SAMPLE_MAP -> {
                if (ctx.exp == null || ctx.dview == null)
                    yield null;
                if (ctx.dview.getProp(Prop_type.PROP_SAMPLE) != null)
                    yield (long) ctx.dview.getIntValue(Prop_type.PROP_SAMPLE.ordinal(), ctx.eventId);
                long tstamp = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
                Experiment.Sample sample = ctx.exp.mapEventToSample(tstamp);
                yield sample != null ? (long) sample.number : -1L;
            }
            case PROP_GCEVENT_MAP -> {
                if (ctx.exp == null || ctx.dview == null)
                    yield null;
                if (ctx.dview.getProp(Prop_type.PROP_GCEVENT) != null)
                    yield (long) ctx.dview.getIntValue(Prop_type.PROP_GCEVENT.ordinal(), ctx.eventId);
                long tstamp = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
                Experiment.GCEvent gcevent = ctx.exp.mapEventToGCEvent(tstamp);
                yield gcevent != null ? (long) gcevent.id : 0L;
            }
            case PROP_JTHREAD -> {
                if (ctx.exp == null || ctx.dview == null
                        || ctx.dview.getProp(Prop_type.PROP_THRID) == null
                        || ctx.dview.getProp(Prop_type.PROP_TSTAMP) == null)
                    yield null;
                long thrid = ctx.dview.getLongValue(Prop_type.PROP_THRID.ordinal(), ctx.eventId);
                long ts = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
                Experiment.JThread jt = ctx.exp.lookupJThread(thrid, ts);
                long jthrId = (jt != null && jt != Experiment.JTHREAD_NONE) ? jt.jthr_id : 0;
                yield ((long) ctx.exp.getUserExpId() << 32) | jthrId;
            }
            case PROP_LEAF, PROP_STACKID -> {
                if (ctx.dview == null
                        || ctx.dview.getProp(Prop_type.PROP_FRINFO) == null
                        || ctx.dview.getProp(Prop_type.PROP_THRID) == null
                        || ctx.dview.getProp(Prop_type.PROP_TSTAMP) == null)
                    yield null;
                long frinfo = ctx.dview.getLongValue(Prop_type.PROP_FRINFO.ordinal(), ctx.eventId);
                if (propId == Prop_type.PROP_STACKID)
                    yield frinfo; // synthetic: this port's stable "stack handle" is the FRINFO id itself
                long thrid = ctx.dview.getLongValue(Prop_type.PROP_THRID.ordinal(), ctx.eventId);
                long ts = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
                List<Function> stack = ctx.exp.resolveStack(frinfo, thrid, ts);
                yield stack.get(0).id; // PROP_LEAF: the top-of-stack function
            }
            default -> {
                if (ctx.dview == null)
                    yield null;
                PropDescr propDscr = ctx.dview.getProp(propId);
                if (propDscr == null)
                    yield null;
                yield switch (propDscr.vtype) {
                    case TYPE_INT32 -> (long) ctx.dview.getIntValue(propId.ordinal(), ctx.eventId);
                    case TYPE_UINT32 -> ctx.dview.getIntValue(propId.ordinal(), ctx.eventId) & 0xFFFFFFFFL;
                    case TYPE_INT64, TYPE_UINT64 -> ctx.dview.getLongValue(propId.ordinal(), ctx.eventId);
                    case TYPE_OBJ -> {
                        Object o = ctx.dview.getObjValue(propId.ordinal(), ctx.eventId);
                        yield o == null ? null : (long) o.hashCode();
                    }
                    default -> null; // TYPE_STRING/TYPE_DOUBLE: "weird, programming error?" in native too
                };
            }
        };
    }

    private List<Long> evalStackList(Context ctx) {
        if (ctx.dview == null
                || ctx.dview.getProp(Prop_type.PROP_FRINFO) == null
                || ctx.dview.getProp(Prop_type.PROP_THRID) == null
                || ctx.dview.getProp(Prop_type.PROP_TSTAMP) == null)
            return null;
        long frinfo = ctx.dview.getLongValue(Prop_type.PROP_FRINFO.ordinal(), ctx.eventId);
        long thrid = ctx.dview.getLongValue(Prop_type.PROP_THRID.ordinal(), ctx.eventId);
        long ts = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
        List<Function> stack = ctx.exp.resolveStack(frinfo, thrid, ts);
        List<Long> ids = new ArrayList<>(stack.size() + 1);
        for (Function f : stack)
            ids.add(f.id);
        ids.add(DbeSession.getInstance().getTotalFunction().id);
        return ids;
    }

    private Long getTstamp(Prop_type propId, Context ctx) {
        if (ctx.exp == null || ctx.dview == null || ctx.dview.getProp(Prop_type.PROP_TSTAMP) == null)
            return null;
        long tstamp = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP.ordinal(), ctx.eventId);
        long val = tstamp - ctx.exp.getStartTime() + ctx.exp.getRelativeStartTime();
        if (propId == Prop_type.PROP_TSTAMP)
            return val;
        if (ctx.dview.getProp(Prop_type.PROP_EVT_TIME) != null) {
            long delta = ctx.dview.getLongValue(Prop_type.PROP_EVT_TIME.ordinal(), ctx.eventId);
            if (propId == Prop_type.PROP_TSTAMP_LO) {
                if (delta > 0) // positive delta means TSTAMP is at the end of the event
                    val -= delta;
            } else { // PROP_TSTAMP_HI
                if (delta < 0) // negative delta means TSTAMP is at the start of the event
                    val -= delta;
            }
            return val;
        }
        if (ctx.dview.getProp(Prop_type.PROP_TSTAMP2) != null) {
            if (propId == Prop_type.PROP_TSTAMP_HI) {
                long tstamp2 = ctx.dview.getLongValue(Prop_type.PROP_TSTAMP2.ordinal(), ctx.eventId);
                if (tstamp2 == 0)
                    return val; // not initialized, event has no duration
                if (tstamp2 == Long.MAX_VALUE)
                    tstamp2 = ctx.exp.getLastEvent();
                long delta = tstamp2 - tstamp;
                if (delta >= 0)
                    val += delta;
            }
            return val; // PROP_TSTAMP_LO: no modification needed
        }
        return val;
    }
}
