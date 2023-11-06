package io.github.kik.navivoicechangerex;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CannedMessageParser {

    public static void convert(OutputStream out, InputStream in) throws IOException {
        try (var zipIn = new ZipInputStream(in)) {
            try (var zipOut = new ZipOutputStream(out)) {
                while (true) {
                    var entryIn = zipIn.getNextEntry();
                    if (entryIn == null) break;
                    if (entryIn.getName().equals("messages.xml")) {
                        List<Entry> list = parse(zipIn);
                        zipOut.putNextEntry(new ZipEntry("messages.xml"));
                        writeMessageXml(zipOut, list);
                        zipOut.putNextEntry(new ZipEntry("messages.plist"));
                        writeMessagePlist(zipOut, list);
                    } else if (entryIn.getName().equals("messages.plist")) {
                    } else {
                        zipOut.putNextEntry(new ZipEntry(entryIn.getName()));
                        byte[] buffer = new byte[4096];
                        while (true) {
                            int len = zipIn.read(buffer);
                            if (len < 0) {
                                break;
                            }
                            zipOut.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private static void writeMessageXml(ZipOutputStream zipOut, List<Entry> list) throws IOException {
        var xs = Xml.newSerializer();
        xs.setOutput(new OutputStreamWriter(zipOut, StandardCharsets.UTF_8));
        xs.startTag(null, "voice_instructions");
        for (var e : list) {
            xs.startTag(null, "canned_message");
            xs.attribute(null, "id", Integer.toString(e.id()));
            xs.text(e.file());
            xs.endTag(null, "canned_message");
        }
        xs.endTag(null, "voice_instructions");
        xs.endDocument();
    }

    private static void writeMessagePlist(ZipOutputStream zipOut, List<Entry> list) throws IOException {
        var xs = Xml.newSerializer();
        xs.setOutput(new OutputStreamWriter(zipOut, StandardCharsets.UTF_8));
        xs.startDocument("UTF-8", null);
        xs.docdecl(" plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"");
        xs.startTag(null, "plist");
        xs.attribute(null, "version", "1.0");
        xs.startTag(null, "array");
        for (var e : list) {
            xs.startTag(null, "dict");

            xs.startTag(null, "key");
            xs.text("filename");
            xs.endTag(null, "key");
            xs.startTag(null, "string");
            xs.text(e.file());
            xs.endTag(null, "string");

            xs.startTag(null, "key");
            xs.text("id");
            xs.endTag(null, "key");
            xs.startTag(null, "string");
            xs.text(Integer.toString(e.id()));
            xs.endTag(null, "string");

            xs.endTag(null, "dict");
        }
        xs.endTag(null, "array");
        xs.endTag(null, "plist");
        xs.endDocument();
    }

    private interface Entry
    {
        public int id();
        public String file();
    }

    // 最近のCannedMessageBundleに入ってる<canned_message>エントリー
    private static class CannedMessage implements Entry
    {
        private final int id;
        private final String file;
        public CannedMessage(int id, String file) {
            this.id = id;
            this.file = file;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String file() {
            return file;
        }
    }

    // 古いCannedMessageBundleに入ってる<predefined_message>エントリー
    private static class PredefinedMessage implements Entry {
        private final String id;
        private final String file;

        public PredefinedMessage(String id, String file) {
            this.id = id;
            this.file = file;
        }

        @Override
        public int id() {
            switch (id) {
                case "THEN": return 83;
                case "NAVIGATION_RESUMED": return 60;
                case "DATA_LOST": return 4;
                case "PLEASE_DESCRIBE_PROBLEM": return 61;
                case "ARRIVED": return 1;
                case "DESTINATION_WILL_BE_ON_RIGHT": return 8;
                case "DESTINATION_WILL_BE_ON_LEFT": return 7;
                case "GPS_LOST": return 12;
                case "DESTINATION_ON_RIGHT": return 6;
                case "DESTINATION_ON_LEFT": return 5;
                case "GENERIC_CONTINUE": return 11;
                case "WILL_ARRIVE": return 88;
            }
            return -1;
        }

        @Override
        public String file() {
            return file;
        }
    }

    // 古いCannedMessageBundleに入ってる<distance_message>エントリー
    private static class DistanceMessage implements Entry {
        private final int min;
        private final int max;
        private final String file;

        public DistanceMessage(int min, int max, String file) {
            this.min = min;
            this.max = max;
            this.file = file;
        }

        @Override
        public int id() {
            if (min <= 1000 && 1000 < max) {
                return 30;
            } else if (min <= 3000 && 3000< max) {
                return 40;
            } else if (min <= 500 && 500 < max) {
                return 46;
            } else if (min <= 150 && 150 < max) {
                return 26;
            } else if (min <= 100 && 100 < max) {
                return 23;
            } else if (min <= 600 && 600 < max) {
                return 51;
            } else if (min <= 800 && 800 < max) {
                return 53;
            } else if (min <= 300 && 300 < max) {
                return 38;
            } else if (min <= 200 && 200 < max) {
                return 33;
            } else if (min <= 1500 && 1500 < max) {
                return 28;
            } else if (min <= 2000 && 2000 < max) {
                return 35;
            } else if (min <= 50 && 50 < max) {
                return 48;
            } else if (min <= 400 && 400 < max) {
                return 43;
            }
            return -1;
        }

        @Override
        public String file() {
            return file;
        }
    }

    // 古いCannedMessageBundleに入ってる<maneuver_message>エントリー
    private static class ManeuverMessage implements Entry {
        private static final int[] table = {
                -1,
                // HEAD_[NEWS]
                14, 15, 13, 18, 17, 19, 20, 16,
                // TURN
                70, 72, 84, 86, 68, 69, 71, 73, 85, 87,
                81, 77, 78, 76, 57, 58, 2,
                // ROUNDABOUT
                9, 62, 64, 65, 66, 67, 63, 10,
                //
                3, 79, 82, 59, 75, 80, 74, -1,
        };

        private final int id;
        private final String file;

        public ManeuverMessage(int id, String file) {
            this.id = id;
            this.file = file;
        }

        @Override
        public int id() {
            if (id < table.length) {
                return table[id];
            }
            return -1;
        }

        @Override
        public String file() {
            return file;
        }
    }

    private static List<Entry> parse(InputStream in) throws IOException {
        try {
            var factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            var xpp = factory.newPullParser();
            xpp.setInput(new InputStreamReader(in, StandardCharsets.UTF_8));

            var list = new ArrayList<Entry>();
            while (true) {
                int entryType = xpp.getEventType();
                if (entryType == XmlPullParser.END_DOCUMENT) {
                    break;
                } else if (entryType == XmlPullParser.START_TAG) {
                    String name = xpp.getName();
                    switch (name) {
                        case "canned_message": {
                            String id = xpp.getAttributeValue(null, "id");
                            String text = xpp.nextText();
                            list.add(new CannedMessage(Integer.parseInt(id), text));
                            break;
                        }
                        case "predefined_message": {
                            String id = xpp.getAttributeValue(null, "type");
                            String text = xpp.nextText();
                            list.add(new PredefinedMessage(id, text));
                            break;
                        }
                        case "distance_message": {
                            int min = Integer.parseInt(xpp.getAttributeValue(null, "min"));
                            int max = Integer.parseInt(xpp.getAttributeValue(null, "max"));
                            String text = xpp.nextText();
                            list.add(new DistanceMessage(min, max, text));
                            break;
                        }
                        case "maneuver_message": {
                            int id = Integer.parseInt(xpp.getAttributeValue(null, "id"));
                            String text = xpp.nextText();
                            list.add(new ManeuverMessage(id, text));
                            break;
                        }
                    }
                }
            }
            return list;
        } catch (XmlPullParserException e) {
            throw new IOException("XmlPullParserException", e);
        }
    }
}
