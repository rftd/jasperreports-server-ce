/*
 * OpenJRS Server - JasperReports 7 compatibility.
 *
 * JasperReports 7.1.0 made the corresponding exception message keys private.
 * Keys verified against the JasperReports 7.1.0 upstream implementation.
 * Preserve REST classification of page-range and crosstab limit failures.
 */
package com.jaspersoft.jasperserver.remote.utils;

/**
 * Exception message identifiers no longer publicly exported by JasperReports.
 */
public final class JasperReportsExceptionKeys {
    private static final String PAGE_INDEX_OUT_OF_RANGE = "export.common.page.index.out.of.range";
    private static final String START_PAGE_INDEX_OUT_OF_RANGE = "export.common.start.page.index.out.of.range";
    private static final String END_PAGE_INDEX_OUT_OF_RANGE = "export.common.end.page.index.out.of.range";
    private static final String BUCKET_MEASURE_LIMIT = "crosstabs.bucket.measure.limit";

    private JasperReportsExceptionKeys() {
    }

    public static boolean isPageIndexOutOfRange(String messageKey) {
        return PAGE_INDEX_OUT_OF_RANGE.equals(messageKey)
                || START_PAGE_INDEX_OUT_OF_RANGE.equals(messageKey)
                || END_PAGE_INDEX_OUT_OF_RANGE.equals(messageKey);
    }

    public static boolean isBucketMeasureLimit(String messageKey) {
        return BUCKET_MEASURE_LIMIT.equals(messageKey);
    }
}
