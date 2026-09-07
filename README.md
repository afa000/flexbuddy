# FlexBuddy

FlexBuddy is a shift and earnings tracker for delivery drivers. It can read shift details from screenshots with OCR, lets you review the detected information before saving it, and summarizes your work and earnings history.

## [Open the live app](https://flexbuddy.onrender.com)

The app is hosted on Render. Because it uses a free web service, the first request after a period of inactivity may take up to about 50 seconds while the service wakes up.

## Features

- Import shift details from PNG or JPEG screenshots
- Review and correct OCR results before saving
- View, edit, and delete saved shifts
- Track total earnings, base pay, tips, time worked, and average earnings
- Switch between light and dark themes
- Store shift data in PostgreSQL

## Built with

- Java 21 and Spring Boot
- Spring MVC, Thymeleaf, and Spring Data JPA
- PostgreSQL
- Tess4J and Tesseract OCR
- HTML, CSS, and JavaScript
- Docker and Render

## Run locally

You need Java 21, PostgreSQL, and Tesseract OCR installed.

Create a PostgreSQL database named `flexbuddy`, then provide the connection settings as environment variables. The defaults are:

```text
DB_URL=jdbc:postgresql://localhost:5432/flexbuddy
DB_USER=flexbuddy
DB_PASSWORD=flexbuddy
```

From the application directory, start the app with:

```powershell
cd flexbuddy
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080>.

## Run the tests

```powershell
cd flexbuddy
.\mvnw.cmd test
```

## Deployment

The root [`render.yaml`](render.yaml) defines the Render web service and PostgreSQL database. Pushes to `main` are automatically deployed through the connected Render Blueprint.
