package com.example.sucharu;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

/** Reads the existing service-coverage copy from an app-owned XML resource. */
public final class ServiceCoverageParser {
    private ServiceCoverageParser() {}

    public static List<String> parse(Context context) {
        List<String> areas = new ArrayList<>();
        android.content.res.XmlResourceParser parser = context.getResources().getXml(R.xml.service_coverage);
        try {
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "area".equals(parser.getName())) {
                    String name = parser.getAttributeValue(null, "name");
                    if (name != null && !name.trim().isEmpty()) areas.add(name.trim());
                }
                event = parser.next();
            }
        } catch (Exception ignored) {
            areas.clear();
        } finally {
            parser.close();
        }
        return areas;
    }
}
