# hmpps-visit-allocation-api

[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-visit-allocation-api)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-visit-allocation-api "Link to report")
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-visit-allocation-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html)
[![GitHub Actions Pipeline](https://github.com/ministryofjustice/hmpps-visit-allocation-api/actions/workflows/pipeline.yml/badge.svg)](https://github.com/ministryofjustice/hmpps-visit-allocation-api/actions/workflows/pipeline.yml)

This is a Spring Boot application, written in Kotlin, providing visit allocation services. Used by the Visits team.

## Building

To build the project (without tests):
```
./gradlew clean build -x test
```

## Testing

Run:
```
./gradlew test 
```

## Running

The hmpps-visit-allocation-api uses the deployed dev environment to connect to most of the required services,
with an exception of the visit-allocation-db.

To run the hmpps-visit-allocation-api, first start the required local services using docker-compose.

```
docker-compose up -d
```
Next create a .env file at the project root and add 2 secrets to it
```
SYSTEM_CLIENT_ID="get from kubernetes secrets for dev namespace"
SYSTEM_CLIENT_SECRET"get from kubernetes secrets for dev namespace"
```

Then create a Spring Boot run configuration with active profile of 'dev' and set an environments file to the
`.env` file we just created. Run the service in your chosen IDE.

Ports

| Service                    | Port |  
|----------------------------|------|
| hmpps-visit-allocation-api | 8079 |
| visit-allocation-db        | 5445 |

### Populating local Db with data
TODO

### Auth token retrieval

To create a Token via curl (local):
```
curl --location --request POST "https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token?grant_type=client_credentials" --header "Authorization: Basic $(echo -n {Client}:{ClientSecret} | base64)"
```

or via postman collection using the following authorisation urls:
```
Grant type: Client Credentials
Access Token URL: https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token
Client ID: <get from kubernetes secrets for dev namespace>
Client Secret: <get from kubernetes secrets for dev namespace>
Client Authentication: "Send as Basic Auth Header"
```

Call info endpoint:
```
$ curl 'http://localhost:8081/info' -i -X GET
```
