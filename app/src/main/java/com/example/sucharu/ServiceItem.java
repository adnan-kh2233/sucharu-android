package com.example.sucharu;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public class ServiceItem {
    public final String id, name, category, icon, description, pricingModel;
    public final Integer startingPrice;

    public ServiceItem(String id, String name, String category, String icon, String description, String pricingModel, Integer startingPrice) {
        this.id = id; this.name = name; this.category = category; this.icon = icon;
        this.description = description; this.pricingModel = pricingModel; this.startingPrice = startingPrice;
    }

    public static ServiceItem fromJson(JSONObject json) {
        String model = json.optString("pricingModel", "fixed");
        Integer price = json.isNull("startingPrice") ? null : json.optInt("startingPrice");
        if ("quote".equals(model)) price = null;
        return new ServiceItem(json.optString("_id", ""), json.optString("name", "Service"),
                json.optString("category", "Other"), json.optString("icon", "🛠️"),
                json.optString("description", ""), model, price);
    }

    public String priceLabel() {
        if (startingPrice == null) return "Quotation required";
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-BD"));
        return "Starting estimate · ৳" + format.format(startingPrice);
    }
}
