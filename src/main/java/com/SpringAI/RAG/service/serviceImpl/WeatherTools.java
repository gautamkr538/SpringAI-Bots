package com.SpringAI.RAG.service.serviceImpl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class WeatherTools {

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5";
    private static final String GEO_URL = "https://api.openweathermap.org/geo/1.0";
    private static final String API_KEY = "your-openweather-api-key";
    private final RestClient restClient;

    public WeatherTools(RestClient.Builder builder) {
        this.restClient = builder
                .defaultHeader("Accept", "application/json")
                .build();
    }

    // Data models
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentWeather(
            @JsonProperty("main") Main main,
            @JsonProperty("weather") List<Weather> weather,
            @JsonProperty("wind") Wind wind,
            @JsonProperty("clouds") Clouds clouds,
            @JsonProperty("visibility") Integer visibility,
            @JsonProperty("name") String name,
            @JsonProperty("sys") Sys sys
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Main(
                @JsonProperty("temp") Double temp,
                @JsonProperty("feels_like") Double feelsLike,
                @JsonProperty("temp_min") Double tempMin,
                @JsonProperty("temp_max") Double tempMax,
                @JsonProperty("pressure") Integer pressure,
                @JsonProperty("humidity") Integer humidity
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Weather(
                @JsonProperty("main") String main,
                @JsonProperty("description") String description,
                @JsonProperty("icon") String icon
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Wind(
                @JsonProperty("speed") Double speed,
                @JsonProperty("deg") Integer deg,
                @JsonProperty("gust") Double gust
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Clouds(@JsonProperty("all") Integer all) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Sys(
                @JsonProperty("sunrise") Long sunrise,
                @JsonProperty("sunset") Long sunset
        ) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastResponse(
            @JsonProperty("list") List<ForecastItem> list,
            @JsonProperty("city") City city
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ForecastItem(
                @JsonProperty("dt") Long dt,
                @JsonProperty("main") CurrentWeather.Main main,
                @JsonProperty("weather") List<CurrentWeather.Weather> weather,
                @JsonProperty("wind") CurrentWeather.Wind wind,
                @JsonProperty("dt_txt") String dtTxt
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record City(@JsonProperty("name") String name, @JsonProperty("country") String country) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AirPollution(
            @JsonProperty("list") List<AirData> list
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AirData(
                @JsonProperty("main") AirMain main,
                @JsonProperty("components") Map<String, Double> components
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AirMain(@JsonProperty("aqi") Integer aqi) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeoLocation(
            @JsonProperty("name") String name,
            @JsonProperty("lat") Double lat,
            @JsonProperty("lon") Double lon,
            @JsonProperty("country") String country,
            @JsonProperty("state") String state
    ) {}

    // Tool methods
    @Tool(description = "Get current weather for Bangalore or any city")
    public String getCurrentWeather(
            @ToolParam(description = "City name (default: Bangalore)") String city) {

        if (city == null || city.trim().isEmpty()) {
            city = "Bangalore,Karnataka,IN";
        }

        try {
            CurrentWeather weather = restClient.get()
                    .uri(BASE_URL + "/weather?q={city}&appid={apiKey}&units=metric", city, API_KEY)
                    .retrieve()
                    .body(CurrentWeather.class);

            if (weather != null) {
                return formatCurrentWeather(weather);
            }
            return "Unable to fetch weather data for " + city;
        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }

    @Tool(description = "Get 5-day weather forecast for Bangalore or any city")
    public String getWeatherForecast(
            @ToolParam(description = "City name (default: Bangalore)") String city) {

        if (city == null || city.trim().isEmpty()) {
            city = "Bangalore,Karnataka,IN";
        }

        try {
            ForecastResponse forecast = restClient.get()
                    .uri(BASE_URL + "/forecast?q={city}&appid={apiKey}&units=metric", city, API_KEY)
                    .retrieve()
                    .body(ForecastResponse.class);

            if (forecast != null) {
                return formatForecast(forecast);
            }
            return "Unable to fetch forecast data for " + city;
        } catch (Exception e) {
            return "Error fetching forecast: " + e.getMessage();
        }
    }

    @Tool(description = "Get air quality index for Bangalore or any city")
    public String getAirQuality(
            @ToolParam(description = "City name (default: Bangalore)") String city) {

        if (city == null || city.trim().isEmpty()) {
            city = "Bangalore";
        }

        try {
            // Use ParameterizedTypeReference for proper generic type handling
            List<GeoLocation> locations = restClient.get()
                    .uri(GEO_URL + "/direct?q={city}&limit=1&appid={apiKey}", city, API_KEY)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GeoLocation>>() {});

            if (locations != null && !locations.isEmpty()) {
                GeoLocation location = locations.getFirst();
                Double lat = location.lat();
                Double lon = location.lon();

                AirPollution airData = restClient.get()
                        .uri(BASE_URL + "/air_pollution?lat={lat}&lon={lon}&appid={apiKey}", lat, lon, API_KEY)
                        .retrieve()
                        .body(AirPollution.class);

                if (airData != null) {
                    return formatAirQuality(airData, city);
                }
            }
            return "Unable to fetch air quality data for " + city;
        } catch (Exception e) {
            return "Error fetching air quality: " + e.getMessage();
        }
    }

    @Tool(description = "Get weather by coordinates (latitude, longitude)")
    public String getWeatherByCoordinates(
            @ToolParam(description = "Latitude") double latitude,
            @ToolParam(description = "Longitude") double longitude) {

        try {
            CurrentWeather weather = restClient.get()
                    .uri(BASE_URL + "/weather?lat={lat}&lon={lon}&appid={apiKey}&units=metric",
                            latitude, longitude, API_KEY)
                    .retrieve()
                    .body(CurrentWeather.class);

            if (weather != null) {
                return formatCurrentWeather(weather);
            }
            return "Unable to fetch weather data for coordinates: " + latitude + ", " + longitude;
        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }

    @Tool(description = "Compare weather between two cities")
    public String compareWeather(
            @ToolParam(description = "First city name") String city1,
            @ToolParam(description = "Second city name") String city2) {

        String weather1 = getCurrentWeather(city1);
        String weather2 = getCurrentWeather(city2);

        return String.format("Weather Comparison:\n\n%s vs %s\n\n%s\n\n%s",
                city1, city2, weather1, weather2);
    }

    // Helper methods
    private String formatCurrentWeather(CurrentWeather weather) {
        return String.format("""
                Current Weather in %s:
                Temperature: %.1f°C (Feels like %.1f°C)
                Condition: %s
                Humidity: %d%%
                Pressure: %d hPa
                Wind: %.1f m/s
                Visibility: %s km
                """,
                weather.name(),
                weather.main().temp(),
                weather.main().feelsLike(),
                weather.weather().getFirst().description(),
                weather.main().humidity(),
                weather.main().pressure(),
                weather.wind().speed(),
                weather.visibility() != null ? weather.visibility() / 1000.0 : "N/A"
        );
    }

    private String formatForecast(ForecastResponse forecast) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("5-Day Forecast for %s:\n\n", forecast.city().name()));

        forecast.list().stream().limit(5).forEach(item -> {
            sb.append(String.format("""
                    Date: %s:
                    Temp: %.1f°C, %s
                    Humidity: %d%%, Wind: %.1f m/s
                    
                    """,
                    item.dtTxt(),
                    item.main().temp(),
                    item.weather().getFirst().description(),
                    item.main().humidity(),
                    item.wind().speed()
            ));
        });

        return sb.toString();
    }

    private String formatAirQuality(AirPollution airData, String city) {
        AirPollution.AirData data = airData.list().getFirst();
        String[] aqiLevels = {"Good", "Fair", "Moderate", "Poor", "Very Poor"};
        String aqiDescription = data.main().aqi() <= aqiLevels.length ?
                aqiLevels[data.main().aqi() - 1] : "Unknown";

        return String.format("""
                Air Quality in %s:
                AQI: %d (%s)
                CO: %.2f μg/m³
                NO2: %.2f μg/m³
                O3: %.2f μg/m³
                PM2.5: %.2f μg/m³
                PM10: %.2f μg/m³
                """,
                city,
                data.main().aqi(),
                aqiDescription,
                data.components().getOrDefault("co", 0.0),
                data.components().getOrDefault("no2", 0.0),
                data.components().getOrDefault("o3", 0.0),
                data.components().getOrDefault("pm2_5", 0.0),
                data.components().getOrDefault("pm10", 0.0)
        );
    }
}