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

import java.util.Arrays;

public class LargeArrays {

    private static final int CHUNK_SIZE = 1 << 26;
    private static final int MAX_CHUNKS = 4096;

    static int chunkIdx(long idx) {
        return (int) (idx / CHUNK_SIZE);
    }

    static int chunkOff(long idx) {
        return (int) (idx % CHUNK_SIZE);
    }

    public static class LargeIntArray {
        private final int[][] chunks;
        private long size;

        public LargeIntArray() {
            chunks = new int[MAX_CHUNKS][];
        }

        public long size() {
            return size;
        }

        public void append(int val) {
            if (size % CHUNK_SIZE == 0) {
                chunks[chunkIdx(size)] = new int[CHUNK_SIZE];
            }
            chunks[chunkIdx(size)][chunkOff(size)] = val;
            size++;
        }

        public int fetch(long idx) {
            return chunks[chunkIdx(idx)][chunkOff(idx)];
        }

        public void store(long idx, int val) {
            if (idx < 0) {
                throw new IndexOutOfBoundsException(idx);
            }
            if (idx >= size) {
                for (int i = chunkIdx(idx); i >= 0 && chunks[i] == null; i--) {
                    chunks[i] = new int[CHUNK_SIZE];
                }
                size = idx + 1;
            }
            chunks[chunkIdx(idx)][chunkOff(idx)] = val;
        }

        public void reset() {
            size = 0;
            Arrays.fill(chunks, null);
        }
    }

    public static class LargeLongArray {
        private final long[][] chunks;
        private long size;

        public LargeLongArray() {
            chunks = new long[MAX_CHUNKS][];
        }

        public long size() {
            return size;
        }

        public void append(long val) {
            if (size % CHUNK_SIZE == 0) {
                chunks[chunkIdx(size)] = new long[CHUNK_SIZE];
            }
            chunks[chunkIdx(size)][chunkOff(size)] = val;
            size++;
        }

        public long fetch(long idx) {
            return chunks[chunkIdx(idx)][chunkOff(idx)];
        }

        public void store(long idx, long val) {
            if (idx < 0) {
                throw new IndexOutOfBoundsException(idx);
            }
            if (idx >= size) {
                for (int i = chunkIdx(idx); i >= 0 && chunks[i] == null; i--) {
                    chunks[i] = new long[CHUNK_SIZE];
                }
                size = idx + 1;
            }
            chunks[chunkIdx(idx)][chunkOff(idx)] = val;
        }

        public void reset() {
            size = 0;
            Arrays.fill(chunks, null);
        }
    }

    public static class LargeDoubleArray {
        private final double[][] chunks;
        private long size;

        public LargeDoubleArray() {
            chunks = new double[MAX_CHUNKS][];
        }

        public long size() {
            return size;
        }

        public void append(double val) {
            if (size % CHUNK_SIZE == 0) {
                chunks[chunkIdx(size)] = new double[CHUNK_SIZE];
            }
            chunks[chunkIdx(size)][chunkOff(size)] = val;
            size++;
        }

        public double fetch(long idx) {
            return chunks[chunkIdx(idx)][chunkOff(idx)];
        }

        public void store(long idx, double val) {
            if (idx < 0) {
                throw new IndexOutOfBoundsException(idx);
            }
            if (idx >= size) {
                for (int i = chunkIdx(idx); i >= 0 && chunks[i] == null; i--) {
                    chunks[i] = new double[CHUNK_SIZE];
                }
                size = idx + 1;
            }
            chunks[chunkIdx(idx)][chunkOff(idx)] = val;
        }

        public void reset() {
            size = 0;
            Arrays.fill(chunks, null);
        }
    }

    public static class LargeObjectArray {
        private final Object[][] chunks;
        private long size;

        public LargeObjectArray() {
            chunks = new Object[MAX_CHUNKS][];
        }

        public long size() {
            return size;
        }

        public void append(Object val) {
            if (size % CHUNK_SIZE == 0) {
                chunks[chunkIdx(size)] = new Object[CHUNK_SIZE];
            }
            chunks[chunkIdx(size)][chunkOff(size)] = val;
            size++;
        }

        public Object fetch(long idx) {
            return chunks[chunkIdx(idx)][chunkOff(idx)];
        }

        public void store(long idx, Object val) {
            if (idx < 0) {
                throw new IndexOutOfBoundsException(idx);
            }
            if (idx >= size) {
                for (int i = chunkIdx(idx); i >= 0 && chunks[i] == null; i--) {
                    chunks[i] = new Object[CHUNK_SIZE];
                }
                size = idx + 1;
            }
            chunks[chunkIdx(idx)][chunkOff(idx)] = val;
        }

        public void reset() {
            size = 0;
            Arrays.fill(chunks, null);
        }
    }

}
