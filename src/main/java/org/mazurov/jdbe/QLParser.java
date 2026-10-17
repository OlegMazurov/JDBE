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

import java.util.List;

import static org.mazurov.jdbe.Enums.Prop_type;
import static org.mazurov.jdbe.QLLexer.TokType;
import static org.mazurov.jdbe.QLLexer.Token;

class QLParser {

    static class QLParseException extends RuntimeException {
        QLParseException(String msg) {
            super(msg);
        }
    }

    static Expression parse(String src) {
        List<Token> tokens = new QLLexer(src).tokenize();
        if (tokens.size() == 1 && tokens.get(0).type == TokType.EOF)
            return Expression.num(1);
        QLParser p = new QLParser(tokens);
        Expression e = p.parseIn();
        p.expect(TokType.EOF, "trailing input");
        return e;
    }

    private final List<Token> tokens;
    private int pos;

    private QLParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private boolean check(TokType t) {
        return peek().type == t;
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private boolean match(TokType t) {
        if (check(t)) {
            pos++;
            return true;
        }
        return false;
    }

    private Token expect(TokType t, String what) {
        if (!check(t))
            throw new QLParseException("Expected " + what + ", got " + peek());
        return advance();
    }

    private Expression parseIn() {
        Expression left = parseComma();
        if (match(TokType.IN)) {
            Expression right = parseComma();
            left = new Expression(Expression.OpCode.IN, left, right);
        } else if (match(TokType.SOME)) {
            expect(TokType.IN, "IN after SOME");
            Expression right = parseComma();
            left = new Expression(Expression.OpCode.SOMEIN, left, right);
        } else if (match(TokType.ORDR)) {
            expect(TokType.IN, "IN after ORDERED");
            Expression right = parseComma();
            left = new Expression(Expression.OpCode.ORDRIN, left, right);
        } else {
            return left;
        }
        if (check(TokType.IN) || check(TokType.SOME) || check(TokType.ORDR))
            throw new QLParseException("IN/SOME/ORDERED cannot be chained");
        return left;
    }

    private Expression parseComma() {
        Expression left = parseTernary();
        while (match(TokType.COMMA))
            left = new Expression(Expression.OpCode.COMMA, left, parseTernary());
        return left;
    }

    private Expression parseTernary() {
        Expression cond = parseLogical();
        if (match(TokType.QWE)) {
            Expression thenExpr = parseTernary();
            expect(TokType.COLON, "':' in ternary expression");
            Expression elseExpr = parseTernary();
            return new Expression(Expression.OpCode.QWE, cond,
                    new Expression(Expression.OpCode.COLON, thenExpr, elseExpr));
        }
        return cond;
    }

    private Expression parseLogical() {
        Expression left = parseComparison();
        while (true) {
            Expression.OpCode op = switch (peek().type) {
                case AND -> Expression.OpCode.AND;
                case OR -> Expression.OpCode.OR;
                case EQV -> Expression.OpCode.EQV;
                case NEQV -> Expression.OpCode.NEQV;
                case BITAND -> Expression.OpCode.BITAND;
                case BITOR -> Expression.OpCode.BITOR;
                case BITXOR -> Expression.OpCode.BITXOR;
                default -> null;
            };
            if (op == null)
                return left;
            advance();
            left = new Expression(op, left, parseComparison());
        }
    }

    private Expression parseComparison() {
        Expression left = parseAdditive();
        Expression.OpCode op = switch (peek().type) {
            case EQ -> Expression.OpCode.EQ;
            case NE -> Expression.OpCode.NE;
            case LT -> Expression.OpCode.LT;
            case GT -> Expression.OpCode.GT;
            case LE -> Expression.OpCode.LE;
            case GE -> Expression.OpCode.GE;
            default -> null;
        };
        if (op == null)
            return left;
        advance();
        left = new Expression(op, left, parseAdditive());
        switch (peek().type) {
            case EQ, NE, LT, GT, LE, GE -> throw new QLParseException("Comparison operators cannot be chained");
            default -> { }
        }
        return left;
    }

    private Expression parseAdditive() {
        Expression left = parseDeg();
        while (true) {
            Expression.OpCode op = switch (peek().type) {
                case LS -> Expression.OpCode.LS;
                case RS -> Expression.OpCode.RS;
                case ADD -> Expression.OpCode.ADD;
                case MINUS -> Expression.OpCode.MINUS;
                case MUL -> Expression.OpCode.MUL;
                case DIV -> Expression.OpCode.DIV;
                case REM -> Expression.OpCode.REM;
                default -> null;
            };
            if (op == null)
                return left;
            advance();
            left = new Expression(op, left, parseDeg());
        }
    }

    private Expression parseDeg() {
        Expression left = parseUnary();
        if (match(TokType.DEG))
            return new Expression(Expression.OpCode.DEG, left, parseDeg());
        return left;
    }

    private Expression parseUnary() {
        if (match(TokType.MINUS))
            return new Expression(Expression.OpCode.MINUS, Expression.num(0), parseUnary());
        if (match(TokType.NOT))
            return new Expression(Expression.OpCode.NOT, parseUnary());
        if (match(TokType.BITNOT))
            return new Expression(Expression.OpCode.BITNOT, parseUnary());
        return parseAtom();
    }

    private Expression parseAtom() {
        Token t = peek();
        switch (t.type) {
            case LPAR -> {
                advance();
                Expression e = parseIn();
                expect(TokType.RPAR, "')'");
                return e;
            }
            case NUM -> {
                advance();
                return Expression.num(t.numVal);
            }
            case FNAME, DNAME -> {
                advance();
                expect(TokType.LPAR, "'(' after " + t.type);
                String pattern = expect(TokType.QSTR, "quoted string").strVal;
                expect(TokType.RPAR, "')'");
                return Expression.strCall(Expression.OpCode.FUNC, pattern);
            }
            case HASPROP -> {
                advance();
                expect(TokType.LPAR, "'(' after HAS_PROP");
                Token name = expect(TokType.NAME, "property name");
                expect(TokType.RPAR, "')'");
                return new Expression(Expression.OpCode.HASPROP, Expression.name(resolveName(name.strVal)));
            }
            case JGROUP, JPARENT -> {
                advance();
                expect(TokType.LPAR, "'(' after " + t.type);
                String pattern = expect(TokType.QSTR, "quoted string").strVal;
                expect(TokType.RPAR, "')'");
                return Expression.strCall(Expression.OpCode.JAVA, pattern);
            }
            case FILEIOVFD -> {
                advance();
                expect(TokType.LPAR, "'(' after FILEIOVFD");
                String pattern = expect(TokType.QSTR, "quoted string").strVal;
                expect(TokType.RPAR, "')'");
                return Expression.strCall(Expression.OpCode.FILE, pattern);
            }
            case NAME -> {
                advance();
                return Expression.name(resolveName(t.strVal));
            }
            default -> throw new QLParseException("Syntax error at " + t);
        }
    }

    private Prop_type resolveName(String name) {
        Prop_type propId = DbeSession.getInstance().getPropIdByName(name);
        if (propId == Prop_type.PROP_NONE)
            throw new QLParseException("Name not found: " + name);
        return propId;
    }
}
