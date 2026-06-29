# hmpps-visit-allocation-api

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-visit-allocation-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-visit-allocation-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-visit-allocation-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html)
[![GitHub Actions Pipeline](https://github.com/ministryofjustice/hmpps-visit-allocation-api/actions/workflows/pipeline.yml/badge.svg)](https://github.com/ministryofjustice/hmpps-visit-allocation-api/actions/workflows/pipeline.yml)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-visit-allocation-api/main/async-api.yml)

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
except for the visit-allocation-db and localstack (for AWS SNS/SQS services locally).

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
| localstack                 | 4566 |

### Populating local Db with data
Manually run in postgresql the ./resources/db/scripts/add_test_data.sql script to populate your local database with a few entries.

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
$ curl 'http://localhost:8079/info' -i -X GET
```

### Send event notifications locally (AWS SNS, SQS / LocalStack)
To help test notification events locally we can send events to localstack to replicate what NOMIS would do.

#### Step 1 - Start visit-allocation-api service locally
Follow steps for set-up / running at top of README

#### Step 2 - Install awscli (if not already installed)
```
brew install awscli
```

#### Step 3 - configure aws with dummy values (if not already configured)
```
aws configure
```
Put any dummy value for AWS_ACCESS_KEY=test and AWS_SECRET_KEY=test and eu-west-2 as default region.
The queueName is the value of hmpps.sqs.queues.prisonvisitsallocationevents.queueName on the application-<env>.yml file.
So the queue URL should be - http://localhost:4566/000000000000/{queueName}

#### Step 4 - Send a message to the queue. The below is a prisoner.conviction-status-updated event for prisoner A8713DY.
```
aws sqs send-message \
  --endpoint-url=http://localhost:4566 \
  --queue-url=http://localhost:4566/000000000000/sqs_hmpps_visits_allocation_events_queue \
  --message-body \
    '{"Type":"Notification", "Message": "{\"eventType\": \"prisoner-offender-search.prisoner.convicted-status-changed\", \"additionalInformation\": {\"nomsNumber\": \"A8713DY\", \"convictedStatus\": \"Convicted\"}}", "MessageId": "123"}'
```

If you are unsure about the queue name you can check the queue names using the following command and replace it in the above --queue-url value parameter
```
aws sqs list-queues --endpoint-url=http://localhost:4566
```

to check messages on the actual queues on localstack (queue and dead-letter queue), visit the following URL.
```
http://localhost.localstack.cloud:4566/_aws/sqs/messages?QueueUrl=http://sqs.eu-west-2.loca[…]000000000000/sqs_hmpps_visits_allocation_events_queue
http://localhost.localstack.cloud:4566/_aws/sqs/messages?QueueUrl=http://sqs.eu-west-2.loca[…]6/000000000000/sqs_hmpps_visits_allocation_events_dlq
```

### Periodic Visit allocation cronjob
The endpoint to allocate visit allocations for active prisons is triggered by a Kubernetes Cronjob that is configured to run once a day. 

The endpoint is protected from being called externally, so it is not possible to call the endpoint directly. Only the Cronjob can call the endpoint.

So to manually trigger the periodic visit allocation, we just trigger the Cronjob

Below is an example to trigger the cronjob manually -
```
kubectl create job --from=cronjob/hmpps-visit-allocation-api-allocate-visit-orders-job <job-name> -n <namespace>
```

### Syncing with NOMIS
This service has a 2-way sync with NOMIS. 

##### NOMIS -> DPS
When visit order data on NOMIS changes for a prisoner, a request will be made to this service, to sync the change so that 
DPS can be aligned with NOMIS. See the NomisController.kt for more information.

##### DPS -> NOMIS
When prisoner balance changes and the prison is owned by this service (enabled in prison DB table), an event will be raised for 
NOMIS to listen and consume. Event name: "prison-visit-allocation.adjustment.created". The event will contain a prisoner ID and 
adjustment ID which NOMIS can then use to call via the NomisController "getPrisonerAdjustment" endpoint to re-sync with DPS.

### SQS
This service uses the HMPPS SQS library to consume events from multiple queues. The queues are configured in application.yml, 
including their retry configurations, which combine with settings in the cloud-platform-environments repository under our namespace, to 
create the full retry policy.

#### 1. Nightly Prison Visit Allocation Queue
This queue is used to receive events from the Cronjob that is responsible for allocating visit orders for active prisons. One message 
is processed per prison per day. To see the cron configuration for this queue, see the 'periodic visit allocation cronjob' section above. 
The DLQ for this queue is terminal, with no retry mechanism.

#### 2. Prisoner Retry Queue
During the nightly visit allocation process, if a prisoner fails to be allocated, a message is placed on this queue. This queue 
is used to retry the allocation process for a prisoner without failing the entire prison job. The DLQ for this queue is terminal, with no retry mechanism.

#### 3. Event Queue
This queue is used to receive events from the 'domain events' topic. The "domain events" we subscribe to can be found in the 
cloud-platform-environments repository, under our namespace.

The queue processes domain events from other DPS APIs and NOMIS.

##### Batch Jobs
This queue has a custom batch job configured to allow for DLQ retries. This allows us to reprocess messages that
failed due to transient errors without needing to rely on a robust retry/visibility timeout mechanism in the main queue.

Note: We can't rely on the generic-service "retryDlqCronJob" as this enables DLQ retries for all queues.
