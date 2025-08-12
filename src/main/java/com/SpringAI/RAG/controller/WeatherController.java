package com.SpringAI.RAG.controller;

import com.SpringAI.RAG.service.serviceImpl.WeatherServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    private final WeatherServiceImpl weatherService;

    public WeatherController(WeatherServiceImpl weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ResponseEntity<String> getCurrentWeather(
            @RequestParam(required = false, defaultValue = "Bangalore") String city) {
        try {
            String weather = weatherService.getCurrentWeatherForCity(city);
            return ResponseEntity.ok(weather);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/forecast")
    public ResponseEntity<String> getWeatherForecast(
            @RequestParam(required = false, defaultValue = "Bangalore") String city) {
        try {
            String forecast = weatherService.getWeatherForecast(city);
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/air-quality")
    public ResponseEntity<String> getAirQuality(
            @RequestParam(required = false, defaultValue = "Bangalore") String city) {
        try {
            String airQuality = weatherService.getAirQualityInfo(city);
            return ResponseEntity.ok(airQuality);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/compare")
    public ResponseEntity<String> compareWeather(
            @RequestParam(required = false, defaultValue = "Bangalore") String city1,
            @RequestParam(required = false, defaultValue = "Delhi") String city2) {
        try {
            String comparison = weatherService.compareWeatherBetweenCities(city1, city2);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/coordinates")
    public ResponseEntity<String> getWeatherByCoordinates(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        try {
            String weather = weatherService.getWeatherByCoordinates(latitude, longitude);
            return ResponseEntity.ok(weather);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/query")
    public ResponseEntity<String> processWeatherQuery(@RequestBody String query) {
        try {
            String response = weatherService.processWeatherQuery(query);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Bangalore specific endpoints
    @GetMapping("/bangalore/current")
    public ResponseEntity<String> getBangaloreWeather() {
        return getCurrentWeather("Bangalore,Karnataka,IN");
    }

    @GetMapping("/bangalore/forecast")
    public ResponseEntity<String> getBangaloreForecast() {
        return getWeatherForecast("Bangalore,Karnataka,IN");
    }

    @GetMapping("/bangalore/air-quality")
    public ResponseEntity<String> getBangaloreAirQuality() {
        return getAirQuality("Bangalore");
    }
}
