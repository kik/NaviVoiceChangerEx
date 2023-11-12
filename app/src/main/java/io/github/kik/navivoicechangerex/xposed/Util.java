package io.github.kik.navivoicechangerex.xposed;

import android.annotation.SuppressLint;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    // 型情報のわからないprotobufのメッセージを文字列にする
    private static String dumpProto(byte[] obj, List<Integer> path)
    {
        Locale loc = Locale.ROOT;
        try {
            final String indent = "  ".repeat(path.size());
            var reader = CodedInputStream.newInstance(obj);
            var result = new StringWriter();
            var os = new PrintWriter(result);
            os.println(indent);
            while (!reader.isAtEnd()) {
                int tag = reader.readTag();
                int field = WireFormat.getTagFieldNumber(tag);
                int type = WireFormat.getTagWireType(tag);
                switch (type) {
                    case WireFormat.WIRETYPE_VARINT: {
                        os.format(loc, "%s%d: %s = %s\n", indent, field, "VARIANT", reader.readRawVarint64());
                        break;
                    }
                    case WireFormat.WIRETYPE_FIXED64: {
                        os.format(loc, "%s%d: %s = %s\n", indent, field, "INT64", reader.readFixed64());
                        break;
                    }
                    case WireFormat.WIRETYPE_FIXED32: {
                        os.format(loc, "%s%d: %s = %s\n", indent, field, "INT32", reader.readFixed32());
                        break;
                    }
                    case WireFormat.WIRETYPE_LENGTH_DELIMITED: {
                        var bytes = reader.readByteArray();
                        // 文字列っぽいなら文字列で
                        var decoder = StandardCharsets.UTF_8.newDecoder();
                        decoder.reset();
                        decoder.onMalformedInput(CodingErrorAction.REPORT);
                        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                        try {
                            var str = decoder.decode(ByteBuffer.wrap(bytes)).toString();
                            if (str.chars().noneMatch(Character::isISOControl)) {
                                os.format(loc, "%s%d: %s = \"%s\"\n", indent, field, "String", str);
                                break;
                            }
                        } catch (CharacterCodingException ignore) {
                        }
                        // 再帰的にオブジェクトとして読めればそれで
                        var s = dumpProto(bytes, new ImmutableList.Builder<Integer>()
                                .addAll(path).add(field).build());
                        if (s != null) {
                            os.format(loc, "%s%d: %s =\n%s", indent, field, "Object", s);
                            break;
                        }
                        // だめだったらバイト列
                        os.format(loc, "%s%d: %s = ", indent, field, "bytes");
                        os.print("[ ");
                        for (var b : bytes) {
                            os.format(loc, "%02X ", b);
                        }
                        os.println("]");
                        break;
                    }
                    default:
                        return null;
                }
            }
            os.println("}");
            return result.toString();
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
