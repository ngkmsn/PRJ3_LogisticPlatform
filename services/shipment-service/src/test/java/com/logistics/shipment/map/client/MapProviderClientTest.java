package com.logistics.shipment.map.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.shipment.map.config.MapConfiguration;
import com.logistics.shipment.map.exception.MapConfigurationException;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import com.logistics.shipment.map.exception.MapRateLimitException;
import com.logistics.shipment.map.exception.MapServiceTimeoutException;
import com.logistics.shipment.map.exception.MapServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapProviderClientTest {

    private MockMapProviderClient mockProvider;
    private OsrmMapProviderClient osrmProvider;
    private GenericRestMapProviderClient genericRestProvider;
    private MapConfiguration config;
    private ObjectMapper objectMapper;
    private HttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        config = new MapConfiguration();
        config.setServiceUrl("http://localhost:5000");
        config.setApiKey("test-api-key");
        config.setTimeoutMs(1000);

        mockProvider = new MockMapProviderClient();

        osrmProvider = new OsrmMapProviderClient();
        osrmProvider.setConfiguration(config);
        osrmProvider.setObjectMapper(objectMapper);
        mockHttpClient = mock(HttpClient.class);
        osrmProvider.setHttpClient(mockHttpClient);

        genericRestProvider = new GenericRestMapProviderClient();
        genericRestProvider.setConfiguration(config);
        genericRestProvider.setObjectMapper(objectMapper);
        genericRestProvider.setHttpClient(mockHttpClient);
    }

    @Test
    @DisplayName("MockProvider: Calculate route successfully with valid coordinates")
    void mockProviderCalculatesRouteSuccessfully() {
        MapCoordinatesDto origin = MapCoordinatesDto.of(21.0285, 105.8542); // Hanoi
        MapCoordinatesDto dest = MapCoordinatesDto.of(21.0362, 105.7905);   // Cau Giay
        RouteDirectionRequest request = new RouteDirectionRequest(origin, dest, "MOTORCYCLE");

        RouteDirectionResponse response = mockProvider.getDirections(request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDistanceMeters()).isGreaterThan(5000.0);
        assertThat(response.getDurationSeconds()).isGreaterThan(300L);
        assertThat(response.getGeometry()).isNotEmpty();
        assertThat(response.getSteps()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("MOCK");
        assertThat(response.isFallback()).isFalse();
    }

    @Test
    @DisplayName("MockProvider: Rejects invalid coordinates")
    void mockProviderRejectsInvalidCoordinates() {
        MapCoordinatesDto invalidOrigin = MapCoordinatesDto.of(120.0, 105.8542); // Lat > 90
        MapCoordinatesDto dest = MapCoordinatesDto.of(21.0362, 105.7905);
        RouteDirectionRequest request = new RouteDirectionRequest(invalidOrigin, dest);

        assertThatThrownBy(() -> mockProvider.getDirections(request))
                .isInstanceOf(MapInvalidRequestException.class)
                .hasMessageContaining("Invalid or missing origin coordinates");
    }

    @Test
    @DisplayName("MockProvider: Geocoding Vietnam landmarks and coordinates")
    void mockProviderGeocode() {
        GeocodeResponse hanoi = mockProvider.geocode("Hoàn Kiếm, Hà Nội");
        assertThat(hanoi.isSuccess()).isTrue();
        assertThat(hanoi.getLatitude()).isEqualTo(21.0288);
        assertThat(hanoi.getLongitude()).isEqualTo(105.8525);

        GeocodeResponse hcm = mockProvider.geocode("Quận 1, TP. Hồ Chí Minh");
        assertThat(hcm.isSuccess()).isTrue();
        assertThat(hcm.getLatitude()).isEqualTo(10.7756);
        assertThat(hcm.getLongitude()).isEqualTo(106.7004);

        GeocodeResponse coord = mockProvider.geocode("10.7769, 106.7009");
        assertThat(coord.isSuccess()).isTrue();
        assertThat(coord.getLatitude()).isEqualTo(10.7769);
        assertThat(coord.getLongitude()).isEqualTo(106.7009);

        assertThatThrownBy(() -> mockProvider.geocode(""))
                .isInstanceOf(MapInvalidRequestException.class);
    }

    @Test
    @DisplayName("OSRMProvider: Handles successful OSRM JSON response")
    void osrmProviderSuccess() throws Exception {
        String json = """
                {
                  "code": "Ok",
                  "routes": [
                    {
                      "distance": 8450.5,
                      "duration": 920.0,
                      "geometry": {
                        "coordinates": [
                          [105.8542, 21.0285],
                          [105.8200, 21.0300],
                          [105.7905, 21.0362]
                        ]
                      },
                      "legs": [
                        {
                          "steps": [
                            {
                              "name": "Trần Phú",
                              "distance": 1200.0,
                              "duration": 180.0,
                              "maneuver": { "type": "depart", "modifier": "" }
                            },
                            {
                              "name": "Kim Mã",
                              "distance": 7250.5,
                              "duration": 740.0,
                              "maneuver": { "type": "turn", "modifier": "left" }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(json);
        Mockito.doReturn(mockResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(21.0285, 105.8542),
                MapCoordinatesDto.of(21.0362, 105.7905)
        );

        RouteDirectionResponse response = osrmProvider.getDirections(req);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(8450.5);
        assertThat(response.getDurationSeconds()).isEqualTo(920L);
        assertThat(response.getGeometry()).hasSize(3);
        assertThat(response.getSteps()).hasSize(2);
        assertThat(response.getProvider()).isEqualTo("OSRM");
    }

    @Test
    @DisplayName("OSRMProvider: Handles timeout from OSRM service")
    void osrmProviderTimeout() throws Exception {
        Mockito.doThrow(new HttpTimeoutException("Connection timed out"))
                .when(mockHttpClient).send(any(HttpRequest.class), any());

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(21.0285, 105.8542),
                MapCoordinatesDto.of(21.0362, 105.7905)
        );

        assertThatThrownBy(() -> osrmProvider.getDirections(req))
                .isInstanceOf(MapServiceTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("OSRMProvider: Handles 429 rate limit and 500 error")
    void osrmProviderErrors() throws Exception {
        HttpResponse<String> rateLimitResp = mock(HttpResponse.class);
        when(rateLimitResp.statusCode()).thenReturn(429);
        when(rateLimitResp.body()).thenReturn("Too Many Requests");
        Mockito.doReturn(rateLimitResp).when(mockHttpClient).send(any(HttpRequest.class), any());

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(21.0285, 105.8542),
                MapCoordinatesDto.of(21.0362, 105.7905)
        );

        assertThatThrownBy(() -> osrmProvider.getDirections(req))
                .isInstanceOf(MapRateLimitException.class);

        HttpResponse<String> serverErrorResp = mock(HttpResponse.class);
        when(serverErrorResp.statusCode()).thenReturn(500);
        when(serverErrorResp.body()).thenReturn("Internal Server Error");
        Mockito.doReturn(serverErrorResp).when(mockHttpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> osrmProvider.getDirections(req))
                .isInstanceOf(MapServiceUnavailableException.class);
    }

    @Test
    @DisplayName("GenericRestProvider: Missing API key or service URL throws MapConfigurationException")
    void genericRestProviderMissingConfig() {
        config.setApiKey("");
        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(21.0285, 105.8542),
                MapCoordinatesDto.of(21.0362, 105.7905)
        );

        assertThatThrownBy(() -> genericRestProvider.getDirections(req))
                .isInstanceOf(MapConfigurationException.class)
                .hasMessageContaining("API key is missing or blank");

        config.setApiKey("valid-key");
        config.setServiceUrl("");
        assertThatThrownBy(() -> genericRestProvider.getDirections(req))
                .isInstanceOf(MapConfigurationException.class)
                .hasMessageContaining("Service URL is missing or blank");
    }
}
