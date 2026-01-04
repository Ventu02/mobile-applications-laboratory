# Mobile Applications Laboratory Project

This repository contains the **Mobile Applications Laboratory (LAM)** project developed by me. The application is a personal physical activity tracker designed for Android devices, capable of monitoring various types of activities, providing statistics, and automatically detecting movement in the background.

## Project Description

The goal of the project is to provide a simple and effective tool for tracking daily habits. Users can manually record specific activities or rely on the application's background service to detect movement based on speed.

The application focuses on the 6 most common activity categories:
* Non-sporting activities (sedentary, house chores, etc.)
* Car
* Walking
* Running
* Bike
* Other sports

## Main Features

### 1. Activity Tracking
From the main dashboard, users can start tracking a specific activity. The interface displays:
* A **Timer** showing the duration of the session.
* A **Step Counter** (exclusive to "Walking" and "Running" modes) utilizing the device's step detector sensor.
* A **Stop** button to save the session to the local database.

### 2. Statistics Dashboard
A dedicated "Statistics" page provides visual insights into the user's performance:
* **Pie Chart**: Visualizes the distribution of recorded activities for the current month.
* **Line Chart**: Displays the total daily steps for the days of the current month.
* **Activity History**: A `RecyclerView` list showing details (date, duration, steps) of all past activities.
* **Filtering**: Users can filter the history list by specific activity types via a dropdown spinner.

### 3. Background Activity Recognition
The app includes a background service that monitors the user's speed via GPS to infer the current activity:
* **0 - 1 km/h**: Non-sporting activities
* **1 - 7 km/h**: Walking
* **7 - 20 km/h**: Running
* **> 20 km/h**: Car

When a change is detected, the app sends a notification ("New activity detected") allowing the user to confirm or deny the activity. To prevent notification spam, a 15-minute pause is implemented after an activity is set.

## Technical Implementation

The project uses modern Android development standards and libraries:

* **Database**: **Room Database** is used for persistent local storage. It includes Entities, DAOs for data access, and TypeConverters to handle Date/Long conversions.
* **Concurrency**: **Coroutines** are used to perform database operations and data processing off the main thread, ensuring a smooth UI experience.
* **Sensors & Permissions**:
    * `ACTIVITY_RECOGNITION`: For step counting.
    * `LOCATION` (GPS): For speed calculation in the background service.
* **UI Components**: `RecyclerView`, `Spinner`, and charting libraries for the statistics visualization.
