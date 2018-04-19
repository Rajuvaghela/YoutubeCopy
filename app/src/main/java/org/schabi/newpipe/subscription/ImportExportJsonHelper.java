/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ImportExportJsonHelper.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.subscription;

import android.support.annotation.Nullable;

import com.grack.nanojson.JsonAppendableWriter;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonSink;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.InvalidSourceException;
import org.schabi.newpipe.extractor.subscription.SubscriptionItem;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A JSON implementation capable of importing and exporting subscriptions, it has the advantage
 * of being able to transfer subscriptions to any device.
 */
public class ImportExportJsonHelper {

    /*//////////////////////////////////////////////////////////////////////////
    // Json implementation
    //////////////////////////////////////////////////////////////////////////*/

    private static final String JSON_APP_VERSION_KEY = "app_version";
    private static final String JSON_APP_VERSION_INT_KEY = "app_version_int";

    private static final String JSON_SUBSCRIPTIONS_ARRAY_KEY = "subscriptions";

    private static final String JSON_SERVICE_ID_KEY = "service_id";
    private static final String JSON_URL_KEY = "url";
    private static final String JSON_NAME_KEY = "name";

    /**
     * Read a JSON source through the input stream and return the parsed subscription items.
     *
     * @param in            the input stream (e.g. a file)
     * @param eventListener listener for the events generated
     */
    public static List<SubscriptionItem> readFrom(InputStream in, @Nullable ImportExportEventListener eventListener) throws InvalidSourceException {
        if (in == null) throw new InvalidSourceException("input is null");

        final List<SubscriptionItem> channels = new ArrayList<>();

        try {
            JsonObject parentObject = JsonParser.object().from(in);
            JsonArray channelsArray = parentObject.getArray(JSON_SUBSCRIPTIONS_ARRAY_KEY);
            if (eventListener != null) eventListener.onSizeReceived(channelsArray.size());

            if (channelsArray == null) {
                throw new InvalidSourceException("Channels array is null");
            }

            for (Object o : channelsArray) {
                if (o instanceof JsonObject) {
                    JsonObject itemObject = (JsonObject) o;
                    int serviceId = itemObject.getInt(JSON_SERVICE_ID_KEY, 0);
                    String url = itemObject.getString(JSON_URL_KEY);
                    String name = itemObject.getString(JSON_NAME_KEY);

                    if (url != null && name != null && !url.isEmpty() && !name.isEmpty()) {
                        channels.add(new SubscriptionItem(serviceId, url, name));
                        if (eventListener != null) eventListener.onItemCompleted(name);
                    }
                }
            }
        } catch (Throwable e) {
            throw new InvalidSourceException("Couldn't parse json", e);
        }

        return channels;
    }

    /**
     * Write the subscriptions items list as JSON to the output.
     *
     * @param items         the list of subscriptions items
     * @param out           the output stream (e.g. a file)
     * @param eventListener listener for the events generated
     */
    public static void writeTo(List<SubscriptionItem> items, OutputStream out, @Nullable ImportExportEventListener eventListener) {
        JsonAppendableWriter writer = JsonWriter.on(out);
        writeTo(items, writer, eventListener);
        writer.done();
    }

    /**
     * @see #writeTo(List, OutputStream, ImportExportEventListener)
     */
    public static void writeTo(List<SubscriptionItem> items, JsonSink writer, @Nullable ImportExportEventListener eventListener) {
        if (eventListener != null) eventListener.onSizeReceived(items.size());

        writer.object();

        writer.value(JSON_APP_VERSION_KEY, BuildConfig.VERSION_NAME);
        writer.value(JSON_APP_VERSION_INT_KEY, BuildConfig.VERSION_CODE);

        writer.array(JSON_SUBSCRIPTIONS_ARRAY_KEY);
        for (SubscriptionItem item : items) {
            writer.object();
            writer.value(JSON_SERVICE_ID_KEY, item.getServiceId());
            writer.value(JSON_URL_KEY, item.getUrl());
            writer.value(JSON_NAME_KEY, item.getName());
            writer.end();

            if (eventListener != null) eventListener.onItemCompleted(item.getName());
        }
        writer.end();

        writer.end();
    }

}
