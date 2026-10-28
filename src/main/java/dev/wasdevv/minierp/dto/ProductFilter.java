package dev.wasdevv.minierp.dto;

/** The chips above the product list. Unknown values from the query string fall back to {@link #ALL}. */
public enum ProductFilter {
    ALL("all"),
    LOW_STOCK("low-stock"),
    INACTIVE("inactive");

    private final String param;

    ProductFilter(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    public static ProductFilter fromParam(String value) {
        for (ProductFilter filter : values()) {
            if (filter.param.equals(value)) {
                return filter;
            }
        }
        return ALL;
    }
}
