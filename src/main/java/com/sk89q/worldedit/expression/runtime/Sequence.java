// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit.expression.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * A sequence of operations, usually separated by semicolons in the input stream.
 *
 * @author TomyLobo
 */
public class Sequence extends RValue {
    private final RValue[] sequence;

    public Sequence(int position, RValue... sequence) {
        super(position);

        this.sequence = sequence;
    }

    @Override
    public char id() {
        return 's';
    }

    @Override
    public double getValue() throws EvaluationException {
        double ret = 0;
        for (RValue invokable : sequence) {
            ret = invokable.getValue();
        }
        return ret;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("seq(");
        boolean first = true;
        for (RValue invokable : sequence) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(invokable);
            first = false;
        }

        return sb.append(')').toString();
    }

    @Override
    public RValue optimize() throws EvaluationException {
        List<RValue> newSequence = new ArrayList<RValue>();

        for (RValue invokable : sequence) {
            invokable = invokable.optimize();
            if (invokable instanceof Sequence) {
                for (RValue subInvokable : ((Sequence) invokable).sequence) {
                    newSequence.add(subInvokable);
                }
            }
            else {
                newSequence.add(invokable);
            }
        }

        return new Sequence(getPosition(), newSequence.toArray(new RValue[newSequence.size()]));
    }
}
