package me.lyh.tfpq;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

/**
 * Codec for TFRecords file format. See
 * https://www.tensorflow.org/api_guides/python/python_io#TFRecords_Format_Details
 */
public class TFRecordCodec {
  private static final int HEADER_LEN = (Long.SIZE + Integer.SIZE) / Byte.SIZE;
  private static final int FOOTER_LEN = Integer.SIZE / Byte.SIZE;
  private static HashFunction crc32c = Hashing.crc32c();

  private ByteBuffer header = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.LITTLE_ENDIAN);
  private ByteBuffer footer = ByteBuffer.allocate(FOOTER_LEN).order(ByteOrder.LITTLE_ENDIAN);

  private int mask(int crc) {
    return ((crc >>> 15) | (crc << 17)) + 0xa282ead8;
  }

  private int hashLong(long x) {
    return mask(crc32c.hashLong(x).asInt());
  }

  private int hashBytes(byte[] x) {
    return mask(crc32c.hashBytes(x).asInt());
  }

  public int recordLength(byte[] data) {
    return HEADER_LEN + data.length + FOOTER_LEN;
  }

  @Nullable
  byte[] read(ReadableByteChannel inChannel) throws IOException {
    header.clear();
    int headerBytes = inChannel.read(header);
    if (headerBytes <= 0) {
      return null;
    }
    checkState(headerBytes == HEADER_LEN, "Not a valid TFRecord. Fewer than 12 bytes.");

    header.rewind();
    long length = header.getLong();
    long lengthHash = hashLong(length);
    int maskedCrc32OfLength = header.getInt();
    if (lengthHash != maskedCrc32OfLength) {
      throw new IOException(
              String.format(
                      "Mismatch of length mask when reading a record. Expected %d but received %d.",
                      maskedCrc32OfLength, lengthHash));
    }

    ByteBuffer data = ByteBuffer.allocate((int) length);
    while (data.hasRemaining() && inChannel.read(data) >= 0) {}
    if (data.hasRemaining()) {
      throw new IOException(
              String.format(
                      "EOF while reading record of length %d. Read only %d bytes. Input might be truncated.",
                      length, data.position()));
    }

    footer.clear();
    inChannel.read(footer);
    footer.rewind();

    int maskedCrc32OfData = footer.getInt();
    int dataHash = hashBytes(data.array());
    if (dataHash != maskedCrc32OfData) {
      throw new IOException(
              String.format(
                      "Mismatch of data mask when reading a record. Expected %d but received %d.",
                      maskedCrc32OfData, dataHash));
    }
    return data.array();
  }

  public void write(WritableByteChannel outChannel, byte[] data) throws IOException {
    int maskedCrc32OfLength = hashLong(data.length);
    int maskedCrc32OfData = hashBytes(data);

    header.clear();
    header.putLong(data.length).putInt(maskedCrc32OfLength);
    header.rewind();
    outChannel.write(header);

    outChannel.write(ByteBuffer.wrap(data));

    footer.clear();
    footer.putInt(maskedCrc32OfData);
    footer.rewind();
    outChannel.write(footer);
  }
}