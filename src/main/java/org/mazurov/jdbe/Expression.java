package org.mazurov.jdbe;

public class Expression {

    Expression() {}

    Expression(Expression rhs) {}

    public Expression copy() {
        return new Expression(this);
    }
}
