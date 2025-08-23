# Spring AI RAG Workflow

This repository contains a **Spring Boot + Selenium + Jsoup + Spring AI powered application** that demonstrates **Retrieval-Augmented Generation (RAG)** for AI-driven features, including chatbots, blog generation, code assistance, weather integration, multimedia and Website crawl services and query over crawled data.

---

## 📌 Overview
This project integrates:
- **Spring AI** for LLM-based interactions  
- **Vector Database** for semantic embeddings and similarity search  
- **RESTful APIs** to expose chatbot, weather (Current), content-generation and Web crawl features
- **External Services** like OpenAI APIs, Selenium for data crawling, and external weather APIs  

The following diagram provides a complete architecture flow:

<img width="3840" height="1823" alt="SpringAI_Project_WorkFlow_Chart" src="https://github.com/user-attachments/assets/bb996dbd-31a0-4965-b583-3346ac5d8139" />

---

## 🚀 Features

### **1️⃣ Chat Controller**
- **pdfStore** – Stores and processes PDFs for embedding  
- **chatBot** – AI-powered conversational bot  
- **blogGenerationBot** – AI blog/content generation  
- **imageDetectionBot** – AI-based image analysis  
- **imageGenerationBot** – AI-driven image creation  
- **voiceGenerationBot** – AI voice/speech synthesis  
- **codeBot** – Code-related assistance
- **webCrawlBot** – Crawl any website URL  

### **2️⃣ Weather Controller**
Provides weather-related endpoints:
- **current** – Get current weather by location  
- **forecast** – Get weather forecast  
- **air-quality** – Check air quality data  
- **compare** – Compare weather between cities  
- **coordinates** – Search weather by coordinates  
- **query** – General query endpoint for crawled data 

---

## 🛠 Tech Stack
- **Backend Framework:** Spring Boot 3, Spring AI  
- **Database:** PostgreSQL / MySQL (configurable)  
- **Vector Database:** pgVector, Milvus, etc.  
- **External APIs:** OpenAI API, Weather APIs  
- **Utilities:** Selenium, Jsoup for data crawling  
- **Build Tools:** Maven, Docker Compose (optional for local setup)  

---

## 📂 Project Structure

| **Layer**           | **Description** |
|---------------------|-----------------|
| **API Layer**       | `ChatController`, `WeatherController` exposing REST endpoints |
| **Service Layer**   | Handles business logic (`ChatService`, `WeatherService`, `WebDataService`) |
| **Utilities & Config** | Utility classes, embedding configs, and Swagger for API docs |
| **External & Infra**   | External API calls, Vector DB, Database operations |
| **Config Layer**    | `pom.xml`, `compose.yaml`, and application configuration files |

---

## ⚡ How to Run

### **1. Clone the repository**
```bash
git clone https://github.com/gautamkr538/SpringAI-Bots.git
cd spring-ai-rag

2. Configure Environment
Update your application.properties:

properties
Copy
Edit
openai.api.key=your_openai_api_key
vector.db.url=your_vector_db_url
weather.api.key=your_weather_api_key

3. Build and Run
bash
Copy
Edit
./mvnw clean install
./mvnw spring-boot:run

4. Swagger API Docs
After the app runs, open in your browser:
bash
Copy
Edit
http://localhost:8080/swagger-ui.html

📜 Endpoints Summary
ChatController
/pdfStore
/chatBot
/blogGenerationBot
/imageDetectionBot
/imageGenerationBot
/voiceGenerationBot
/codeBot
/webCrawl

WeatherController

/current
/forecast
/air-quality
/compare
/coordinates
/query
/bangalore/current
/bangalore/forecast
/bangalore/air-quality

🔗 External Integrations
OpenAI / Spring AI – LLM and embedding services
Weather API with Tool – Real-time weather and forecast data
Vector DB – Storing and querying embeddings
Selenium / Jsoup – Web crawling utilities

🧩 Future Enhancements
Add authentication and API keys per user
Support for multi-modal embeddings
Extend analytics and monitoring with Prometheus + Grafana
