package aisafe.flights.infrastructure.http;

import java.util.List;

final class UrlLists {
    private UrlLists() {
    }

    static List<String> clean(List<String> urls) {
        if (urls == null) {
            return List.of();
        }
        return urls.stream()
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url)
                .toList();
    }
}
