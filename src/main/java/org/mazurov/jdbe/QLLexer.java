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

class QLLexer {

    enum TokType {
        NUM, QSTR, NAME,
        IN, SOME, ORDR, FNAME, DNAME, HASPROP, JGROUP, JPARENT, FILEIOVFD,
        LPAR, RPAR, COMMA, QWE, COLON,
        AND, OR, EQV, NEQV, BITAND, BITOR, BITXOR,
        EQ, NE, LT, GT, LE, GE,
        LS, RS, ADD, MINUS, MUL, DIV, REM, DEG,
        NOT, BITNOT,
        EOF
    }

    static final class Token {
        final TokType type;
        final long numVal;
        final String strVal;

        Token(TokType type) {
            this(type, 0, null);
        }

        Token(TokType type, long numVal) {
            this(type, numVal, null);
        }

        Token(TokType type, long numVal, String strVal) {
            this.type = type;
            this.numVal = numVal;
            this.strVal = strVal;
        }

        @Override
        public String toString() {
            return strVal != null ? type + "(" + strVal + ")"
                    : numVal != 0 ? type + "(" + numVal + ")" : type.toString();
        }
    }

    static class QLSyntaxException extends RuntimeException {
        QLSyntaxException(String msg) {
            super(msg);
        }
    }

    private final String src;
    private int pos;

    QLLexer(String src) {
        this.src = src;
    }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type != TokType.EOF);
        return tokens;
    }

    private int peek() {
        return pos < src.length() ? src.charAt(pos) : -1;
    }

    private Token next() {
        while (peek() == ' ' || peek() == '\t')
            pos++;
        int c = peek();
        if (c == -1 || c == 0 || c == '\n')
            return new Token(TokType.EOF);
        if (c >= '0' && c <= '9')
            return lexNumber();
        if (c == '"')
            return lexString();
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
            return lexIdent();

        pos++;
        switch (c) {
            case '(': return new Token(TokType.LPAR);
            case ')': return new Token(TokType.RPAR);
            case ',': return new Token(TokType.COMMA);
            case '%': return new Token(TokType.REM);
            case '/': return new Token(TokType.DIV);
            case '*': return new Token(TokType.MUL);
            case '-': return new Token(TokType.MINUS);
            case '+': return new Token(TokType.ADD);
            case '~': return new Token(TokType.BITNOT);
            case '^': return new Token(TokType.BITXOR);
            case '?': return new Token(TokType.QWE);
            case ':': return new Token(TokType.COLON);
            case '|':
                if (peek() == '|') { pos++; return new Token(TokType.OR); }
                return new Token(TokType.BITOR);
            case '&':
                if (peek() == '&') { pos++; return new Token(TokType.AND); }
                return new Token(TokType.BITAND);
            case '!':
                if (peek() == '=') { pos++; return new Token(TokType.NE); }
                return new Token(TokType.NOT);
            case '=':
                if (peek() == '=') { pos++; return new Token(TokType.EQ); }
                throw new QLSyntaxException("Syntax error after =");
            case '<':
                if (peek() == '=') { pos++; return new Token(TokType.LE); }
                if (peek() == '<') { pos++; return new Token(TokType.LS); }
                return new Token(TokType.LT);
            case '>':
                if (peek() == '=') { pos++; return new Token(TokType.GE); }
                if (peek() == '>') { pos++; return new Token(TokType.RS); }
                return new Token(TokType.GT);
        }
        throw new QLSyntaxException("Syntax error");
    }

    private Token lexNumber() {
        int base = 10;
        int c = peek();
        if (c == '0') {
            pos++;
            base = 8;
            if (peek() == 'x' || peek() == 'X') {
                pos++;
                base = 16;
            }
        }
        long val = 0;
        while (true) {
            c = peek();
            int digit = Character.digit(c, base);
            if (digit < 0)
                break;
            val = val * base + digit;
            pos++;
        }
        return new Token(TokType.NUM, val);
    }

    private Token lexString() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length())
                throw new QLSyntaxException("Unclosed \"");
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                return new Token(TokType.QSTR, 0, sb.toString());
            }
            if (c == 0 || c == '\n')
                throw new QLSyntaxException("Multiline strings are not supported");
            sb.append(c);
            pos++;
        }
    }

    private Token lexIdent() {
        int start = pos;
        pos++;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')
                pos++;
            else
                break;
        }
        String name = src.substring(start, pos);
        return switch (name.toUpperCase()) {
            case "IN" -> new Token(TokType.IN);
            case "SOME" -> new Token(TokType.SOME);
            case "ORDERED" -> new Token(TokType.ORDR);
            case "TRUE" -> new Token(TokType.NUM, 1);
            case "FALSE" -> new Token(TokType.NUM, 0);
            case "FNAME" -> new Token(TokType.FNAME);
            case "DNAME" -> new Token(TokType.DNAME);
            case "HAS_PROP" -> new Token(TokType.HASPROP);
            case "JGROUP" -> new Token(TokType.JGROUP);
            case "JPARENT" -> new Token(TokType.JPARENT);
            case "FILEIOVFD" -> new Token(TokType.FILEIOVFD);
            default -> new Token(TokType.NAME, 0, name);
        };
    }
}
