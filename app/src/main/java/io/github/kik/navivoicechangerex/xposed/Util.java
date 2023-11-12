package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class Util {
    static String dumpProto(byte[] obj) {
        return dumpProto(obj, List.of());
    }

    private static String dumpProto(byte[] obj, List<Integer> path)
    {
        Locale loc = Locale.ROOT;
        try {
            final String indent = "  ".repeat(path.size());
            var reader = CodedInputStream.newInstance(obj);
            var buffer = new StringBuilder();
            buffer.append(indent).append("{\n");

            while (!reader.isAtEnd()) {
                int tag = reader.readTag();
                int field = WireFormat.getTagFieldNumber(tag);
                int type = WireFormat.getTagWireType(tag);
                switch (type) {
                    case WireFormat.WIRETYPE_VARINT: {
                        buffer.append(String.format(loc, "%s%d: %s = %s\n", indent, field, "VARIANT", reader.readRawVarint64()));
                        break;
                    }
                    case WireFormat.WIRETYPE_FIXED64: {
                        buffer.append(String.format(loc, "%s%d: %s = %s\n", indent, field, "INT64", reader.readFixed64()));
                        break;
                    }
                    case WireFormat.WIRETYPE_FIXED32: {
                        buffer.append(String.format(loc, "%s%d: %s = %s\n", indent, field, "INT32", reader.readFixed32()));
                        break;
                    }
                    case WireFormat.WIRETYPE_LENGTH_DELIMITED: {
                        var bytes = reader.readByteArray();
                        var decoder = StandardCharsets.UTF_8.newDecoder();
                        decoder.reset();
                        decoder.onMalformedInput(CodingErrorAction.REPORT);
                        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                        try {
                            var s = decoder.decode(ByteBuffer.wrap(bytes)).toString();
                            if (s.chars().noneMatch(Character::isISOControl)) {
                                buffer.append(String.format(loc, "%s%d: %s = \"%s\"\n", indent, field, "String", s));
                                break;
                            }
                        } catch (CharacterCodingException ignore) {
                        }
                        var s = dumpProto(bytes, new ImmutableList.Builder<Integer>()
                                .addAll(path).add(field).build());
                        if (s != null) {
                            buffer.append(String.format(loc, "%s%d: %s =\n", indent, field, "Object"));
                            buffer.append(s);
                            break;
                        }
                        buffer.append(String.format(loc, "%s%d: %s = ", indent, field, "bytes"));
                        buffer.append("[ ");
                        for (var b : bytes) {
                            buffer.append(String.format(loc, "%02X ", b));
                        }
                        buffer.append("]\n");
                        break;
                    }
                    default:
                        return null;
                }
            }
            buffer.append(indent).append("}\n");
            return buffer.toString();
        } catch (IOException ioe) {
            return null;
        }
    }

    static String hexdump(byte[] array) {
        Locale loc = Locale.ROOT;
        var buf = new StringBuilder();
        for (int i = 0; i < array.length; i += 16) {
            for (int j = 0; j < 16; j++) {
                if (i + j < array.length) {
                    buf.append(String.format(loc, "%02X ", array[i + j] & 0xFF));
                } else {
                    buf.append("   ");
                }
                if (j == 7) {
                    buf.append(' ');
                }
            }
            buf.append("     ");
            for (int j = 0; j < 16; j++) {
                if (i + j < array.length) {
                    int b = array[i + j] & 0xFF;
                    buf.append(0x20 <= b && b < 0x7F ? (char)b : '.');
                }
            }
            buf.append("\n");
        }
        return buf.toString();
    }
}
