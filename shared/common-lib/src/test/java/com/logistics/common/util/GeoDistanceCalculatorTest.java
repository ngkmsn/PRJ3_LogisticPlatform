package com.logistics.common.util;

import com.logistics.common.dto.MapCoordinatesDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoDistanceCalculatorTest {

    @Test
    @DisplayName("Should return 0.0 for identical coordinates")
    void sameCoordinatesReturnZero() {
        Double dist = GeoDistanceCalculator.calculateDistanceKm(10.7769, 106.7009, 10.7769, 106.7009);
        assertThat(dist).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate realistic distance between Hanoi and HCMC")
    void hanoiToHcmcDistance() {
        // Hanoi (21.0285, 105.8542) to HCMC (10.7769, 106.7009) is ~1130-1160 km
        Double dist = GeoDistanceCalculator.calculateDistanceKm(21.0285, 105.8542, 10.7769, 106.7009);
        assertThat(dist).isNotNull();
        assertThat(dist).isCloseTo(1138.0, within(20.0));
    }

    @Test
    @DisplayName("Should return null for invalid or null coordinates")
    void invalidCoordinatesReturnNull() {
        assertThat(GeoDistanceCalculator.calculateDistanceKm(null, 106.7, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(10.7, null, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(95.0, 106.7, 10.7, 106.7)).isNull();
        assertThat(GeoDistanceCalculator.calculateDistanceKm(10.7, 190.0, 10.7, 106.7)).isNull();
    }

    @Test
    @DisplayName("Should calculate distance using MapCoordinatesDto")
    void calculateWithDto() {
        MapCoordinatesDto p1 = MapCoordinatesDto.of(10.7769, 106.7009);
        MapCoordinatesDto p2 = MapCoordinatesDto.of(10.7800, 106.7050);
        Double dist = GeoDistanceCalculator.calculateDistanceKm(p1, p2);
        assertThat(dist).isNotNull();
        assertThat(dist).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("MapCoordinatesDto parse and validation")
    void parseAndValidateCoordinates() {
        MapCoordinatesDto valid = MapCoordinatesDto.parse(" 10.7769, 106.7009 ");
        assertThat(valid).isNotNull();
        assertThat(valid.isValid()).isTrue();
        assertThat(valid.getLatitude()).isEqualTo(10.7769);
        assertThat(valid.getLongitude()).isEqualTo(106.7009);

        assertThat(MapCoordinatesDto.parse("invalid,text")).isNull();
        assertThat(MapCoordinatesDto.parse("100.0, 50.0")).isNull(); // Lat > 90
        assertThat(MapCoordinatesDto.parse(null)).isNull();
        assertThat(MapCoordinatesDto.parse("")).isNull();
    }
}
