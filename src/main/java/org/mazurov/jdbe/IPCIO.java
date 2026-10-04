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

import java.io.IOException;
import static org.mazurov.jdbe.ERIPC.ipc_log;

public class IPCIO {

    private static final int REQUEST_HAS_NO_BODY = 0xFFFFFFFF;
    private static final int RESPONSE_STATUS_DEFAULT   = 0;
    private static final int RESPONSE_STATUS_SUCCESS   = 1;
    private static final int RESPONSE_STATUS_FAILURE   = 2;
    private static final int RESPONSE_STATUS_CANCELLED = 3;
    private static final int RESPONSE_TYPE_ACK = 0;
    private static final int RESPONSE_TYPE_PROGRESS = 1;
    private static final int RESPONSE_TYPE_COMPLETE = 2;
    private static final int RESPONSE_TYPE_HANDSHAKE = 3;
    private static final int HEADER_MARKER = 0xff;
    private static final int REQUEST_TYPE_DEFAULT   = 0;
    private static final int REQUEST_TYPE_CANCEL    = 1;
    private static final int REQUEST_TYPE_HANDSHAKE = 2;

    private static final int IPC_VERSION_NUMBER = 38;

    private static final int EOF = -1;
    private static final int	L_PROGRESS = 0;
    private static final int	L_INTEGER  = 1;
    private static final int	L_BOOLEAN  = 2;
    private static final int	L_LONG     = 3;
    private static final int	L_STRING   = 4;
    private static final int	L_DOUBLE   = 5;
    private static final int	L_ARRAY    = 6;
    private static final int	L_OBJECT   = 7;
    private static final int	L_CHAR     = 8;

    private static int readByte() throws IOException {
        int val = 0;
        for (int i = 0; i < 2; ++i) {
            int c = System.in.read();
            switch (c) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> val = val * 16 + c - '0';
                case 'a', 'b', 'c', 'd', 'e', 'f' -> val = val * 16 + c - 'a' + 10;
                case EOF -> val = EOF;
                default -> ipc_log("readByte: Unknown byte: %d%n", c);
            }
        }
        return val;
    }

    private static int readIVal() throws IOException {
        int val = readByte();
        val = val * 256 + readByte();
        val = val * 256 + readByte();
        val = val * 256 + readByte();
        return val;
    }

    enum IPCrequestStatus
    {
        INITIALIZED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED_DEFAULT,
        CANCELLED_IMMEDIATE
    };

    static class IPCrequest {
        int size;
        int requestID;
        int channelID;
        IPCrequestStatus status;
        int idx;
        boolean cancelImmediate;
        byte[] buf;

        IPCrequest (int size, int requestID, int channelID) {
            this.size = size;
            this.requestID = requestID;
            this.channelID = channelID;
            status = IPCrequestStatus.INITIALIZED;
            idx = 0;
            buf = new byte[size];
            cancelImmediate = false;
        }

        void read() throws IOException {
            System.in.read(buf);
        }

        int readByte() throws IOException {
            int val = 0;
            for (int i = 0; i < 2; ++i) {
                int c = buf[idx++];
                switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> val = val * 16 + c - '0';
                    case 'a', 'b', 'c', 'd', 'e', 'f' -> val = val * 16 + c - 'a' + 10;
                    case EOF -> val = EOF;
                    default -> ipc_log("readByte: Unknown byte: %d%n", c);
                }
            }
            return val;
        }

        boolean readBVal() throws IOException {
            int val = readByte();
            return val != 0;
        }

        int readIVal() throws IOException {
            int val = readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            return val;
        }

        long readLVal() throws IOException {
            long val = readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            val = val * 256 + readByte();
            return val;
        }

        String readSVal() throws IOException {
            int len = readIVal();
            if (len == -1) {
                return null;
            }
            byte[] bytes = new byte[len];
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = buf[idx++];
            }
            return new String(bytes);
        }

        double readDVal() throws IOException {
            String s = readSVal();
            return Double.parseDouble(s);
        }

        char readCVal() throws IOException {
            int val = readByte();
            return (char)val;
        }

        Object readAVal() throws IOException {
            boolean twoD = false;
            int type = readByte();
            if (type == L_ARRAY) {
                twoD = true;
                type = readByte();
            }

            int len = readIVal();
            if (len == -1) {
                return null;
            }
            switch (type) {
                case L_INTEGER:
                    if (twoD) {
                        int[][] array = new int[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (int[])readAVal();
                        }
                        return array;
                    }
                    else {
                        int[] array = new int[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readIVal();
                        }
                        return array;
                    }
                case L_LONG:
                    if (twoD) {
                        long[][] array = new long[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (long[])readAVal();
                        }
                        return array;
                    }
                    else {
                        long[] array = new long[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readLVal();
                        }
                        return array;
                    }
                case L_DOUBLE:
                    if (twoD) {
                        double[][] array = new double[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (double[])readAVal();
                        }
                        return array;
                    }
                    else {
                        double[] array = new double[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readDVal();
                        }
                        return array;
                    }
                case L_BOOLEAN:
                    if (twoD) {
                        boolean[][] array = new boolean[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (boolean[])readAVal();
                        }
                        return array;
                    }
                    else {
                        boolean[] array = new boolean[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readBVal();
                        }
                        return array;
                    }
                case L_CHAR:
                    if (twoD) {
                        char[][] array = new char[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (char[])readAVal();
                        }
                        return array;
                    }
                    else {
                        char[] array = new char[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readCVal();
                        }
                        return array;
                    }
                case L_STRING:
                    if (twoD) {
                        String[][] array = new String[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (String[])readAVal();
                        }
                        return array;
                    }
                    else {
                        String[] array = new String[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readSVal();
                        }
                        return array;
                    }
                case L_OBJECT:
                    if (twoD) {
                        Object[][] array = new Object[len][];
                        for (int i = 0; i < len; ++i) {
                            array[i] = (Object[])readAVal();
                        }
                        return array;
                    }
                    else {
                        Object[] array = new Object[len];
                        for (int i = 0; i < len; ++i) {
                            array[i] = readAVal();
                        }
                        return array;
                    }
                default:
                    ipc_log("readAVal: Unknown code: %d%n", type);
                    break;
            }
            return null;
        }

        public boolean readBoolean() throws IOException {
            int type = readByte();
            if (type != L_BOOLEAN) {
                throw new IOException(String.format("readString: parameter mismatch: type=%d should be L_BOOLEAN%n", type));
            }
            return readBVal();
        }

        public int readInt() throws IOException {
            int type = readByte();
            if (type != L_INTEGER) {
                throw new IOException(String.format("readString: parameter mismatch: type=%d should be L_INTEGER%n", type));
            }
            return readIVal();
        }

        public String readString() throws IOException {
            int type = readByte();
            if (type != L_STRING) {
                throw new IOException(String.format("readString: parameter mismatch: type=%d should be L_STRING%n", type));
            }
            return readSVal();
        }

        public long readLong() throws IOException {
            int type = readByte();
            if (type != L_LONG) {
                throw new IOException(String.format("readString: parameter mismatch: type=%d should be L_LONG%n", type));
            }
            return readLVal();
        }

        public Object readArray() throws IOException {
            int type = readByte();
            if (type != L_ARRAY) {
                throw new IOException(String.format("readString: parameter mismatch: type=%d should be L_ARRAY%n", type));
            }
            return readAVal();
        }

        public void writeBoolean (boolean b) {
            if (status == IPCrequestStatus.CANCELLED_IMMEDIATE) {
                return;
            }
            IPCresponse OUTS = new IPCresponse();
            OUTS.sendByte(L_BOOLEAN);
            OUTS.sendByte(b ? 1 : 0);
            writeResponseWithHeader(requestID, channelID,
                    RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }

        public void writeInt(int i) {
            if (status == IPCrequestStatus.CANCELLED_IMMEDIATE) {
                return;
            }
            IPCresponse OUTS = new IPCresponse();
            OUTS.sendByte(L_INTEGER);
            OUTS.sendIVal(i);
            writeResponseWithHeader(requestID, channelID,
                    RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }

        public void writeLong(long l) {
            if (status == IPCrequestStatus.CANCELLED_IMMEDIATE) {
                return;
            }
            IPCresponse OUTS = new IPCresponse();
            OUTS.sendByte(L_LONG);
            OUTS.sendLVal(l);
            writeResponseWithHeader(requestID, channelID,
                    RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }

        public void writeString(String s) {
            if (status == IPCrequestStatus.CANCELLED_IMMEDIATE) {
                return;
            }
            IPCresponse OUTS = new IPCresponse();
            OUTS.sendByte(L_STRING);
            OUTS.sendSVal(s);
            writeResponseWithHeader(requestID, channelID,
                    RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }

        void writeArray(Object ptr) {
            if (status == IPCrequestStatus.CANCELLED_IMMEDIATE) {
                return;
            }
            IPCresponse OUTS = new IPCresponse();
            OUTS.sendByte(L_ARRAY);
            OUTS.sendAVal(ptr);
            writeResponseWithHeader (requestID, channelID,
                    RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }

        void writeResponseGeneric() {
            IPCresponse OUTS = new IPCresponse();
            writeResponseWithHeader(requestID, channelID, RESPONSE_TYPE_COMPLETE, RESPONSE_STATUS_SUCCESS, OUTS);
        }
    }

    public static IPCrequest readRequestHeader() throws IOException {
        int marker = readByte();
        if (marker != HEADER_MARKER) {
            ipc_log("Internal error: received request (%d) without header marker\n", marker);
            return null;
        }
        int requestID = readIVal();
        int requestType = readByte();
        int channelID = readIVal();
        int nBytes = readIVal();
        if (requestType == REQUEST_TYPE_HANDSHAKE) {
            writeAck(requestID, channelID);
            writeHandshake(requestID, channelID);
            return readRequestHeader();
        } else if (requestType == REQUEST_TYPE_CANCEL) {
            writeAck (requestID, channelID);
//            if (channelID == cancellableChannelID)
//            {
//                // we have worked on at least one request belonging to this channel
//                writeResponseGeneric (RESPONSE_STATUS_SUCCESS, requestID, channelID);
//                setCancelRequestedCh (channelID);
//                ipc_trace ("CANCELLABLE %x %x\n", channelID, currentChannelID);
//                if (channelID == currentChannelID)
//                    //  request for this channel is currently in progress
//                    ipc_request_trace (TRACE_LVL_1, "IN PROGRESS REQUEST NEEDS CANCELLATION");
//                //              ssp_post_cond(waitingToFinish);
//            }
//            else
//            {
//                // FIXME:
//                // it is possible that a request for this channel is on the requestQ
//                // or has been submitted to the work group queue but is waiting for a thread to pick it up
//                writeResponseGeneric (RESPONSE_STATUS_FAILURE, requestID, channelID);
//                setCancelRequestedCh (channelID);
//                ipc_request_trace (TRACE_LVL_1, "RETURNING FAILURE TO CANCEL REQUEST channel %d\n", channelID);
//            }
            ipc_log("Internal error: REQUEST_TYPE_CANCEL%n");
            return null;
        } else {
            writeAck(requestID, channelID);
            IPCrequest nreq = new IPCrequest (nBytes, requestID, channelID);
            nreq.read();
//            if (cancelNeeded (channelID))
//            {
//                ipc_request_trace (TRACE_LVL_1, "CANCELLABLE REQ RECVD %x %x\n", channelID, requestID);
//                writeResponseGeneric (RESPONSE_STATUS_CANCELLED, requestID, channelID);
//                delete nreq;
//                return;
//            }
//            DbeQueue *q = new DbeQueue (ipc_doWork, nreq);
//            ipcThreadPool->put_queue (q);
            return nreq;
        }
    }

    static void writeAck(int requestID, int channelID) {
        IPCresponse OUTS = new IPCresponse(); //responseBufferPool->getNewResponse (BUFFER_SIZE_SMALL);
        writeResponseWithHeader(requestID, channelID, RESPONSE_TYPE_ACK,
                    RESPONSE_STATUS_SUCCESS, OUTS);
    }

    static void writeHandshake(int requestID, int channelID) {
        IPCresponse OUTS = new IPCresponse(); //responseBufferPool->getNewResponse (BUFFER_SIZE_SMALL);
        writeResponseWithHeader(requestID, channelID, RESPONSE_TYPE_HANDSHAKE, RESPONSE_STATUS_SUCCESS, OUTS);
        // writeResponseHeader(requestID, RESPONSE_TYPE_HANDSHAKE, RESPONSE_STATUS_SUCCESS, IPC_VERSION_NUMBER);
    }

    static class IPCresponse {
        int requestID;
        int channelID;
        int responseType;
        int responseStatus;
        StringBuilder sb;
        IPCresponse next;

        IPCresponse() {
            requestID = -1;
            channelID = -1;
            responseType = -1;
            responseStatus = RESPONSE_STATUS_SUCCESS;
            sb = new StringBuilder();
            next = null;
        }

        void reset() {
            requestID = -1;
            channelID = -1;
            responseType = -1;
            responseStatus = RESPONSE_STATUS_SUCCESS;
            sb.setLength(0);
        }

        void setRequestID(int r) {
            requestID = r;
        }

        void setChannelID(int c) {
            channelID = c;
        }

        void setResponseType(int r) {
            responseType = r;
        }
        void setResponseStatus(int s) {
            responseStatus = s;
        }

        void print() {
            String payload = sb.toString();
            writeResponseHeader(requestID, responseType, responseStatus, payload.length());
            System.out.print(payload);
        }

        void sendByte(int b) {
            sb.append(String.format("%02x", b));
        }

        void sendBVal(boolean b) {
            sendByte(b ? 1 : 0);
        }

        void sendCVal(char c) {
            sendByte(c);
        }

        void sendIVal(int i) {
            sb.append(String.format("%08x", i));
        }

        void sendLVal(long l) {
            sb.append(String.format("%016x", l));
        }

        void sendDVal(double d) {
            sb.append(String.format("%f", d));
        }
        void sendSVal(String s) {
            if (s == null) {
                sendIVal(-1);
                return;
            }
            sendIVal(s.length());
            sb.append(s);
        }

        void sendAVal(Object ptr) {
            if (ptr == null) {
                sendByte(L_INTEGER);
                sendIVal(-1);
                return;
            }

            if (ptr instanceof int[] ints) {
                sendByte(L_INTEGER);
                sendIVal(ints.length);
                for (int i : ints) {
                    sendIVal(i);
                }
            } else if (ptr instanceof boolean[] bools) {
                sendByte(L_BOOLEAN);
                sendIVal(bools.length);
                for (boolean b : bools) {
                    sendBVal(b);
                }
            } else if (ptr instanceof char[] chars) {
                sendByte(L_CHAR);
                sendIVal(chars.length);
                for (char ch : chars) {
                    sendCVal(ch);
                }
            } else if (ptr instanceof long[] longs) {
                sendByte(L_LONG);
                sendIVal(longs.length);
                for (long l : longs) {
                    sendLVal(l);
                }
            } else if (ptr instanceof double[] doubles) {
                sendByte(L_DOUBLE);
                sendIVal(doubles.length);
                for (double d : doubles) {
                    sendDVal(d);
                }
            } else if (ptr instanceof String[] strings) {
                sendByte(L_STRING);
                sendIVal(strings.length);
                for (String s : strings) {
                    sendSVal(s);
                }
            } else if (ptr instanceof String[][] array) {
                sendByte(L_ARRAY);
                sendByte(L_STRING);
                sendIVal(array.length);
                for (String[] a : array) {
                    sendAVal(a);
                }
            } else if (ptr instanceof int[][] array) {
                sendByte(L_ARRAY);
                sendByte(L_INTEGER);
                sendIVal(array.length);
                for (int[] a : array) {
                    sendAVal(a);
                }
            } else if (ptr instanceof long[][] array) {
                sendByte(L_ARRAY);
                sendByte(L_LONG);
                sendIVal(array.length);
                for (long[] a : array) {
                    sendAVal(a);
                }
            } else if (ptr instanceof Object[] array) {
                sendByte(L_OBJECT);
                sendIVal(array.length);
                for (Object o : array) {
                    sendAVal(o);
                }
            } else {
                throw new RuntimeException("sendAVal: Unknown type: " + ptr);
            }
        }
    }

    public static void writePlainString(String s) {
        System.out.printf("%02x%08x%s", L_STRING, s.length(), s);
    }

    static void writeResponseHeader(int requestID, int responseType, int responseStatus, int nBytes) {
        if (responseType == RESPONSE_TYPE_HANDSHAKE) {
            nBytes = IPC_VERSION_NUMBER;
        }
        System.out.printf("%02x%08x%02x%02x%08x", HEADER_MARKER, requestID,
                responseType, responseStatus, nBytes);
    }

    static void writeResponseWithHeader (int requestID, int channelID, int responseType,
                             int responseStatus, IPCresponse os) {
//        if (cancelNeeded (channelID))
//        {
//            responseStatus = RESPONSE_STATUS_CANCELLED;
//            ipc_trace ("CANCELLING %d %d\n", requestID, channelID);
//            // This is for gracefully cancelling regular ops like openExperiment - getFiles should never reach here
//        }
        os.setRequestID (requestID);
        os.setChannelID (channelID);
        os.setResponseType (responseType);
        os.setResponseStatus (responseStatus);
        os.print();
        os.reset();
//        responseBufferPool.recycle(os);
    }

    static int currentRequestID;
    static int currentChannelID;

    public static void setProgress(int percentage, String str) {
        IPCresponse OUTS = new IPCresponse();
        OUTS.sendByte(L_PROGRESS);
        OUTS.sendIVal(percentage);
        OUTS.sendSVal(str);
        writeResponseWithHeader(currentRequestID, currentChannelID, RESPONSE_TYPE_PROGRESS, RESPONSE_STATUS_SUCCESS, OUTS);
    }
}
