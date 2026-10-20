package com.logistics.shipment.eligibility.util;

import com.logistics.shipment.eligibility.model.GeoLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GeoDistanceCalculatorTest {

    @Test
    @DisplayName("Identical coordinates return 0.0 km")
    void calculateDistance_sameCoordinates_returnsZero() {
        Double distance = GeoDistanceCalculator.calculateDistanceKm(10.7769, 106.7009, 10.7769, 106.7009);

        assertThat(distance).isNotNull();
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Short city distance between landmarks in HCMC is accurate")
    void calculateDistance_shortDistance_accurate() {
        // Ben Thanh Market: 10.7725, 106.6980
        // Notre Dame Cathedral: 10.7798, 106.6990
        // Actual great-circle distance is approx 0.82 km
        Double distance = GeoDistanceCalculator.calculateDistanceKm(10.7725, 106.6980, 10.7798, 106.6990);

        assertThat(distance).isNotNull();
        assertThat(distance).isCloseTo(0.82, offset(0.05));
    }

    @Test
    @DisplayName("Long-distance calculation between HCMC and Hanoi is accurate")
    void calculateDistance_longDistance_accurate() {
        // HCMC: 10.7769, 106.7009
        // Hanoi: 21.0285, 105.8542
        // Actual great-circle distance is approx 1138 km
        Double distance = GeoDistanceCalculator.calculateDistanceKm(10.7769, 106.7009, 21.0285, 105.8542);

        assertThat(distance).isNotNull();
        assertThat(distance).isCloseTo(1138.0, offset(10.0));
    }

    @ParameterizedTest
    @CsvSource({
            "90.1, 106.7, 'Latitude > 90'",
            "-90.1, 106.7, 'Latitude < -90'",
            "10.7, 180.1, 'Longitude > 180'",
            "10.7, -180.1, 'Longitude < -180'"
    })
    @DisplayName("Out-of-bounds coordinates return null")
    void calculateDistance_outOfBoundsCoordinates_returnsNull(double lat, double lon, String reason) {
        Double distance = GeoDistanceCalculator.calculateDistanceKm(lat, lon, 10.7769, 106.7009);
        assertThat(distance).isNull();

        Double distanceRev = GeoDistanceCalculator.calculateDistanceKm(10.7769, 106.7009, lat, lon);
        assertThat(distanceRev).isNull();
    }

    @Test
    @DisplayName("Null, NaN, or Infinite coordinates return null safely")
    void calculateDistance_nullOrInvalidSpecialValues_returnsNull() {
        assertThat(GeoDistanceCalculator.calculateDistanceKm(null, 106.7, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(10.7, null, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(Double.NaN, 106.7, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(Double.POSITIVE_INFINITY, 106.7, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(Double.NEGATIVE_INFINITY, 106.7, 10.7, 106.7)).isNull();
    }

    @Test
    @DisplayName("GeoLocation objects calculate distance correctly")
    void calculateDistance_withGeoLocationObjects() {
        GeoLocation p1 = GeoLocation.of(10.7725, 106.6980);
        GeoLocation p2 = GeoLocation.of(10.7798, 106.6990);

        Double distance = GeoDistanceCalculator.calculateDistanceKm(p1, p2);

        assertThat(distance).isNotNull();
        assertThat(distance).isCloseTo(0.82, offset(0.05));
    }

    @Test
    @DisplayName("GeoLocation parsing valid and invalid strings")
    void geoLocation_parse() {
        GeoLocation loc = GeoLocation.parse("10.7769, 106.7009");
        assertThat(loc).isNotNull();
        assertThat(loc.getLatitude()).isEqualTo(10.7769);
        assertThat(loc.getLongitude()).isEqualTo(106.7009);

        assertThat(GeoLocation.parse(null)).isNull();
        assertThat(GeoLocation.parse("")).isNull();
        assertThat(GeoLocation.parse("District 1, HCMC")).isNull(); // Non-numeric text
        assertThat(GeoLocation.parse("95.0, 106.0")).isNull(); // Invalid lat > 90
    }
}
