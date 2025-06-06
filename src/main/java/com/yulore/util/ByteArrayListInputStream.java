/**
 *
 */
package com.yulore.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author isdom
 *
 */
public class ByteArrayListInputStream extends InputStream {

    /**
     * An array of bytes that was provided
     * by the creator of the stream. Elements <code>buf[0]</code>
     * through <code>buf[count-1]</code> are the
     * only bytes that can ever be read from the
     * stream;  element <code>buf[pos]</code> is
     * the next byte to be read.
     */
    protected final List<byte[]> bufs;

    /**
     * The index of the next character to read from the input stream buffer.
     * This value should always be nonnegative
     * and not larger than the value of <code>count</code>.
     * The next byte to be read from the input stream buffer 
     * will be <code>buf[pos]</code>.
     */

    //	目前在第几个buf
    protected int idxOfBuf = 0;

    //	当前的buf中的位置
    protected int posInBuf = 0;

    //	总位置
    protected int totalPos = 0;

    /**
     * The currently marked position in the stream.
     * ByteArrayInputStream objects are marked at position zero by
     * default when constructed.  They may be marked at another
     * position within the buffer by the <code>mark()</code> method.
     * The current buffer position is set to this point by the
     * <code>reset()</code> method.
     * <p>
     * If no mark has been set, then the value of mark is the offset
     * passed to the constructor (or 0 if the offset was not supplied).
     *
     * @since   JDK1.1
     */
    protected int markIdxOfBuf = 0;
    protected int markPosInBuf = 0;

    /**
     * The index one greater than the last valid character in the input 
     * stream buffer. 
     * This value should always be nonnegative
     * and not larger than the length of <code>buf</code>.
     * It  is one greater than the position of
     * the last byte within <code>buf</code> that
     * can ever be read  from the input stream buffer.
     */
    protected int count = 0;

    private byte[] currentBuf() {
        return bufs.get(idxOfBuf);
    }

    /**
     * Creates a <code>ByteArrayInputStream</code>
     * so that it  uses <code>buf</code> as its
     * buffer array. 
     * The buffer array is not copied. 
     * The initial value of <code>pos</code>
     * is <code>0</code> and the initial value
     * of  <code>count</code> is the length of
     * <code>buf</code>.
     *
     * @param   bufs   the input buffer.
     */
    public ByteArrayListInputStream(List<byte[]> bufs) {
        this.bufs = bufs;

        for ( byte[] buf : this.bufs ) {
            this.count += buf.length;
        }
    }

    /**
     * Reads the next byte of data from this input stream. The value 
     * byte is returned as an <code>int</code> in the range 
     * <code>0</code> to <code>255</code>. If no byte is available 
     * because the end of the stream has been reached, the value 
     * <code>-1</code> is returned. 
     * <p>
     * This <code>read</code> method 
     * cannot block. 
     *
     * @return  the next byte of data, or <code>-1</code> if the end of the
     *          stream has been reached.
     */
    public synchronized int read() {

        while ( this.idxOfBuf < bufs.size() ) {
            final byte[] buf = currentBuf();

            if ( this.posInBuf < buf.length ) {
                this.totalPos++;
                return	(buf[this.posInBuf++] & 0xff);
            }
            else {
                this.idxOfBuf++;
                this.posInBuf = 0;
            }
        }

        return -1;
    }

    /**
     * Reads up to <code>len</code> bytes of data into an array of bytes 
     * from this input stream. 
     * If <code>pos</code> equals <code>count</code>,
     * then <code>-1</code> is returned to indicate
     * end of file. Otherwise, the  number <code>k</code>
     * of bytes read is equal to the smaller of
     * <code>len</code> and <code>count-pos</code>.
     * If <code>k</code> is positive, then bytes
     * <code>buf[pos]</code> through <code>buf[pos+k-1]</code>
     * are copied into <code>b[off]</code>  through
     * <code>b[off+k-1]</code> in the manner performed
     * by <code>System.arraycopy</code>. The
     * value <code>k</code> is added into <code>pos</code>
     * and <code>k</code> is returned.
     * <p>
     * This <code>read</code> method cannot block. 
     *
     * @param   b     the buffer into which the data is read.
     * @param   off   the start offset in the destination array <code>b</code>
     * @param   len   the maximum number of bytes read.
     * @return  the total number of bytes read into the buffer, or
     *          <code>-1</code> if there is no more data because the end of
     *          the stream has been reached.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IndexOutOfBoundsException If <code>off</code> is negative, 
     * <code>len</code> is negative, or <code>len</code> is greater than 
     * <code>b.length - off</code>
     */
    public synchronized int read(final byte b[], int off, final int len) {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        int leftLen = len, readSize = leftLen;
        while (this.idxOfBuf < bufs.size()) {
            final byte[] buf = currentBuf();

            if (this.posInBuf < buf.length) {
                if (this.posInBuf + readSize > buf.length) {
                    readSize = buf.length - this.posInBuf;
                }

                System.arraycopy(buf, this.posInBuf, b, off, readSize);
                off += readSize;
                this.posInBuf += readSize;
                this.totalPos += readSize;
                leftLen -= readSize;
                readSize = leftLen;
                if (leftLen == 0) {
                    // buffer for read has been full-filled
                    return len;
                }
            }
            else {
                this.idxOfBuf++;
                this.posInBuf = 0;
            }
        }
        int readed = len - leftLen;

        return readed == 0 ? -1 : readed;
    }

    /**
     * Skips <code>n</code> bytes of input from this input stream. Fewer 
     * bytes might be skipped if the end of the input stream is reached. 
     * The actual number <code>k</code>
     * of bytes to be skipped is equal to the smaller
     * of <code>n</code> and  <code>count-pos</code>.
     * The value <code>k</code> is added into <code>pos</code>
     * and <code>k</code> is returned.
     *
     * @param   n   the number of bytes to be skipped.
     * @return  the actual number of bytes skipped.
     */
    public synchronized long skip(final long n) {
        long leftLen = n, skipSize = leftLen;
        while (this.idxOfBuf < bufs.size()) {
            final byte[] buf = currentBuf();

            if (this.posInBuf < buf.length) {
                if (this.posInBuf + skipSize > buf.length) {
                    skipSize = buf.length - this.posInBuf;
                }

                this.posInBuf += (int)skipSize;
                this.totalPos += (int)skipSize;
                leftLen -= skipSize;
                skipSize = leftLen;
                if (leftLen == 0) {
                    return n;
                }
            }
            else {
                this.idxOfBuf++;
                this.posInBuf = 0;
            }
        }
        return n - leftLen;
    }

    /**
     * Returns the number of remaining bytes that can be read (or skipped over)
     * from this input stream.
     * <p>
     * The value returned is <code>count&nbsp;- pos</code>, 
     * which is the number of bytes remaining to be read from the input buffer.
     *
     * @return  the number of remaining bytes that can be read (or skipped
     *          over) from this input stream without blocking.
     */
    public synchronized int available() {
        return count - this.totalPos;
    }

    /**
     * Tests if this <code>InputStream</code> supports mark/reset. The
     * <code>markSupported</code> method of <code>ByteArrayInputStream</code>
     * always returns <code>true</code>.
     *
     * @since   JDK1.1
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Set the current marked position in the stream.
     * ByteArrayInputStream objects are marked at position zero by
     * default when constructed.  They may be marked at another
     * position within the buffer by this method.
     * <p>
     * If no mark has been set, then the value of the mark is the
     * offset passed to the constructor (or 0 if the offset was not
     * supplied).
     *
     * <p> Note: The <code>readAheadLimit</code> for this class
     *  has no meaning.
     *
     * @since   JDK1.1
     */
    public void mark(int readAheadLimit) {
        this.markIdxOfBuf = this.idxOfBuf;
        this.markPosInBuf = this.posInBuf;
    }

    /**
     * Resets the buffer to the marked position.  The marked position
     * is 0 unless another position was marked or an offset was specified
     * in the constructor.
     */
    public synchronized void reset() {
        this.idxOfBuf = this.markIdxOfBuf;
        this.posInBuf = this.markPosInBuf;
    }

    /**
     * Closing a <tt>ByteArrayInputStream</tt> has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an <tt>IOException</tt>.
     * <p>
     */
    public void close() throws IOException {
    }
}