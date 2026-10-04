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

public class Emsgqueue {

    Emsg first;
    Emsg last;
    String qname;

    public Emsgqueue(String qname) {
        this.qname = qname;
    }

    public Emsg find_msg (Emsg.Cmsg_warn w, String msg) {
        for (Emsg m = first; m != null; m = m.next) {
            if (m.get_warn() == w && m.get_msg().equals(msg)) {
                return m;
            }
        }
        return null;
    }

    public Emsg append(Emsg.Cmsg_warn w, String msg) {
        Emsg m = find_msg(w, msg);
        if (m != null) {
            return m;
        }
        m = new Emsg (w, msg);
        append(m);
        return m;
    }

    // Append a single message to a queue
    public void append(Emsg m) {
        m.next = null;
        if (last == null) {
            first = m;
            last = m;
        } else {
            last.next = m;
            last = m;
        }
    }

    // Append a queue of messages to a queue
    public void appendqueue(Emsgqueue mq) {
        Emsg m = mq.first;
        if (m == null)
            return;
        if (last == null)
            first = m;
        else
            last.next = m;
        // now find the new last
        while (m.next != null) {
            m = m.next;
        }
        last = m;
    }

    public Emsg fetch() {
        return first;
    }

    // Empty the queue, deleting all messages
    public void clear() {
        first = null;
        last = null;
    }

    // Mark the queue empty, without deleting the messages --
    //	used when the messages have been requeued somewhere else
    public void mark_clear() {
        first = null;
        last = null;
    }
}
