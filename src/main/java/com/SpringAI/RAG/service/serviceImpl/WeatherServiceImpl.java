package com.SpringAI.RAG.service.serviceImpl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WeatherServiceImpl {

    private final ChatClient chatClient;
    private final WeatherTools weatherTools;

    public WeatherServiceImpl(ChatClient.Builder chatClient, WeatherTools weatherTools) {
        this.chatClient = chatClient.build();
        this.weatherTools = weatherTools;
    }

    public String getCurrentWeatherForCity(String city) {
        String prompt = city != null && !city.trim().isEmpty() ? "Get current weather for " + city : "Get current weather for Bangalore";
        return chatClient.prompt()
                .tools(weatherTools)
                .user(prompt)
                .call()
                .content();
    }

    public String getWeatherForecast(String city) {
        String prompt = city != null && !city.trim().isEmpty() ? "Get 5-day weather forecast for " + city : "Get 5-day weather forecast for Bangalore";
        return chatClient.prompt()
                .tools(weatherTools)
                .user(prompt)
                .call()
                .content();
    }

    public String getAirQualityInfo(String city) {
        String prompt = city != null && !city.trim().isEmpty() ? "Get air quality information for " + city : "Get air quality information for Bangalore";
        return chatClient.prompt()
                .tools(weatherTools)
                .user(prompt)
                .call()
                .content();
    }

    public String compareWeatherBetweenCities(String city1, String city2) {
        String prompt = String.format("Compare weather between %s and %s", city1 != null ? city1 : "Bangalore", city2 != null ? city2 : "Delhi");
        return chatClient.prompt()
                .tools(weatherTools)
                .user(prompt)
                .call()
                .content();
    }

    public String getWeatherByCoordinates(double latitude, double longitude) {
        String prompt = String.format("Get weather for coordinates latitude %.6f and longitude %.6f", latitude, longitude);
        return chatClient.prompt()
                .tools(weatherTools)
                .user(prompt)
                .call()
                .content();
    }

    public String processWeatherQuery(String query) {
        return chatClient.prompt()
                .tools(weatherTools)
                .user(query)
                .call()
                .content();
    }
}
