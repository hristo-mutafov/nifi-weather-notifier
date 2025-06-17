# Nifi Weather Notifier

Weather Notifier is a NiFi-powered dataflow that monitors weather conditions for a specified location, calculates a rolling temperature average, and sends email alerts when significant changes (deltas) are detected.
It uses Redis for state management and is designed to showcase core NiFi features: API integration, data processing, custom scripting, and alerting.

## How it works

-   Scheduled Fetch:

The flow triggers on a schedule, fetching current weather data for the configured location.

-   Data Extraction:

The API response is processed to extract the current temperature value.

-   State Management in Redis:

The latest temperature readings are stored in Redis as a sliding window (last 5 values), enabling rolling average calculation.

-   Delta Evaluation:

A custom script calculates the average temperature and checks if it exceeds the configured threshold.

If the threshold is exceeded and an alert hasn't been sent yet today, the flow proceeds.

-   Email Notification:

An alert email is sent (one per day maximum) when a significant temperature change is detected, with details on the location and values.

## Prerequisites

-   Apache NiFi 2.x (tested on 2.3.0)

-   Docker (for Redis, optional)

-   A Redis server (local or remote)

-   Mailtrap.io (or any SMTP server, for email testing)

-   Weather API key ([e.g. from OpenWeatherMap](https://openweathermap.org/api))

## Setup Guide

### Start Redis

### 1. Import NIFI Flow

Import provided NiFi template (WeatherAlertFlow.json) into your Nifi

### 2. Start Redis

```
docker run --name redis -d -p 6379:6379 redis
```

### 3. Add Jedis and Required JARs to NiFi

-   Download [jedis-3.3.0.jar](https://repo1.maven.org/maven2/redis/clients/jedis/3.3.0/jedis-3.3.0.jar)

-   Download [commons-pool2-2.11.1.jar](https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.11.1/commons-pool2-2.11.1.jar)

-   Download [gson-2.8.9.jar](https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar)

-   Place all three in your NiFi /lib directory

-   Restart NiFi

### 4. Configure Mail (PutEmail) Processor

-   Use Mailtrap SMTP settings or your own provider

-   Set required fields to PutEmail Processor: host, port, username, password, from, to, subject, and message body

### 5. Run the Flow

-   Run Nifi

```
./nifi.sh start
./nifi.sh status
```

-   Start all processors or the process group

-   Check Mailtrap inbox for alerts

-   Use Redis CLI to inspect saved temperature values:

```
redis-cli lrange temps:<lat>:<lon> 0 4
```
