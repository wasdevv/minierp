package dev.wasdevv.minierp.service;

final class SearchTerms {

    private SearchTerms() {
    }

    /** Makes {@code %}, {@code _} and {@code \} match literally in a LIKE ... ESCAPE '\' pattern. */
    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
